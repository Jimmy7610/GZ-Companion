package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.model.ChestManagerStatus;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TextInputHandler;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders the Kistor (Chest Manager) tab: a searchable, filterable, sortable local list of
 * storage the player has personally and legitimately opened, showing "senast känt innehåll"
 * (last known contents) — never presented as live/current state.
 */
public class KistorTabComponent implements TextInputHandler {
    private static final int MAX_SEARCH_LENGTH = 48;
    private static final int MAX_LABEL_LENGTH = 32;

    private String searchText = "";
    private boolean searchFocused = false;
    private StoredContainerId selectedId = null;
    private int listScrollOffset = 0;
    private int detailScrollOffset = 0;
    private boolean compactShowingDetail = false;
    private boolean confirmingForget = false;
    private long forgetConfirmExpiry = 0;

    private ChestTypeFilter typeFilter = ChestTypeFilter.ALL;
    private ChestSortMode sortMode = ChestSortMode.RECENT;

    private boolean labelEditFocused = false;
    private String labelEditText = "";

    private long copyFeedbackExpiry = 0;

    private KistorLayout layout;
    private final List<ListRowHit> listHitTargets = new ArrayList<>();
    private UiRect labelFieldRect = null;

    public record ListRowHit(UiRect rect, StoredContainerId id) {}

    public KistorLayout getLayout() {
        return layout;
    }

    /** True while ANY Companion text input owned by this tab (search or label editor) is focused. */
    public boolean isTextInputFocused() {
        return searchFocused || labelEditFocused;
    }

