package se.jimmyeliasson.gzcompanion.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Structural guard for the permanent performance rule "FEATURE COUNT MAY GROW. IDLE COST MUST NOT
 * GROW WITH FEATURE COUNT." - enforced by scanning production source rather than merely asserting
 * runtime behavior, exactly like {@code LeaderboardFairPlayTest} does for fair-play guarantees.
 *
 * <p>Only the classes on {@link #ALLOWED_BASENAMES} may directly construct an {@code HttpClient},
 * a raw executor (via any {@code Executors.new*} factory or by constructing a {@code
 * ThreadPoolExecutor}/{@code ScheduledThreadPoolExecutor} directly), or a raw {@code Thread}. Every
 * other class - crucially, any FUTURE GameZone live-data module (Bounty Board, Chronicles, Live
 * Relics, Settlement Explorer, ...) - must go through the shared {@code GameZoneLiveDataRuntime}
 * (network/executor) or {@code LocalPersistenceRuntime} (local-disk executor) instead, per
 * docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 follow-up"/"correctness follow-up" sections.
 *
 * <p><b>2026-09-13 correctness follow-up.</b> The original version of this test only stopped a
 * feature from creating its OWN executor - it did not stop a feature from taking the APPROVED
 * shared runtime's raw {@code ExecutorService} and calling {@code execute}/{@code submit} on it
 * directly, which could grow that shared queue without bound (the runtimes' own scheduling
 * discipline lives in each FEATURE, e.g. {@code LeaderboardManager}'s active+pending scheme - the
 * runtime itself used to just hand out a raw, unbounded executor and trust every caller to behave).
 * This is now closed two ways: (1) {@code GameZoneLiveDataRuntime}/{@code LocalPersistenceRuntime}
 * no longer expose a raw executor at all - only a controlled {@code boolean submit(Runnable)} that
 * itself uses a BOUNDED queue and an explicit rejection policy; (2) no non-allow-listed file may
 * even reference the {@code ExecutorService}/{@code ScheduledExecutorService} types at all (a
 * feature that cannot name the type cannot hold or misuse a reference to one).
 *
 * <p>Deliberately narrow: the updater ({@code UpdateManager}/{@code GitHubReleaseSource}/{@code
 * HttpUpdateByteSource}) is explicitly allow-listed - it has genuinely different requirements
 * (GitHub host, redirect-following, multi-minute download timeouts, independent scheduling) and is
 * NOT meant to be folded into the GameZone live-data runtime. This test must never be widened to
 * block that legitimate, intentional separation.
 */
class ThreadingInfrastructureRulesTest {
    private static final Path SRC_ROOT = Path.of("src", "main", "java");

    /** The ONLY files allowed to directly construct an HttpClient, a raw executor, or a raw
     * Thread. Add a new entry here ONLY for genuinely new shared infrastructure (mirroring
     * GameZoneLiveDataRuntime/LocalPersistenceRuntime) - never to let an individual feature bypass
     * the shared runtimes. */
    private static final Set<String> ALLOWED_BASENAMES = Set.of(
            "GameZoneLiveDataRuntime.java",
            "LocalPersistenceRuntime.java",
            "UpdateManager.java",
            "GitHubReleaseSource.java",
            "HttpUpdateByteSource.java"
    );

    /** The two shared-runtime files - checked more specifically (not just "may use forbidden
     * patterns") to prove they expose ONLY the controlled {@code submit(...)} API, never a raw
     * executor getter. */
    private static final Set<String> RUNTIME_BASENAMES = Set.of(
            "GameZoneLiveDataRuntime.java",
            "LocalPersistenceRuntime.java"
    );

    private static final List<String> FORBIDDEN_PATTERNS = List.of(
            "HttpClient.newBuilder(",
            "HttpClient.newHttpClient(",
            "Executors.newSingleThreadExecutor",
            "Executors.newSingleThreadScheduledExecutor",
            "Executors.newFixedThreadPool",
            "Executors.newCachedThreadPool",
            "Executors.newScheduledThreadPool",
            "Executors.newWorkStealingPool",
            "new ThreadPoolExecutor(",
            "new ScheduledThreadPoolExecutor(",
            "new Thread(",
            // Fully-qualified/import forms only - deliberately NOT the bare word "ExecutorService",
            // which would false-positive on prose Javadoc (e.g. "no longer constructs its own
            // ExecutorService") in files that don't actually reference the type in real Java code.
            "java.util.concurrent.ExecutorService",
            "java.util.concurrent.ScheduledExecutorService"
    );

    private static List<Path> allJavaSources() throws IOException {
        assertTrue(Files.isDirectory(SRC_ROOT), "expected main source directory to exist: " + SRC_ROOT.toAbsolutePath());
        try (Stream<Path> files = Files.walk(SRC_ROOT)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static Path findByBasename(String basename) throws IOException {
        return allJavaSources().stream()
                .filter(p -> p.getFileName().toString().equals(basename))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected to find " + basename + " under " + SRC_ROOT));
    }

    @Test
    @DisplayName("Only approved infrastructure classes may construct an HttpClient, a raw executor, or a raw Thread - and no other class may even reference ExecutorService/ScheduledExecutorService")
    void onlyApprovedClassesOwnThreadsOrHttpClients() throws IOException {
        List<Path> javaFiles = allJavaSources();
        assertFalse(javaFiles.isEmpty(), "expected to find production Java sources to scan");

        for (Path file : javaFiles) {
            String basename = file.getFileName().toString();
            if (ALLOWED_BASENAMES.contains(basename)) continue;

            String content = Files.readString(file);
            for (String forbidden : FORBIDDEN_PATTERNS) {
                assertFalse(content.contains(forbidden),
                        file + " directly uses \"" + forbidden + "\" - route through GameZoneLiveDataRuntime "
                                + "(GameZone public web features) or LocalPersistenceRuntime (local-disk "
                                + "persistence), or add this file to ThreadingInfrastructureRulesTest's "
                                + "allow-list ONLY if it is genuinely new shared infrastructure.");
            }
        }
    }

    @Test
    @DisplayName("The shared runtimes expose only the controlled submit(...) API, never a raw executor getter")
    void sharedRuntimesExposeOnlyControlledSubmission() throws IOException {
        for (String basename : RUNTIME_BASENAMES) {
            Path file = findByBasename(basename);
            String content = Files.readString(file);

            assertTrue(content.contains("public boolean submit("),
                    file + " must expose the controlled boolean submit(Runnable) API");
            assertFalse(content.contains("public ExecutorService"),
                    file + " must never publicly return a raw ExecutorService");
            assertFalse(content.contains("public ThreadPoolExecutor"),
                    file + " must never publicly return a raw ThreadPoolExecutor");
            // Looks for an actual instantiation (a "(" right after the name), not just the class
            // name appearing in explanatory Javadoc prose (e.g. "never uses CallerRunsPolicy").
            assertFalse(content.contains("CallerRunsPolicy("),
                    file + " must never use CallerRunsPolicy - a rejected job must never run on the caller's (potentially render/tick) thread");
            assertTrue(content.contains("AbortPolicy") || content.contains("RejectedExecutionException"),
                    file + " must use an explicit, observable rejection policy rather than silently dropping or discarding a job");
        }
    }

    @Test
    @DisplayName("Every allow-listed infrastructure file still exists (catches a stale allow-list entry)")
    void allowListEntriesStillExist() throws IOException {
        Set<String> actualBasenames = allJavaSources().stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toSet());

        for (String allowed : ALLOWED_BASENAMES) {
            assertTrue(actualBasenames.contains(allowed), "allow-listed file no longer exists in src/main/java: " + allowed);
        }
    }
}
