package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationManager;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationMath;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationReading;
import se.jimmyeliasson.gzcompanion.chest.nav.KistorNavigationText;
import se.jimmyeliasson.gzcompanion.chest.nav.NavigationDestination;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.marketwatch.MarketWatchLocationInput;
import se.jimmyeliasson.gzcompanion.marketwatch.MarketWatchNavigation;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNote;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchSavedLocation;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Human QA findings: the "Kopiera /marketwatch" reference-card button and the "Ta bort" note
 * delete button were both hardcoded to a fixed pixel width, truncating to "Kopiera /mark..." and
 * "Ta bo..." once real text exceeded it. The fix sizes each button to its actual (widest possible)
 * label instead of a magic-number width - these tests inject a synthetic width measurer so the
 * sizing policy is verifiable without a live Minecraft {@code Font}.
 */
class MarketWatchTabComponentTest {

    /** A stand-in for Font-based measurement: every character is 6px wide. */
    private static int fakeMeasure(String s) {
        return s.length() * 6;
    }

    @Test
    @DisplayName("Button width grows to fit real text instead of staying pinned to a magic-number width")
    void buttonWidthFitsActualText() {
        String longCommand = "Kopiera /marketwatch";
        int width = MarketWatchTabComponent.widestButtonWidth(MarketWatchTabComponentTest::fakeMeasure, 10, longCommand);

        int oldHardcodedWidth = 70;
        assertTrue(width >= fakeMeasure(longCommand) + 10, "Width must actually fit the measured text plus padding.");
        assertTrue(width > oldHardcodedWidth, "The old fixed 70px was too small for this real command and caused the truncation QA reported.");
    }

    @Test
    @DisplayName("Button width picks the widest of several possible labels, so a later label swap (e.g. the delete confirmation) never shrinks it")
    void buttonWidthPicksWidestOfMultipleLabels() {
        int width = MarketWatchTabComponent.widestButtonWidth(MarketWatchTabComponentTest::fakeMeasure, 8, "Ta bort", "Säker?");
        int expected = Math.max(fakeMeasure("Ta bort"), fakeMeasure("Säker?")) + 8;
        assertEquals(expected, width);
    }

    @Test
    @DisplayName("Delete button label reflects the two-step confirmation state")
    void deleteLabelReflectsConfirmationState() {
        assertEquals("Ta bort", MarketWatchTabComponent.deleteButtonLabel(false));
        assertEquals("Säker?", MarketWatchTabComponent.deleteButtonLabel(true));
    }

    private static final String CTX = "p@@server:play.gamezonemc.se";

    private static MarketWatchNote located() {
        return new MarketWatchNote("n1", null, "Raw Beef", null, "", 0L, false,
                MarketWatchSavedLocation.of("minecraft:overworld", 10, 64, 10, 0L).orElseThrow());
    }

    @Test
    @DisplayName("Only notes with a saved location grow by one line; every other note keeps its original height")
    void rowHeightOnlyGrowsForLocatedNotes() {
        MarketWatchNote plain = new MarketWatchNote("p", null, "A", null, "", 0L, false);
        assertEquals(34, MarketWatchTabComponent.rowHeight(plain));
        assertTrue(MarketWatchTabComponent.rowHeight(located()) > 34);
    }

    @Test
    @DisplayName("The location line always says the place is the player's own, and reuses the shared distance wording")
    void locationLineWording() {
        ChestNavigationReading reading = ChestNavigationMath.evaluate(new PlayerPose("minecraft:overworld", 10.5, 64, -836.5, 0f),
                NavigationDestination.of(MarketWatchNavigation.toNavigationPoint(CTX, located()).orElseThrow()));
        assertEquals("Din sparade plats • 847 block fågelvägen", MarketWatchTabComponent.locationLineText(false, reading, false));
        assertEquals("● NAVIGERAR HIT • 847 block fågelvägen", MarketWatchTabComponent.locationLineText(true, reading, false));
        assertEquals(KistorNavigationText.NAVIGATION_STARTED, MarketWatchTabComponent.locationLineText(true, reading, true));
        assertEquals("Din sparade plats", MarketWatchTabComponent.locationLineText(false, null, false), "No live position: no invented distance");

        ChestNavigationReading nether = ChestNavigationMath.evaluate(new PlayerPose("minecraft:the_nether", 0, 64, 0, 0f),
                NavigationDestination.of(MarketWatchNavigation.toNavigationPoint(CTX, located()).orElseThrow()));
        assertEquals("Din sparade plats • Finns i Overworld", MarketWatchTabComponent.locationLineText(false, nether, false));
    }

