package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.keybind.KeybindHandler;
import se.jimmyeliasson.gzcompanion.settings.CompanionSettings;
import se.jimmyeliasson.gzcompanion.settings.DiagnosticsTextBuilder;
import se.jimmyeliasson.gzcompanion.settings.SettingsManager;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.SettingsLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Inställningar tab: general toggles, a Privacy & Fair Play information block, local
 * data management actions (each destructive action requires confirmation, "Clear ALL" requires a
 * stronger two-step confirmation), a redacted diagnostics summary, and a dynamically-read keybind
 * display. Never deletes anything outside {@code config/gzcompanion/}.
 */
public class SettingsTabComponent {
    private static final long CONFIRM_WINDOW_MS = 4000L;
    private static final int ROW_H = 13;
    private static final int TOGGLE_W = 34;

    private record RowHit(UiRect rect, Runnable action) {}

    private int scroll = 0;
    private String pendingConfirmId = null;
    private long pendingConfirmAtMs = 0L;
    private int clearAllStage = 0; // 0 = idle, 1 = first confirm shown, 2 would execute
    private long copyFeedbackExpiry = 0L;

    private SettingsLayout layout;
    private final List<RowHit> hitTargets = new ArrayList<>();
    private int contentHeight = 0;

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.layout = SettingsLayout.calculate(bounds);
        hitTargets.clear();

        CompanionSession session = CompanionSession.getInstance();
        SettingsManager settingsManager = session.getSettingsManager();

        renderHeader(extractor, font, layout.headerRect(), settingsManager);

