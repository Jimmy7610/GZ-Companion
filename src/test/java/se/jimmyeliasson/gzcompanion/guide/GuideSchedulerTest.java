package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GuideSchedulerTest {

    @Test
    @DisplayName("Should trigger evaluation exactly at interval tick boundaries")
    void testTickIntervalTrigger() {
        GuideScheduler scheduler = new GuideScheduler(15);
        AtomicInteger triggerCount = new AtomicInteger(0);

        for (int i = 1; i <= 45; i++) {
            boolean triggered = scheduler.onTick(triggerCount::incrementAndGet);
            if (i % 15 == 0) {
                assertTrue(triggered, "Should trigger at tick " + i);
                assertEquals(0, scheduler.getTickCounter());
            } else {
                assertFalse(triggered, "Should not trigger at tick " + i);
                assertEquals(i % 15, scheduler.getTickCounter());
            }
        }

        assertEquals(3, triggerCount.get(), "Should have triggered exactly 3 times in 45 ticks");
    }

    @Test
    @DisplayName("Should enforce minimum 1 tick interval")
    void testMinimumInterval() {
        GuideScheduler scheduler = new GuideScheduler(0);
        assertEquals(1, scheduler.getIntervalTicks());
    }
}
