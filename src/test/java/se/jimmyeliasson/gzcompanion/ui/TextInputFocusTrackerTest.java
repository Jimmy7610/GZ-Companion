package se.jimmyeliasson.gzcompanion.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextInputFocusTrackerTest {

    @Test
    @DisplayName("Focus changes are reported to Minecraft exactly once per change")
    void reportsOnlyChanges() {
        List<Boolean> reported = new ArrayList<>();
        TextInputFocusTracker tracker = new TextInputFocusTracker(reported::add);

        tracker.sync(false); // initial state is unfocused - nothing to report
        tracker.sync(true);
        tracker.sync(true);  // per-frame repeats are free
        tracker.sync(true);
        tracker.sync(false);
        tracker.sync(false);
        tracker.sync(true);

        assertEquals(List.of(true, false, true), reported);
        assertTrue(tracker.isReportedFocused());
    }

    @Test
    @DisplayName("Closing the screen releases text input only if it was started")
    void releaseStopsTextInput() {
        List<Boolean> reported = new ArrayList<>();
        TextInputFocusTracker tracker = new TextInputFocusTracker(reported::add);

        tracker.release();
        assertEquals(List.of(), reported, "never started - nothing to stop");

        tracker.sync(true);
        tracker.release();
        assertEquals(List.of(true, false), reported);
        assertFalse(tracker.isReportedFocused());
    }

    @Test
    @DisplayName("A failing notifier never breaks the screen")
    void notifierFailureIsContained() {
        TextInputFocusTracker tracker = new TextInputFocusTracker(focused -> {
            throw new IllegalStateException("no window");
        });
        tracker.sync(true);
        tracker.release();
        assertFalse(tracker.isReportedFocused());
    }
}
