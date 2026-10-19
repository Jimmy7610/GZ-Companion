package se.jimmyeliasson.gzcompanion.usage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.net.GameZoneLiveDataRuntime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Throttling, failure handling, concurrency and threading of the anonymous installation counter. */
class UsageHeartbeatServiceTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-19T12:00:00Z"));

    private static final class MemoryStore implements InstallationIdentityStore {
        volatile InstallationIdentity stored;
        final AtomicInteger loads = new AtomicInteger();
        final List<InstallationIdentity> saves = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Optional<InstallationIdentity> load() {
            loads.incrementAndGet();
            return Optional.ofNullable(stored);
        }

        @Override
        public void save(InstallationIdentity identity) {
            saves.add(identity);
            stored = identity;
        }
    }

    private static final class FakeTransport implements HeartbeatTransport {
        final List<HeartbeatPayload> sent = Collections.synchronizedList(new ArrayList<>());
        final List<String> threads = Collections.synchronizedList(new ArrayList<>());
        volatile boolean accept = true;
        volatile RuntimeException toThrow;
        volatile CountDownLatch block;

        @Override
        public boolean send(HeartbeatPayload payload) {
            threads.add(Thread.currentThread().getName());
            CountDownLatch b = block;
            if (b != null) {
                try {
                    b.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            sent.add(payload);
            if (toThrow != null) throw toThrow;
            return accept;
        }
    }

    private UsageHeartbeatService service(MemoryStore store, HeartbeatTransport transport, String version) {
        return new UsageHeartbeatService(store, transport, new GameZoneLiveDataRuntime("0.1.0-test"), () -> version, now::get);
    }

    private void advance(Duration d) {
        now.set(now.get().plus(d));
    }

    @Test
    @DisplayName("Without an endpoint the counter is fully inactive: no id, no file access, no thread, no network")
    void inactiveWithoutEndpoint() {
        MemoryStore store = new MemoryStore();
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        UsageHeartbeatService s = new UsageHeartbeatService(store, null, runtime, () -> "1", now::get);
        assertFalse(s.isActive());
        for (int i = 0; i < 10; i++) s.onClientActive();
        assertEquals(0, store.loads.get());
        assertTrue(store.saves.isEmpty());
        assertFalse(runtime.isExecutorInitializedForTesting(), "No worker thread was started");
        assertFalse(runtime.isHttpClientInitializedForTesting(), "No HTTP client was created");
    }

    @Test
    @DisplayName("Production wiring is active, yet construction starts no thread and creates no HTTP client")
    void productionWiringActiveButLazy() {
        GameZoneLiveDataRuntime runtime = new GameZoneLiveDataRuntime("0.1.0-test");
        assertTrue(UsageHeartbeatService.createDefault(runtime, () -> "1").isActive());
        assertFalse(runtime.isExecutorInitializedForTesting(), "No worker thread was started");
        assertFalse(runtime.isHttpClientInitializedForTesting(), "No HTTP client was created");
    }

    @Test
    @DisplayName("First heartbeat creates one random id, sends only id + version, and records attempt and success")
    void firstHeartbeat() {
        MemoryStore store = new MemoryStore();
        FakeTransport transport = new FakeTransport();
        service(store, transport, "0.1.0-alpha.12").runOnce();
        assertEquals(1, transport.sent.size());
        HeartbeatPayload p = transport.sent.get(0);
        assertEquals(store.stored.installationId(), p.installationId());
        assertEquals("0.1.0-alpha.12", p.version());
        assertEquals(now.get(), store.stored.lastAttemptAt());
        assertEquals(now.get(), store.stored.lastSuccessAt());
        assertEquals(1, store.saves.stream().map(InstallationIdentity::installationId).distinct().count(), "exactly one id ever");
    }

    @Test
    @DisplayName("At most once per 24 hours: a restart within 24 h sends nothing, after 24 h the same id is sent again")
    void dailyThrottle() {
        MemoryStore store = new MemoryStore();
        FakeTransport transport = new FakeTransport();
        service(store, transport, "1").runOnce();
        UUID id = store.stored.installationId();

        advance(Duration.ofHours(23).plusMinutes(59));
        service(store, transport, "1").runOnce();
        assertEquals(1, transport.sent.size(), "Within 24 hours");

        advance(Duration.ofMinutes(1));
        service(store, transport, "1").runOnce();
        assertEquals(2, transport.sent.size());
        assertEquals(id, transport.sent.get(1).installationId(), "Same id - stable across sessions");
    }

    @Test
    @DisplayName("At most one attempt per game session, however often it is triggered")
    void oncePerSession() {
        MemoryStore store = new MemoryStore();
        FakeTransport transport = new FakeTransport();
        transport.accept = false;
        UsageHeartbeatService s = service(store, transport, "1");
        s.runOnce();
        advance(Duration.ofDays(3));
        s.runOnce();
        s.runOnce();
        assertEquals(1, transport.sent.size());
    }

    @Test
    @DisplayName("A failed heartbeat records the attempt only; no retry for 6 hours (no aggressive retries)")
    void failureBackoff() {
        MemoryStore store = new MemoryStore();
        FakeTransport transport = new FakeTransport();
        transport.accept = false;
        service(store, transport, "1").runOnce();
        assertEquals(now.get(), store.stored.lastAttemptAt());
        assertNull(store.stored.lastSuccessAt());

        advance(Duration.ofHours(5).plusMinutes(59));
        service(store, transport, "1").runOnce();
        assertEquals(1, transport.sent.size(), "Backing off");

        transport.accept = true;
        advance(Duration.ofMinutes(1));
        service(store, transport, "1").runOnce();
        assertEquals(2, transport.sent.size());
        assertNotNull(store.stored.lastSuccessAt());
    }

    @Test
    @DisplayName("A transport that throws is harmless and still counts as an attempt")
    void throwingTransportHarmless() {
        MemoryStore store = new MemoryStore();
        FakeTransport transport = new FakeTransport();
        transport.toThrow = new IllegalStateException("boom");
        assertDoesNotThrow(() -> service(store, transport, "1").runOnce());
        assertNotNull(store.stored.lastAttemptAt());
        assertNull(store.stored.lastSuccessAt());
    }

    @Test
    @DisplayName("Updating GZ Companion keeps the same id; only the reported version changes")
    void stableAcrossUpdates() {
        MemoryStore store = new MemoryStore();
        FakeTransport transport = new FakeTransport();
        service(store, transport, "0.1.0-alpha.12").runOnce();
        advance(Duration.ofDays(2));
        service(store, transport, "0.1.0-alpha.13").runOnce();
        assertEquals(transport.sent.get(0).installationId(), transport.sent.get(1).installationId());
        assertEquals("0.1.0-alpha.13", transport.sent.get(1).version());
    }

    @Test
    @DisplayName("50 concurrent triggers produce exactly one id and one heartbeat")
    void concurrentTriggers() throws Exception {
        MemoryStore store = new MemoryStore();
        FakeTransport transport = new FakeTransport();
        UsageHeartbeatService s = service(store, transport, "1");
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                s.onClientActive();
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : threads) t.join(3000);
        long deadline = System.currentTimeMillis() + 3000;
        while (transport.sent.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(5);
        Thread.sleep(100);
        assertEquals(1, transport.sent.size());
        assertEquals(1, store.saves.stream().map(InstallationIdentity::installationId).distinct().count());
    }

    @Test
    @DisplayName("Concurrent id lookups create the id exactly once")
    void concurrentIdCreation() throws Exception {
        MemoryStore store = new MemoryStore();
        UsageHeartbeatService s = service(store, new FakeTransport(), "1");
        Set<UUID> ids = Collections.synchronizedSet(new HashSet<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            Thread t = new Thread(() -> ids.add(s.loadOrCreateIdentity().installationId()));
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join(3000);
        assertEquals(1, ids.size());
        assertEquals(1, store.saves.size(), "Generated and saved once");
    }

    @Test
    @DisplayName("onClientActive never blocks the caller: the request runs on the shared worker, not the calling (render) thread")
    void neverOnCallerThread() throws Exception {
        MemoryStore store = new MemoryStore();
        FakeTransport transport = new FakeTransport();
        transport.block = new CountDownLatch(1);
        UsageHeartbeatService s = service(store, transport, "1");
        long start = System.nanoTime();
        s.onClientActive();
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 500, "Returned immediately while the request is blocked");
        transport.block.countDown();
        long deadline = System.currentTimeMillis() + 3000;
        while (transport.threads.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(5);
        assertEquals(List.of("gzcompanion-gamezone-live"), transport.threads);
        assertNotEquals(Thread.currentThread().getName(), transport.threads.get(0));
    }

    @Test
    @DisplayName("isDue: never sent -> due; success < 24 h -> not due; failed attempt < 6 h -> not due")
    void isDueRules() {
        Instant t = now.get();
        InstallationIdentity fresh = InstallationIdentity.generate();
        assertTrue(UsageHeartbeatService.isDue(fresh, t));
        assertFalse(UsageHeartbeatService.isDue(fresh.withAttemptAt(t).withSuccessAt(t), t.plus(Duration.ofHours(23))));
        assertTrue(UsageHeartbeatService.isDue(fresh.withAttemptAt(t).withSuccessAt(t), t.plus(Duration.ofHours(24))));
        assertFalse(UsageHeartbeatService.isDue(fresh.withAttemptAt(t), t.plus(Duration.ofHours(5))));
        assertTrue(UsageHeartbeatService.isDue(fresh.withAttemptAt(t), t.plus(Duration.ofHours(6))));
        assertTrue(UsageHeartbeatService.isDue(fresh.withAttemptAt(t.plus(Duration.ofHours(1))), t.minus(Duration.ofDays(1))) == false,
                "A clock that went backwards does not cause a burst of retries");
    }
}
