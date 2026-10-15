package se.jimmyeliasson.gzcompanion.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Cache, TTL, cooldown, coalescing and fallback behaviour of {@link EventScheduleManager}, with an injected clock. */
class EventScheduleManagerTest {
    private final GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-15T18:00:00Z"));
    private final Supplier<Instant> clock = now::get;

    private static final class FakeSource implements EventScheduleSource {
        final AtomicInteger calls = new AtomicInteger();
        volatile Supplier<EventFetchResult> behavior = () -> {
            try {
                return new EventFetchResult.Success(EventsTestData.schedule(EventsTestData.REAL_JSON));
            } catch (EventScheduleIncompatibleException e) {
                throw new IllegalStateException(e);
            }
        };
        volatile CountDownLatch block;

        @Override
        public EventFetchResult fetch() {
            calls.incrementAndGet();
            CountDownLatch latch = block;
            if (latch != null) {
                try {
                    latch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return behavior.get();
        }
    }

    private static void await(EventScheduleManager m, EventScheduleStatus expected) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (m.getSnapshot().status() == expected) return;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("Timed out waiting for " + expected + ", last: " + m.getSnapshot().status());
    }

    private void advance(Duration d) {
        now.set(now.get().plus(d));
    }

    @Test
    @DisplayName("Constructing the manager fetches nothing and starts no worker thread")
    void lazyConstruction() {
        FakeSource source = new FakeSource();
        EventScheduleManager m = new EventScheduleManager(source, runtime, clock);
        assertEquals(EventScheduleStatus.IDLE, m.getSnapshot().status());
        assertEquals(0, source.calls.get());
        assertFalse(runtime.isExecutorInitializedForTesting());
    }

    @Test
    @DisplayName("First open fetches once and caches the result")
    void firstOpenFetches() {
        FakeSource source = new FakeSource();
        EventScheduleManager m = new EventScheduleManager(source, runtime, clock);
        m.ensureFresh();
        await(m, EventScheduleStatus.LOADED);
        assertEquals(1, source.calls.get());
        assertEquals(14, m.getSnapshot().schedule().entries().size());
        assertEquals(now.get(), m.getSnapshot().fetchedAt());
    }

    @Test
    @DisplayName("Calling ensureFresh every frame within the TTL never fetches again (no request spam)")
    void noDuplicateFetchSpam() {
        FakeSource source = new FakeSource();
        EventScheduleManager m = new EventScheduleManager(source, runtime, clock);
        m.ensureFresh();
        await(m, EventScheduleStatus.LOADED);
        for (int frame = 0; frame < 600; frame++) {
            m.ensureFresh();
            if (frame % 100 == 0) advance(Duration.ofSeconds(29)); // ~3 minutes of frames
        }
        assertEquals(1, source.calls.get());
    }

    @Test
    @DisplayName("After the 5-minute TTL the next open refreshes once")
    void refreshAfterTtl() {
        FakeSource source = new FakeSource();
        EventScheduleManager m = new EventScheduleManager(source, runtime, clock);
        m.ensureFresh();
        await(m, EventScheduleStatus.LOADED);
        advance(EventScheduleManager.REFRESH_TTL.minusSeconds(1));
        m.ensureFresh();
        assertEquals(1, source.calls.get(), "Still inside the TTL");
        advance(Duration.ofSeconds(1));
        m.ensureFresh();
        m.ensureFresh();
        await(m, EventScheduleStatus.LOADED);
        assertEquals(2, source.calls.get());
    }

    @Test
    @DisplayName("Requests while a fetch is in flight are coalesced into that one fetch")
    void coalescesWhileInFlight() throws Exception {
        FakeSource source = new FakeSource();
        source.block = new CountDownLatch(1);
        EventScheduleManager m = new EventScheduleManager(source, runtime, clock);
        m.ensureFresh();
        advance(Duration.ofMinutes(10));
        m.ensureFresh();
        assertFalse(m.manualRefresh());
        assertEquals(EventScheduleStatus.LOADING, m.getSnapshot().status());
        source.block.countDown();
        await(m, EventScheduleStatus.LOADED);
        assertEquals(1, source.calls.get());
    }

