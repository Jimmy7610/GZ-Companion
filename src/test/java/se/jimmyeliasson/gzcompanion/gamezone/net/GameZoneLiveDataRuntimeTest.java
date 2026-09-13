package se.jimmyeliasson.gzcompanion.gamezone.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.leaderboard.GameZoneLeaderboardRegistry;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardManager;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardFetchResult;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardSource;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardEntry;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardStatus;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link GameZoneLiveDataRuntime}'s own laziness/singleton guarantees - the foundation the alpha.4
 * performance-audit follow-up rests on. See docs/PERFORMANCE-AUDIT-ALPHA4.md's "2026-09-13
 * follow-up" section. Lives in the same package as {@link GameZoneLiveDataRuntime} specifically so
 * it can use the package-private {@code isExecutorInitializedForTesting}/{@code
 * isHttpClientInitializedForTesting} introspection seams - proving "not yet created" precisely,
 * rather than inferring it indirectly (which would be flaky across a JVM that may already have
 * other same-named daemon threads alive from other tests).
 */
class GameZoneLiveDataRuntimeTest {

    @Test
    @DisplayName("Constructing the runtime creates neither the executor nor the HttpClient")
    void constructionIsFullyLazy() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");

        assertFalse(runtime.isExecutorInitializedForTesting(), "executor must not exist right after construction");
        assertFalse(runtime.isHttpClientInitializedForTesting(), "HttpClient must not exist right after construction");
    }

    @Test
    @DisplayName("executor() creates the worker only on first call, then reuses the same instance")
    void executorIsCreatedOnceAndReused() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        assertFalse(runtime.isExecutorInitializedForTesting());

        ExecutorService first = runtime.executor();
        assertTrue(runtime.isExecutorInitializedForTesting());

        ExecutorService second = runtime.executor();
        assertSame(first, second, "repeated calls must return the identical executor, never a new one");
    }

    @Test
    @DisplayName("httpClient() creates the client only on first call, then reuses the same instance")
    void httpClientIsCreatedOnceAndReused() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        assertFalse(runtime.isHttpClientInitializedForTesting());

        HttpClient first = runtime.httpClient();
        assertTrue(runtime.isHttpClientInitializedForTesting());

        HttpClient second = runtime.httpClient();
        assertSame(first, second, "repeated calls must return the identical HttpClient, never a new one");
    }

    @Test
    @DisplayName("The shared HttpClient never automatically follows redirects")
    void httpClientNeverFollowsRedirectsAutomatically() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        assertEquals(HttpClient.Redirect.NEVER, runtime.httpClient().followRedirects());
    }

    @Test
    @DisplayName("Requesting the executor never creates the HttpClient, and vice versa - the two are independently lazy")
    void executorAndHttpClientAreIndependentlyLazy() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");

        runtime.executor();
        assertTrue(runtime.isExecutorInitializedForTesting());
        assertFalse(runtime.isHttpClientInitializedForTesting(), "requesting the executor alone must not construct the HttpClient");

        GameZoneLiveDataRuntime runtime2 = new GameZoneLiveDataRuntime("0.1.0-test");
        runtime2.httpClient();
        assertTrue(runtime2.isHttpClientInitializedForTesting());
        assertFalse(runtime2.isExecutorInitializedForTesting(), "requesting the HttpClient alone must not construct the executor");
    }

    @Test
    @DisplayName("Constructing a LeaderboardManager against a fresh runtime creates neither the executor nor the HttpClient")
    void leaderboardManagerConstructionStaysFullyLazy() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        LeaderboardSource noOpSource = definition -> new LeaderboardFetchResult.Success(List.of());

        new LeaderboardManager(noOpSource, runtime);

        assertFalse(runtime.isExecutorInitializedForTesting(),
                "constructing LeaderboardManager (as CompanionSession does eagerly at startup) must not create the shared worker");
        assertFalse(runtime.isHttpClientInitializedForTesting(),
                "constructing LeaderboardManager must not create the shared HttpClient either");
    }

    @Test
    @DisplayName("An actual fetch through LeaderboardManager creates the executor, and only the executor - the HttpClient is a separate module's concern")
    void actualFetchCreatesOnlyTheExecutor() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        LeaderboardSource fakeSource = definition ->
                new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "Alfa", "1 coins", "Coins")));
        LeaderboardManager manager = new LeaderboardManager(fakeSource, runtime);

        manager.ensureFresh(GameZoneLeaderboardRegistry.all().get(0));

        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline
                && manager.getSnapshot(GameZoneLeaderboardRegistry.all().get(0)).status() != LeaderboardStatus.LOADED) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        assertTrue(runtime.isExecutorInitializedForTesting(), "an actual fetch must have created the shared executor");
        // This FakeSource never sends real HTTP itself, so the runtime's HttpClient stays unused
        // and therefore uncreated - a real GameZoneLeaderboardSource is what would create it, on
        // its own first send() call, per GameZoneLeaderboardSourceTest.
        assertFalse(runtime.isHttpClientInitializedForTesting(),
                "a fake source that never touches the shared HttpClient must not cause it to be created");
    }

    @Test
    @DisplayName("A shorter test connect-timeout constructor overload is honored")
    void testConnectTimeoutOverloadIsHonored() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test", Duration.ofMillis(250));
        assertNotNull(runtime.httpClient().connectTimeout());
        assertEquals(Duration.ofMillis(250), runtime.httpClient().connectTimeout().orElseThrow());
    }
}