    @Test
    @DisplayName("The edit form explains the place is saved by the player, and shows exactly what was saved")
    void editLocationStatusText() {
        String[] blank = {"", " ", ""};
        assertEquals("Ingen plats. Skriv X, Y, Z eller tryck Använd min plats.", MarketWatchTabComponent.editLocationStatusText(blank, null, null));

        MarketWatchSavedLocation loc = MarketWatchSavedLocation.of("minecraft:the_nether", -5, 70, 9, 0L).orElseThrow();
        assertEquals("Din plats i Nether", MarketWatchTabComponent.editLocationStatusText(new String[]{"-5", "70", "9"}, loc.dimensionKey(), loc),
                "The coordinates themselves are in the X/Y/Z fields; the status names the dimension they will be saved in");
        assertEquals("Din plats i Nether", MarketWatchTabComponent.editLocationStatusText(new String[]{"100", "70", "9"}, loc.dimensionKey(), loc),
                "Edited coordinates keep the saved location's dimension");
        assertEquals("Din plats sparas i din nuvarande dimension.", MarketWatchTabComponent.editLocationStatusText(new String[]{"1", "2", "3"}, null, null),
                "A new typed location says it will use the current dimension - never a guessed Overworld");

        MarketWatchSavedLocation dated = MarketWatchSavedLocation.of("minecraft:overworld", 1, 2, 3, 1_789_000_000_000L).orElseThrow();
        assertTrue(MarketWatchTabComponent.editLocationStatusText(new String[]{" 1", "2", "3 "}, dated.dimensionKey(), dated).startsWith("Din plats i Overworld • sparad "),
                "An unchanged saved location still shows when it was saved");
        assertEquals("Din plats i Overworld", MarketWatchTabComponent.editLocationStatusText(new String[]{"9", "2", "3"}, dated.dimensionKey(), dated));
    }

    @Test
    @DisplayName("Editing the navigated note keeps the target in step; removing its place stops navigation")
    void syncActiveNavigationFollowsEdits() {
        ChestNavigationManager nav = new ChestNavigationManager();
        MarketWatchNote note = located();
        MarketWatchNavigation.startNavigation(nav, CTX, note, 1L);

        MarketWatchNote moved = note.withLocation(MarketWatchSavedLocation.of("minecraft:overworld", 99, 70, 99, 2L).orElseThrow());
        MarketWatchTabComponent.syncActiveNavigation(nav, CTX, moved);
        assertEquals(new StoragePosition(99, 70, 99), nav.pointTarget().orElseThrow().position());

        MarketWatchTabComponent.syncActiveNavigation(nav, CTX, moved.withLocation(null));
        assertFalse(nav.isActive());
        assertEquals(ChestNavigationManager.StopReason.TARGET_MISSING, nav.lastStopReason().orElseThrow());
    }

    @Test
    @DisplayName("Editing a note that is NOT the target never starts or stops navigation")
    void syncIgnoresOtherNotes() {
        ChestNavigationManager nav = new ChestNavigationManager();
        MarketWatchTabComponent.syncActiveNavigation(nav, CTX, located());
        assertFalse(nav.isActive(), "Saving a located note never auto-starts navigation");
    }

    @Test
    @DisplayName("Tab walks item -> note -> X -> Y -> Z -> item, so coordinates are keyboard-editable like the other fields")
    void tabOrderIncludesCoordinates() {
        Object first = MarketWatchTabComponent.nextField(null);
        StringBuilder order = new StringBuilder(first.toString());
        Object f = first;
        for (int i = 0; i < 5; i++) {
            f = MarketWatchTabComponent.nextField((MarketWatchTabComponent.EditField) f);
            order.append(">").append(f);
        }
        assertEquals("ITEM>NOTE>X>Y>Z>ITEM", order.toString());
    }

    @Test
    @DisplayName("Clearing all three fields (or Ta bort plats) saves no location and stops active navigation to that note")
    void clearingLocationStopsNavigation() {
        ChestNavigationManager nav = new ChestNavigationManager();
        MarketWatchNote note = located();
        MarketWatchNavigation.startNavigation(nav, CTX, note, 1L);

        MarketWatchLocationInput.Outcome cleared = MarketWatchLocationInput.resolve("", "", "", null, java.util.Optional.empty(), note.location(), 2L);
        assertTrue(cleared.isValid());
        assertNull(cleared.location());
        MarketWatchTabComponent.syncActiveNavigation(nav, CTX, note.withLocation(cleared.location()));
        assertFalse(nav.isActive());
        assertEquals(ChestNavigationManager.StopReason.TARGET_MISSING, nav.lastStopReason().orElseThrow());
    }

    @Test
    @DisplayName("Saving manually edited coordinates while navigating re-targets Hitta to exactly the new place")
    void manualEditRetargetsNavigation() {
        ChestNavigationManager nav = new ChestNavigationManager();
        MarketWatchNote note = located();
        MarketWatchNavigation.startNavigation(nav, CTX, note, 1L);

        MarketWatchSavedLocation moved = MarketWatchLocationInput.resolve("-250", "-12", "4000", note.location().dimensionKey(),
                java.util.Optional.empty(), note.location(), 2L).location();
        MarketWatchTabComponent.syncActiveNavigation(nav, CTX, note.withLocation(moved));
        assertEquals(new StoragePosition(-250, -12, 4000), nav.pointTarget().orElseThrow().position());
        assertEquals("minecraft:overworld", nav.pointTarget().orElseThrow().dimensionKey());
    }
}
