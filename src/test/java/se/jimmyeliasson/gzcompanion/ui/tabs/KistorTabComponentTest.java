package se.jimmyeliasson.gzcompanion.ui.tabs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Human QA blocker: a legitimately-opened chest with more than five distinct item types could
 * not be scrolled in the compact chest detail pane. Root cause was shared with Byggplaner/
 * Crafting - in compact mode, {@code listRect()} and {@code detailRect()} are the SAME
 * rectangle (only one pane renders at a time), so {@code mouseScrolled} used to always resolve
 * to the list branch first, silently eating every scroll event meant for the detail pane.
 *
 * <p>No selected container is used here (only the routing decision is under test) - resolving an
 * actual container requires a live {@code CompanionSession}/{@code ChestManager}, which is not
 * safe to construct in a headless unit test.
 */
class KistorTabComponentTest {

    private static final UiRect COMPACT_BOUNDS = new UiRect(0, 0, 200, 300);

    @Test
    @DisplayName("Compact layout aliases list and detail rects - the precondition the routing fix guards against")
    void compactLayoutAliasesListAndDetailRects() {
        KistorLayout layout = KistorLayout.calculate(COMPACT_BOUNDS);
        assertTrue(layout.isCompact());
        assertEquals(layout.listRect(), layout.detailRect());
    }

    @Test
    @DisplayName("Scrolling while the compact detail pane is showing must never fall through to the hidden list's scroll branch")
    void compactDetailScrollNeverMovesListOffset() {
        KistorTabComponent tab = new KistorTabComponent();
        tab.setCompactStateForTesting(COMPACT_BOUNDS, true, null);

        boolean handled = tab.mouseScrolled(100, 150, 0, -1);

        // With nothing selected, the detail pane has nothing to scroll - the old bug would still
        // have reached the list branch here (since listRect()==detailRect() in compact mode) and
        // scrolled the hidden list instead.
        assertFalse(handled);
        assertEquals(0, tab.listScrollOffsetForTesting(),
                "The list pane is hidden behind the detail pane in compact mode and must never scroll instead of it.");
    }

    @Test
    @DisplayName("An open inline editor (label/group/note) takes typed characters - including 'g' - and respects its length cap")
    void inlineEditorTakesTypingWithCap() {
        KistorTabComponent tab = new KistorTabComponent();
        se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorUiState state = tab.stateForTesting();
        state.beginEdit(se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorUiState.EditField.GROUP, "");
        assertTrue(tab.isTextInputFocused(), "G must type into the editor instead of closing the Companion");

        for (int i = 0; i < 100; i++) {
            assertTrue(tab.charTyped(new net.minecraft.client.input.CharacterEvent((int) 'g')));
        }
        assertEquals(se.jimmyeliasson.gzcompanion.chest.model.StorageMetadata.MAX_GROUP_LENGTH, state.editText.length());
    }

    @Test
    @DisplayName("ESC cancels an open inline editor first (without saving) and does not fall through to closing the Companion")
    void escCancelsEditorFirst() {
        KistorTabComponent tab = new KistorTabComponent();
        se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorUiState state = tab.stateForTesting();
        state.beginEdit(se.jimmyeliasson.gzcompanion.ui.tabs.kistor.KistorUiState.EditField.NOTE, "Källaren");
        assertTrue(tab.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0)));
        assertNull(state.editField);
        assertFalse(tab.isTextInputFocused());
        assertFalse(tab.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0)),
                "With nothing focused ESC is left for the screen to close the Companion");
    }

    @Test
    @DisplayName("SAKER is the default mode; clicking the FÖRVARING segment switches mode without focusing search")
    void modeSegmentsSwitch() {
        KistorTabComponent tab = new KistorTabComponent();
        UiRect bounds = new UiRect(0, 0, 360, 220);
        assertEquals(KistorLayout.Mode.SAKER, tab.stateForTesting().mode);
        KistorLayout layout = KistorLayout.calculate(bounds, KistorLayout.Mode.SAKER, false, false, false);
        UiRect seg = layout.modeForvaringRect();
        assertTrue(tab.mouseClicked(seg.x() + 2, seg.y() + 2, 0, bounds, null));
        assertEquals(KistorLayout.Mode.FORVARING, tab.stateForTesting().mode);
        assertFalse(tab.isSearchFocused());
    }

    @Test
    @DisplayName("Compact SAKER list scroll routes to the list only while the list pane is showing")
    void compactItemListScrollRouting() {
        KistorTabComponent tab = new KistorTabComponent();
        tab.setCompactStateForTesting(COMPACT_BOUNDS, false, null);
        tab.stateForTesting().mode = KistorLayout.Mode.SAKER;
        // Nothing has been rendered, so nothing is scrollable: the event must not be claimed.
        assertFalse(tab.mouseScrolled(100, 150, 0, -1));
        assertEquals(0, tab.listScrollOffsetForTesting());
    }
}
