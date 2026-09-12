package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.leaderboard.GameZoneLeaderboardRegistry;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardDefinition;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardEntry;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardFormatter;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardGroup;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardManager;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardSnapshot;
import se.jimmyeliasson.gzcompanion.leaderboard.LeaderboardStatus;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the Leaderboards tab: GameZone's public Spelare/Settlements/Företag/Servern leaderboards
 * (see docs/LEADERBOARDS.md), read-only, top 10 only, with a group selector, a central board
 * selector (arrows + dropdown picker), and a podium-style ranking list. Contains NO network or
 * parsing logic itself - it only ever reads {@link LeaderboardManager#getSnapshot} and triggers
 * {@link LeaderboardManager#ensureFresh}/{@link LeaderboardManager#manualRefresh}, per this
 * project's "keep orchestration testable, no logic in render code" convention (see
 * {@code LeaderboardManager}, {@code GameZoneLeaderboardSource}, {@code LeaderboardHtmlParser} for
 * where the real logic - and its tests - live).
 *
 * <p><b>Fair play / privacy:</b> the local Minecraft username is compared LOCALLY against an
 * already-downloaded public top-10 to show "DU" - it is never sent anywhere. See
 * {@link #isLocalPlayerRow}.
 */
public class LeaderboardsTabComponent {
    private static final int HEADER_H = 26;
    private static final int GROUP_TABS_H = 16;
    private static final int SELECTOR_H = 18;
    private static final int DESCRIPTION_H = 10;
    private static final int FOOTER_H = 14;
    private static final int GAP = 3;

    private static final int PODIUM1_ROW_H = 20;
    private static final int PODIUM23_ROW_H = 16;
    private static final int COMPACT_ROW_H = 12;

    private LeaderboardGroup selectedGroup = LeaderboardGroup.SPELARE;
    private final Map<LeaderboardGroup, LeaderboardDefinition> selectedBoardByGroup = new EnumMap<>(LeaderboardGroup.class);
    private boolean pickerOpen = false;
    private int listScrollOffset = 0;

    private final List<Hit> hitTargets = new ArrayList<>();
    private record Hit(UiRect rect, Runnable action) {}

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        hitTargets.clear();
        CompanionSession session = CompanionSession.getInstance();
        LeaderboardManager manager = session.getLeaderboardManager();
        LeaderboardDefinition current = currentDefinition();

        manager.ensureFresh(current);
        LeaderboardSnapshot snapshot = manager.getSnapshot(current);

        int y = bounds.y();
        y = renderHeader(extractor, font, bounds, y, snapshot);
        y += GAP;
        y = renderGroupTabs(extractor, font, bounds, y, mouseX, mouseY);
        y += GAP;
        y = renderSelector(extractor, font, bounds, y, mouseX, mouseY, current);
        y += 1;
        y = renderDescription(extractor, font, bounds, y, current);
        y += GAP;

        int footerY = bounds.bottom() - FOOTER_H;
        UiRect listRect = new UiRect(bounds.x(), y, bounds.width(), Math.max(10, footerY - GAP - y));
        renderList(extractor, font, listRect, mouseX, mouseY, session, snapshot);

        renderFooter(extractor, font, new UiRect(bounds.x(), footerY, bounds.width(), FOOTER_H), mouseX, mouseY, manager, current, snapshot);

        if (pickerOpen) {
            renderPicker(extractor, font, bounds, mouseX, mouseY, current);
        }
    }

    private LeaderboardDefinition currentDefinition() {
        return selectedBoardByGroup.computeIfAbsent(selectedGroup,
                g -> GameZoneLeaderboardRegistry.firstInGroup(g).orElse(null));
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    private int renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int y, LeaderboardSnapshot snapshot) {
        GZTheme.drawIcon(extractor, IconId.LEADERBOARDS, bounds.x(), y + 1, 11, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Leaderboards", bounds.x() + 14, y + 1, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        String freshness = LeaderboardFormatter.freshnessLabel(snapshot);
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

        TextUtil.drawScaledEllipsizedText(extractor, font, "Serverns främsta spelare, settlements och företag.",
                bounds.x(), y + 12, bounds.width(), TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        return y + HEADER_H;
    }

    // ------------------------------------------------------------------
    // Group tabs
    // ------------------------------------------------------------------

    private int renderGroupTabs(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int y, int mouseX, int mouseY) {
        LeaderboardGroup[] groups = LeaderboardGroup.values();
        int gap = 2;
        int tabW = (bounds.width() - (gap * (groups.length - 1))) / groups.length;
        int x = bounds.x();
        for (LeaderboardGroup group : groups) {
            UiRect tabRect = new UiRect(x, y, tabW, GROUP_TABS_H);
            boolean active = group == selectedGroup;
            boolean hovered = tabRect.contains(mouseX, mouseY);
            int bg = active ? GZTheme.COLOR_NAV_ACTIVE : (hovered ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER);
            int border = active ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
            GZTheme.drawCard(extractor, tabRect, bg, border);
            TextUtil.drawScaledCenteredText(extractor, font, group.getDisplayName().toUpperCase(), tabRect.x() + tabRect.width() / 2,
                    tabRect.y() + 4, tabRect.width() - 2, TypographyScale.META.getScale(),
                    active ? GZTheme.COLOR_MINT : GZTheme.COLOR_TEXT_SECONDARY, false);

            hitTargets.add(new Hit(tabRect, () -> {
                selectedGroup = group;
                pickerOpen = false;
                listScrollOffset = 0;
            }));
            x += tabW + gap;
        }
        return y + GROUP_TABS_H;
    }

    // ------------------------------------------------------------------
    // Board selector (arrows + center dropdown trigger)
    // ------------------------------------------------------------------

    private int renderSelector(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int y, int mouseX, int mouseY, LeaderboardDefinition current) {
        int arrowW = 16;
        UiRect leftArrow = new UiRect(bounds.x(), y, arrowW, SELECTOR_H);
        UiRect rightArrow = new UiRect(bounds.right() - arrowW, y, arrowW, SELECTOR_H);
        UiRect centerRect = new UiRect(leftArrow.right() + 2, y, rightArrow.x() - leftArrow.right() - 4, SELECTOR_H);

        boolean leftHov = leftArrow.contains(mouseX, mouseY);
        boolean rightHov = rightArrow.contains(mouseX, mouseY);
        GZTheme.drawCard(extractor, leftArrow, leftHov ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        GZTheme.drawCard(extractor, rightArrow, rightHov ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledCenteredText(extractor, font, "◀", leftArrow.x() + leftArrow.width() / 2, leftArrow.y() + 5, leftArrow.width(), TypographyScale.BODY.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        TextUtil.drawScaledCenteredText(extractor, font, "▶", rightArrow.x() + rightArrow.width() / 2, rightArrow.y() + 5, rightArrow.width(), TypographyScale.BODY.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);

        boolean centerHov = centerRect.contains(mouseX, mouseY);
        GZTheme.drawCard(extractor, centerRect, centerHov ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_EMERALD);
        String title = current != null ? current.title().toUpperCase() : "-";
        TextUtil.drawScaledCenteredText(extractor, font, title + "  ▼", centerRect.x() + centerRect.width() / 2, centerRect.y() + 5,
                centerRect.width() - 4, TypographyScale.BODY.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        List<LeaderboardDefinition> boardsInGroup = GameZoneLeaderboardRegistry.byGroup(selectedGroup);
        hitTargets.add(new Hit(leftArrow, () -> cycleBoard(boardsInGroup, -1)));
        hitTargets.add(new Hit(rightArrow, () -> cycleBoard(boardsInGroup, 1)));
        hitTargets.add(new Hit(centerRect, () -> pickerOpen = !pickerOpen));

        return y + SELECTOR_H;
    }

    private void cycleBoard(List<LeaderboardDefinition> boardsInGroup, int direction) {
        if (boardsInGroup.isEmpty()) return;
        LeaderboardDefinition current = selectedBoardByGroup.get(selectedGroup);
        int idx = Math.max(0, boardsInGroup.indexOf(current));
        int nextIdx = nextCycleIndex(idx, boardsInGroup.size(), direction);
        selectedBoardByGroup.put(selectedGroup, boardsInGroup.get(nextIdx));
        listScrollOffset = 0;
    }

    /** Pure wraparound arithmetic for the ◀/▶ selector arrows - left from index 0 wraps to the
     * last board, right from the last board wraps back to 0. Static and side-effect-free so it's
     * directly unit-testable without constructing a whole tab component. */
    static int nextCycleIndex(int currentIndex, int size, int direction) {
        if (size <= 0) return 0;
        return Math.floorMod(currentIndex + direction, size);
    }

    /** Pure scroll-offset clamp shared by rendering and mouse-wheel input - never negative, never
     * past the point where the list's bottom would leave empty space above the last row. */
    static int clampScroll(int offset, int totalContentHeight, int viewportHeight) {
        int maxScroll = Math.max(0, totalContentHeight - Math.max(1, viewportHeight));
        return Math.max(0, Math.min(offset, maxScroll));
    }

    private int renderDescription(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int y, LeaderboardDefinition current) {
        String desc = current != null ? current.description() : "";
        TextUtil.drawScaledEllipsizedText(extractor, font, desc, bounds.x() + 2, y, bounds.width() - 4,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        return y + DESCRIPTION_H;
    }

    // ------------------------------------------------------------------
    // Dropdown picker
    // ------------------------------------------------------------------

    private void renderPicker(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, LeaderboardDefinition current) {
        List<LeaderboardDefinition> boards = GameZoneLeaderboardRegistry.byGroup(selectedGroup);
        int rowH = 12;
        int pickerH = Math.min(bounds.height() - 20, boards.size() * rowH + 4);
        UiRect pickerRect = new UiRect(bounds.x() + 10, bounds.y() + HEADER_H + GROUP_TABS_H + GAP * 2, bounds.width() - 20, pickerH);
        GZTheme.drawCard(extractor, pickerRect, GZTheme.COLOR_CARD_HOVER, GZTheme.COLOR_BORDER_EMERALD);

        int rowY = pickerRect.y() + 2;
        for (LeaderboardDefinition def : boards) {
            UiRect rowRect = new UiRect(pickerRect.x() + 2, rowY, pickerRect.width() - 4, rowH);
            boolean isCurrent = def.equals(current);
            boolean hov = rowRect.contains(mouseX, mouseY);
            if (isCurrent || hov) {
                GZTheme.drawCard(extractor, rowRect, isCurrent ? GZTheme.COLOR_NAV_ACTIVE : GZTheme.COLOR_NAV_HOVER, 0);
            }
            TextUtil.drawScaledEllipsizedText(extractor, font, def.title(), rowRect.x() + 3, rowRect.y() + 2, rowRect.width() - 6,
                    TypographyScale.SMALL.getScale(), isCurrent ? GZTheme.COLOR_MINT : GZTheme.COLOR_TEXT_SECONDARY, false);
            hitTargets.add(new Hit(rowRect, () -> {
                selectedBoardByGroup.put(selectedGroup, def);
                pickerOpen = false;
                listScrollOffset = 0;
            }));
            rowY += rowH;
        }

        // Clicking anywhere else while open closes the picker - handled in mouseClicked's fallback.
    }

    // ------------------------------------------------------------------
    // Ranking list
    // ------------------------------------------------------------------

    private int computeListContentHeight(List<LeaderboardEntry> entries) {
        int h = 0;
        for (int i = 0; i < entries.size(); i++) {
            h += rowHeightFor(i, entries.get(i));
        }
        return h;
    }

    /** Pure row-height policy - #1 gets the tallest podium treatment, #2/#3 a smaller podium
     * treatment, #4-10 a compact row (slightly taller if a secondary value needs its own line).
     * Package-visible and static (no Font/instance state needed) so it's directly unit-testable,
     * matching this project's convention for UI logic that doesn't need a live Minecraft Font (see
     * {@code MarketWatchTabComponent.widestButtonWidth}). */
    static int rowHeightFor(int index, LeaderboardEntry entry) {
        if (index == 0) return PODIUM1_ROW_H;
        if (index == 1 || index == 2) return PODIUM23_ROW_H;
        return entry.hasSecondaryValue() ? COMPACT_ROW_H + 8 : COMPACT_ROW_H;
    }

    private void renderList(GuiGraphicsExtractor extractor, Font font, UiRect listRect, int mouseX, int mouseY,
                             CompanionSession session, LeaderboardSnapshot snapshot) {
        GZTheme.drawCard(extractor, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        if (snapshot.status() == LeaderboardStatus.LOADING && !snapshot.hasEntries()) {
            TextUtil.drawCenteredText(extractor, font, "Hämtar topplista...", listRect.x() + listRect.width() / 2,
                    listRect.y() + listRect.height() / 2 - 4, listRect.width(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }
        if (!snapshot.hasEntries()) {
            String message = switch (snapshot.status()) {
                case INCOMPATIBLE -> "GameZone har ändrat sidan - den här topplistan stöds inte just nu.";
                case UNAVAILABLE, ERROR -> "Kunde inte hämta topplistan just nu.";
                default -> "Ingen data ännu.";
            };
            TextUtil.drawScaledWrappedText(extractor, font, message, listRect.x() + 4, listRect.y() + 4, listRect.width() - 8,
                    TypographyScale.SMALL.getScale(), 4, 1, GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        List<LeaderboardEntry> entries = snapshot.entries();
        int totalH = computeListContentHeight(entries);
        listScrollOffset = clampScroll(listScrollOffset, totalH, listRect.height() - 4);

        extractor.enableScissor(listRect.x() + 1, listRect.y() + 1, listRect.right() - 1, listRect.bottom() - 1);
        int y = listRect.y() + 2 - listScrollOffset;
        String localPlayerName = session.getBridge().getPlayerName();

        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            int rowH = rowHeightFor(i, entry);
            if (y + rowH >= listRect.y() && y <= listRect.bottom()) {
                boolean isLocal = isLocalPlayerRow(entry, localPlayerName);
                if (i == 0) {
                    renderPodiumRow(extractor, font, listRect.x() + 2, y, listRect.width() - 4, rowH, entry, isLocal, GZTheme.COLOR_MINT);
                } else if (i == 1 || i == 2) {
                    renderPodiumRow(extractor, font, listRect.x() + 2, y, listRect.width() - 4, rowH, entry, isLocal, GZTheme.COLOR_TEXT_PRIMARY);
                } else {
                    renderCompactRow(extractor, font, listRect.x() + 2, y, listRect.width() - 4, rowH, entry, isLocal);
                }
            }
            y += rowH;
        }
        extractor.disableScissor();
    }

    /**
     * LOCAL-ONLY comparison against an already-downloaded public top-10 - the username is never
     * sent to GameZone or anywhere else (see class doc comment). Non-player boards (settlements,
     * companies, the server itself) never highlight - Companion never guesses which settlement or
     * company belongs to the local player.
     */
    static boolean isLocalPlayerRow(LeaderboardEntry entry, String localPlayerName) {
        if (localPlayerName == null || localPlayerName.isBlank()) return false;
        return entry.displayName().equalsIgnoreCase(localPlayerName);
    }

    private void renderPodiumRow(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int rowH,
                                  LeaderboardEntry entry, boolean isLocal, int rankColor) {
        int bg = isLocal ? GZTheme.COLOR_NAV_ACTIVE : GZTheme.COLOR_CARD_INNER;
        int border = isLocal ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, x, y, width, rowH - 1, bg, border);

        float rankScale = rowH == PODIUM1_ROW_H ? TypographyScale.DISPLAY.getScale() : TypographyScale.HEADING.getScale();
        String rankText = "#" + entry.rank();
        TextUtil.drawScaledText(extractor, font, rankText, x + 3, y + (rowH - 10) / 2, rankScale, rankColor, true);
        int rankW = TextUtil.scaledWidth(font, rankText, rankScale) + 6;

        int valueW = TextUtil.scaledWidth(font, entry.primaryValue(), TypographyScale.SMALL.getScale()) + 4;
        int nameX = x + rankW;
        int nameMaxW = Math.max(10, width - rankW - valueW - (isLocal ? 16 : 4));
        TextUtil.drawScaledEllipsizedText(extractor, font, entry.displayName(), nameX, y + 3, nameMaxW,
                TypographyScale.BODY.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
        if (isLocal) {
            TextUtil.drawScaledText(extractor, font, "DU", nameX + nameMaxW + 2, y + 3, TypographyScale.META.getScale(), GZTheme.COLOR_MINT, false);
        }

        TextUtil.drawScaledRightAlignedText(extractor, font, entry.primaryValue(), x + width - 3, y + 3, width - rankW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);

        if (entry.hasSecondaryValue()) {
            TextUtil.drawScaledEllipsizedText(extractor, font, entry.secondaryValue(), nameX, y + 12, width - rankW - 4,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }
    }

    private void renderCompactRow(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int rowH,
                                   LeaderboardEntry entry, boolean isLocal) {
        UiRect rowRect = new UiRect(x, y, width, rowH - 1);
        if (isLocal) {
            GZTheme.drawCard(extractor, rowRect, GZTheme.COLOR_NAV_ACTIVE, GZTheme.COLOR_BORDER_EMERALD);
        }

        String rankText = "#" + entry.rank();
        int rankW = TextUtil.scaledWidth(font, rankText, TypographyScale.SMALL.getScale()) + 4;
        TextUtil.drawScaledText(extractor, font, rankText, x + 2, y + 2, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);

        int valueW = TextUtil.scaledWidth(font, entry.primaryValue(), TypographyScale.SMALL.getScale()) + 4;
        int nameX = x + rankW + 2;
        int nameMaxW = Math.max(10, width - rankW - valueW - (isLocal ? 16 : 4) - 2);
        TextUtil.drawScaledEllipsizedText(extractor, font, entry.displayName(), nameX, y + 2, nameMaxW,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        if (isLocal) {
            TextUtil.drawScaledText(extractor, font, "DU", nameX + nameMaxW + 2, y + 2, TypographyScale.META.getScale(), GZTheme.COLOR_MINT, false);
        }

        TextUtil.drawScaledRightAlignedText(extractor, font, entry.primaryValue(), x + width - 2, y + 2, width - rankW - 2,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);

        if (entry.hasSecondaryValue()) {
            TextUtil.drawScaledEllipsizedText(extractor, font, entry.secondaryValue(), nameX, y + 11, width - rankW - 4,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }
    }

    // ------------------------------------------------------------------
    // Footer
    // ------------------------------------------------------------------

    private void renderFooter(GuiGraphicsExtractor extractor, Font font, UiRect footerRect, int mouseX, int mouseY,
                               LeaderboardManager manager, LeaderboardDefinition current, LeaderboardSnapshot snapshot) {
        String detail = LeaderboardFormatter.freshnessDetail(snapshot, Instant.now());
        TextUtil.drawScaledEllipsizedText(extractor, font, detail, footerRect.x(), footerRect.y() + 3, footerRect.width() - 60,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);

        UiRect refreshBtn = new UiRect(footerRect.right() - 56, footerRect.y(), 56, footerRect.height() - 1);
        boolean canRefresh = manager.canManualRefresh(current);
        boolean hov = canRefresh && refreshBtn.contains(mouseX, mouseY);
        GZTheme.drawButton(extractor, font, refreshBtn, "Uppdatera", false, hov, TypographyScale.META.getScale());
        if (canRefresh) {
            hitTargets.add(new Hit(refreshBtn, () -> manager.manualRefresh(current)));
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        for (Hit hit : hitTargets) {
            if (hit.rect().contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }
        if (pickerOpen) {
            pickerOpen = false;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        listScrollOffset = Math.max(0, listScrollOffset - (int) (scrollY * 14));
        return true;
    }
}
