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
 * an {@code ExecutorService} (via any {@code Executors.new*} factory), or a raw {@code Thread}.
 * Every other class - crucially, any FUTURE GameZone live-data module (Bounty Board, Chronicles,
 * Live Relics, Settlement Explorer, ...) - must go through the shared {@code
 * GameZoneLiveDataRuntime} (network/executor) or {@code LocalPersistenceRuntime} (local-disk
 * executor) instead, per docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13 follow-up" section.
 *
 * <p>Deliberately narrow: the updater ({@code UpdateManager}/{@code GitHubReleaseSource}/{@code
 * HttpUpdateByteSource}) is explicitly allow-listed - it has genuinely different requirements
 * (GitHub host, redirect-following, multi-minute download timeouts, independent scheduling) and is
 * NOT meant to be folded into the GameZone live-data runtime. This test must never be widened to
 * block that legitimate, intentional separation.
 */
class ThreadingInfrastructureRulesTest {
    private static final Path SRC_ROOT = Path.of("src", "main", "java");

    /** The ONLY files allowed to directly construct an HttpClient, an Executors.new* executor, or
     * a raw Thread. Add a new entry here ONLY for genuinely new shared infrastructure (mirroring
     * GameZoneLiveDataRuntime/LocalPersistenceRuntime) - never to let an individual feature bypass
     * the shared runtimes. */
    private static final Set<String> ALLOWED_BASENAMES = Set.of(
            "GameZoneLiveDataRuntime.java",
            "LocalPersistenceRuntime.java",
            "UpdateManager.java",
            "GitHubReleaseSource.java",
            "HttpUpdateByteSource.java"
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
            "new Thread("
    );

    private static List<Path> allJavaSources() throws IOException {
        assertTrue(Files.isDirectory(SRC_ROOT), "expected main source directory to exist: " + SRC_ROOT.toAbsolutePath());
        try (Stream<Path> files = Files.walk(SRC_ROOT)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    @DisplayName("Only approved infrastructure classes may construct an HttpClient, an Executors.new* executor, or a raw Thread")
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
