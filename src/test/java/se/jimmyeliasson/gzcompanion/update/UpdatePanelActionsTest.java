package se.jimmyeliasson.gzcompanion.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdatePanelActionsTest {

    @Test
    @DisplayName("Update available: [Ladda ner] [Senare]")
    void updateAvailable() {
        UpdatePanelActions a = UpdatePanelActions.forState(UpdateState.UPDATE_AVAILABLE);
        assertEquals("Ladda ner", a.primary().label());
        assertEquals("Senare", a.secondary().label());
    }

    @Test
    @DisplayName("Ready to install: [Stäng och uppdatera] [Senare]")
    void readyToInstall() {
        UpdatePanelActions a = UpdatePanelActions.forState(UpdateState.READY_TO_INSTALL);
        assertEquals("Stäng och uppdatera", a.primary().label());
        assertEquals("Senare", a.secondary().label());
    }

    @Test
    @DisplayName("Error: [Försök igen] [Senare]")
    void error() {
        UpdatePanelActions a = UpdatePanelActions.forState(UpdateState.ERROR);
        assertEquals("Försök igen", a.primary().label());
        assertEquals("Senare", a.secondary().label());
    }

    @Test
    @DisplayName("In-progress and idle states offer no clickable action")
    void noActionsWhileInProgress() {
        for (UpdateState s : new UpdateState[]{UpdateState.DOWNLOADING, UpdateState.VERIFYING, UpdateState.STARTING_INSTALLER,
                UpdateState.IDLE, UpdateState.CHECKING, UpdateState.UP_TO_DATE}) {
            assertFalse(UpdatePanelActions.forState(s).hasActions(), s.name());
        }
        assertFalse(UpdatePanelActions.forState(null).hasActions());
    }

    @Test
    @DisplayName("Every state is covered (a new UpdateState can't be silently forgotten)")
    void everyStateMapped() {
        for (UpdateState s : UpdateState.values()) {
            assertNotNull(UpdatePanelActions.forState(s));
        }
    }
}
