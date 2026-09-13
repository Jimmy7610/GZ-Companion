package se.jimmyeliasson.gzcompanion.gamezone.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.bounty.BountyManager;
import se.jimmyeliasson.gzcompanion.bounty.BountySource;
import se.jimmyeliasson.gzcompanion.bounty.BountyStatus;
import se.jimmyeliasson.gzcompanion.bounty.BountyFetchResult;
import se.jimmyeliasson.gzcompanion.bounty.BountyEntry;
import se.jimmyeliasson.gzcompanion.bounty.BountyExpiry;
import se.jimmyeliasson.gzcompanion.leaderboard.GameZoneLeaderboardRegistry;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardManager;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardFetchResult;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardSource;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardEntry;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardStatus;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    @DisplayName("submit() creates the worker only on first call, then reuses the same one for later submissions")
    void executorIsCreatedOnceAndReused() throws InterruptedException {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        assertFalse(runtime.isExecutorInitializedForTesting());

        AtomicReference<Thread> firstThread = new AtomicReference<>();
        CountDownLatch firstDone = new CountDownLatch(1);
        assertTrue(runtime.submit(() -> {
            firstThread.set(Thread.currentThread());
            firstDone.countDown();
        }));
        assertTrue(firstDone.await(2, TimeUnit.SECONDS));
        assertTrue(runtime.isExecutorInitializedForTesting());

        AtomicReference<Thread> secondThread = new AtomicReference<>();
        CountDownLatch secondDone = new CountDownLatch(1);
        assertTrue(runtime.submit(() -> {
            secondThread.set(Thread.currentThread());
            secondDone.countDown();
        }));
        assertTrue(secondDone.await(2, TimeUnit.SECONDS));

        assertSame(firstThread.get(), secondThread.get(), "repeated submissions must run on the identical worker thread, never a new one");
        assertEquals("gzcompanion-gamezone-live", firstThread.get().getName());
        assertTrue(firstThread.get().isDaemon());
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
    @DisplayName("Submitting work never creates the HttpClient, and vice versa - the two are independently lazy")
    void executorAndHttpClientAreIndependentlyLazy() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");

        runtime.submit(() -> {});
        assertTrue(runtime.isExecutorInitializedForTesting());
        assertFalse(runtime.isHttpClientInitializedForTesting(), "submitting work alone must not construct the HttpClient");

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
    @DisplayName("Constructing a BountyManager against a fresh runtime creates neither the executor nor the HttpClient")
    void bountyManagerConstructionStaysFullyLazy() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        BountySource noOpSource = () -> new BountyFetchResult.Success(List.of());

        new BountyManager(noOpSource, runtime);

        assertFalse(runtime.isExecutorInitializedForTesting(),
                "constructing BountyManager (as CompanionSession does eagerly at startup) must not create the shared worker");
        assertFalse(runtime.isHttpClientInitializedForTesting(),
                "constructing BountyManager must not create the shared HttpClient either");
    }

    @Test
    @DisplayName("An actual fetch through BountyManager creates the executor, and only the executor")
    void actualBountyFetchCreatesOnlyTheExecutor() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        BountySource fakeSource = () -> new BountyFetchResult.Success(
                List.of(new BountyEntry("Alfa", null, 100, null, "ACTIVE", null, BountyExpiry.UNKNOWN)));
        BountyManager manager = new BountyManager(fakeSource, runtime);

        manager.ensureFresh();

        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && manager.getSnapshot().status() != BountyStatus.LOADED) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        assertTrue(runtime.isExecutorInitializedForTesting(), "an actual fetch must have created the shared executor");
        assertFalse(runtime.isHttpClientInitializedForTesting(),
                "a fake source that never touches the shared HttpClient must not cause it to be created");
    }

    @Test
    @DisplayName("Leaderboards and Bounty Board share the exact same runtime infrastructure - a fetch by one never creates a second executor/HttpClient for the other")
    void leaderboardsAndBountiesShareTheSameRuntimeInfrastructure() throws InterruptedException {
        GameZoneLiveDataRuntime sharedRuntime = new GameZoneLiveDataRuntime("0.1.0-test");
        List<Thread> observedThreads = Collections.synchronizedList(new ArrayList<>());

        LeaderboardSource leaderboardSource = definition -> {
            observedThreads.add(Thread.currentThread());
            return new LeaderboardFetchResult.Success(List.of(LeaderboardEntry.of(1, "Alfa", "1 coins", "Coins")));
        };
        BountySource bountySource = () -> {
            observedThreads.add(Thread.currentThread());
            return new BountyFetchResult.Success(List.of(new BountyEntry("Alfa", null, 100, null, "ACTIVE", null, BountyExpiry.UNKNOWN)));
        };

        LeaderboardManager leaderboardManager = new LeaderboardManager(leaderboardSource, sharedRuntime);
        BountyManager bountyManager = new BountyManager(bountySource, sharedRuntime);

        leaderboardManager.ensureFresh(GameZoneLeaderboardRegistry.all().get(0));
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline
                && leaderboardManager.getSnapshot(GameZoneLeaderboardRegistry.all().get(0)).status() != LeaderboardStatus.LOADED) {
            Thread.sleep(5);
        }
        bountyManager.ensureFresh();
        deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && bountyManager.getSnapshot().status() != BountyStatus.LOADED) {
            Thread.sleep(5);
        }

        assertEquals(2, observedThreads.size());
        assertEquals("gzcompanion-gamezone-live", observedThreads.get(0).getName());
        assertSame(observedThreads.get(0), observedThreads.get(1),
                "Leaderboards and Bounty Board must dispatch through the exact same single shared worker thread - never one each");
    }

    @Test
    @DisplayName("A shorter test connect-timeout constructor overload is honored")
    void testConnectTimeoutOverloadIsHonored() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test", Duration.ofMillis(250));
        assertNotNull(runtime.httpClient().connectTimeout());
        assertEquals(Duration.ofMillis(250), runtime.httpClient().connectTimeout().orElseThrow());
    }

    // ------------------------------------------------------------------
    // Bounded scheduling / explicit rejection (correctness follow-up): the shared worker's queue
    // is bounded and a rejection is always explicit - never CallerRunsPolicy, which could
    // otherwise run GameZone HTTP work on whatever thread called submit().
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Once the worker is busy and the bounded queue is completely full, a further submit() is explicitly rejected (returns false)")
    void submitIsRejectedOnceQueueIsSaturated() throws InterruptedException {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        CountDownLatch blockWorker = new CountDownLatch(1);
        CountDownLatch workerEntered = new CountDownLatch(1);

        // Occupies the single worker thread, blocked, so nothing queued behind it can start.
        assertTrue(runtime.submit(() -> {
            workerEntered.countDown();
            try {
                blockWorker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(workerEntered.await(2, TimeUnit.SECONDS));

        // Fill the bounded queue completely.
        for (int i = 0; i < GameZoneLiveDataRuntime.MAX_QUEUED_JOBS; i++) {
            assertTrue(runtime.submit(() -> {}), "job " + i + " should still fit in the bounded queue");
        }

        // The queue is now completely full (worker busy + MAX_QUEUED_JOBS queued) - one more must be rejected.
        boolean accepted = runtime.submit(() -> {});
        assertFalse(accepted, "a submit() beyond the bounded capacity must be explicitly rejected, not silently queued or run inline");

        blockWorker.countDown(); // release the worker so the JVM can shut down cleanly
    }

    @Test
    @DisplayName("A rejected submit() never runs the task on the calling thread")
    void rejectedSubmitNeverRunsOnCallingThread() throws InterruptedException {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        CountDownLatch blockWorker = new CountDownLatch(1);
        CountDownLatch workerEntered = new CountDownLatch(1);
        Thread testThread = Thread.currentThread();

        assertTrue(runtime.submit(() -> {
            workerEntered.countDown();
            try {
                blockWorker.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(workerEntered.await(2, TimeUnit.SECONDS));
        for (int i = 0; i < GameZoneLiveDataRuntime.MAX_QUEUED_JOBS; i++) {
            runtime.submit(() -> {});
        }

        AtomicInteger ranOnCallingThread = new AtomicInteger(0);
        boolean accepted = runtime.submit(() -> {
            if (Thread.currentThread() == testThread) {
                ranOnCallingThread.incrementAndGet();
            }
        });

        assertFalse(accepted);
        assertEquals(0, ranOnCallingThread.get(), "a rejected job must never execute at all, and certainly never on the calling thread");

        blockWorker.countDown();
    }
}
