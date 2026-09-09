package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.model.ChestManagerStatus;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Kistor (Chest Manager) tab: a searchable local list of storage the player has
 * personally and legitimately opened, showing "senast känt innehåll" (last known contents) —
 * never presented as live/current state.
 */
public class KistorTabComponent {
    private static final int MAX_SEARCH_LENGTH = 48;

    private String searchText = "";
    private boolean searchFocused = false;
    private StoredContainerId selectedId = null;
    private int listScrollOffset = 0;
    private int detailScrollOffset = 0;
    private boolean compactShowingDetail = false;
    private boolean confirmingForget = false;
    private long forgetConfirmExpiry = 0;

    private KistorLayout layout;
    private final List<ListRowHit> listHitTargets = new ArrayList<>();

    public record ListRowHit(UiRect rect, StoredContainerId id) {}

    private static final int ROW_H = 22;

    public KistorLayout getLayout() {
        return layout;
    }

    public boolean isSearchFocused() {
        return searchFocused;
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.layout = KistorLayout.calculate(bounds);
        listHitTargets.clear();

        CompanionSession session = CompanionSession.getInstance();
        ChestManager manager = session.getChestManager();
        String contextKey = session.getCurrentStorageContext();

        if (manager == null || !manager.getStatus().isAvailable()) {
            drawUnavailableState(extractor, font, bounds, manager != null ? manager.getStatus() : ChestManagerStatus.UNAVAILABLE);
            return;
        }

        List<StoredContainer> all = manager.getContainers(contextKey);
        List<StoredContainer> filtered = manager.search(contextKey, searchText);

        renderHeader(extractor, font, layout.headerRect(), all.size());
        renderSearch(extractor, font, layout.searchRect(), mouseX, mouseY);

        if (all.isEmpty()) {
            renderEmptyState(extractor, font, layout.listRect().right() > layout.detailRect().x()
                    ? new UiRect(bounds.x(), layout.listRect().y(), bounds.width(), layout.listRect().height())
                    : layout.listRect());
            return;
        }

        if (selectedId == null || filtered.stream().noneMatch(c -> c.id().equals(selectedId))) {
            if (!filtered.isEmpty()) {
                selectedId = filtered.get(0).id();
            }
        }

        if (layout.isCompact()) {
            if (compactShowingDetail && selectedId != null) {
                renderDetailPane(extractor, font, layout.detailRect(), manager, contextKey, mouseX, mouseY, true);
            } else {
                renderList(extractor, font, layout.listRect(), filtered, mouseX, mouseY);
            }
        } else {
            renderList(extractor, font, layout.listRect(), filtered, mouseX, mouseY);
            renderDetailPane(extractor, font, layout.detailRect(), manager, contextKey, mouseX, mouseY, false);
        }
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, int totalCount) {
        GZTheme.drawIcon(extractor, IconId.CHEST, headerRect.x(), headerRect.y() + 1, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Kistor", headerRect.x() + 13, headerRect.y() + 1,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        String countLabel = totalCount + " sparade";
        int badgeW = TextUtil.scaledWidth(font, countLabel, TypographyScale.META.getScale()) + 14;
        GZTheme.drawBadge(extractor, font, headerRect.right() - badgeW, headerRect.y(), countLabel, GZTheme.COLOR_TEXT_SECONDARY, GZTheme.COLOR_STATUS_GREY);
    }

    private void renderSearch(GuiGraphicsExtractor extractor, Font font, UiRect searchRect, int mouseX, int mouseY) {
        int bg = searchFocused ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER;
        int border = searchFocused ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, searchRect, bg, border);

        int textX = searchRect.x() + 4;
        int textY = searchRect.y() + 3;
        int maxW = searchRect.width() - 8;

        if (searchText.isEmpty() && !searchFocused) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Sök föremål eller koordinat...", textX, textY,
                    maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        } else {
            String shown = searchText + (searchFocused && ((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, shown, textX, textY,
                    maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        }
    }

    private void renderEmptyState(GuiGraphicsExtractor extractor, Font font, UiRect area) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int centerX = area.x() + (area.width() / 2);
        int maxW = area.width() - 20;
        int y = area.y() + Math.max(6, (area.height() / 2) - 24);

        GZTheme.drawIcon(extractor, IconId.CHEST, centerX - 8, y, 16, GZTheme.COLOR_MINT);
        y += 20;
        TextUtil.drawCenteredText(extractor, font, "Du har inga sparade förvaringar ännu.", centerX, y, maxW, GZTheme.COLOR_TEXT_PRIMARY, true);
        y += 12;
        y += TextUtil.drawScaledWrappedText(extractor, font,
                "Öppna en kista, tunna eller annan stödd förvaring så sparar GZ Companion senast känt innehåll automatiskt.",
                area.x() + 10, y, maxW, TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_TEXT_SECONDARY, false);
        y += 6;
        TextUtil.drawScaledCenteredText(extractor, font, "Inga oöppnade förvaringar skannas.", centerX, y, maxW,
                TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_GREEN, false);
    }

    public static int calculateMaxListScroll(UiRect listRect, int rowCount) {
        int totalH = 15 + (rowCount * (ROW_H + 1)) + 4;
        int visibleH = Math.max(1, listRect.height() - 4);
        return Math.max(0, totalH - visibleH);
    }

    private void renderList(GuiGraphicsExtractor extractor, Font font, UiRect listRect, List<StoredContainer> containers, int mouseX, int mouseY) {
        GZTheme.drawCard(extractor, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int maxScroll = calculateMaxListScroll(listRect, containers.size());
        this.listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));

        extractor.enableScissor(listRect.x() + 1, listRect.y() + 1, listRect.right() - 1, listRect.bottom() - 1);

        int currentY = listRect.y() + 3 - listScrollOffset;
        TextUtil.drawScaledText(extractor, font, "FÖRVARINGAR", listRect.x() + 4, currentY,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currentY += 12;

        if (containers.isEmpty()) {
            TextUtil.drawScaledText(extractor, font, "Inga träffar.", listRect.x() + 4, currentY,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }

        for (StoredContainer container : containers) {
            UiRect rowRect = new UiRect(listRect.x() + 2, currentY, listRect.width() - 4, ROW_H);
            if (rowRect.bottom() >= listRect.y() + 2 && rowRect.y() <= listRect.bottom() - 2) {
                listHitTargets.add(new ListRowHit(rowRect, container.id()));
            }

            if (currentY + ROW_H >= listRect.y() && currentY <= listRect.bottom()) {
                boolean isSelected = container.id().equals(selectedId);
                boolean isHovered = rowRect.contains(mouseX, mouseY);

                int bg = isSelected ? GZTheme.COLOR_NAV_ACTIVE : (isHovered ? GZTheme.COLOR_NAV_HOVER : 0);
                int border = isSelected ? GZTheme.COLOR_BORDER_EMERALD : 0;
                if (bg != 0) GZTheme.drawCard(extractor, rowRect, bg, border);

                String title = container.kind().getDisplayName() + (container.label() != null ? " · " + container.label() : "");
                int textTint = isSelected ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_PRIMARY;
                TextUtil.drawScaledEllipsizedText(extractor, font, title, rowRect.x() + 4, rowRect.y() + 3,
                        rowRect.width() - 8, TypographyScale.SMALL.getScale(), textTint, isSelected);

                String coords = container.anchor().toDisplayString();
                TextUtil.drawScaledEllipsizedText(extractor, font, coords, rowRect.x() + 4, rowRect.y() + 12,
                        rowRect.width() - 8, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            }

            currentY += ROW_H + 1;
        }

        extractor.disableScissor();
    }

    private int calculateMaxDetailScroll(Font font, UiRect contentArea, StoredContainer container) {
        if (font == null || contentArea == null || container == null) return 0;
        int maxW = Math.max(10, contentArea.width() - 8);
        int totalH = 4;
        totalH += 26; // title + coords + last-opened block
        totalH += 12; // "SENAST KÄNT INNEHÅLL" header
        totalH += Math.max(1, container.aggregatedItems().size()) * 10;
        totalH += TextUtil.measureWrappedHeight(font, "Kan ha ändrats sedan du öppnade förvaringen.", maxW, TypographyScale.META.getScale(), 1) + 10;
        return Math.max(0, totalH - contentArea.height());
    }

    private void renderDetailPane(GuiGraphicsExtractor extractor, Font font, UiRect detailRect, ChestManager manager,
                                  String contextKey, int mouseX, int mouseY, boolean isCompact) {
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        StoredContainer container = selectedId != null ? manager.getContainer(contextKey, selectedId).orElse(null) : null;
        if (container == null) {
            TextUtil.drawCenteredText(extractor, font, "Välj en förvaring i listan", detailRect.x() + (detailRect.width() / 2),
                    detailRect.y() + (detailRect.height() / 2) - 4, detailRect.width(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        int pad = 5;
        UiRect forgetBtn = layout.forgetBtnRect();
        int contentTop = detailRect.y() + (isCompact ? 16 : 4);
        int contentBottom = forgetBtn.y() - 3;
        int contentH = Math.max(1, contentBottom - contentTop);
        UiRect contentArea = new UiRect(detailRect.x() + 1, contentTop, detailRect.width() - 2, contentH);

        int maxScroll = calculateMaxDetailScroll(font, contentArea, container);
        this.detailScrollOffset = Math.max(0, Math.min(detailScrollOffset, maxScroll));

        if (isCompact) {
            UiRect backBtn = layout.backBtnRect();
            boolean backHover = backBtn.contains(mouseX, mouseY);
            GZTheme.drawButton(extractor, font, backBtn, "< Lista", false, backHover, TypographyScale.SMALL.getScale());
        }

        extractor.enableScissor(contentArea.x(), contentArea.y(), contentArea.right(), contentArea.bottom());
        int currY = contentArea.y() + 2 - detailScrollOffset;
        int maxW = contentArea.width() - (pad * 2);

        String title = container.kind().getDisplayName() + " · " + shortDimensionName(container.dimensionKey())
                + (container.label() != null ? " · " + container.label() : "");
        TextUtil.drawScaledEllipsizedText(extractor, font, title, contentArea.x() + pad, currY,
                maxW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
        currY += 10;

        String coordText = container.anchor().toDisplayString() + (container.isDoubleWide() ? "  (dubbel)"
                : (container.partnerUnknown() && container.kind().isChestFamily() ? "  (kan vara dubbel)" : ""));
        TextUtil.drawScaledEllipsizedText(extractor, font, coordText, contentArea.x() + pad, currY,
                maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        currY += 9;

        TextUtil.drawScaledEllipsizedText(extractor, font, "Senast öppnad: " + formatRelativeTime(container.lastOpenedAtMs()),
                contentArea.x() + pad, currY, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currY += 12;

        TextUtil.drawScaledText(extractor, font, "SENAST KÄNT INNEHÅLL", contentArea.x() + pad, currY,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currY += 10;

        List<StoredContainer.AggregatedItem> items = container.aggregatedItems();
        if (items.isEmpty()) {
            TextUtil.drawScaledText(extractor, font, "(Tom förvaring senast öppnad)", contentArea.x() + pad, currY,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            currY += 10;
        } else {
            for (StoredContainer.AggregatedItem item : items) {
                String name = manager.itemDisplayName(item.itemId());
                String countText = String.valueOf(item.count());
                int countW = TextUtil.scaledWidth(font, countText, TypographyScale.SMALL.getScale());
                TextUtil.drawScaledEllipsizedText(extractor, font, name, contentArea.x() + pad, currY,
                        maxW - countW - 6, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
                TextUtil.drawScaledRightAlignedText(extractor, font, countText, contentArea.x() + pad + maxW, currY,
                        countW + 2, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
                currY += 10;
            }
        }
        currY += 4;

        TextUtil.drawScaledWrappedText(extractor, font, "Kan ha ändrats sedan du öppnade förvaringen.",
                contentArea.x() + pad, currY, maxW, TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_STATUS_YELLOW, false);

        extractor.disableScissor();

        boolean confirmActive = isForgetConfirmActive();
        String forgetLabel = confirmActive ? "Bekräfta! Tas bara bort lokalt" : "Glöm förvaring";
        int fBg = confirmActive ? 0x99EF4444 : (forgetBtn.contains(mouseX, mouseY) ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER);
        int fBorder = confirmActive ? 0xFFEF4444 : GZTheme.COLOR_BORDER_SUBTLE;
        int fText = confirmActive ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED;
        GZTheme.drawCard(extractor, forgetBtn, fBg, fBorder);
        TextUtil.drawScaledCenteredText(extractor, font, forgetLabel, forgetBtn.x() + (forgetBtn.width() / 2),
                forgetBtn.y() + ((forgetBtn.height() - 7) / 2), forgetBtn.width() - 4, TypographyScale.SMALL.getScale(), fText, false);
    }

    private void drawUnavailableState(GuiGraphicsExtractor extractor, Font font, UiRect bounds, ChestManagerStatus status) {
        GZTheme.drawCard(extractor, bounds, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        String msg = switch (status) {
            case ERROR -> "Fel inträffade vid inläsning av kistindexet.";
            case UNAVAILABLE -> "Kistor är inte tillgängligt just nu.";
            case LOADED -> "Laddar...";
        };
        TextUtil.drawCenteredText(extractor, font, msg, bounds.x() + (bounds.width() / 2),
                bounds.y() + (bounds.height() / 2) - 4, bounds.width(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    private boolean isForgetConfirmActive() {
        return confirmingForget && System.currentTimeMillis() < forgetConfirmExpiry;
    }

    private String shortDimensionName(String dimensionKey) {
        if (dimensionKey == null) return "Okänd värld";
        return switch (dimensionKey) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            default -> dimensionKey.contains(":") ? dimensionKey.substring(dimensionKey.indexOf(':') + 1) : dimensionKey;
        };
    }

    private String formatRelativeTime(long thenMs) {
        if (thenMs <= 0) return "okänt";
        long diff = Math.max(0, System.currentTimeMillis() - thenMs);
        if (diff < 60_000L) return "just nu";
        if (diff < 3_600_000L) return (diff / 60_000L) + " min sedan";
        if (diff < 86_400_000L) return (diff / 3_600_000L) + " h sedan";
        return (diff / 86_400_000L) + " dagar sedan";
    }

    // ------------------------------------------------------------------
    // Input handling
    // ------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        if (layout == null) layout = KistorLayout.calculate(bounds);

        boolean insideSearch = layout.searchRect().contains(mouseX, mouseY);
        searchFocused = insideSearch;

        if (insideSearch) {
            return true;
        }

        if (layout.isCompact() && compactShowingDetail) {
            if (layout.backBtnRect().contains(mouseX, mouseY)) {
                compactShowingDetail = false;
                return true;
            }
        }

        if (layout.listRect().contains(mouseX, mouseY)) {
            for (ListRowHit hit : listHitTargets) {
                if (hit.rect.contains(mouseX, mouseY)) {
                    selectedId = hit.id;
                    compactShowingDetail = true;
                    confirmingForget = false;
                    return true;
                }
            }
        }

        if (selectedId != null && layout.forgetBtnRect().contains(mouseX, mouseY)) {
            CompanionSession session = CompanionSession.getInstance();
            ChestManager manager = session.getChestManager();
            String contextKey = session.getCurrentStorageContext();

            if (isForgetConfirmActive()) {
                manager.forgetContainer(contextKey, selectedId);
                selectedId = null;
                confirmingForget = false;
                forgetConfirmExpiry = 0;
                compactShowingDetail = false;
            } else {
                confirmingForget = true;
                forgetConfirmExpiry = System.currentTimeMillis() + 4000;
            }
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null) return false;

        if (layout.listRect().contains(mouseX, mouseY)) {
            CompanionSession session = CompanionSession.getInstance();
            ChestManager manager = session.getChestManager();
            int count = manager != null ? manager.getContainers(session.getCurrentStorageContext()).size() : 0;
            int maxScroll = calculateMaxListScroll(layout.listRect(), count);
            this.listScrollOffset = Math.max(0, Math.min(listScrollOffset - (int) (scrollY * 14), maxScroll));
            return true;
        }

        if (layout.detailRect().contains(mouseX, mouseY) && selectedId != null) {
            CompanionSession session = CompanionSession.getInstance();
            ChestManager manager = session.getChestManager();
            String contextKey = session.getCurrentStorageContext();
            StoredContainer container = manager != null ? manager.getContainer(contextKey, selectedId).orElse(null) : null;
            if (container != null) {
                Font font = net.minecraft.client.Minecraft.getInstance().font;
                UiRect forgetBtn = layout.forgetBtnRect();
                int contentTop = layout.detailRect().y() + (layout.isCompact() ? 16 : 4);
                int contentBottom = forgetBtn.y() - 3;
                int contentH = Math.max(1, contentBottom - contentTop);
                UiRect contentArea = new UiRect(layout.detailRect().x() + 1, contentTop, layout.detailRect().width() - 2, contentH);
                int maxScroll = calculateMaxDetailScroll(font, contentArea, container);
                this.detailScrollOffset = Math.max(0, Math.min(detailScrollOffset - (int) (scrollY * 14), maxScroll));
                return true;
            }
        }

        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!searchFocused || event == null) return false;
        String s = event.codepointAsString();
        if (s == null || s.isEmpty()) return false;
        if (searchText.length() < MAX_SEARCH_LENGTH) {
            searchText = searchText + s;
        }
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (!searchFocused || event == null) return false;
        int key = event.key();
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            searchFocused = false;
            return true;
        }
        return false;
    }
}
