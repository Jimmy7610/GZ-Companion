package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.bounty.BountyEntry;
import se.jimmyeliasson.gzcompanion.bounty.BountyFormatter;
import se.jimmyeliasson.gzcompanion.bounty.BountyManager;
import se.jimmyeliasson.gzcompanion.bounty.BountySnapshot;
import se.jimmyeliasson.gzcompanion.bounty.BountyStatus;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.BountyLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Bounty Board tab: GameZone's public active-bounty registry (see
 * docs/BOUNTY-BOARD.md), read-only, with a list + detail split (wide) or a Guide-style single-pane
 * list/detail (compact). Contains NO network or parsing logic itself - it only ever reads {@link
 * BountyManager#getSnapshot} and triggers {@link BountyManager#ensureFresh}/{@link
 * BountyManager#manualRefresh}, matching {@code LeaderboardsTabComponent}'s "no logic in render
 * code" convention.
 *
 * <p><b>Fair play.</b> This component never scans loaded entities, never shows a coordinate,
 * distance, or direction, and never sends a {@code /bounty}-family command automatically - the
 * command-copy button only ever copies text to the clipboard on a deliberate click (see
 * {@link #copyCommand}). See docs/BOUNTY-BOARD.md's fair-play section.
 */
public class BountiesTabComponent {
    /** Shared by list rendering and click-to-row resolution ({@link #rowIndexAt}) - kept as one
     * constant so the two can never drift apart. */
    static final int ROW_H = 22;

    private int selectedIndex = 0;
    private boolean compactShowingDetail = false;
    private int listScrollOffset = 0;
    private long copyFeedbackExpiryMs = 0L;

    private final List<Hit> hitTargets = new ArrayList<>();
    private record Hit(UiRect rect, Runnable action) {}

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        hitTargets.clear();
        CompanionSession session = CompanionSession.getInstance();
        BountyManager manager = session.getBountyManager();

        manager.ensureFresh();
        BountySnapshot snapshot = manager.getSnapshot();
        List<BountyEntry> entries = snapshot.entries();
        selectedIndex = clampIndex(selectedIndex, entries.size());

        BountyLayout layout = BountyLayout.calculate(bounds);

        renderHeader(extractor, font, layout.headerRect(), snapshot);
        renderCount(extractor, font, layout.countRect(), snapshot, entries.size());

        if (layout.isCompact()) {
            if (compactShowingDetail && !entries.isEmpty()) {
                renderDetailPane(extractor, font, layout.detailRect(), mouseX, mouseY, entries.get(selectedIndex), true);
            } else {
                renderListPane(extractor, font, layout.listRect(), mouseX, mouseY, snapshot, entries, true);
            }
        } else {
            renderListPane(extractor, font, layout.listRect(), mouseX, mouseY, snapshot, entries, false);
            if (!entries.isEmpty()) {
                renderDetailPane(extractor, font, layout.detailRect(), mouseX, mouseY, entries.get(selectedIndex), false);
            } else {
                renderEmptyDetail(extractor, font, layout.detailRect(), snapshot);
            }
        }

        renderFooter(extractor, font, layout, mouseX, mouseY, manager, snapshot);
    }

    /** Pure clamp - never negative, never past the last valid entry; 0 when there are none. */
    static int clampIndex(int index, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(index, size - 1));
    }

    /** Pure scroll-offset clamp, identical policy to {@code LeaderboardsTabComponent.clampScroll}. */
    static int clampScroll(int offset, int totalContentHeight, int viewportHeight) {
        int maxScroll = Math.max(0, totalContentHeight - Math.max(1, viewportHeight));
        return Math.max(0, Math.min(offset, maxScroll));
    }

    /**
     * The message shown in place of a list when there are no entries to show - distinguishes a
     * genuine "zero active bounties" SUCCESS from a real loading/failure state, per this feature's
     * explicit "do not show Error when the real result is simply no active bounties" requirement.
     *
     * <p><b>{@code STALE} is deliberately its own case, not folded into the {@code LOADED}
     * wording.</b> A {@code STALE} empty snapshot means the LAST successful fetch found zero
     * bounties, but a MORE RECENT refresh attempt failed - GameZone may have created a bounty since
     * then, so Companion must never claim "there is no active hunt right now" here. Only a genuine
     * {@code LOADED} empty result (the current refresh itself succeeded) may say that.
     */
    static String emptyStateHeadline(BountyStatus status) {
        return switch (status) {
            case LOADING -> "HÄMTAR BOUNTIES...";
            case UNAVAILABLE, ERROR -> "KUNDE INTE HÄMTA";
            case INCOMPATIBLE -> "OTILLGÄNGLIG";
            case STALE -> "INGA BOUNTIES I CACHAD DATA";
            default -> "INGA AKTIVA BOUNTIES"; // LOADED (genuine current zero) / IDLE
        };
    }

    static String emptyStateBody(BountyStatus status) {
        return switch (status) {
            case LOADING -> "";
            case UNAVAILABLE, ERROR -> "Kunde inte hämta bounty-registret just nu.";
            case INCOMPATIBLE -> "GameZone har ändrat gränssnittet - stöds inte just nu.";
            case STALE -> "Senast hämtade data innehöll inga aktiva jakter. Uppdatera för aktuell status.";
            default -> "Det finns ingen aktiv jakt just nu. Kontrollera igen senare.";
        };
    }

    // ------------------------------------------------------------------
    // Header / count
    // ------------------------------------------------------------------

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect bounds, BountySnapshot snapshot) {
        int y = bounds.y();
        GZTheme.drawIcon(extractor, IconId.BOUNTY, bounds.x(), y + 1, 11, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Bounties", bounds.x() + 14, y + 1, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        String freshness = BountyFormatter.freshnessLabel(snapshot);
        if (!freshness.isBlank()) {
            int dotColor = switch (snapshot.status()) {
                case LOADED -> GZTheme.COLOR_STATUS_GREEN;
                case STALE, LOADING -> GZTheme.COLOR_STATUS_YELLOW;
                default -> GZTheme.COLOR_STATUS_GREY;
            };
            int textW = TextUtil.scaledWidth(font, freshness, TypographyScale.META.getScale());
            int badgeX = bounds.right() - textW - 10;
            GZTheme.drawStatusDot(extractor, badgeX, y + 4, dotColor);
            TextUtil.drawScaledText(extractor, font, freshness, badgeX + 7, y + 1, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        }

        TextUtil.drawScaledEllipsizedText(extractor, font, "Aktiva jakter från GameZone.",
                bounds.x(), y + 12, bounds.width(), TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    private void renderCount(GuiGraphicsExtractor extractor, Font font, UiRect bounds, BountySnapshot snapshot, int count) {
        String text = snapshot.status().hasUsableData()
                ? (count == 0 ? "INGA AKTIVA BOUNTIES" : count + (count == 1 ? " AKTIV BOUNTY" : " AKTIVA BOUNTIES"))
                : "";
        if (!text.isBlank()) {
            TextUtil.drawScaledText(extractor, font, text, bounds.x(), bounds.y(), TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        }
    }

    // ------------------------------------------------------------------
    // List pane
    // ------------------------------------------------------------------

    private void renderListPane(GuiGraphicsExtractor extractor, Font font, UiRect listRect, int mouseX, int mouseY,
                                 BountySnapshot snapshot, List<BountyEntry> entries, boolean compact) {
        GZTheme.drawCard(extractor, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        if (entries.isEmpty()) {
            String headline = emptyStateHeadline(snapshot.status());
            String body = emptyStateBody(snapshot.status());
            int cy = listRect.y() + 6;
            TextUtil.drawScaledCenteredText(extractor, font, headline, listRect.x() + listRect.width() / 2, cy,
                    listRect.width() - 8, TypographyScale.BODY.getScale(), GZTheme.COLOR_TEXT_SECONDARY, true);
            if (!body.isBlank()) {
                TextUtil.drawScaledWrappedText(extractor, font, body, listRect.x() + 6, cy + 12, listRect.width() - 12,
                        TypographyScale.SMALL.getScale(), 4, 2, GZTheme.COLOR_TEXT_MUTED, false);
            }
            return;
        }

        int rowH = ROW_H;
        int totalH = entries.size() * rowH;
        listScrollOffset = clampScroll(listScrollOffset, totalH, listRect.height() - 4);

        extractor.enableScissor(listRect.x() + 1, listRect.y() + 1, listRect.right() - 1, listRect.bottom() - 1);
        int y = listRect.y() + 2 - listScrollOffset;
        Instant now = Instant.now();

        for (int i = 0; i < entries.size(); i++) {
            BountyEntry entry = entries.get(i);
            if (y + rowH >= listRect.y() && y <= listRect.bottom()) {
                renderListRow(extractor, font, listRect.x() + 2, y, listRect.width() - 4, rowH, entry, i == selectedIndex, mouseX, mouseY, compact);
            }
            y += rowH;
        }
        extractor.disableScissor();
    }

    private void renderListRow(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int rowH,
                                BountyEntry entry, boolean selected, int mouseX, int mouseY, boolean compact) {
        UiRect rowRect = new UiRect(x, y, width, rowH - 1);
        boolean hov = rowRect.contains(mouseX, mouseY);
        int bg = selected ? GZTheme.COLOR_NAV_ACTIVE : (hov ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER);
        int border = selected ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, rowRect, bg, border);

        String reward = BountyFormatter.formatReward(entry.rewardCoins()) + " Coins";
        int rewardW = TextUtil.scaledWidth(font, reward, TypographyScale.SMALL.getScale()) + 4;
        int nameMaxW = Math.max(10, width - rewardW - 6);
        TextUtil.drawScaledEllipsizedText(extractor, font, entry.name(), x + 3, y + 2, nameMaxW,
                TypographyScale.BODY.getScale(), selected ? GZTheme.COLOR_MINT : GZTheme.COLOR_TEXT_PRIMARY, true);
        TextUtil.drawScaledRightAlignedText(extractor, font, reward, x + width - 3, y + 2, width - 6,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);

        String subtitle = entry.hasEntityType() ? BountyFormatter.formatEntityType(entry.entityType()) : "";
        String remaining = BountyFormatter.formatRemainingTime(entry.expiry(), Instant.now());
        String secondLine = subtitle.isBlank() ? remaining : (subtitle + "  •  " + remaining);
        TextUtil.drawScaledEllipsizedText(extractor, font, secondLine, x + 3, y + 11, width - 6,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        // Row clicks are NOT added to hitTargets - see mouseClicked's dedicated rowIndexAt
        // resolution, which derives the clicked row directly from mouseY/scrollOffset instead of a
        // per-row closure, since a row's identity (its index into the current entries list) is
        // simpler to compute geometrically than to capture correctly across re-renders.
    }

    // ------------------------------------------------------------------
    // Detail pane
    // ------------------------------------------------------------------

    private void renderEmptyDetail(GuiGraphicsExtractor extractor, Font font, UiRect detailRect, BountySnapshot snapshot) {
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledCenteredText(extractor, font, "Välj en bounty i listan", detailRect.x() + detailRect.width() / 2,
                detailRect.y() + detailRect.height() / 2 - 4, detailRect.width() - 8, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    private void renderDetailPane(GuiGraphicsExtractor extractor, Font font, UiRect detailRect, int mouseX, int mouseY, BountyEntry entry, boolean compact) {
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int pad = 5;
        int contentX = detailRect.x() + pad;
        int contentW = detailRect.width() - pad * 2;
        int y = detailRect.y() + pad;

        if (compact) {
            UiRect backBtn = new UiRect(contentX, y, 44, 11);
            boolean backHov = backBtn.contains(mouseX, mouseY);
            GZTheme.drawButton(extractor, font, backBtn, "< Lista", false, backHov, TypographyScale.SMALL.getScale());
            hitTargets.add(new Hit(backBtn, () -> compactShowingDetail = false));
            y += 15;
        }

        TextUtil.drawScaledEllipsizedText(extractor, font, entry.name().toUpperCase(), contentX, y, contentW,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
        y += 11;
        if (entry.hasEntityType()) {
            TextUtil.drawScaledEllipsizedText(extractor, font, BountyFormatter.formatEntityType(entry.entityType()), contentX, y, contentW,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            y += 11;
        }
        y += 2;

        y = renderDetailSection(extractor, font, contentX, y, contentW, "BELÖNING",
                BountyFormatter.formatReward(entry.rewardCoins()) + " Coins", GZTheme.COLOR_MINT);

        String hintText = entry.hasHint() ? entry.hint() : "Ingen offentlig ledtråd";
        y += 2;
        TextUtil.drawScaledText(extractor, font, "LEDTRÅD", contentX, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 9;
        y += TextUtil.drawScaledWrappedText(extractor, font, hintText, contentX, y, contentW,
                TypographyScale.SMALL.getScale(), 4, 2, GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 4;

        String remaining = BountyFormatter.formatRemainingTime(entry.expiry(), Instant.now());
        y = renderDetailSection(extractor, font, contentX, y, contentW, "TID KVAR", remaining, GZTheme.COLOR_TEXT_PRIMARY);

        String command = BountyFormatter.bountyInfoCommand(entry.name());
        if (command != null) {
            y += 3;
            UiRect copyBtn = new UiRect(contentX, y, Math.min(contentW, 150), 13);
            boolean showingFeedback = System.currentTimeMillis() < copyFeedbackExpiryMs;
            boolean hov = copyBtn.contains(mouseX, mouseY);
            GZTheme.drawButton(extractor, font, copyBtn, showingFeedback ? "Kopierat!" : ("Kopiera " + command), false, hov, TypographyScale.META.getScale());
            hitTargets.add(new Hit(copyBtn, () -> copyCommand(command)));
        }
    }

    private int renderDetailSection(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, String label, String value, int valueColor) {
        TextUtil.drawScaledText(extractor, font, label, x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 9;
        TextUtil.drawScaledEllipsizedText(extractor, font, value, x, y, width, TypographyScale.BODY.getScale(), valueColor, true);
        return y + 10;
    }

    /** Copies the exact documented {@code /bounty info <name>} command to the OS clipboard - a
     * pure local convenience triggered ONLY by this deliberate click. Never sends the command,
     * never types it into chat - matches {@code CommandsTabComponent.copyCommand}'s convention. */
    private void copyCommand(String command) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.keyboardHandler != null) {
                client.keyboardHandler.setClipboard(command);
                copyFeedbackExpiryMs = System.currentTimeMillis() + 2000;
            }
        } catch (Exception ignored) {
            // Clipboard access is a pure local OS convenience - never let a failure here affect anything else.
        }
    }

    // ------------------------------------------------------------------
    // Footer
    // ------------------------------------------------------------------

    private void renderFooter(GuiGraphicsExtractor extractor, Font font, BountyLayout layout, int mouseX, int mouseY,
                               BountyManager manager, BountySnapshot snapshot) {
        UiRect footerRect = layout.footerRect();
        String detail = BountyFormatter.freshnessDetail(snapshot, Instant.now());
        TextUtil.drawScaledEllipsizedText(extractor, font, detail, footerRect.x(), footerRect.y() + 3, footerRect.width() - 60,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);

        UiRect refreshBtn = layout.refreshBtnRect();
        boolean canRefresh = manager.canManualRefresh();
        boolean hov = canRefresh && refreshBtn.contains(mouseX, mouseY);
        GZTheme.drawButton(extractor, font, refreshBtn, "Uppdatera", false, hov, TypographyScale.META.getScale());
        if (canRefresh) {
            hitTargets.add(new Hit(refreshBtn, manager::manualRefresh));
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;

        // List row selection is resolved by re-deriving the clicked row's index from the CURRENT
        // snapshot/layout at click time (never trusting stale per-frame Hit closures for row
        // identity) - see selectRowAt. All OTHER controls (back/refresh/copy) use the normal
        // render-order hitTargets list, exactly like every other tab component.
        BountyManager manager = CompanionSession.getInstance().getBountyManager();
        List<BountyEntry> entries = manager.getSnapshot().entries();
        BountyLayout layout = BountyLayout.calculate(bounds);
        boolean listVisible = !layout.isCompact() || !compactShowingDetail;
        if (listVisible && !entries.isEmpty() && layout.listRect().contains(mouseX, mouseY)) {
            int clickedIndex = rowIndexAt(layout.listRect(), mouseY, listScrollOffset, entries.size());
            if (clickedIndex >= 0) {
                selectedIndex = clickedIndex;
                if (layout.isCompact()) {
                    compactShowingDetail = true;
                }
                return true;
            }
        }

        for (Hit hit : hitTargets) {
            if (hit.rect().contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }
        return false;
    }

    /** Pure row-index-from-click-Y resolution for the scrollable list - static and side-effect-free
     * so it's directly unit-testable without a live Font/Minecraft instance. Returns -1 if the click
     * landed outside any row (e.g. in the list's own padding). */
    static int rowIndexAt(UiRect listRect, double mouseY, int scrollOffset, int entryCount) {
        int rowH = ROW_H;
        int relativeY = (int) mouseY - (listRect.y() + 2) + scrollOffset;
        if (relativeY < 0) return -1;
        int index = relativeY / rowH;
        if (index < 0 || index >= entryCount) return -1;
        return index;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        listScrollOffset = Math.max(0, listScrollOffset - (int) (scrollY * 14));
        return true;
    }
}
