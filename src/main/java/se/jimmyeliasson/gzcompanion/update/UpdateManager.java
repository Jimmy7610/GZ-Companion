package se.jimmyeliasson.gzcompanion.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * THE single authoritative update state machine - Home and Inställningar both read the exact same
 * {@link Snapshot} from the exact same instance (via {@code CompanionSession#getUpdateManager()}),
 * never two separate updater implementations.
 *
 * <p>All networking/file I/O happens on a dedicated single-thread background executor - render
 * code only ever calls {@link #getSnapshot()}, a cheap volatile read, never networking itself.
 * Automatic checks are throttled to at most once per {@link #MIN_CHECK_INTERVAL}; a manual check
 * (the Inställningar "Sök efter uppdateringar" button, or Home's own retry) always bypasses that
 * cooldown. A metadata check only ever happens automatically - the installer is downloaded only
 * when the player explicitly presses "Ladda ner".
 */
public final class UpdateManager {
    private static final Duration MIN_CHECK_INTERVAL = Duration.ofMinutes(45);
    private static final Duration INITIAL_CHECK_DELAY = Duration.ofSeconds(20);

    public record Snapshot(
            UpdateState state,
            UpdateRelease availableUpdate,
            long downloadedBytes,
            long totalBytes,
            Path readyInstallerPath,
            String errorMessage
    ) {
        public static Snapshot idle() {
            return new Snapshot(UpdateState.IDLE, null, 0, 0, null, null);
        }
    }

    private final SemanticVersion currentVersion;
    private final UpdateChannel currentChannel;
    private final UpdateReleaseSource releaseSource;
    private final UpdateDownloader downloader;
    private final ScheduledExecutorService executor;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.idle());
    private final AtomicBoolean checkInProgress = new AtomicBoolean(false);
    private volatile Instant lastCheckAt;

    public UpdateManager(SemanticVersion currentVersion, UpdateChannel currentChannel,
                          UpdateReleaseSource releaseSource, UpdateDownloader downloader) {
        this.currentVersion = currentVersion;
        this.currentChannel = currentChannel;
        this.releaseSource = releaseSource;
        this.downloader = downloader;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "gzcompanion-updater");
            thread.setDaemon(true);
            return thread;
        });
        pruneAbandonedPartFilesQuietly();
    }

    /** Schedules the first automatic check shortly after startup, then a recurring one at the cooldown interval. */
    public void startBackgroundChecks() {
        executor.schedule(() -> checkNow(false), INITIAL_CHECK_DELAY.toSeconds(), TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(() -> checkNow(false),
                MIN_CHECK_INTERVAL.toMinutes(), MIN_CHECK_INTERVAL.toMinutes(), TimeUnit.MINUTES);
    }

    public Snapshot getSnapshot() {
        return snapshot.get();
    }

    /** @param manual true for a user-initiated "Sök efter uppdateringar" click - bypasses the cooldown. */
    public void checkNow(boolean manual) {
        if (!manual) {
            Instant last = lastCheckAt;
            if (last != null && Duration.between(last, Instant.now()).compareTo(MIN_CHECK_INTERVAL) < 0) {
                return;
            }
        }
        if (!checkInProgress.compareAndSet(false, true)) {
            return; // a check is already running - never run two concurrently
        }
        executor.execute(() -> {
            try {
                runCheck();
            } finally {
                checkInProgress.set(false);
            }
        });
    }

    private void runCheck() {
        lastCheckAt = Instant.now();
        snapshot.set(new Snapshot(UpdateState.CHECKING, null, 0, 0, null, null));
        try {
            List<GitHubRelease> releases = releaseSource.fetchReleases();
            Optional<UpdateRelease> found = UpdateChecker.findUpdate(releases, currentVersion, currentChannel, releaseSource);
            snapshot.set(found.isPresent()
                    ? new Snapshot(UpdateState.UPDATE_AVAILABLE, found.get(), 0, 0, null, null)
                    : new Snapshot(UpdateState.UP_TO_DATE, null, 0, 0, null, null));
        } catch (IOException e) {
            // GitHub unreachable/offline is normal and must never spam the player - quietly return
            // to idle, exactly as if no automatic check had run at all.
            snapshot.set(Snapshot.idle());
        }
    }

    /**
     * Begins downloading the currently-available update. No-op if there isn't one. Also allowed
     * from ERROR (retrying a previously-failed download) as long as the release that failed is
     * still known - the "Försök igen" button re-enters through here.
     */
    public void startDownload() {
        Snapshot current = snapshot.get();
        boolean allowedState = current.state() == UpdateState.UPDATE_AVAILABLE || current.state() == UpdateState.ERROR;
        if (!allowedState || current.availableUpdate() == null) return;

        UpdateRelease update = current.availableUpdate();
        long totalBytes = update.manifest().installerSizeBytes();
        snapshot.set(new Snapshot(UpdateState.DOWNLOADING, update, 0, totalBytes, null, null));

        executor.execute(() -> {
            UpdateDownloader.Result result = downloader.downloadAndVerify(update, (downloaded, total) ->
                    snapshot.set(new Snapshot(UpdateState.DOWNLOADING, update, downloaded, total, null, null)));

            switch (result) {
                case UpdateDownloader.Result.Success success ->
                        snapshot.set(new Snapshot(UpdateState.READY_TO_INSTALL, update, totalBytes, totalBytes, success.installerPath(), null));
                case UpdateDownloader.Result.Failure failure ->
                        snapshot.set(new Snapshot(UpdateState.ERROR, update, 0, 0, null, failure.reason()));
            }
        });
    }

    /**
     * Dismisses the current update-available/error notice back to a neutral state (the "Senare"
     * button) - never deletes an already-downloaded installer, so pressing "Ladda ner" again later
     * in the same session doesn't need to redownload it. A future check may re-surface the update.
     */
    public void dismiss() {
        Snapshot current = snapshot.get();
        if (current.state() == UpdateState.UPDATE_AVAILABLE || current.state() == UpdateState.ERROR) {
            snapshot.set(Snapshot.idle());
        }
    }

    /**
     * Re-verifies the downloaded installer and starts it in update-apply mode. Returns {@code true}
     * only if the installer process was actually started - the caller must request a Minecraft
     * shutdown ONLY when this returns {@code true}, never before (see
     * {@link UpdateInstallerLauncher}'s own ordering guarantee).
     */
    public boolean applyUpdate(long currentMinecraftPid) {
        Snapshot current = snapshot.get();
        boolean allowedState = current.state() == UpdateState.READY_TO_INSTALL || current.state() == UpdateState.ERROR;
        if (!allowedState || current.readyInstallerPath() == null) {
            return false;
        }
        snapshot.set(new Snapshot(UpdateState.STARTING_INSTALLER, current.availableUpdate(),
                current.downloadedBytes(), current.totalBytes(), current.readyInstallerPath(), null));

        UpdateInstallerLauncher.LaunchResult result = UpdateInstallerLauncher.launch(
                current.readyInstallerPath(),
                current.availableUpdate().manifest().installerSha256(),
                currentMinecraftPid,
                currentVersion.toDisplayString());

        if (result instanceof UpdateInstallerLauncher.LaunchResult.Started) {
            return true;
        }
        String reason = result instanceof UpdateInstallerLauncher.LaunchResult.Failed failed ? failed.reason() : "Okänt fel.";
        snapshot.set(new Snapshot(UpdateState.ERROR, current.availableUpdate(),
                current.downloadedBytes(), current.totalBytes(), current.readyInstallerPath(), reason));
        return false;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void pruneAbandonedPartFilesQuietly() {
        try {
            Path root = UpdatePaths.updatesRootDir();
            if (!Files.isDirectory(root)) return;
            try (var stream = Files.walk(root, 2)) {
                stream.filter(p -> p.toString().endsWith(".part")).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // Best-effort cleanup only.
                    }
                });
            }
        } catch (IOException ignored) {
            // Best-effort cleanup only - never fail startup over this.
        }
    }
}