        UiRect content = layout.contentRect();
        GZTheme.drawCard(extractor, content, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int maxScroll = Math.max(0, contentHeight - content.height());
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        extractor.enableScissor(content.x() + 1, content.y() + 1, content.right() - 1, content.bottom() - 1);
        int x = content.x() + 4;
        int maxW = content.width() - 8;
        int y = content.y() + 3 - scroll;

        y = renderTogglesSection(extractor, font, session, settingsManager, x, y, maxW, mouseX, mouseY);
        y += 4;
        y = renderPrivacySection(extractor, font, x, y, maxW);
        y += 4;
        y = renderDataManagementSection(extractor, font, session, x, y, maxW, mouseX, mouseY);
        y += 4;
        y = renderDiagnosticsSection(extractor, font, session, x, y, maxW, mouseX, mouseY);
        y += 4;
        y = renderKeybindSection(extractor, font, x, y, maxW);

        this.contentHeight = (y + scroll) - (content.y() + 3) + 6;
        extractor.disableScissor();
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, SettingsManager settingsManager) {
        GZTheme.drawIcon(extractor, IconId.SETTINGS, headerRect.x(), headerRect.y() + 1, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Inställningar", headerRect.x() + 13, headerRect.y() + 1,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        boolean available = settingsManager.getStatus().isAvailable();
        String badgeLabel = available ? "Laddad" : settingsManager.getStatus().getDisplayName();
        int badgeW = TextUtil.scaledWidth(font, badgeLabel, TypographyScale.META.getScale()) + 14;
        int dot = available ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_RED;
        GZTheme.drawBadge(extractor, font, headerRect.right() - badgeW, headerRect.y(), badgeLabel, GZTheme.COLOR_TEXT_SECONDARY, dot);
    }

    // ------------------------------------------------------------------
    // General toggles
    // ------------------------------------------------------------------

    private int renderTogglesSection(GuiGraphicsExtractor extractor, Font font, CompanionSession session, SettingsManager settingsManager,
                                      int x, int y, int maxW, int mouseX, int mouseY) {
        TextUtil.drawScaledText(extractor, font, "ALLMÄNT", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 11;

        CompanionSettings settings = settingsManager.getSettings();
        y = renderToggleRow(extractor, font, x, y, maxW, "Companion-notiser", settings.companionNotificationsEnabled(),
                mouseX, mouseY, settingsManager::setCompanionNotificationsEnabled);
        y = renderToggleRow(extractor, font, x, y, maxW, "GameZone-händelsenotiser", settings.gameZoneToastsEnabled(),
                mouseX, mouseY, settingsManager::setGameZoneToastsEnabled);
        y = renderToggleRow(extractor, font, x, y, maxW, "Visa tekniska Minecraft-ID", settings.showTechnicalIds(),
                mouseX, mouseY, settingsManager::setShowTechnicalIds);
        y = renderToggleRow(extractor, font, x, y, maxW, "Visa overifierad kunskap", settings.showUnverifiedKnowledge(),
                mouseX, mouseY, settingsManager::setShowUnverifiedKnowledge);
        y = renderToggleRow(extractor, font, x, y, maxW, "Använd senast kända kistodata i planerare", settings.useLastKnownChestDataInPlanners(),
                mouseX, mouseY, settingsManager::setUseLastKnownChestDataInPlanners);
        return y;
    }

    private int renderToggleRow(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, String label, boolean value,
                                 int mouseX, int mouseY, java.util.function.Predicate<Boolean> setter) {
        UiRect toggleRect = new UiRect(x + maxW - TOGGLE_W, y, TOGGLE_W, 10);
        boolean hov = toggleRect.contains(mouseX, mouseY);

        TextUtil.drawScaledEllipsizedText(extractor, font, label, x, y + 1, maxW - TOGGLE_W - 4, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        GZTheme.drawCard(extractor, toggleRect, value ? GZTheme.COLOR_NAV_ACTIVE : (hov ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER),
                value ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawCenteredText(extractor, font, value ? "PÅ" : "AV", toggleRect.x() + (toggleRect.width() / 2), toggleRect.y() + 1,
                toggleRect.width(), value ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED, false);

        hitTargets.add(new RowHit(toggleRect, () -> setter.test(!value)));
        return y + ROW_H;
    }

    // ------------------------------------------------------------------
    // Privacy & Fair Play
    // ------------------------------------------------------------------

    private int renderPrivacySection(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW) {
        TextUtil.drawScaledText(extractor, font, "INTEGRITET OCH FAIR PLAY", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 11;
        String[] lines = {
                "Endast lokalt - all data sparas på din egen dator.",
                "Ingen telemetri skickas någonsin.",
                "Ingen molnsynkronisering.",
                "Inga automatiska kommandon körs åt dig.",
                "Ingen container-scanning av världen.",
                "Ingen dold serverdata läses eller lagras."
        };
        for (String line : lines) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "- " + line, x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 9;
        }
        return y;
    }

    // ------------------------------------------------------------------
    // Local Data Management
    // ------------------------------------------------------------------

    private int renderDataManagementSection(GuiGraphicsExtractor extractor, Font font, CompanionSession session, int x, int y, int maxW, int mouseX, int mouseY) {
        TextUtil.drawScaledText(extractor, font, "LOKAL DATAHANTERING", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 11;

        String contextKey = session.getCurrentStorageContext();

        y = renderConfirmButton(extractor, font, x, y, maxW, "guide_reset", "Återställ Guide-progression", mouseX, mouseY,
                () -> session.getGuideEngine().resetGuideProgress(session.getCurrentGuideContext()));
        y = renderConfirmButton(extractor, font, x, y, maxW, "chest_clear", "Rensa Kistor-index", mouseX, mouseY,
                () -> session.getChestManager().clearContext(contextKey));
        y = renderConfirmButton(extractor, font, x, y, maxW, "settlement_clear", "Rensa Settlement-planerare", mouseX, mouseY,
                () -> session.getSettlementPlannerManager().clearContext(contextKey));
        y = renderConfirmButton(extractor, font, x, y, maxW, "building_clear", "Rensa Byggplaner", mouseX, mouseY,
                () -> session.getBuildingPlanManager().clearContext(contextKey));
        y = renderConfirmButton(extractor, font, x, y, maxW, "marketwatch_clear", "Rensa MarketWatch-anteckningar", mouseX, mouseY,
                () -> session.getMarketWatchNotesManager().clearContext(contextKey));
        y = renderConfirmButton(extractor, font, x, y, maxW, "settings_reset", "Återställ inställningar", mouseX, mouseY,
                () -> session.getSettingsManager().resetToDefaults());
        y += 2;
        y = renderClearAllButton(extractor, font, session, contextKey, x, y, maxW, mouseX, mouseY);
        return y;
    }

    private int renderConfirmButton(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, String id, String label,
                                     int mouseX, int mouseY, Runnable action) {
        UiRect btn = new UiRect(x, y, maxW, 11);
        long now = System.currentTimeMillis();
        boolean pendingConfirm = id.equals(pendingConfirmId) && (now - pendingConfirmAtMs) < CONFIRM_WINDOW_MS;
        String text = pendingConfirm ? "Bekräfta: " + label + "?" : label;
        int bg = pendingConfirm ? 0x99EF4444 : (btn.contains(mouseX, mouseY) ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER);
        GZTheme.drawCard(extractor, btn, bg, pendingConfirm ? GZTheme.COLOR_STATUS_RED : GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledEllipsizedText(extractor, font, text, btn.x() + 3, btn.y() + 2, maxW - 6, TypographyScale.SMALL.getScale(),
                pendingConfirm ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_SECONDARY, false);

        hitTargets.add(new RowHit(btn, () -> {
            if (id.equals(pendingConfirmId) && (System.currentTimeMillis() - pendingConfirmAtMs) < CONFIRM_WINDOW_MS) {
                action.run();
                pendingConfirmId = null;
            } else {
                pendingConfirmId = id;
                pendingConfirmAtMs = System.currentTimeMillis();
            }
        }));
        return y + ROW_H;
    }

    /**
     * "Clear ALL" requires a STRONGER confirmation than every other destructive action here: two
     * separate confirming clicks (three total clicks) rather than one, and any click elsewhere
     * resets the stage back to idle instead of silently expiring on a timer alone.
     */
    private int renderClearAllButton(GuiGraphicsExtractor extractor, Font font, CompanionSession session, String contextKey,
                                      int x, int y, int maxW, int mouseX, int mouseY) {
        UiRect btn = new UiRect(x, y, maxW, 12);
        String text = switch (clearAllStage) {
            case 1 -> "Säker? Detta rensar ALL lokal Companion-data.";
            case 2 -> "Bekräfta EN GÅNG TILL för att rensa ALLT.";
            default -> "Rensa ALL lokal GZ Companion-data";
        };
        int bg = clearAllStage > 0 ? 0x99EF4444 : (btn.contains(mouseX, mouseY) ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER);
        GZTheme.drawCard(extractor, btn, bg, clearAllStage > 0 ? GZTheme.COLOR_STATUS_RED : GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledEllipsizedText(extractor, font, text, btn.x() + 3, btn.y() + 2, maxW - 6, TypographyScale.SMALL.getScale(),
                clearAllStage > 0 ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_STATUS_RED, false);

        hitTargets.add(new RowHit(btn, () -> {
            if (clearAllStage >= 2) {
                clearAllEverything(session, contextKey);
                clearAllStage = 0;
            } else {
                clearAllStage++;
            }
        }));
        return y + 14;
    }

    private void clearAllEverything(CompanionSession session, String contextKey) {
        session.getGuideEngine().resetGuideProgress(session.getCurrentGuideContext());
        session.getChestManager().clearContext(contextKey);
        session.getSettlementPlannerManager().clearContext(contextKey);
        session.getBuildingPlanManager().clearContext(contextKey);
        session.getMarketWatchNotesManager().clearContext(contextKey);
        session.getSettingsManager().resetToDefaults();
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    private int renderDiagnosticsSection(GuiGraphicsExtractor extractor, Font font, CompanionSession session, int x, int y, int maxW, int mouseX, int mouseY) {
        TextUtil.drawScaledText(extractor, font, "DIAGNOSTIK", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 11;

        String diagnostics = DiagnosticsTextBuilder.build(session);
        for (String line : diagnostics.split("\n")) {
            if (line.isBlank()) continue;
            TextUtil.drawScaledEllipsizedText(extractor, font, line, x, y, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 9;
        }

        UiRect copyBtn = new UiRect(x, y + 2, Math.min(120, maxW), 11);
        boolean hov = copyBtn.contains(mouseX, mouseY);
        GZTheme.drawButton(extractor, font, copyBtn, "Kopiera diagnostik", false, hov, TypographyScale.META.getScale());
        hitTargets.add(new RowHit(copyBtn, () -> copyToClipboard(diagnostics)));
        y += 14;

        if (copyFeedbackExpiry > System.currentTimeMillis()) {
            TextUtil.drawScaledText(extractor, font, "Kopierat!", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_GREEN, false);
            y += 9;
        }
        return y;
    }

    // ------------------------------------------------------------------
    // Keybind
    // ------------------------------------------------------------------

    private int renderKeybindSection(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW) {
        TextUtil.drawScaledText(extractor, font, "SNABBTANGENT", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 11;

        String keyLabel;
        try {
            keyLabel = KeybindHandler.getOpenMenuKey() != null
                    ? KeybindHandler.getOpenMenuKey().getTranslatedKeyMessage().getString()
                    : "Okänd";
        } catch (Exception ignored) {
            keyLabel = "Okänd";
        }
        TextUtil.drawScaledEllipsizedText(extractor, font, "Öppna GZ Companion: " + keyLabel + " (ändra i Minecrafts kontrollinställningar)",
                x, y, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        return y + ROW_H;
    }

    private void copyToClipboard(String text) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.keyboardHandler != null) {
                client.keyboardHandler.setClipboard(text);
                copyFeedbackExpiry = System.currentTimeMillis() + 2000;
            }
        } catch (Exception ignored) {
            // Clipboard access is a pure local OS convenience - never let a failure here affect anything else.
        }
    }

    // ------------------------------------------------------------------
    // Input handling
    // ------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        if (layout == null) layout = SettingsLayout.calculate(bounds);

        for (RowHit hit : hitTargets) {
            if (hit.rect().contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }

        // Clicking anywhere else inside the tab cancels any pending confirmation, so a stray
        // click can never accidentally "count" toward a later confirm.
        boolean hadPending = pendingConfirmId != null || clearAllStage != 0;
        pendingConfirmId = null;
        clearAllStage = 0;
        return hadPending;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null) return false;
        if (layout.contentRect().contains(mouseX, mouseY)) {
            scroll = Math.max(0, scroll - (int) (scrollY * 14));
            return true;
        }
        return false;
    }
}
