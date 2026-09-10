package se.jimmyeliasson.gzcompanion.gamezone.bridge;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.gamezone.toast.GameZoneToastManager;
import se.jimmyeliasson.gzcompanion.gamezone.toast.ToastEntry;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.Optional;

/**
 * A minimal local notification card in the top-right corner of the screen, shown only while a
 * {@link GameZoneToastManager} toast is active. Uses {@code fabric-rendering-v1}'s
 * {@code HudElementRegistry} (confirmed in this Minecraft/Fabric API version via the merged
 * jar's sources), which - like every other vanilla HUD layer - simply does not render while any
 * {@code Screen} (including GZ Companion's own) is open, so this never overlaps or fights with
 * the Companion UI. Purely read-only presentation: it never blocks input, never intercepts
 * anything, and draws nothing when there is no active toast.
 */
public final class GameZoneToastHudElement implements HudElement {
    private static final int CARD_WIDTH = 160;
    private static final int CARD_HEIGHT = 24;
    private static final int MARGIN = 6;

    private final GameZoneToastManager toastManager;

    public GameZoneToastHudElement(GameZoneToastManager toastManager) {
        this.toastManager = toastManager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(CompanionConstants.MOD_ID, "gamezone_toast"), this);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.font == null || client.getWindow() == null) {
                return;
            }
            Optional<ToastEntry> active = toastManager.currentToast(System.currentTimeMillis());
            if (active.isEmpty()) {
                return;
            }
            ToastEntry toast = active.get();
            Font font = client.font;
            int screenWidth = client.getWindow().getGuiScaledWidth();

            UiRect card = new UiRect(screenWidth - CARD_WIDTH - MARGIN, MARGIN, CARD_WIDTH, CARD_HEIGHT);
            GZTheme.drawCard(graphics, card, GZTheme.COLOR_PANEL_BG, GZTheme.COLOR_BORDER_EMERALD);

            TextUtil.drawScaledEllipsizedText(graphics, font, toast.title(), card.x() + 5, card.y() + 3,
                    card.width() - 10, TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, true);
            TextUtil.drawScaledEllipsizedText(graphics, font, toast.body(), card.x() + 5, card.y() + 13,
                    card.width() - 10, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        } catch (Exception ignored) {
            // A HUD render failure must never crash the game - just skip this frame's toast.
        }
    }
}
