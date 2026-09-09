package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeModuleStatus;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.ClientRecipeCachePolicy;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.ClientRecipeSnapshot;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.CraftingKnowledgeBase;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.GameZoneCraftingEntry;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.IngredientOption;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.IngredientRef;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.RecipeKind;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.bridge.MinecraftRecipeDisplayAdapter;
import se.jimmyeliasson.gzcompanion.knowledge.items.CustomItemKnowledge;
import se.jimmyeliasson.gzcompanion.knowledge.items.ItemKnowledgeBase;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TextInputHandler;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.CraftingLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Renders the Crafting tab: legitimately-unlocked client/server-synced crafting-table recipes
 * (from the local player's own recipe book) alongside any GameZone-specific crafting overrides
 * and GameZone custom-item/relic knowledge, all sourced from the bundled Rule Pack plus the
 * client's own recipe book. Never crafts, moves inventory, or touches any recipe registry -
 * strictly read-only reference data.
 *
 * <p>The client recipe book is expensive to read (iterates every unlocked recipe collection and
 * resolves every ingredient's {@code ItemStack}), so it is cached and only re-read when
 * {@link ClientRecipeCachePolicy} says to - never on every render frame. See
 * {@link #refreshClientRecipesIfNeeded(long, String)}.
 */
public class CraftingTabComponent implements TextInputHandler {
    private static final int MAX_SEARCH_LENGTH = 48;
    private static final int GRID_CELL_SIZE = 20;
    private static final int GRID_ICON_SIZE = 16;
    private static final int GRID_GAP = 1;

    /** Package-private (not private) so tests can exercise mode-specific logic directly. */
    enum Mode {
        ALLA("Allt"), RECEPT("Recept"), GAMEZONE_FOREMAL("GameZone-föremål");

        final String label;

        Mode(String label) {
            this.label = label;
        }

        Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private enum EntryKind { GAMEZONE_RECIPE, CLIENT_RECIPE, GAMEZONE_ITEM }

    /** Package-private (not private) so tests can inspect search results directly. */
    record ListEntry(EntryKind kind, String entryId, String primaryLabel, String secondaryLabel, int dotColor) {}

    private String searchText = "";
    private boolean searchFocused = false;
    private Mode mode = Mode.ALLA;
    private String selectedEntryId = null;
    private int listScrollOffset = 0;
    private int detailScrollOffset = 0;
    private boolean compactShowingDetail = false;

    private CraftingLayout layout;
    private final List<ListRowHit> listHitTargets = new ArrayList<>();

    // --- Client recipe book cache (see ClientRecipeCachePolicy) ---
    private Supplier<List<ClientRecipeSnapshot>> clientRecipeSupplier = MinecraftRecipeDisplayAdapter::readClientRecipeBook;
    private List<ClientRecipeSnapshot> cachedClientRecipes = List.of();
    private Map<String, String> searchTextByClientRecipeKey = Map.of();
    private long lastRecipeRefreshAtMs = 0L;
    private String lastRecipeContextKey = null;

    private record ListRowHit(UiRect rect, String entryId) {}

    public CraftingLayout getLayout() {
        return layout;
    }

    @Override
    public boolean isTextInputFocused() {
        return searchFocused;
    }

    /** Test-only injection seam - lets tests count/control recipe-book reads without a live client. */
    void setClientRecipeSupplierForTesting(Supplier<List<ClientRecipeSnapshot>> supplier) {
        this.clientRecipeSupplier = supplier != null ? supplier : MinecraftRecipeDisplayAdapter::readClientRecipeBook;
    }

    long getLastRecipeRefreshAtMsForTesting() {
        return lastRecipeRefreshAtMs;
    }

    void setSearchTextForTesting(String text) {
        this.searchText = text != null ? text : "";
    }

    void setModeForTesting(Mode mode) {
        this.mode = mode;
    }

    List<ClientRecipeSnapshot> getCachedClientRecipesForTesting() {
        return cachedClientRecipes;
    }

    /**
     * Re-reads the client's recipe book only if {@link ClientRecipeCachePolicy} says the cache is
     * due for a refresh (first read, the player/world/server context changed, or the modest
     * throttle interval elapsed) - never unconditionally. Rebuilds the precomputed search index
     * alongside the cache itself, exactly once per actual refresh, never per keystroke.
     *
     * @return true if a refresh actually happened.
     */
    boolean refreshClientRecipesIfNeeded(long nowMs, String contextKey) {
        boolean contextChanged = lastRecipeContextKey == null || !lastRecipeContextKey.equals(contextKey);
        if (!ClientRecipeCachePolicy.shouldRefresh(lastRecipeRefreshAtMs, nowMs, contextChanged)) {
            return false;
        }
        List<ClientRecipeSnapshot> fresh = clientRecipeSupplier.get();
        this.cachedClientRecipes = fresh != null ? fresh : List.of();
        this.lastRecipeRefreshAtMs = nowMs;
        this.lastRecipeContextKey = contextKey;
        rebuildClientRecipeSearchIndex();
        return true;
    }

    private void rebuildClientRecipeSearchIndex() {
        Map<String, String> index = new HashMap<>();
        for (ClientRecipeSnapshot snap : cachedClientRecipes) {
            index.put(snap.stableKey(), buildClientRecipeSearchText(snap));
        }
        this.searchTextByClientRecipeKey = index;
    }

    /** Indexes both the raw item id and its translated display name, for every output and every ingredient alternative. */
    private static String buildClientRecipeSearchText(ClientRecipeSnapshot snap) {
        StringBuilder sb = new StringBuilder();
        appendNormalized(sb, snap.outputItemId());
        appendNormalized(sb, snap.outputDisplayName());
        for (List<IngredientOption> slot : snap.slotAlternatives()) {
            for (IngredientOption option : slot) {
                appendNormalized(sb, option.itemId());
                appendNormalized(sb, option.displayName());
            }
        }
        return sb.toString();
    }

    /** Appends both the raw lowercase form and an underscore-to-space variant, so "oak_planks" and "oak planks" both match. */
    private static void appendNormalized(StringBuilder sb, String raw) {
        if (raw == null || raw.isBlank()) return;
        String lower = raw.toLowerCase(Locale.ROOT);
        sb.append(lower).append(' ');
        String spaced = lower.replace('_', ' ');
        if (!spaced.equals(lower)) {
            sb.append(spaced).append(' ');
        }
    }

    private static String normalizeQuery(String raw) {
        return (raw != null && !raw.isBlank()) ? raw.trim().toLowerCase(Locale.ROOT) : null;
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.layout = CraftingLayout.calculate(bounds);
        listHitTargets.clear();

        CompanionSession session = CompanionSession.getInstance();
        KnowledgeModuleStatus craftingStatus = session.getCraftingKnowledgeStatus();
        KnowledgeModuleStatus itemStatus = session.getItemKnowledgeStatus();
        CraftingKnowledgeBase craftingBase = craftingStatus.isAvailable() ? session.getCraftingKnowledgeBase() : null;
        ItemKnowledgeBase itemBase = itemStatus.isAvailable() ? session.getItemKnowledgeBase() : null;

        // The client's own recipe book is independent of both Rule Pack modules above - a broken
        // crafting-overrides.json must never hide the player's legitimately-unlocked recipes.
        if (mode != Mode.GAMEZONE_FOREMAL) {
            refreshClientRecipesIfNeeded(System.currentTimeMillis(), session.getCurrentStorageContext());
        }

        // A true hard-failure screen only applies when NO source at all could possibly serve the
        // current mode: both Rule Pack modules broken (nothing in this tab can work), or the
        // item module broken while GameZone-föremål mode has no alternative source.
        if (!craftingStatus.isAvailable() && !itemStatus.isAvailable()) {
            drawUnavailableState(extractor, font, bounds, craftingStatus);
            return;
        }
        if (mode == Mode.GAMEZONE_FOREMAL && !itemStatus.isAvailable()) {
            drawUnavailableState(extractor, font, bounds, itemStatus);
            return;
        }

        List<ListEntry> entries = buildEntries(craftingBase, itemBase);
        boolean isFiltering = !searchText.isBlank();

        renderHeader(extractor, font, layout.headerRect(), entries.size(), isFiltering);
        renderSearch(extractor, font, layout.searchRect(), layout.clearBtnRect(), mouseX, mouseY);
        renderModeButton(extractor, font, layout.modeBtnRect(), mouseX, mouseY);

        boolean hasCraftingSideData = (craftingBase != null && craftingBase.size() > 0) || !cachedClientRecipes.isEmpty();
        boolean hasItemSideData = itemBase != null && itemBase.size() > 0;
        if (!hasDataForMode(mode, hasCraftingSideData, hasItemSideData)) {
            renderEmptyState(extractor, font, contentArea(bounds), emptyStateMessage());
            return;
        }

        if (selectedEntryId == null || entries.stream().noneMatch(e -> e.entryId().equals(selectedEntryId))) {
            selectedEntryId = entries.isEmpty() ? null : entries.get(0).entryId();
            if (selectedEntryId == null) {
                compactShowingDetail = false;
            }
        }

        if (entries.isEmpty()) {
            renderEmptyState(extractor, font, contentArea(bounds), "Inga poster matchar sökningen.");
            return;
        }

        if (layout.isCompact()) {
            if (compactShowingDetail && selectedEntryId != null) {
                renderDetailPane(extractor, font, layout.detailRect(), craftingBase, itemBase, mouseX, mouseY, true);
            } else {
                renderList(extractor, font, layout.listRect(), entries, mouseX, mouseY);
            }
        } else {
            renderList(extractor, font, layout.listRect(), entries, mouseX, mouseY);
            renderDetailPane(extractor, font, layout.detailRect(), craftingBase, itemBase, mouseX, mouseY, false);
        }
    }

    private String emptyStateMessage() {
        return emptyStateMessage(mode);
    }

    /**
     * Whether the given mode has ANY true data (as opposed to a search producing zero results -
     * that is a separate, later check). RECEPT only ever looks at the crafting side (GameZone
     * overrides + client recipes); GAMEZONE_FOREMAL only ever looks at the item side; ALLA is
     * empty only if both sides are.
     */
    static boolean hasDataForMode(Mode mode, boolean hasCraftingSideData, boolean hasItemSideData) {
        return switch (mode) {
            case RECEPT -> hasCraftingSideData;
            case GAMEZONE_FOREMAL -> hasItemSideData;
            case ALLA -> hasCraftingSideData || hasItemSideData;
        };
    }

    static String emptyStateMessage(Mode mode) {
        return switch (mode) {
            case RECEPT -> "Inget verifierat craftingrecept finns i detta Rule Pack.";
            case GAMEZONE_FOREMAL -> "Inga GameZone-föremål är dokumenterade i detta Rule Pack.";
            case ALLA -> "Inget verifierat craftingrecept eller GameZone-föremål finns i detta Rule Pack.";
        };
    }

    /** Package-private (not private) so tests can inspect search/filter results directly. */
    List<ListEntry> buildEntries(CraftingKnowledgeBase craftingBase, ItemKnowledgeBase itemBase) {
        List<ListEntry> result = new ArrayList<>();
        String q = normalizeQuery(searchText);

        if (mode != Mode.GAMEZONE_FOREMAL) {
            if (craftingBase != null) {
                for (GameZoneCraftingEntry entry : craftingBase.search(searchText)) {
                    result.add(new ListEntry(EntryKind.GAMEZONE_RECIPE, "gz:" + entry.id(), entry.outputItemId(),
                            entry.source().getDisplayName(), entry.verification().status().getArgbColor()));
                }
            }
            for (ClientRecipeSnapshot snap : cachedClientRecipes) {
                if (q != null) {
                    String haystack = searchTextByClientRecipeKey.getOrDefault(snap.stableKey(), "");
                    if (!haystack.contains(q)) continue;
                }
                result.add(new ListEntry(EntryKind.CLIENT_RECIPE, "client:" + snap.stableKey(), snap.outputDisplayName(),
                        "Tillgängligt Minecraft-recept", GZTheme.COLOR_STATUS_GREY));
            }
        }
        if (mode != Mode.RECEPT && itemBase != null) {
            for (CustomItemKnowledge item : itemBase.search(searchText)) {
                result.add(new ListEntry(EntryKind.GAMEZONE_ITEM, "item:" + item.id(), item.displayName(),
                        item.tier() != null ? item.tier() : "GameZone-föremål", item.verification().status().getArgbColor()));
            }
        }
        return result;
    }

    private UiRect contentArea(UiRect bounds) {
        return new UiRect(bounds.x(), layout.listRect().y(), bounds.width(), layout.listRect().height());
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, int count, boolean isFiltering) {
        GZTheme.drawIcon(extractor, IconId.CRAFTING, headerRect.x(), headerRect.y() + 1, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Crafting", headerRect.x() + 13, headerRect.y() + 1,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        String countLabel = count + (isFiltering ? " matchande" : " poster");
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
            TextUtil.drawScaledEllipsizedText(extractor, font, "Sök recept eller föremål...", textX, textY,
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

    private void renderModeButton(GuiGraphicsExtractor extractor, Font font, UiRect rect, int mouseX, int mouseY) {
        boolean hov = rect.contains(mouseX, mouseY);
        boolean active = mode != Mode.ALLA;
        GZTheme.drawCard(extractor, rect, active ? GZTheme.COLOR_NAV_ACTIVE : (hov ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER),
                active ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledEllipsizedText(extractor, font, "Visa: " + mode.label, rect.x() + 3, rect.y() + 2,
                rect.width() - 6, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void renderEmptyState(GuiGraphicsExtractor extractor, Font font, UiRect area, String message) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int centerX = area.x() + (area.width() / 2);
        int maxW = area.width() - 20;
        int y = area.y() + Math.max(6, (area.height() / 2) - 12);
        GZTheme.drawIcon(extractor, IconId.CRAFTING, centerX - 8, y, 16, GZTheme.COLOR_MINT);
        y += 20;
        TextUtil.drawScaledWrappedText(extractor, font, message, area.x() + 10, y, maxW,
                TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private static int calculateMaxListScroll(UiRect listRect, int rowCount) {
        int rowH = 22;
        int totalH = 15 + (rowCount * (rowH + 1)) + 4;
        int visibleH = Math.max(1, listRect.height() - 4);
        return Math.max(0, totalH - visibleH);
    }

    private void renderList(GuiGraphicsExtractor extractor, Font font, UiRect listRect, List<ListEntry> entries, int mouseX, int mouseY) {
        GZTheme.drawCard(extractor, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int rowH = 22;
        int maxScroll = calculateMaxListScroll(listRect, entries.size());
        this.listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));

        extractor.enableScissor(listRect.x() + 1, listRect.y() + 1, listRect.right() - 1, listRect.bottom() - 1);

        int currentY = listRect.y() + 3 - listScrollOffset;
        TextUtil.drawScaledText(extractor, font, "POSTER", listRect.x() + 4, currentY,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currentY += 12;

        for (ListEntry entry : entries) {
            UiRect rowRect = new UiRect(listRect.x() + 2, currentY, listRect.width() - 4, rowH);
            if (rowRect.bottom() >= listRect.y() + 2 && rowRect.y() <= listRect.bottom() - 2) {
                listHitTargets.add(new ListRowHit(rowRect, entry.entryId()));
            }

            if (currentY + rowH >= listRect.y() && currentY <= listRect.bottom()) {
                boolean isSelected = entry.entryId().equals(selectedEntryId);
                boolean isHovered = rowRect.contains(mouseX, mouseY);

                int bg = isSelected ? GZTheme.COLOR_NAV_ACTIVE : (isHovered ? GZTheme.COLOR_NAV_HOVER : 0);
                int border = isSelected ? GZTheme.COLOR_BORDER_EMERALD : 0;
                if (bg != 0) GZTheme.drawCard(extractor, rowRect, bg, border);

                GZTheme.drawStatusDot(extractor, rowRect.x() + 4, rowRect.y() + 5, entry.dotColor());

                TextUtil.drawScaledEllipsizedText(extractor, font, entry.primaryLabel(), rowRect.x() + 11, rowRect.y() + 2,
                        rowRect.width() - 15, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, isSelected);
                TextUtil.drawScaledEllipsizedText(extractor, font, entry.secondaryLabel(), rowRect.x() + 11, rowRect.y() + 11,
                        rowRect.width() - 15, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            }

            currentY += rowH + 1;
        }

        extractor.disableScissor();
    }

    private void renderDetailPane(GuiGraphicsExtractor extractor, Font font, UiRect detailRect, CraftingKnowledgeBase craftingBase,
                                   ItemKnowledgeBase itemBase, int mouseX, int mouseY, boolean isCompact) {
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        if (selectedEntryId == null) {
            TextUtil.drawCenteredText(extractor, font, "Välj en post i listan", detailRect.x() + (detailRect.width() / 2),
                    detailRect.y() + (detailRect.height() / 2) - 4, detailRect.width(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        int pad = 5;
        int contentTop = detailRect.y() + (isCompact ? 16 : 4);
        UiRect contentArea = new UiRect(detailRect.x() + 1, contentTop, detailRect.width() - 2, detailRect.bottom() - contentTop - 1);

        if (isCompact) {
            UiRect backBtn = layout.backBtnRect();
            boolean backHover = backBtn.contains(mouseX, mouseY);
            GZTheme.drawButton(extractor, font, backBtn, "< Lista", false, backHover, TypographyScale.SMALL.getScale());
        }

        extractor.enableScissor(contentArea.x(), contentArea.y(), contentArea.right(), contentArea.bottom());

        if (selectedEntryId.startsWith("gz:") && craftingBase != null) {
            String id = selectedEntryId.substring(3);
            craftingBase.entries().stream().filter(e -> e.id().equals(id)).findFirst()
                    .ifPresent(e -> renderGameZoneRecipeDetail(extractor, font, contentArea, pad, e));
        } else if (selectedEntryId.startsWith("client:")) {
            String key = selectedEntryId.substring(7);
            cachedClientRecipes.stream().filter(s -> s.stableKey().equals(key)).findFirst()
                    .ifPresent(s -> renderClientRecipeDetail(extractor, font, contentArea, pad, s));
        } else if (selectedEntryId.startsWith("item:") && itemBase != null) {
            String id = selectedEntryId.substring(5);
            itemBase.items().stream().filter(i -> i.id().equals(id)).findFirst()
                    .ifPresent(i -> renderItemDetail(extractor, font, contentArea, pad, i));
        }

        extractor.disableScissor();
    }

    private void renderGameZoneRecipeDetail(GuiGraphicsExtractor extractor, Font font, UiRect contentArea, int pad, GameZoneCraftingEntry entry) {
        int currY = contentArea.y() + 2 - detailScrollOffset;
        int maxW = contentArea.width() - (pad * 2);
        int x = contentArea.x() + pad;

        TextUtil.drawScaledEllipsizedText(extractor, font, entry.outputItemId() + " x" + entry.outputCount(), x, currY,
                maxW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        currY += 11;

        TextUtil.drawScaledEllipsizedText(extractor, font, entry.source().getDisplayName(), x, currY,
                maxW, TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_YELLOW, false);
        currY += 10;

        if (entry.kind() == RecipeKind.SHAPED) {
            List<List<IngredientOption>> slots = new ArrayList<>();
            for (IngredientRef ref : entry.grid()) slots.add(alternativesOf(ref));
            currY = renderGrid(extractor, font, x, currY, entry.width(), entry.height(), slots);
        } else {
            TextUtil.drawScaledText(extractor, font, "Formlöst recept", x, currY, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            currY += 10;
            for (IngredientRef ref : entry.ingredients()) {
                String line = "- " + String.join(" / ", alternativesOf(ref).stream().map(IngredientOption::displayName).toList());
                currY += TextUtil.drawScaledWrappedText(extractor, font, line, x, currY, maxW,
                        TypographyScale.SMALL.getScale(), 1, 1, GZTheme.COLOR_TEXT_PRIMARY, false) + 2;
            }
        }

        if (entry.notes() != null && !entry.notes().isBlank()) {
            currY += 4;
            currY += TextUtil.drawScaledWrappedText(extractor, font, entry.notes(), x, currY, maxW,
                    TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_TEXT_SECONDARY, false);
        }

        currY += 6;
        renderVerificationTrail(extractor, font, x, currY, maxW, entry.verification());
    }

    private void renderClientRecipeDetail(GuiGraphicsExtractor extractor, Font font, UiRect contentArea, int pad, ClientRecipeSnapshot snap) {
        int currY = contentArea.y() + 2 - detailScrollOffset;
        int maxW = contentArea.width() - (pad * 2);
        int x = contentArea.x() + pad;

        TextUtil.drawScaledEllipsizedText(extractor, font, snap.outputDisplayName() + " x" + snap.outputCount(), x, currY,
                maxW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        currY += 11;

        TextUtil.drawScaledEllipsizedText(extractor, font, snap.outputItemId(), x, currY,
                maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currY += 9;

        TextUtil.drawScaledEllipsizedText(extractor, font, "Tillgängligt Minecraft-recept (upplåst i din receptbok)", x, currY,
                maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
        currY += 10;

        if (snap.kind() == RecipeKind.SHAPED) {
            renderGrid(extractor, font, x, currY, snap.width(), snap.height(), snap.slotAlternatives());
        } else {
            TextUtil.drawScaledText(extractor, font, "Formlöst recept", x, currY, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            currY += 10;
            for (List<IngredientOption> alts : snap.slotAlternatives()) {
                String line = "- " + String.join(" / ", alts.stream().map(IngredientOption::displayName).toList());
                currY += TextUtil.drawScaledWrappedText(extractor, font, line, x, currY, maxW,
                        TypographyScale.SMALL.getScale(), 1, 1, GZTheme.COLOR_TEXT_PRIMARY, false) + 2;
            }
        }
    }

    private void renderItemDetail(GuiGraphicsExtractor extractor, Font font, UiRect contentArea, int pad, CustomItemKnowledge item) {
        int currY = contentArea.y() + 2 - detailScrollOffset;
        int maxW = contentArea.width() - (pad * 2);
        int x = contentArea.x() + pad;

        TextUtil.drawScaledEllipsizedText(extractor, font, item.displayName(), x, currY,
                maxW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        currY += 11;

        StringBuilder meta = new StringBuilder();
        if (item.tier() != null) meta.append(item.tier());
        if (item.culture() != null) meta.append(meta.length() > 0 ? " · " : "").append(item.culture());
        if (item.serial() != null) meta.append(meta.length() > 0 ? " · " : "").append(item.serial());
        if (item.baseMinecraftItemId() != null) meta.append(meta.length() > 0 ? " · " : "").append(item.baseMinecraftItemId());
        if (meta.length() > 0) {
            TextUtil.drawScaledEllipsizedText(extractor, font, meta.toString(), x, currY, maxW,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            currY += 10;
        }

        if (item.description() != null && !item.description().isBlank()) {
            currY += TextUtil.drawScaledWrappedText(extractor, font, item.description(), x, currY, maxW,
                    TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_TEXT_PRIMARY, false) + 4;
        }

        if (!item.enchants().isEmpty()) {
            String line = "Enchants: " + String.join(", ", item.enchants());
            currY += TextUtil.drawScaledWrappedText(extractor, font, line, x, currY, maxW,
                    TypographyScale.SMALL.getScale(), 2, 1, GZTheme.COLOR_TEXT_SECONDARY, false) + 2;
        }

        if (item.specialEffect() != null && !item.specialEffect().isBlank()) {
            currY += TextUtil.drawScaledWrappedText(extractor, font, item.specialEffect(), x, currY, maxW,
                    TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_STATUS_YELLOW, false) + 4;
        }

        currY += 4;
        renderVerificationTrail(extractor, font, x, currY, maxW, item.verification());
    }

    /**
     * Renders the shared trust trail for a GameZone fact: the status badge, then "Källa: X" and
     * "Senast kontrollerad: YYYY-MM-DD" when a source is present, or an honest "not yet
     * confirmed" line when it isn't. The raw {@code sourceReference} URL is deliberately never
     * shown here - it stays in the data for traceability, not the normal UI.
     */
    private int renderVerificationTrail(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, VerificationMetadata verification) {
        int startY = y;
        VerificationStatus status = verification.status();
        GZTheme.drawBadge(extractor, font, x, y, status.getDisplayName(), GZTheme.COLOR_TEXT_SECONDARY, status.getArgbColor());
        y += 12;

        if (verification.hasSource()) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Källa: " + verification.sourceName(), x, y, maxW,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
            y += 9;
            if (verification.lastVerified() != null) {
                TextUtil.drawScaledEllipsizedText(extractor, font, "Senast kontrollerad: " + verification.lastVerified(), x, y, maxW,
                        TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
                y += 9;
            }
        } else {
            y += TextUtil.drawScaledWrappedText(extractor, font, "Den här informationen har ännu inte bekräftats.", x, y, maxW,
                    TypographyScale.META.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false) + 2;
        }
        return y - startY;
    }

    /**
     * Renders a width x height grid of cells, each showing a real item icon for its first
     * alternative (via {@code GuiGraphicsExtractor.fakeItem}) with a small "+" when more than one
     * legitimate alternative exists - never implying the shown item is the only valid one. Falls
     * back to a short text label if an id can't be resolved to an actual item (e.g. a malformed
     * Rule Pack entry, or a tag reference with no concrete representative). Returns the Y
     * position after the grid.
     */
    private int renderGrid(GuiGraphicsExtractor extractor, Font font, int x, int y, int width, int height, List<List<IngredientOption>> slots) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int idx = row * width + col;
                UiRect cell = new UiRect(x + col * (GRID_CELL_SIZE + GRID_GAP), y + row * (GRID_CELL_SIZE + GRID_GAP), GRID_CELL_SIZE, GRID_CELL_SIZE);
                GZTheme.drawCard(extractor, cell, GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);

                List<IngredientOption> alts = idx < slots.size() ? slots.get(idx) : List.of();
                if (!alts.isEmpty()) {
                    IngredientOption representative = alts.get(0);
                    ItemStack stack = MinecraftRecipeDisplayAdapter.resolveDisplayStack(representative.itemId());
                    if (!stack.isEmpty()) {
                        int iconX = cell.x() + ((cell.width() - GRID_ICON_SIZE) / 2);
                        int iconY = cell.y() + ((cell.height() - GRID_ICON_SIZE) / 2);
                        extractor.fakeItem(stack, iconX, iconY);
                    } else {
                        // No concrete item resolvable (a tag reference, or a malformed Rule Pack
                        // entry) - fall back to text using the real display name when one exists,
                        // never the raw id alone where a better name is available.
                        TextUtil.drawScaledEllipsizedText(extractor, font, shortItemLabel(representative.displayName()), cell.x() + 2, cell.y() + 6,
                                cell.width() - 4, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
                    }
                    if (alts.size() > 1) {
                        TextUtil.drawScaledText(extractor, font, "+", cell.right() - 7, cell.bottom() - 8,
                                TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_GREEN, false);
                    }
                }
            }
        }
        return y + (height * (GRID_CELL_SIZE + GRID_GAP)) + 4;
    }

    private String shortItemLabel(String label) {
        if (label == null) return "?";
        if (label.startsWith("#")) return label; // tag reference - keep the marker so it isn't mistaken for a concrete item
        int colon = label.indexOf(':');
        return colon >= 0 ? label.substring(colon + 1) : label;
    }

    /** Wraps a GameZone Rule Pack ingredient reference as {@link IngredientOption}s (no separately resolved display name exists for hand-authored Rule Pack data - the id itself is the label). */
    private List<IngredientOption> alternativesOf(IngredientRef ref) {
        if (ref == null || ref.isEmpty()) return List.of();
        if (!ref.itemIds().isEmpty()) {
            return ref.itemIds().stream().map(id -> new IngredientOption(id, id)).toList();
        }
        String tagLabel = "#" + ref.tag();
        return List.of(new IngredientOption(tagLabel, tagLabel));
    }

    private void drawUnavailableState(GuiGraphicsExtractor extractor, Font font, UiRect bounds, KnowledgeModuleStatus status) {
        GZTheme.drawCard(extractor, bounds, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        String msg = switch (status) {
            case ERROR -> "Fel inträffade vid inläsning av craftingdatan.";
            case UNAVAILABLE -> "Crafting är inte tillgängligt just nu.";
            case INCOMPATIBLE -> "Craftingdatan är sparad med ett schema som inte stöds av denna version.";
            case LOADED -> "Laddar...";
        };
        TextUtil.drawCenteredText(extractor, font, msg, bounds.x() + (bounds.width() / 2),
                bounds.y() + (bounds.height() / 2) - 4, bounds.width(), GZTheme.COLOR_TEXT_MUTED, false);
    }

    // ------------------------------------------------------------------
    // Input handling
    // ------------------------------------------------------------------

    public boolean mouseClicked(double mouseX, double mouseY, int button, UiRect bounds, GZCompanionMainScreen mainScreen) {
        if (button != 0) return false;
        if (layout == null) layout = CraftingLayout.calculate(bounds);

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

        if (layout.modeBtnRect().contains(mouseX, mouseY)) {
            mode = mode.next();
            selectedEntryId = null;
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
                    selectedEntryId = hit.entryId;
                    compactShowingDetail = true;
                    detailScrollOffset = 0;
                    return true;
                }
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null) return false;

        if (layout.listRect().contains(mouseX, mouseY)) {
            CompanionSession session = CompanionSession.getInstance();
            CraftingKnowledgeBase craftingBase = session.getCraftingKnowledgeStatus().isAvailable() ? session.getCraftingKnowledgeBase() : null;
            ItemKnowledgeBase itemBase = session.getItemKnowledgeStatus().isAvailable() ? session.getItemKnowledgeBase() : null;
            int count = buildEntries(craftingBase, itemBase).size();
            int maxScroll = calculateMaxListScroll(layout.listRect(), count);
            this.listScrollOffset = Math.max(0, Math.min(listScrollOffset - (int) (scrollY * 14), maxScroll));
            return true;
        }

        if (layout.detailRect().contains(mouseX, mouseY) && selectedEntryId != null) {
            this.detailScrollOffset = Math.max(0, detailScrollOffset - (int) (scrollY * 14));
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
            if (searchText.length() < MAX_SEARCH_LENGTH) {
                searchText = searchText + s;
            }
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
