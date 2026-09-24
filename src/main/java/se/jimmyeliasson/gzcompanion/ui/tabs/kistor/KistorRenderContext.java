package se.jimmyeliasson.gzcompanion.ui.tabs.kistor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.KistorRuntime;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.ui.ItemHoverTooltips;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.List;
import java.util.Optional;

/**
 * Everything one Kistor view needs for one render frame. Views register their clickable areas in
 * {@link #hits()} (drawn-this-frame hit targets only), so input always matches what is visible.
 */
public record KistorRenderContext(
    GuiGraphicsExtractor extractor,
    Font font,
    int mouseX,
    int mouseY,
    long nowMs,
    KistorLayout layout,
    KistorUiState state,
    ChestManager manager,
    KistorRuntime runtime,
    String contextKey,
    Optional<PlayerPose> playerPose,
    boolean showTechnicalIds,
    boolean chestDataInPlannersEnabled,
    List<Hit> hits,
    ItemHoverTooltips tooltips
) {
    /**
     * @param keepsEdit true for controls that are part of an open inline editor (Spara, group
     *                  chips) - clicking them must not first cancel the edit.
     */
    public record Hit(UiRect rect, Runnable action, boolean keepsEdit) {}

    public boolean hovered(UiRect rect) {
        return rect != null && rect.contains(mouseX, mouseY);
    }

    public void addHit(UiRect rect, Runnable action) {
        hits.add(new Hit(rect, action, false));
    }

    public void addEditHit(UiRect rect, Runnable action) {
        hits.add(new Hit(rect, action, true));
    }

    /**
     * Adds a hit clipped to the visible (scissored) viewport, so a row scrolled partly out of view
     * is only clickable on its visible part - and not at all once fully hidden.
     */
    public void addVisibleHit(UiRect rect, UiRect viewport, Runnable action) {
        int top = Math.max(rect.y(), viewport.y());
        int bottom = Math.min(rect.bottom(), viewport.bottom());
        if (bottom > top) {
            hits.add(new Hit(new UiRect(rect.x(), top, rect.width(), bottom - top), action, false));
        }
    }
}
