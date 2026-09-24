package se.jimmyeliasson.gzcompanion.chest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.chest.model.ChestCaptureEvent;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.gamezone.toast.GameZoneToastManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChestCaptureFeedbackTest {

    static StoredContainer doubleChest(String label, int distinctItems) {
        List<ChestSlotEntry> slots = new ArrayList<>();
        for (int i = 0; i < distinctItems; i++) slots.add(new ChestSlotEntry(i, "minecraft:item_" + i, 1));
        return new StoredContainer(new StoredContainerId("c", "minecraft:overworld", new StoragePosition(0, 64, 0), StorageKind.CHEST),
                label, new StoragePosition(1, 64, 0), StorageShape.DOUBLE, 1L, slots);
    }

    @Test
    @DisplayName("New storage: 'Ny förvaring sparad' with type and distinct item count")
    void newStorage() {
        ChestCaptureFeedback.Message m = ChestCaptureFeedback.describe(
                new ChestCaptureEvent(ChestCaptureEvent.Kind.NEW, doubleChest(null, 17), true), false).orElseThrow();
        assertEquals("Ny förvaring sparad", m.title());
        assertEquals("Dubbel kista • 17 olika föremål", m.body());
    }

    @Test
    @DisplayName("Known storage reopened with changes: '<label> uppdaterad'; unchanged reopen stays silent (anti-spam)")
    void updatedStorage() {
        ChestCaptureFeedback.Message m = ChestCaptureFeedback.describe(
                new ChestCaptureEvent(ChestCaptureEvent.Kind.UPDATED, doubleChest("Materiallager", 3), true), false).orElseThrow();
        assertEquals("Materiallager uppdaterad", m.title());
        assertEquals("Senast känt innehåll sparat", m.body());

        assertTrue(ChestCaptureFeedback.describe(new ChestCaptureEvent(ChestCaptureEvent.Kind.UPDATED, doubleChest("Materiallager", 3), false), false).isEmpty());
    }

    @Test
    @DisplayName("Finding the navigation target always notifies '✓ <title> hittad'")
    void found() {
        ChestCaptureFeedback.Message m = ChestCaptureFeedback.describe(
                new ChestCaptureEvent(ChestCaptureEvent.Kind.UPDATED, doubleChest("Värdesaker", 1), false), true).orElseThrow();
        assertEquals("✓ Värdesaker hittad", m.title());
    }

    @Test
    @DisplayName("Companion toasts respect the Companion notification setting, dedupe, and stay bounded")
    void companionToastsRespectSettingsAndDedupe() {
        GameZoneToastManager toasts = new GameZoneToastManager();
        boolean[] enabled = {true};
        toasts.setNotificationsEnabledSupplier(() -> enabled[0]);
        toasts.setGameZoneToastsEnabledSupplier(() -> false); // GameZone-event toggle must not silence local Kistor feedback

        assertTrue(toasts.offerCompanion("kistor:new:a", "Ny förvaring sparad", "Kista • 1 föremålstyp", 1000L));
        assertFalse(toasts.offerCompanion("kistor:new:a", "Ny förvaring sparad", "Kista • 1 föremålstyp", 2000L), "Deduped within the window");
        for (int i = 0; i < 10; i++) toasts.offerCompanion("kistor:new:" + i, "t", "b", 3000L + i);
        assertTrue(toasts.queuedCount() <= 3, "The toast queue stays bounded");

        enabled[0] = false;
        assertFalse(toasts.offerCompanion("kistor:new:z", "t", "b", 99_000L), "All Companion notifications off -> nothing");
    }
}
