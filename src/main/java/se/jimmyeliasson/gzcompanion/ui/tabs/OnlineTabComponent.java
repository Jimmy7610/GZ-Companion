package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.online.OnlinePlayerRow;
import se.jimmyeliasson.gzcompanion.online.OnlinePlayersGrouping;
import se.jimmyeliasson.gzcompanion.online.OnlinePlayersView;
import se.jimmyeliasson.gzcompanion.online.OnlinePresence;
import se.jimmyeliasson.gzcompanion.online.PingQuality;
import se.jimmyeliasson.gzcompanion.settings.SettingsManager;
import se.jimmyeliasson.gzcompanion.settlement.storage.MemberNote;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.TextInputHandler;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.OnlineLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Online tab: a clean, beginner-friendly view of the players the normal Minecraft
 * client already knows are online (via {@link se.jimmyeliasson.gzcompanion.minecraft.MinecraftBridge#getOnlinePlayers()}),
 * grouped into FAVORITER / MIN SETTLEMENT / ÖVRIGA ONLINE (see {@link OnlinePlayersGrouping}),
 * with local-only favorites and a simple player detail card.
 *
 * <p><b>Fair play:</b> this tab only ever shows what Minecraft's own player list already delivered
 * to this client - never coordinates, distance, inventory, movement, or join/leave history. See
 * docs/ONLINE-PLAYERS.md.
 */
public class OnlineTabComponent implements TextInputHandler {
    private static final int MAX_SEARCH_LENGTH = 24;
    private static final int ROW_H = 12;
    private static final int SECTION_HEADER_H = 10;
    private static final int SECTION_GAP = 3;
    private static final String DISCONNECTED_MESSAGE = "Online-listan är tillgänglig när du är ansluten till GameZoneMC.";
    private static final String NO_PLAYERS_MESSAGE = "Inga spelare matchar sökningen.";

    private String searchText = "";
    private boolean searchFocused = false;
    private String selectedPlayerName = null;
    private int listScrollOffset = 0;
    private boolean compactShowingDetail = false;
    private long copyFeedbackExpiry = 0L;

    private OnlineLayout layout;
    private final List<ListRowHit> hitTargets = new ArrayList<>();

    private record ListRowHit(UiRect rect, Runnable action) {}

    public OnlineLayout getLayout() {
        return layout;
    }

    @Override
    public boolean isTextInputFocused() {
        return searchFocused;
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.layout = OnlineLayout.calculate(bounds);
        hitTargets.clear();

        CompanionSession session = CompanionSession.getInstance();
        SettingsManager settingsManager = session.getSettingsManager();
        boolean connected = session.getBridge().isConnectedToGameZone();
        List<OnlinePlayerSnapshot> onlinePlayers = connected ? session.getBridge().getOnlinePlayers() : List.of();
        List<String> favorites = settingsManager.getSettings().favoritePlayers();
        List<String> settlementMembers = session.getSettlementPlannerManager()
                .getMembers(session.getCurrentStorageContext())
                .stream().map(MemberNote::playerName).toList();

        OnlinePlayersView view = OnlinePlayersGrouping.build(onlinePlayers, connected, favorites, settlementMembers, searchText);

        renderHeader(extractor, font, layout.headerRect(), view, connected);
        renderStatus(extractor, font, layout.statusRect(), connected);
        renderSearch(extractor, font, mouseX, mouseY);

        if (layout.isCompact()) {
            if (compactShowingDetail && selectedPlayerName != null) {
                renderDetail(extractor, font, layout.detailRect(), mouseX, mouseY, view, session, true);
            } else {
                renderList(extractor, font, layout.listRect(), mouseX, mouseY, view, connected, settingsManager);
            }
        } else {
            renderList(extractor, font, layout.listRect(), mouseX, mouseY, view, connected, settingsManager);
            renderDetail(extractor, font, layout.detailRect(), mouseX, mouseY, view, session, false);
        }
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, OnlinePlayersView view, boolean connected) {
        TextUtil.drawScaledText(extractor, font, "Online", headerRect.x(), headerRect.y() + 1, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
        if (connected) {
            String label = view.onlineCount() + " spelare";
            int w = TextUtil.scaledWidth(font, label, TypographyScale.META.getScale());
            TextUtil.drawScaledText(extractor, font, label, headerRect.right() - w, headerRect.y() + 2, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }
    }

    private void renderStatus(GuiGraphicsExtractor extractor, Font font, UiRect statusRect, boolean connected) {
        GZTheme.drawCard(extractor, statusRect, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        GZTheme.drawStatusDot(extractor, statusRect.x() + 4, statusRect.y() + 4, connected ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_GREY);
        String label = connected ? "GameZoneMC · Ansluten" : "Inte ansluten till GameZoneMC";
        TextUtil.drawScaledEllipsizedText(extractor, font, label, statusRect.x() + 11, statusRect.y() + 2, statusRect.width() - 14,
                TypographyScale.SMALL.getScale(), connected ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED, false);
    }

    private void renderSearch(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        UiRect searchRect = layout.searchRect();
        UiRect clearBtnRect = layout.clearBtnRect();
        int bg = searchFocused ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER;
        int border = searchFocused ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, searchRect, bg, border);

        int textX = searchRect.x() + 4;
        int textY = searchRect.y() + 3;
        int maxW = searchRect.width() - 8;

        if (searchText.isEmpty() && !searchFocused) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Sök spelare...", textX, textY, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        } else {
            String shown = searchText + (searchFocused && ((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, shown, textX, textY, maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        }

        if (!searchText.isEmpty()) {
            boolean hov = clearBtnRect.contains(mouseX, mouseY);
            GZTheme.drawCard(extractor, clearBtnRect, hov ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
            TextUtil.drawCenteredText(extractor, font, "x", clearBtnRect.x() + (clearBtnRect.width() / 2), clearBtnRect.y() + 2,
                    clearBtnRect.width(), hov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED, false);
        }
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    /**
     * The single source of truth for the scrolled list's total content height - used identically
     * by {@link #renderList} and its own max-scroll calculation, so render height and estimated
     * height can never drift apart (the exact class of bug a prior Building Planner human-QA pass
     * found and fixed).
     */
    private int computeListContentHeight(Font font, int maxW, OnlinePlayersView view, boolean connected) {
        int h = 0;
        boolean any = false;
        if (!view.favorites().isEmpty()) {
            h += SECTION_HEADER_H + (view.favorites().size() * ROW_H);
            any = true;
        }
        if (!connected) {
            if (any) h += SECTION_GAP;
            h += TextUtil.measureWrappedHeightCapped(font, DISCONNECTED_MESSAGE, maxW, TypographyScale.SMALL.getScale(), 4, 1);
            any = true;
        } else {
            if (!view.settlementMembers().isEmpty()) {
                if (any) h += SECTION_GAP;
                h += SECTION_HEADER_H + (view.settlementMembers().size() * ROW_H);
                any = true;
            }
            if (!view.others().isEmpty()) {
                if (any) h += SECTION_GAP;
                h += SECTION_HEADER_H + (view.others().size() * ROW_H);
                any = true;
            }
        }
        if (!any) {
            h = ROW_H;
        }
        return h;
    }

    private void renderList(GuiGraphicsExtractor extractor, Font font, UiRect listRect, int mouseX, int mouseY,
                             OnlinePlayersView view, boolean connected, SettingsManager settingsManager) {
        GZTheme.drawCard(extractor, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int innerX = listRect.x() + 2;
        int innerW = listRect.width() - 4;
        int totalContentH = computeListContentHeight(font, innerW, view, connected);
        int maxScroll = Math.max(0, totalContentH - Math.max(1, listRect.height() - 4));
        listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));

        extractor.enableScissor(listRect.x() + 1, listRect.y() + 1, listRect.right() - 1, listRect.bottom() - 1);
        int currentY = listRect.y() + 3 - listScrollOffset;
        boolean any = false;

        if (!view.favorites().isEmpty()) {
            currentY = renderSection(extractor, font, innerX, currentY, innerW, "FAVORITER", view.favorites(), mouseX, mouseY, listRect, settingsManager);
            any = true;
        }

        if (!connected) {
            if (any) currentY += SECTION_GAP;
            if (currentY + 10 >= listRect.y() && currentY <= listRect.bottom()) {
                currentY += TextUtil.drawScaledWrappedText(extractor, font, DISCONNECTED_MESSAGE, innerX, currentY, innerW,
                        TypographyScale.SMALL.getScale(), 4, 1, GZTheme.COLOR_TEXT_MUTED, false);
            } else {
                currentY += TextUtil.measureWrappedHeightCapped(font, DISCONNECTED_MESSAGE, innerW, TypographyScale.SMALL.getScale(), 4, 1);
            }
            any = true;
        } else {
            if (!view.settlementMembers().isEmpty()) {
                if (any) currentY += SECTION_GAP;
                currentY = renderSection(extractor, font, innerX, currentY, innerW, "MIN SETTLEMENT", view.settlementMembers(), mouseX, mouseY, listRect, settingsManager);
                any = true;
            }
            if (!view.others().isEmpty()) {
                if (any) currentY += SECTION_GAP;
                currentY = renderSection(extractor, font, innerX, currentY, innerW, "ÖVRIGA ONLINE", view.others(), mouseX, mouseY, listRect, settingsManager);
                any = true;
            }
        }

        if (!any) {
            TextUtil.drawScaledText(extractor, font, NO_PLAYERS_MESSAGE, innerX, currentY, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }

        extractor.disableScissor();
    }

    private int renderSection(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, String title,
                               List<OnlinePlayerRow> rows, int mouseX, int mouseY, UiRect listRect, SettingsManager settingsManager) {
        if (y + SECTION_HEADER_H >= listRect.y() && y <= listRect.bottom()) {
            TextUtil.drawScaledText(extractor, font, title, x, y, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }
        y += SECTION_HEADER_H;
        for (OnlinePlayerRow row : rows) {
            if (y + ROW_H >= listRect.y() && y <= listRect.bottom()) {
                renderPlayerRow(extractor, font, x, y, width, row, mouseX, mouseY, settingsManager);
            }
            y += ROW_H;
        }
        return y;
    }

    private void renderPlayerRow(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, OnlinePlayerRow row,
                                  int mouseX, int mouseY, SettingsManager settingsManager) {
        UiRect rowRect = new UiRect(x, y, width, ROW_H);
        boolean isSelected = row.displayName().equalsIgnoreCase(selectedPlayerName);
        boolean isHovered = rowRect.contains(mouseX, mouseY);
        int bg = isSelected ? GZTheme.COLOR_NAV_ACTIVE : (isHovered ? GZTheme.COLOR_NAV_HOVER : 0);
        if (bg != 0) GZTheme.drawCard(extractor, rowRect, bg, isSelected ? GZTheme.COLOR_BORDER_EMERALD : 0);

        UiRect starRect = new UiRect(x + 1, y + 1, 10, 10);
        TextUtil.drawScaledText(extractor, font, row.favorite() ? "★" : "☆", starRect.x(), starRect.y(),
                TypographyScale.SMALL.getScale(), row.favorite() ? GZTheme.COLOR_STATUS_YELLOW : GZTheme.COLOR_TEXT_MUTED, false);
        String playerName = row.displayName();
        hitTargets.add(new ListRowHit(starRect, () -> settingsManager.toggleFavoritePlayer(playerName)));

        int statusW = 16;
        int nameX = x + 12;
        int nameMaxW = Math.max(10, width - 12 - statusW - 16);
        TextUtil.drawScaledEllipsizedText(extractor, font, row.displayName(), nameX, y + 2, nameMaxW, TypographyScale.SMALL.getScale(),
                row.localPlayer() ? GZTheme.COLOR_MINT : GZTheme.COLOR_TEXT_PRIMARY, false);
        if (row.localPlayer()) {
            TextUtil.drawScaledText(extractor, font, "DU", nameX + nameMaxW + 2, y + 2, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }

        int statusX = x + width - 10;
        switch (row.presence()) {
            case ONLINE -> {
                int dotColor = row.latencyMs() != null ? pingColorArgb(PingQuality.fromLatencyMs(row.latencyMs())) : GZTheme.COLOR_STATUS_GREEN;
                GZTheme.drawStatusDot(extractor, statusX, y + 4, dotColor);
            }
            case NOT_ONLINE -> TextUtil.drawScaledText(extractor, font, "○", statusX - 2, y + 2, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            case UNKNOWN -> TextUtil.drawScaledText(extractor, font, "?", statusX, y + 2, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }

        hitTargets.add(new ListRowHit(new UiRect(nameX, y, Math.max(10, width - 12 - statusW), ROW_H), () -> {
            selectedPlayerName = playerName;
            compactShowingDetail = true;
        }));
    }

    private static int pingColorArgb(PingQuality quality) {
        return switch (quality) {
            case GOOD -> GZTheme.COLOR_STATUS_GREEN;
            case OK -> GZTheme.COLOR_STATUS_YELLOW;
            case POOR -> GZTheme.COLOR_STATUS_RED;
        };
    }

    private static String pingLabel(PingQuality quality) {
        return switch (quality) {
            case GOOD -> "Bra";
            case OK -> "OK";
            case POOR -> "Dålig";
        };
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    private void renderDetail(GuiGraphicsExtractor extractor, Font font, UiRect detailRect, int mouseX, int mouseY,
                               OnlinePlayersView view, CompanionSession session, boolean isCompact) {
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int contentTop = detailRect.y() + (isCompact ? 16 : 4);
        UiRect contentArea = new UiRect(detailRect.x() + 1, contentTop, detailRect.width() - 2, detailRect.bottom() - contentTop - 1);

        if (isCompact) {
            UiRect backBtn = layout.backBtnRect();
            GZTheme.drawButton(extractor, font, backBtn, "< Lista", false, backBtn.contains(mouseX, mouseY), TypographyScale.SMALL.getScale());
            hitTargets.add(new ListRowHit(backBtn, () -> compactShowingDetail = false));
        }

        if (selectedPlayerName == null) {
            TextUtil.drawCenteredText(extractor, font, "Välj en spelare", contentArea.x() + (contentArea.width() / 2),
                    contentArea.y() + (contentArea.height() / 2) - 4, contentArea.width(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        OnlinePlayerRow row = view.allRows().stream()
                .filter(r -> r.displayName().equalsIgnoreCase(selectedPlayerName))
                .findFirst().orElse(null);
        if (row == null) {
            TextUtil.drawCenteredText(extractor, font, "Spelaren visas inte just nu", contentArea.x() + (contentArea.width() / 2),
                    contentArea.y() + (contentArea.height() / 2) - 4, contentArea.width(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        int x = contentArea.x() + 5;
        int maxW = contentArea.width() - 10;
        int y = contentArea.y() + 3;

        TextUtil.drawScaledEllipsizedText(extractor, font, row.displayName() + (row.localPlayer() ? " (DU)" : ""), x, y, maxW,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        y += 12;

        String statusLabel = switch (row.presence()) {
            case ONLINE -> "Online";
            case NOT_ONLINE -> "Inte online";
            case UNKNOWN -> "Status okänd";
        };
        int statusColor = row.presence() == OnlinePresence.ONLINE ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED;
        GZTheme.drawStatusDot(extractor, x, y + 2, statusColor);
        TextUtil.drawScaledText(extractor, font, statusLabel, x + 7, y, TypographyScale.SMALL.getScale(), statusColor, false);
        y += 11;

        if (row.presence() == OnlinePresence.ONLINE && row.latencyMs() != null) {
            PingQuality pq = PingQuality.fromLatencyMs(row.latencyMs());
            TextUtil.drawScaledText(extractor, font, "Ping: " + pingLabel(pq), x, y, TypographyScale.META.getScale(), pingColorArgb(pq), false);
            y += 10;
        }

        if (row.favorite()) {
            TextUtil.drawScaledText(extractor, font, "★ Favorit", x, y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_STATUS_YELLOW, false);
            y += 10;
        }
        if (row.settlementMember()) {
            TextUtil.drawScaledText(extractor, font, "Settlementmedlem", x, y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 10;
        }

        y += 4;
        UiRect favBtn = new UiRect(x, y, Math.min(120, maxW), 11);
        String favLabel = row.favorite() ? "☆ Ta bort favorit" : "★ Lägg till favorit";
        GZTheme.drawButton(extractor, font, favBtn, favLabel, false, favBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        String pName = row.displayName();
        SettingsManager settingsManager = session.getSettingsManager();
        hitTargets.add(new ListRowHit(favBtn, () -> settingsManager.toggleFavoritePlayer(pName)));
        y += 13;

        UiRect copyBtn = new UiRect(x, y, Math.min(100, maxW), 11);
        GZTheme.drawButton(extractor, font, copyBtn, "Kopiera namn", false, copyBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        hitTargets.add(new ListRowHit(copyBtn, () -> copyToClipboard(pName)));
        y += 13;

        if (copyFeedbackExpiry > System.currentTimeMillis()) {
            TextUtil.drawScaledText(extractor, font, "Kopierat!", x, y, TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_GREEN, false);
        }
    }

    /** Copies the exact username to the OS clipboard - never sends chat, never runs a command. */
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
        if (layout == null) layout = OnlineLayout.calculate(bounds);

        boolean insideSearch = layout.searchRect().contains(mouseX, mouseY);
        boolean insideClear = layout.clearBtnRect().contains(mouseX, mouseY);

        if (insideClear) {
            searchText = "";
            return true;
        }

        searchFocused = insideSearch;
        if (insideSearch) {
            return true;
        }

        if (layout.isCompact() && compactShowingDetail && layout.backBtnRect().contains(mouseX, mouseY)) {
            compactShowingDetail = false;
            return true;
        }

        for (ListRowHit hit : hitTargets) {
            if (hit.rect().contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null) return false;

        if (layout.isCompact() && compactShowingDetail) {
            return false;
        }

        if (layout.listRect().contains(mouseX, mouseY)) {
            listScrollOffset = Math.max(0, listScrollOffset - (int) (scrollY * 14));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event == null) return false;
        String s = event.codepointAsString();
        if (s == null || s.isEmpty()) return false;

        if (searchFocused) {
            if (searchText.length() < MAX_SEARCH_LENGTH) searchText += s;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event == null) return false;
        int key = event.key();

        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            return false;
        }
        return false;
    }
}