    @Test
    @DisplayName("A failed refresh keeps the cached schedule (STALE) with the reason")
    void cachedFallback() {
        FakeSource source = new FakeSource();
        EventScheduleManager m = new EventScheduleManager(source, runtime, clock);
        m.ensureFresh();
        await(m, EventScheduleStatus.LOADED);
        Instant firstFetch = m.getSnapshot().fetchedAt();
        source.behavior = () -> new EventFetchResult.Unavailable("Eventschemat svarade inte i tid.");
        advance(EventScheduleManager.REFRESH_TTL);
        m.ensureFresh();
        await(m, EventScheduleStatus.STALE);
        EventScheduleSnapshot s = m.getSnapshot();
        assertEquals(14, s.schedule().entries().size(), "Cached schedule is still shown");
        assertEquals(firstFetch, s.fetchedAt(), "fetchedAt still describes the cached data");
        assertEquals("Eventschemat svarade inte i tid.", s.errorMessage());
    }

    @Test
    @DisplayName("Failures without any cache end as UNAVAILABLE / INCOMPATIBLE with no schedule")
    void failureWithoutCache() {
        FakeSource source = new FakeSource();
        source.behavior = () -> new EventFetchResult.Unavailable("HTTP 500");
        EventScheduleManager m = new EventScheduleManager(source, runtime, clock);
        m.ensureFresh();
        await(m, EventScheduleStatus.UNAVAILABLE);
        assertNull(m.getSnapshot().schedule());

        FakeSource bad = new FakeSource();
        bad.behavior = () -> new EventFetchResult.Incompatible("status error");
        EventScheduleManager m2 = new EventScheduleManager(bad, runtime, clock);
        m2.ensureFresh();
        await(m2, EventScheduleStatus.INCOMPATIBLE);
        assertNull(m2.getSnapshot().schedule());
    }

    @Test
    @DisplayName("A source that throws unexpectedly is contained as UNAVAILABLE")
    void throwingSourceContained() {
        FakeSource source = new FakeSource();
        source.behavior = () -> {
            throw new IllegalStateException("boom");
        };
        EventScheduleManager m = new EventScheduleManager(source, runtime, clock);
        m.ensureFresh();
        await(m, EventScheduleStatus.UNAVAILABLE);
        assertTrue(m.getSnapshot().errorMessage().contains("boom"));
    }

    @Test
    @DisplayName("Manual refresh (Uppdatera / Försök igen) bypasses the TTL but respects its cooldown")
    void manualRefreshCooldown() {
        FakeSource source = new FakeSource();
        EventScheduleManager m = new EventScheduleManager(source, runtime, clock);
        m.ensureFresh();
        await(m, EventScheduleStatus.LOADED);
        assertFalse(m.canManualRefresh());
        assertFalse(m.manualRefresh(), "Within the cooldown");
        advance(EventScheduleManager.MANUAL_REFRESH_COOLDOWN);
        assertTrue(m.canManualRefresh());
        assertTrue(m.manualRefresh());
        await(m, EventScheduleStatus.LOADED);
        assertEquals(2, source.calls.get());
        for (int i = 0; i < 20; i++) m.manualRefresh();
        assertEquals(2, source.calls.get(), "Click spam inside the cooldown does nothing");
    }

    @Test
    @DisplayName("Retry after a failure without cache works once the cooldown has passed")
    void retryAfterFailure() {
        FakeSource source = new FakeSource();
        source.behavior = () -> new EventFetchResult.Unavailable("offline");
        EventScheduleManager m = new EventScheduleManager(source, runtime, clock);
        m.ensureFresh();
        await(m, EventScheduleStatus.UNAVAILABLE);
        source.behavior = new FakeSource().behavior;
        advance(EventScheduleManager.MANUAL_REFRESH_COOLDOWN);
        assertTrue(m.manualRefresh());
        await(m, EventScheduleStatus.LOADED);
        assertNotNull(m.getSnapshot().schedule());
    }

    @Test
    @DisplayName("apply(): success always replaces; failures keep a cached schedule")
    void applyRules() throws Exception {
        EventSchedule sched = EventsTestData.schedule(EventsTestData.REAL_JSON);
        Instant t = Instant.parse("2026-10-15T18:00:00Z");
        EventScheduleSnapshot loaded = EventScheduleManager.apply(EventScheduleSnapshot.idle(), new EventFetchResult.Success(sched), t);
        assertEquals(EventScheduleStatus.LOADED, loaded.status());
        assertEquals(EventScheduleStatus.STALE, EventScheduleManager.apply(loaded, new EventFetchResult.Incompatible("x"), t).status());
        assertEquals(EventScheduleStatus.INCOMPATIBLE, EventScheduleManager.apply(EventScheduleSnapshot.idle(), new EventFetchResult.Incompatible("x"), t).status());
        EventSchedule empty = EventsTestData.schedule(EventsTestData.json(""));
        assertTrue(EventScheduleManager.apply(loaded, new EventFetchResult.Success(empty), t).schedule().entries().isEmpty(),
                "A genuinely empty new schedule replaces the old one");
    }
}