    public boolean isSearchFocused() {
        return searchFocused;
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.layout = KistorLayout.calculate(bounds);
        listHitTargets.clear();
        labelFieldRect = null;

        CompanionSession session = CompanionSession.getInstance();
        ChestManager manager = session.getChestManager();
        String contextKey = session.getCurrentStorageContext();

        if (manager == null || !manager.getStatus().isAvailable()) {
            drawUnavailableState(extractor, font, bounds, manager != null ? manager.getStatus() : ChestManagerStatus.UNAVAILABLE);
            return;
        }

        List<StoredContainer> all = manager.getContainers(contextKey);
        List<StoredContainer> filtered = manager.search(contextKey, searchText, typeFilter, sortMode);
        boolean isFiltering = !searchText.isBlank() || typeFilter != ChestTypeFilter.ALL;

        renderHeader(extractor, font, layout.headerRect(), all.size(), filtered.size(), isFiltering);
        renderSearch(extractor, font, layout.searchRect(), layout.clearBtnRect(), mouseX, mouseY);
        renderControls(extractor, font, layout.filterBtnRect(), layout.sortBtnRect(), mouseX, mouseY);

        if (all.isEmpty()) {
            renderEmptyState(extractor, font, contentArea(bounds), true);
            return;
        }

        // Deterministic selection upkeep: keep the current selection if it is still visible in
        // the filtered results; otherwise select the first visible result; otherwise clear it.
        if (selectedId == null || filtered.stream().noneMatch(c -> c.id().equals(selectedId))) {
            selectedId = filtered.isEmpty() ? null : filtered.get(0).id();
            if (selectedId == null) {
                compactShowingDetail = false;
            }
        }

        if (filtered.isEmpty()) {
            renderEmptyState(extractor, font, contentArea(bounds), false);
            return;
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

    private UiRect contentArea(UiRect bounds) {
        return new UiRect(bounds.x(), layout.listRect().y(), bounds.width(),
                Math.max(10, layout.forgetBtnRect().y() - 4 - layout.listRect().y()));
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, int totalCount, int filteredCount, boolean isFiltering) {
        GZTheme.drawIcon(extractor, IconId.CHEST, headerRect.x(), headerRect.y() + 1, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Kistor", headerRect.x() + 13, headerRect.y() + 1,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        String countLabel = isFiltering ? (filteredCount + " av " + totalCount) : (totalCount + " sparade");
        int badgeW = TextUtil.scaledWidth(font, countLabel, TypographyScale.META.getScale()) + 14;
        GZTheme.drawBadge(extractor, font, headerRect.right() - badgeW, headerRect.y(), countLabel, GZTheme.COLOR_TEXT_SECONDARY, GZTheme.COLOR_STATUS_GREY);
    }

    private void renderSearch(GuiGraphicsExtractor extractor, Font font, UiRect searchRect, UiRect clearBtnRect, int mouseX, int mouseY) {
        int bg = searchFocused ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER;
        int border = searchFocused ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, searchRect, bg, border);

        int textX = searchRect.x() + 4;
        int textY = searchRect.y() + 3;
        int maxW = searchRect.width() - 8;

        if (searchText.isEmpty() && !searchFocused) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Sök föremål, etikett, typ, dimension eller koordinat...", textX, textY,
                    maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        } else {
            String shown = searchText + (searchFocused && ((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, shown, textX, textY,
                    maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        }

        if (!searchText.isEmpty()) {
            boolean hov = clearBtnRect.contains(mouseX, mouseY);
            GZTheme.drawCard(extractor, clearBtnRect, hov ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
            TextUtil.drawCenteredText(extractor, font, "x", clearBtnRect.x() + (clearBtnRect.width() / 2), clearBtnRect.y() + 2,
                    clearBtnRect.width(), hov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED, false);
        }
    }

    private void renderControls(GuiGraphicsExtractor extractor, Font font, UiRect filterBtnRect, UiRect sortBtnRect, int mouseX, int mouseY) {
        boolean filterHov = filterBtnRect.contains(mouseX, mouseY);
        boolean filterActive = typeFilter != ChestTypeFilter.ALL;
        String filterLabel = "Typ: " + typeFilter.getDisplayName();
        GZTheme.drawCard(extractor, filterBtnRect, filterActive ? GZTheme.COLOR_NAV_ACTIVE : (filterHov ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER),
                filterActive ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledEllipsizedText(extractor, font, filterLabel, filterBtnRect.x() + 3, filterBtnRect.y() + 2,
                filterBtnRect.width() - 6, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);

        boolean sortHov = sortBtnRect.contains(mouseX, mouseY);
        String sortLabel = "Sortering: " + sortMode.getDisplayName();
        GZTheme.drawCard(extractor, sortBtnRect, sortHov ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledEllipsizedText(extractor, font, sortLabel, sortBtnRect.x() + 3, sortBtnRect.y() + 2,
                sortBtnRect.width() - 6, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void renderEmptyState(GuiGraphicsExtractor extractor, Font font, UiRect area, boolean noIndexedStorageAtAll) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int centerX = area.x() + (area.width() / 2);
        int maxW = area.width() - 20;
        int y = area.y() + Math.max(6, (area.height() / 2) - 24);

        GZTheme.drawIcon(extractor, IconId.CHEST, centerX - 8, y, 16, GZTheme.COLOR_MINT);
        y += 20;

        if (noIndexedStorageAtAll) {
            TextUtil.drawCenteredText(extractor, font, "Du har inga sparade förvaringar ännu.", centerX, y, maxW, GZTheme.COLOR_TEXT_PRIMARY, true);
            y += 12;
            y += TextUtil.drawScaledWrappedText(extractor, font,
                    "Öppna en kista, tunna eller annan stödd förvaring så sparar GZ Companion senast känt innehåll automatiskt.",
                    area.x() + 10, y, maxW, TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 6;
            TextUtil.drawScaledCenteredText(extractor, font, "Inga oöppnade förvaringar skannas.", centerX, y, maxW,
                    TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_GREEN, false);
        } else {
            TextUtil.drawCenteredText(extractor, font, "Inga sparade förvaringar matchar sökningen.", centerX, y, maxW, GZTheme.COLOR_TEXT_PRIMARY, true);
        }
    }

    public static int calculateMaxListScroll(UiRect listRect, int rowCount) {
        return calculateMaxListScroll(listRect, rowCount, false);
    }

    public static int calculateMaxListScroll(UiRect listRect, int rowCount, boolean compact) {
        int rowH = rowHeight(compact);
        int totalH = 15 + (rowCount * (rowH + 1)) + 4;
        int visibleH = Math.max(1, listRect.height() - 4);
        return Math.max(0, totalH - visibleH);
    }

    private static int rowHeight(boolean compact) {
        return compact ? 22 : 30;
    }

    private void renderList(GuiGraphicsExtractor extractor, Font font, UiRect listRect, List<StoredContainer> containers, int mouseX, int mouseY) {
        GZTheme.drawCard(extractor, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        boolean compact = layout.isCompact();
        int rowH = rowHeight(compact);
        int maxScroll = calculateMaxListScroll(listRect, containers.size(), compact);
        this.listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));

        extractor.enableScissor(listRect.x() + 1, listRect.y() + 1, listRect.right() - 1, listRect.bottom() - 1);

        int currentY = listRect.y() + 3 - listScrollOffset;
        TextUtil.drawScaledText(extractor, font, "FÖRVARINGAR", listRect.x() + 4, currentY,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currentY += 12;

        for (StoredContainer container : containers) {
            UiRect rowRect = new UiRect(listRect.x() + 2, currentY, listRect.width() - 4, rowH);
            if (rowRect.bottom() >= listRect.y() + 2 && rowRect.y() <= listRect.bottom() - 2) {
                listHitTargets.add(new ListRowHit(rowRect, container.id()));
            }

            if (currentY + rowH >= listRect.y() && currentY <= listRect.bottom()) {
                boolean isSelected = container.id().equals(selectedId);
                boolean isHovered = rowRect.contains(mouseX, mouseY);

                int bg = isSelected ? GZTheme.COLOR_NAV_ACTIVE : (isHovered ? GZTheme.COLOR_NAV_HOVER : 0);
                int border = isSelected ? GZTheme.COLOR_BORDER_EMERALD : 0;
                if (bg != 0) GZTheme.drawCard(extractor, rowRect, bg, border);

                boolean hasLabel = container.label() != null && !container.label().isBlank();
                String title = hasLabel ? container.label() : container.kind().getDisplayName();
                String secondary = hasLabel ? container.kind().getDisplayName() + " · " + container.anchor().toDisplayString()
                        : container.anchor().toDisplayString();

                TextUtil.drawScaledEllipsizedText(extractor, font, title, rowRect.x() + 4, rowRect.y() + 3,
                        rowRect.width() - 8, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, isSelected);
                TextUtil.drawScaledEllipsizedText(extractor, font, secondary, rowRect.x() + 4, rowRect.y() + 12,
                        rowRect.width() - 8, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);

                if (!compact) {
                    TextUtil.drawScaledEllipsizedText(extractor, font, formatRelativeTime(container.lastOpenedAtMs()), rowRect.x() + 4, rowRect.y() + 20,
                            rowRect.width() - 8, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
                }
            }

            currentY += rowH + 1;
        }

        extractor.disableScissor();
    }

    private int calculateMaxDetailScroll(Font font, UiRect contentArea, StoredContainer container) {
        if (font == null || contentArea == null || container == null) return 0;
        int maxW = Math.max(10, contentArea.width() - 8);
        int totalH = 4;
        if (container.label() != null && !container.label().isBlank()) totalH += 11;
        if (labelEditFocused) totalH += 11;
        totalH += 10; // type · dimension
        totalH += 9;  // coordinates
        if (shapeStatusText(container) != null) totalH += 9;
        totalH += 12; // senast öppnad
        totalH += 12; // "SENAST KÄNT INNEHÅLL" header
        totalH += Math.max(1, container.aggregatedItems().size()) * 10;
        totalH += TextUtil.measureWrappedHeight(font, "Kan ha ändrats sedan du öppnade förvaringen.", maxW, TypographyScale.META.getScale(), 1) + 10;
        return Math.max(0, totalH - contentArea.height());
    }

    private String shapeStatusText(StoredContainer container) {
        return switch (container.shape()) {
            case DOUBLE -> "Dubbel kista";
            case UNKNOWN -> container.kind().isChestFamily() ? "(kan vara dubbel)" : null;
            case SINGLE, NOT_APPLICABLE -> null;
        };
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
        int contentTop = detailRect.y() + (isCompact ? 16 : 4);
        UiRect contentArea = new UiRect(detailRect.x() + 1, contentTop, detailRect.width() - 2, detailRect.bottom() - contentTop - 1);

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

        // 1. Local label (if any), or an inline editor while renaming.
        boolean hasLabel = container.label() != null && !container.label().isBlank();
        if (labelEditFocused) {
            UiRect fieldRect = new UiRect(contentArea.x() + pad, currY, maxW, 11);
            this.labelFieldRect = fieldRect;
            GZTheme.drawCard(extractor, fieldRect, GZTheme.COLOR_CARD_HOVER, GZTheme.COLOR_BORDER_EMERALD);
            String shown = labelEditText + (((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, shown, fieldRect.x() + 3, fieldRect.y() + 2,
                    fieldRect.width() - 6, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
            currY += 11;
        } else if (hasLabel) {
            TextUtil.drawScaledEllipsizedText(extractor, font, container.label(), contentArea.x() + pad, currY,
                    maxW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
            currY += 11;
        }

        // 2. Storage type · dimension
        String typeLine = container.kind().getDisplayName() + " · " + shortDimensionName(container.dimensionKey());
        TextUtil.drawScaledEllipsizedText(extractor, font, typeLine, contentArea.x() + pad, currY,
                maxW, hasLabel || labelEditFocused ? TypographyScale.SMALL.getScale() : TypographyScale.HEADING.getScale(),
                hasLabel || labelEditFocused ? GZTheme.COLOR_TEXT_SECONDARY : GZTheme.COLOR_TEXT_PRIMARY, !(hasLabel || labelEditFocused));
        currY += 10;

        // 3. Coordinates
        TextUtil.drawScaledEllipsizedText(extractor, font, container.anchor().toDisplayString(), contentArea.x() + pad, currY,
                maxW, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        currY += 9;

        // 4. Shape status, only if relevant
        String shapeText = shapeStatusText(container);
        if (shapeText != null) {
            int shapeColor = container.shape() == StorageShape.UNKNOWN ? GZTheme.COLOR_STATUS_YELLOW : GZTheme.COLOR_TEXT_MUTED;
            TextUtil.drawScaledEllipsizedText(extractor, font, shapeText, contentArea.x() + pad, currY,
                    maxW, TypographyScale.META.getScale(), shapeColor, false);
            currY += 9;
        }

        // 5. Senast öppnad
        TextUtil.drawScaledEllipsizedText(extractor, font, "Senast öppnad: " + formatRelativeTime(container.lastOpenedAtMs()),
                contentArea.x() + pad, currY, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currY += 12;

        // 6. SENAST KÄNT INNEHÅLL + items
        TextUtil.drawScaledText(extractor, font, "SENAST KÄNT INNEHÅLL", contentArea.x() + pad, currY,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currY += 10;

        List<StoredContainer.AggregatedItem> items = new ArrayList<>(container.aggregatedItems());
        items.sort(Comparator.comparing((StoredContainer.AggregatedItem i) -> manager.itemDisplayName(i.itemId()), String.CASE_INSENSITIVE_ORDER));

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

        // 7. Last-known warning
        TextUtil.drawScaledWrappedText(extractor, font, "Kan ha ändrats sedan du öppnade förvaringen.",
                contentArea.x() + pad, currY, maxW, TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_STATUS_YELLOW, false);

        extractor.disableScissor();

        renderActionRow(extractor, font, mouseX, mouseY, hasLabel);
    }

    private void renderActionRow(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY, boolean hasLabel) {
        UiRect renameBtn = layout.renameBtnRect();
        UiRect copyBtn = layout.copyBtnRect();
        UiRect forgetBtn = layout.forgetBtnRect();

        String renameLabel = labelEditFocused ? "Spara" : (hasLabel ? "Byt namn" : "Namnge");
        GZTheme.drawButton(extractor, font, renameBtn, renameLabel, false, renameBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());

        boolean copyFeedbackActive = System.currentTimeMillis() < copyFeedbackExpiry;
        String copyLabel = copyFeedbackActive ? "Kopierat!" : "Kopiera koord.";
        int copyBg = copyFeedbackActive ? 0x4010B981 : (copyBtn.contains(mouseX, mouseY) ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER);
        int copyBorder = copyFeedbackActive ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, copyBtn, copyBg, copyBorder);
        TextUtil.drawScaledCenteredText(extractor, font, copyLabel, copyBtn.x() + (copyBtn.width() / 2),
                copyBtn.y() + ((copyBtn.height() - 7) / 2), copyBtn.width() - 4,
                TypographyScale.META.getScale(), copyFeedbackActive ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED, false);

        boolean confirmActive = isForgetConfirmActive();
        String forgetLabel = confirmActive ? "Bekräfta!" : "Glöm förvaring";
        int fBg = confirmActive ? 0x99EF4444 : (forgetBtn.contains(mouseX, mouseY) ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER);
        int fBorder = confirmActive ? 0xFFEF4444 : GZTheme.COLOR_BORDER_SUBTLE;
        int fText = confirmActive ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED;
        GZTheme.drawCard(extractor, forgetBtn, fBg, fBorder);
        TextUtil.drawScaledCenteredText(extractor, font, forgetLabel, forgetBtn.x() + (forgetBtn.width() / 2),
                forgetBtn.y() + ((forgetBtn.height() - 7) / 2), forgetBtn.width() - 4, TypographyScale.META.getScale(), fText, false);
    }

    private void drawUnavailableState(GuiGraphicsExtractor extractor, Font font, UiRect bounds, ChestManagerStatus status) {
        GZTheme.drawCard(extractor, bounds, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        String msg = switch (status) {
            case ERROR -> "Fel inträffade vid inläsning av kistindexet.";
            case UNAVAILABLE -> "Kistor är inte tillgängligt just nu.";
            case INCOMPATIBLE -> "Kistindexet är sparat av en nyare version av GZ Companion och kan inte läsas här.";
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

        CompanionSession session = CompanionSession.getInstance();
        ChestManager manager = session.getChestManager();
        String contextKey = session.getCurrentStorageContext();

        // Clicking the search box focuses it and cancels any in-progress label edit (discarded,
        // matching "clicking outside cancels" for the label editor - see keyPressed javadoc).
        boolean insideSearch = layout.searchRect().contains(mouseX, mouseY);
        boolean insideClear = layout.clearBtnRect().contains(mouseX, mouseY);
        boolean insideLabelField = labelEditFocused && labelFieldRect != null && labelFieldRect.contains(mouseX, mouseY);
        boolean insideRenameBtn = layout.renameBtnRect().contains(mouseX, mouseY);

        if (insideLabelField) {
            searchFocused = false;
            return true;
        }
        if (labelEditFocused && !insideRenameBtn) {
            // Clicked outside the label field (and not on the Spara/commit button itself) while
            // editing: cancel without saving. The rename button's own click handler below is
            // what actually commits when the click IS on it - it must not be preempted here.
            cancelLabelEdit();
        }

        if (insideClear) {
            searchText = "";
            return true;
        }

        searchFocused = insideSearch;
        if (insideSearch) {
            return true;
        }

        if (layout.filterBtnRect().contains(mouseX, mouseY)) {
            typeFilter = typeFilter.next();
            return true;
        }
        if (layout.sortBtnRect().contains(mouseX, mouseY)) {
            sortMode = sortMode.next();
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
                    detailScrollOffset = 0;
                    return true;
                }
            }
        }

        if (selectedId != null) {
            StoredContainer selected = manager.getContainer(contextKey, selectedId).orElse(null);

            if (layout.renameBtnRect().contains(mouseX, mouseY)) {
                if (labelEditFocused) {
                    commitLabelEdit(manager, contextKey);
                } else if (selected != null) {
                    labelEditFocused = true;
                    labelEditText = selected.label() != null ? selected.label() : "";
                    detailScrollOffset = 0;
                }
                return true;
            }

            if (layout.copyBtnRect().contains(mouseX, mouseY) && selected != null) {
                copyCoordinates(selected);
                return true;
            }

            if (layout.forgetBtnRect().contains(mouseX, mouseY)) {
                if (isForgetConfirmActive()) {
                    manager.forgetContainer(contextKey, selectedId);
                    confirmingForget = false;
                    forgetConfirmExpiry = 0;

                    // Select a sensible remaining entry rather than leaving a dangling selection.
                    List<StoredContainer> remaining = manager.search(contextKey, searchText, typeFilter, sortMode);
                    selectedId = remaining.isEmpty() ? null : remaining.get(0).id();
                    if (selectedId == null) {
                        compactShowingDetail = false;
                    }
                } else {
                    confirmingForget = true;
                    forgetConfirmExpiry = System.currentTimeMillis() + 4000;
                }
                return true;
            }
        }

        return false;
    }

    private void copyCoordinates(StoredContainer container) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.keyboardHandler != null) {
                client.keyboardHandler.setClipboard(container.anchor().toCoordinateText());
                copyFeedbackExpiry = System.currentTimeMillis() + 2000;
            }
        } catch (Exception ignored) {
            // Clipboard access is a pure local OS convenience - never let a failure here affect anything else.
        }
    }

    private void commitLabelEdit(ChestManager manager, String contextKey) {
        String sanitized = labelEditText.trim();
        manager.setLabel(contextKey, selectedId, sanitized.isEmpty() ? null : sanitized);
        labelEditFocused = false;
        labelEditText = "";
    }

    private void cancelLabelEdit() {
        labelEditFocused = false;
        labelEditText = "";
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null) return false;

        // In compact mode, listRect() and detailRect() are the SAME rectangle (only one pane is
        // rendered at a time), so checking listRect() first would always win and silently eat
        // every scroll event meant for the detail pane. Route explicitly on compactShowingDetail
        // first so the two panes are never aliased.
        if (layout.isCompact() && compactShowingDetail) {
            return layout.detailRect().contains(mouseX, mouseY) && scrollDetail(scrollY);
        }

        if (layout.listRect().contains(mouseX, mouseY)) {
            CompanionSession session = CompanionSession.getInstance();
            ChestManager manager = session.getChestManager();
            int count = manager != null ? manager.search(session.getCurrentStorageContext(), searchText, typeFilter, sortMode).size() : 0;
            int maxScroll = calculateMaxListScroll(layout.listRect(), count, layout.isCompact());
            this.listScrollOffset = Math.max(0, Math.min(listScrollOffset - (int) (scrollY * 14), maxScroll));
            return true;
        }

        if (layout.detailRect().contains(mouseX, mouseY)) {
            return scrollDetail(scrollY);
        }

        return false;
    }

    private boolean scrollDetail(double scrollY) {
        if (selectedId == null) return false;
        CompanionSession session = CompanionSession.getInstance();
        ChestManager manager = session.getChestManager();
        String contextKey = session.getCurrentStorageContext();
        StoredContainer container = manager != null ? manager.getContainer(contextKey, selectedId).orElse(null) : null;
        if (container == null) return false;
        Font font = Minecraft.getInstance().font;
        int contentTop = layout.detailRect().y() + (layout.isCompact() ? 16 : 4);
        UiRect contentArea = new UiRect(layout.detailRect().x() + 1, contentTop, layout.detailRect().width() - 2, layout.detailRect().bottom() - contentTop - 1);
        int maxScroll = calculateMaxDetailScroll(font, contentArea, container);
        this.detailScrollOffset = Math.max(0, Math.min(detailScrollOffset - (int) (scrollY * 14), maxScroll));
        return true;
    }

    /**
     * Test-only: drives the compact list/detail scroll-routing state directly, since {@link #layout}
     * is otherwise only ever set inside the render path, which needs a live Font.
     */
    void setCompactStateForTesting(UiRect bounds, boolean compactShowingDetail, StoredContainerId selectedId) {
        this.layout = KistorLayout.calculate(bounds);
        this.compactShowingDetail = compactShowingDetail;
        this.selectedId = selectedId;
    }

    int listScrollOffsetForTesting() {
        return listScrollOffset;
    }

    public boolean charTyped(CharacterEvent event) {
        if (event == null) return false;
        String s = event.codepointAsString();
        if (s == null || s.isEmpty()) return false;

        if (labelEditFocused) {
            if (labelEditText.length() < MAX_LABEL_LENGTH) {
                labelEditText = labelEditText + s;
            }
            return true;
        }
        if (searchFocused) {
            if (searchText.length() < MAX_SEARCH_LENGTH) {
                searchText = searchText + s;
            }
            return true;
        }
        return false;
    }

    /**
     * Handles a key press while the Kistor tab is active. Priority: an in-progress label edit
     * takes ESC/Enter/Backspace first, then the search field. Any other key while a text input
     * is focused is consumed here (so it never leaks into unrelated global shortcuts) but does
     * nothing beyond that - the actual character insertion happens via {@link #charTyped}.
     */
    public boolean keyPressed(KeyEvent event) {
        if (event == null) return false;
        int key = event.key();

        if (labelEditFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelLabelEdit();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                CompanionSession session = CompanionSession.getInstance();
                commitLabelEdit(session.getChestManager(), session.getCurrentStorageContext());
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!labelEditText.isEmpty()) {
                    labelEditText = labelEditText.substring(0, labelEditText.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (searchFocused) {
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

        return false;
    }
}
