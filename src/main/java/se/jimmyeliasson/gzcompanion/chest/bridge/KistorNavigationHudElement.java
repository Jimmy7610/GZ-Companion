package se.jimmyeliasson.gzcompanion.chest.bridge;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.KistorRuntime;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationManager;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationMath;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationReading;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationStatus;
import se.jimmyeliasson.gzcompanion.chest.nav.KistorNavigationText;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.gamezone.bridge.GameZoneToastHudElement;
import se.jimmyeliasson.gzcompanion.gamezone.toast.GameZoneToastManager;
import se.jimmyeliasson.gzcompanion.mixin.BossHealthOverlayAccessor;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorNavigationHudLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.Locale;
import java.util.Optional;

/**
 * Kistor "HITTA" navigation HUD, registered through the same {@code HudElementRegistry} mechanism
 * as {@link GameZoneToastHudElement}. Draws NOTHING unless a navigation target is active (the
 * inactive path is a single boolean check).
 *
 * <p>While active it only compares the local player's own position and camera yaw with the saved
 * coordinates of the ONE explicitly selected, previously legitimately opened storage location.
 * It never reads blocks, block entities, chunks or entities, never raytraces, never outlines
 * anything, and never moves or turns the player.
 */
public final class KistorNavigationHudElement implements HudElement {
    /** How often the (comparatively costly) world/server context key is re-checked while active. */
    private static final long CONTEXT_CHECK_INTERVAL_MS = 1000L;
    private static final int MAX_BOSS_BARS_CONSIDERED = 6;
    /** Same dark navy as the main panel, but ~45% alpha so gameplay remains visible behind it. */
    static final int NAVIGATION_CARD_BG = 0x730D141C;

    private final KistorRuntime kistor;
    private final GameZoneToastManager toastManager;
    private long lastContextCheckAtMs = 0L;

