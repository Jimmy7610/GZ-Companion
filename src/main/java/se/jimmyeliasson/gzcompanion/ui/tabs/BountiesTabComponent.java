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

    private static final int DETAIL_PAD = 5;
    /** Space the fixed "< Lista" back button (compact only) reserves above the scrollable detail
     * content - 11px button height + 4px gap, matching the button's own drawn height exactly. */
    private static final int DETAIL_BACK_BTN_RESERVED_H = 15;
    private static final int DETAIL_COPY_BTN_H = 13;
    private static final int DETAIL_COPY_BTN_GAP = 3;
    /** {@link #renderDetailSection}'s own net height contribution (a 9px label line plus a
     * returned {@code y + 10}) - kept as a named constant so {@link #calculateDetailContentHeight}
     * cannot silently drift out of sync with what that method actually draws. */
    private static final int DETAIL_SECTION_H = 19;

    private int selectedIndex = 0;
    private boolean compactShowingDetail = false;
    private int listScrollOffset = 0;
    /** Independent from {@link #listScrollOffset} - the compact layout shows at most one of the
     * list/detail panes at a time, but each still needs its own remembered scroll position (e.g.
     * scrolling into a long clue must not move the list, and going back to the list must not have
     * silently scrolled it). Always reset to 0 by {@link #selectRow} whenever a (possibly
     * different) bounty is opened. */
    private int detailScrollOffset = 0;
    private long copyFeedbackExpiryMs = 0L;

    private final List<Hit> hitTargets = new ArrayList<>();
    private record Hit(UiRect rect, Runnable action) {}

    /** Which pane (if any) a scroll event should affect - see {@link #resolveScrollTarget}. */
    enum ScrollTarget { LIST, DETAIL, NONE }

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

    /** Pure scroll-offset clamp, identical policy to {@code LeaderboardsTabComponent.clampScroll} -
     * used for BOTH {@link #listScrollOffset} and {@link #detailScrollOffset}, since the clamping
     * rule (never negative, never past where the content actually ends) is identical for either;
     * only the content height/viewport height passed in differ. */
    static int clampScroll(int offset, int totalContentHeight, int viewportHeight) {
        int maxScroll = Math.max(0, totalContentHeight - Math.max(1, viewportHeight));
        return Math.max(0, Math.min(offset, maxScroll));
    }

    /**
     * Pure - the fixed Y where scrollable detail content begins: just below the fixed "< Lista"
     * back button in compact mode (the button itself is drawn above this line, outside the
     * scrollable/scissored region, so it can never be scrolled out of reach), or just below the
     * pane's own top padding in wide mode (which has no back button at all).
     */
    static int detailContentTop(UiRect detailRect, boolean compact) {
        return detailRect.y() + DETAIL_PAD + (compact ? DETAIL_BACK_BTN_RESERVED_H : 0);
    }

    /**
     * Pure - the fixed Y where scrollable detail content must end, reserving room for the fixed
     * command-copy button (if the entry has one) plus the pane's own bottom padding. This bound -
     * not the copy button's own drawn position - is exactly what {@link #renderDetailPane} passes
     * to {@code enableScissor}, and it is always {@code <= detailRect.bottom()} by construction, so
     * scrollable content (an arbitrarily long name/clue/expiry line) can never draw over the copy
     * button, the pane's own border, or - since {@code detailRect} itself never extends into it -
     * the footer below the pane.
     */
    static int detailContentBottom(UiRect detailRect, boolean hasCommand) {
        int bottomFixedH = hasCommand ? (DETAIL_COPY_BTN_H + DETAIL_COPY_BTN_GAP) : 0;
        return detailRect.bottom() - DETAIL_PAD - bottomFixedH;
    }

    /**
     * Font-dependent (unlike every other helper in this class) - measures the real total height
     * the scrollable detail content (name, entity type, reward, clue, expiry) will occupy, so
     * {@link #renderDetailPane} can clamp {@link #detailScrollOffset} correctly via {@link
     * #clampScroll}. Deliberately mirrors, increment for increment, the exact vertical spacing
     * {@link #renderDetailPane} itself draws with - if that method's layout changes, this must
     * change with it. The clue is measured with the UNCAPPED {@link TextUtil#measureWrappedHeight}
     * (paired with {@link TextUtil#drawScaledWrappedTextUnbounded} in {@link #renderDetailPane}) -
     * GameZone's public clue is never truncated at some arbitrary line count; the scrollable
     * viewport, not a rendering cap, is what contains an unusually long one. Not unit-tested
     * directly (this project's test environment has no live {@code Font} - see this class's own
     * test file), but the pure clamp it feeds is fully tested against synthetic content heights,
     * and a live-client visual check remains required human QA.
     */
    static int calculateDetailContentHeight(Font font, int contentW, BountyEntry entry) {
        if (font == null) return 0;
        int h = 11; // name heading line
        if (entry.hasEntityType()) {
            h += 11;
        }
        h += 2; // gap before BELÖNING
        h += DETAIL_SECTION_H; // BELÖNING section
        h += 2; // gap before LEDTRÅD label
        h += 9; // LEDTRÅD label line
        String hintText = entry.hasHint() ? entry.hint() : "Ingen offentlig ledtråd";
        h += TextUtil.measureWrappedHeight(font, hintText, contentW, TypographyScale.SMALL.getScale(), 2);
        h += 4; // gap after clue
        h += DETAIL_SECTION_H; // TID KVAR section
        return h;
    }

    /**
     * Pure, Font/session-free resolution of which pane (if any) a scroll event should affect -
     * mirrors the exact visibility rules {@link #render}/{@link #mouseClicked} already use: in
     * compact mode exactly one of list/detail is ever visible at a time (governed by {@code
     * compactShowingDetail}); in wide mode both are visible simultaneously and routing is purely by
     * which rect the cursor is over. Never routes to a pane that isn't actually visible, and never
     * routes anywhere at all when there are no entries (the empty-state message shown in that case
     * is not scrollable).
     */
    static ScrollTarget resolveScrollTarget(BountyLayout layout, boolean compactShowingDetail, boolean hasEntries, double mouseX, double mouseY) {
        if (!hasEntries) {
            return ScrollTarget.NONE;
        }
        if (layout.isCompact()) {
            if (compactShowingDetail) {
                return layout.detailRect().contains(mouseX, mouseY) ? ScrollTarget.DETAIL : ScrollTarget.NONE;
            }
            return layout.listRect().contains(mouseX, mouseY) ? ScrollTarget.LIST : ScrollTarget.NONE;
        }
        if (layout.detailRect().contains(mouseX, mouseY)) return ScrollTarget.DETAIL;
        if (layout.listRect().contains(mouseX, mouseY)) return ScrollTarget.LIST;
        return ScrollTarget.NONE;
    }

    /**
     * The message shown in place of a list when there are no entries to show - distinguishes a
     * genuine "zero active bounties" SUCCESS from a real loading/failure/never-loaded state, per
     * this feature's explicit "do not show Error when the real result is simply no active
     * bounties" requirement.
     *
     * <p><b>{@code STALE} is deliberately its own case, not folded into the {@code LOADED}
     * wording.</b> A {@code STALE} empty snapshot means the LAST successful fetch found zero
     * bounties, but a MORE RECENT refresh attempt failed - GameZone may have created a bounty since
     * then, so Companion must never claim "there is no active hunt right now" here. Only a genuine
     * {@code LOADED} empty result (the current refresh itself succeeded) may say that.
     *
     * <p><b>{@code IDLE} is likewise its own case, not folded into {@code LOADED}.</b> {@code IDLE}
     * means the registry has never been successfully fetched at all (this normally flips to
     * {@code LOADING} the instant {@code ensureFresh} runs, but can persist/revert here if the
     * shared runtime rejects the submission) - it must never claim "no active bounties," since
     * Companion simply has no data yet, successful or otherwise.
     */
    static String emptyStateHeadline(BountyStatus status) {
        return switch (status) {
            case IDLE -> "VÄNTAR PÅ DATA";
            case LOADING -> "HÄMTAR BOUNTIES...";
            case UNAVAILABLE, ERROR -> "KUNDE INTE HÄMTA";
            case INCOMPATIBLE -> "OTILLGÄNGLIG";
            case STALE -> "INGA BOUNTIES I CACHAD DATA";
            case LOADED -> "INGA AKTIVA BOUNTIES";
        };
    }

    static String emptyStateBody(BountyStatus status) {
        return switch (status) {
            case IDLE -> "Bounty-registret har inte hämtats ännu.";
            case LOADING -> "";
            case UNAVAILABLE, ERROR -> "Kunde inte hämta bounty-registret just nu.";
            case INCOMPATIBLE -> "GameZone har ändrat gränssnittet - stöds inte just nu.";
            case STALE -> "Senast hämtade data innehöll inga aktiva jakter. Uppdatera för aktuell status.";
            case LOADED -> "Det finns ingen aktiv jakt just nu. Kontrollera igen senare.";
        };
    }

    /**
     * The small "X AKTIVA BOUNTIES" count strip shown above the list/detail panes - a separate,
     * smaller piece of text from {@link #emptyStateHeadline}/{@link #emptyStateBody}, so it must
     * independently respect the same "never claim a confirmed current state you don't actually
     * have" rule:
     *
     * <ul>
     *     <li>{@code LOADED} with zero entries is a genuine current success &rarr; "INGA AKTIVA
     *         BOUNTIES" is accurate here.</li>
     *     <li>{@code STALE} with zero entries must NOT repeat that claim - the current state is
     *         unknown (see {@link #emptyStateHeadline}'s doc comment) - so this strip is blank,
     *         leaving the main empty-state panel's own "INGA BOUNTIES I CACHAD DATA" copy as the
     *         only place that fact is stated.</li>
     *     <li>Any status without usable data ({@code IDLE}, {@code LOADING}, {@code UNAVAILABLE},
     *         {@code ERROR}, {@code INCOMPATIBLE}) shows nothing - there is no confirmed count to
     *         report at all.</li>
     *     <li>A nonzero count is shown identically for {@code LOADED} and {@code STALE} - a
     *         nonempty cache's bounty count is still meaningful/informative even while stale
     *         (the individual rows themselves are already visually marked cached via the header's
     *         "CACHAD" badge).</li>
     * </ul>
     */
    static String countLabel(BountyStatus status, int count) {
        if (!status.hasUsableData()) {
            return "";
        }
        if (count == 0) {
            return status == BountyStatus.LOADED ? "INGA AKTIVA BOUNTIES" : "";
        }
        return count + (count == 1 ? " AKTIV BOUNTY" : " AKTIVA BOUNTIES");
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
        String text = countLabel(snapshot.status(), count);
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

    /**
     * Renders the selected bounty's detail. Content (name, entity type, reward, clue, expiry) is
     * ALWAYS scissored to a fixed sub-region of {@code detailRect} and scrolled via {@link
     * #detailScrollOffset} - it can never draw outside {@code detailRect}, regardless of clue
     * length, name length, or window size (see {@link #detailContentTop}/{@link
     * #detailContentBottom}). The "< Lista" back button (compact only) and the command-copy button
     * are both deliberately drawn OUTSIDE that scissored/scrollable region, at fixed positions
     * pinned to the top and bottom of {@code detailRect} respectively - neither can ever be
     * scrolled out of reach, and the copy button can never overlap the footer below the pane since
     * it never leaves {@code detailRect} at all.
     */
    private void renderDetailPane(GuiGraphicsExtractor extractor, Font font, UiRect detailRect, int mouseX, int mouseY, BountyEntry entry, boolean compact) {
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        if (compact) {
            UiRect backBtn = new UiRect(detailRect.x() + DETAIL_PAD, detailRect.y() + DETAIL_PAD, 44, 11);
            boolean backHov = backBtn.contains(mouseX, mouseY);
            GZTheme.drawButton(extractor, font, backBtn, "< Lista", false, backHov, TypographyScale.SMALL.getScale());
            hitTargets.add(new Hit(backBtn, () -> compactShowingDetail = false));
        }

        String command = BountyFormatter.bountyInfoCommand(entry.name());
        boolean hasCommand = command != null;

        int contentX = detailRect.x() + DETAIL_PAD;
        int contentW = Math.max(10, detailRect.width() - DETAIL_PAD * 2);
        int contentTop = detailContentTop(detailRect, compact);
        int contentBottom = detailContentBottom(detailRect, hasCommand);
        int viewportH = Math.max(1, contentBottom - contentTop);

        int totalContentHeight = calculateDetailContentHeight(font, contentW, entry);
        detailScrollOffset = clampScroll(detailScrollOffset, totalContentHeight, viewportH);

        extractor.enableScissor(detailRect.x() + 1, contentTop, detailRect.right() - 1, contentBottom);
        int y = contentTop - detailScrollOffset;

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

        // The public clue is rendered in FULL, never capped at some arbitrary line count - GameZone
        // publishes it verbatim, and this scrollable/scissored detail pane (not a rendering cap) is
        // what contains an unusually long one. See calculateDetailContentHeight, which measures
        // this same text with the paired uncapped TextUtil.measureWrappedHeight.
        String hintText = entry.hasHint() ? entry.hint() : "Ingen offentlig ledtråd";
        y += 2;
        TextUtil.drawScaledText(extractor, font, "LEDTRÅD", contentX, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        y += 9;
        y += TextUtil.drawScaledWrappedTextUnbounded(extractor, font, hintText, contentX, y, contentW,
                TypographyScale.SMALL.getScale(), 2, GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 4;

        String remaining = BountyFormatter.formatRemainingTime(entry.expiry(), Instant.now());
        renderDetailSection(extractor, font, contentX, y, contentW, "TID KVAR", remaining, GZTheme.COLOR_TEXT_PRIMARY);

        extractor.disableScissor();

        if (hasCommand) {
            UiRect copyBtn = new UiRect(contentX, detailRect.bottom() - DETAIL_PAD - DETAIL_COPY_BTN_H,
                    Math.min(contentW, 150), DETAIL_COPY_BTN_H);
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
                selectRow(clickedIndex, layout.isCompact());
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

    /**
     * Applies a row selection - always resets {@link #detailScrollOffset} to the top, since a
     * freshly selected/opened bounty's detail must never inherit whatever scroll position a
     * PREVIOUSLY selected bounty was left at (applies equally whether a different bounty was
     * chosen, or the same one was re-opened from the compact list). Kept as its own small method,
     * rather than inlined into {@link #mouseClicked}, so this specific state transition is directly
     * unit-testable without a live {@code CompanionSession}.
     */
    void selectRow(int index, boolean compact) {
        this.selectedIndex = index;
        this.detailScrollOffset = 0;
        if (compact) {
            this.compactShowingDetail = true;
        }
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

    /**
     * Routes a scroll event to whichever pane is actually visible under the cursor, per {@link
     * #resolveScrollTarget} - list rows and detail content each keep their own independent scroll
     * offset, and a scroll over an invisible pane (e.g. the list while compact detail is showing)
     * is a no-op, never affecting the other pane's offset. {@code bounds} is the same tab-content
     * rect {@link #render}/{@link #mouseClicked} already receive.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, UiRect bounds) {
        BountyManager manager = CompanionSession.getInstance().getBountyManager();
        List<BountyEntry> entries = manager.getSnapshot().entries();
        BountyLayout layout = BountyLayout.calculate(bounds);

        ScrollTarget target = resolveScrollTarget(layout, compactShowingDetail, !entries.isEmpty(), mouseX, mouseY);
        switch (target) {
            case LIST -> {
                listScrollOffset = Math.max(0, listScrollOffset - (int) (scrollY * 14));
                return true;
            }
            case DETAIL -> {
                BountyEntry entry = entries.get(clampIndex(selectedIndex, entries.size()));
                Font font = Minecraft.getInstance().font;
                boolean hasCommand = BountyFormatter.bountyInfoCommand(entry.name()) != null;
                int contentW = Math.max(10, layout.detailRect().width() - DETAIL_PAD * 2);
                int viewportH = Math.max(1, detailContentBottom(layout.detailRect(), hasCommand)
                        - detailContentTop(layout.detailRect(), layout.isCompact()));
                int totalH = calculateDetailContentHeight(font, contentW, entry);
                detailScrollOffset = clampScroll(detailScrollOffset - (int) (scrollY * 14), totalH, viewportH);
                return true;
            }
            case NONE -> {
                return false;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Test-only accessors - package-private, read by BountiesTabComponentTest to verify scroll
    // state transitions without needing a live CompanionSession/Font.
    // ------------------------------------------------------------------

    int getListScrollOffsetForTesting() {
        return listScrollOffset;
    }

    void setListScrollOffsetForTesting(int value) {
        this.listScrollOffset = value;
    }

    int getDetailScrollOffsetForTesting() {
        return detailScrollOffset;
    }

    void setDetailScrollOffsetForTesting(int value) {
        this.detailScrollOffset = value;
    }

    boolean isCompactShowingDetailForTesting() {
        return compactShowingDetail;
    }
}