    public KistorNavigationHudElement(KistorRuntime kistor, GameZoneToastManager toastManager) {
        this.kistor = kistor;
        this.toastManager = toastManager;
    }

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(CompanionConstants.MOD_ID, "kistor_navigation"), this);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        ChestNavigationManager navigation = kistor.navigation();
        if (!navigation.isActive()) {
            return; // O(1) inactive path: draw nothing, compute nothing.
        }
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.font == null || client.getWindow() == null) return;
            // Any open screen (Companion, a storage menu, inventory...) covers the HUD anyway, and
            // Kistor shows its own NAVIGERAR banner - but chatting keeps the guidance visible.
            if (client.screen != null && !(client.screen instanceof ChatScreen)) return;

            long now = System.currentTimeMillis();
            if (now - lastContextCheckAtMs >= CONTEXT_CHECK_INTERVAL_MS) {
                lastContextCheckAtMs = now;
                navigation.onContextObserved(CompanionSession.getInstance().getCurrentStorageContext());
            }

            ChestManager manager = kistor.chestManager();
            Optional<StoredContainer> target = navigation.resolveTarget(manager);
            if (target.isEmpty()) return;

            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
            Optional<PlayerPose> pose = MinecraftPlayerPoseReader.read(partialTick);
            if (pose.isEmpty()) return;

            draw(graphics, client, target.get(), ChestNavigationMath.evaluate(pose.get(), target.get()), now);
        } catch (Exception ignored) {
            // A HUD failure must never crash the game - skip this frame.
        }
    }

    private void draw(GuiGraphicsExtractor graphics, Minecraft client, StoredContainer target, ChestNavigationReading reading, long now) {
        Font font = client.font;
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        ChestNavigationStatus status = reading.status();
        boolean showArrow = status == ChestNavigationStatus.DIRECTIONAL || status == ChestNavigationStatus.NEAR;
        String vertical = status == ChestNavigationStatus.ARRIVED ? null : KistorNavigationText.vertical(reading);

        String line2;
        String line3 = null;
        int line2Color = GZTheme.COLOR_TEXT_SECONDARY;
        int line3Color = GZTheme.COLOR_TEXT_MUTED;
        switch (status) {
            case WRONG_DIMENSION -> {
                line2 = KistorNavigationText.wrongDimensionTarget(reading);
                line2Color = GZTheme.COLOR_STATUS_YELLOW;
                line3 = KistorNavigationText.wrongDimensionPlayer(reading);
            }
            case ARRIVED -> {
                line2 = KistorNavigationText.arrived();
                line2Color = GZTheme.COLOR_STATUS_GREEN;
            }
            default -> {
                line2 = (status == ChestNavigationStatus.NEAR ? "Nära · " : "") + KistorNavigationText.distance(reading);
                line3 = vertical;
                line3Color = GZTheme.COLOR_TEXT_PRIMARY;
            }
        }
        int lineCount = 2 + (line3 != null ? 1 : 0);

        UiRect avoid = toastManager.currentToast(now).isPresent() ? GameZoneToastHudElement.cardRect(screenW) : null;
        KistorNavigationHudLayout layout = KistorNavigationHudLayout.compute(screenW, screenH, bossBarCount(client), avoid, showArrow, lineCount);

        boolean emphasized = status == ChestNavigationStatus.NEAR || status == ChestNavigationStatus.ARRIVED;
        GZTheme.drawCard(graphics, layout.card(), NAVIGATION_CARD_BG, emphasized ? GZTheme.COLOR_EMERALD : GZTheme.COLOR_BORDER_EMERALD);

        if (showArrow) {
            int arrowColor = status == ChestNavigationStatus.NEAR ? GZTheme.COLOR_EMERALD : GZTheme.COLOR_MINT;
            drawRotatingArrow(graphics, layout.arrowCenterX(), layout.arrowCenterY(), (float) reading.relativeBearingDegrees(), arrowColor);
        }

        int cx = layout.card().x() + layout.card().width() / 2;
        int maxW = layout.card().width() - 8;
        TextUtil.drawScaledCenteredText(graphics, font, target.displayTitle().toUpperCase(Locale.ROOT), cx, layout.line1Y(), maxW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_MINT, true);
        TextUtil.drawScaledCenteredText(graphics, font, line2, cx, layout.line2Y(), maxW,
                TypographyScale.META.getScale(), line2Color, true);
        if (line3 != null) {
            TextUtil.drawScaledCenteredText(graphics, font, line3, cx, layout.line3Y(), maxW,
                    TypographyScale.META.getScale(), line3Color, true);
        }
    }

    private static int bossBarCount(Minecraft client) {
        try {
            if (client.gui == null) return 0;
            Object overlay = client.gui.getBossOverlay();
            if (overlay instanceof BossHealthOverlayAccessor accessor) {
                return Math.min(MAX_BOSS_BARS_CONSIDERED, accessor.gzcompanion$getEvents().size());
            }
        } catch (Exception ignored) {
            // Fall through: assume no boss bars.
        }
        return 0;
    }

    /**
     * A real, smoothly rotating arrow: an upward-pointing arrow built from fills in a local
     * half-pixel grid, drawn under the extractor's own 2D pose rotated by the relative bearing.
     * Up = the camera faces the target; clockwise = target to the right; down = behind.
     */
    static void drawRotatingArrow(GuiGraphicsExtractor graphics, int centerX, int centerY, float bearingDegrees, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().rotate((float) Math.toRadians(bearingDegrees));
        graphics.pose().scale(0.5f, 0.5f);

        int shadow = 0xAA04170E;
        // Head: rows from the tip (y = -15) widening to the base (y = -2).
        for (int row = 0; row < 13; row++) {
            int y = -15 + row;
            int half = 1 + row;
            graphics.fill(-half - 1, y + 1, half + 1, y + 2, shadow);
            graphics.fill(-half, y, half, y + 1, color);
        }
        // Shaft.
        graphics.fill(-5, -2, 5, 14, shadow);
        graphics.fill(-4, -2, 4, 13, color);

        graphics.pose().popMatrix();
    }
}
