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
import se.jimmyeliasson.gzcompanion.knowledge.crafting.bridge.MinecraftRecipeDisplayAdapter;
import se.jimmyeliasson.gzcompanion.knowledge.economy.MarketWatchInfo;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.ProductionCategory;
import se.jimmyeliasson.gzcompanion.marketwatch.MarketWatchNotesManager;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNote;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.ItemHoverTooltips;
import se.jimmyeliasson.gzcompanion.ui.ReferenceModeBanner;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TextInputHandler;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.MarketWatchLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Renders the MarketWatch tab: an always-available offline reference to the verified GameZone
 * MarketWatch system (resource demand for settlement upgrades - NOT an auction price list),
 * plus a purely local "Mina anteckningar" watchlist. Never runs {@code /marketwatch}
 * automatically and never presents a local note as GameZone's actual live market state.
 */
public class MarketWatchTabComponent implements TextInputHandler {
    private static final int MAX_FIELD_LENGTH = 48;
    private static final int ROW_H = 34;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private enum EditField { ITEM, NOTE }

    private record ListRowHit(UiRect rect, Runnable action) {}

    private String searchText = "";
    private boolean searchFocused = false;
    private int listScroll = 0;

    private boolean editing = false;
    private String editingNoteId = null;
    private String editItemText = "";
    private String editNoteText = "";
    private String editCategoryId = null;
    private EditField focusedField = null;

    private String pendingDeleteId = null;
    private long pendingDeleteAtMs = 0L;
    private long copyFeedbackExpiry = 0L;

    private MarketWatchLayout layout;
    private final List<ListRowHit> hitTargets = new ArrayList<>();
    private final ItemHoverTooltips itemHoverTooltips = new ItemHoverTooltips();

    public ItemHoverTooltips getItemHoverTooltips() {
        return itemHoverTooltips;
    }

    @Override
    public boolean isTextInputFocused() {
        return searchFocused || (editing && focusedField != null);
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        // MarketWatch is GameZone-specific reference/notes data - show a small, unobtrusive note
        // when the current server/world isn't GameZoneMC, so the verified system facts and local
        // notes are never mistaken for the current server's actual live market state. The layout
        // must RESERVE this strip rather than let the banner overlay live content.
        boolean showReferenceBanner = !CompanionSession.getInstance().isConnectedToGameZone();
        renderContent(extractor, font, ReferenceModeBanner.reserveBottomSpace(bounds, showReferenceBanner), mouseX, mouseY, mainScreen);
        if (showReferenceBanner) {
            ReferenceModeBanner.renderAtBottom(extractor, font, bounds);
        }
    }

    private void renderContent(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.layout = MarketWatchLayout.calculate(bounds);
        hitTargets.clear();
        itemHoverTooltips.clear();

        CompanionSession session = CompanionSession.getInstance();
        KnowledgeModuleStatus status = session.getMarketWatchInfoStatus();

        renderHeader(extractor, font, layout.headerRect(), status);
        renderReferenceCard(extractor, font, session, mouseX, mouseY);

        if (!session.getMarketWatchNotesManager().getStatus().isAvailable()) {
            renderMessage(extractor, font, layout.listRect(), "Lokala MarketWatch-anteckningar kunde inte laddas just nu.");
            return;
        }

        renderSearchAndAdd(extractor, font, mouseX, mouseY);

        if (editing) {
            renderEditForm(extractor, font, session, mouseX, mouseY);
            return;
        }

        renderNotesList(extractor, font, session, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, KnowledgeModuleStatus status) {
        GZTheme.drawIcon(extractor, IconId.MARKET, headerRect.x(), headerRect.y() + 1, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "MarketWatch", headerRect.x() + 13, headerRect.y() + 1,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        String badgeLabel = status.isAvailable() ? "Laddad" : status.getDisplayName();
        int badgeW = TextUtil.scaledWidth(font, badgeLabel, TypographyScale.META.getScale()) + 14;
        int dot = status.isAvailable() ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_STATUS_RED;
        GZTheme.drawBadge(extractor, font, headerRect.right() - badgeW, headerRect.y(), badgeLabel, GZTheme.COLOR_TEXT_SECONDARY, dot);
    }

    private void renderReferenceCard(GuiGraphicsExtractor extractor, Font font, CompanionSession session, int mouseX, int mouseY) {
        UiRect card = layout.referenceCardRect();
        GZTheme.drawCard(extractor, card, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        MarketWatchInfo info = session.getMarketWatchInfo();
        boolean available = session.getMarketWatchInfoStatus().isAvailable();
        int x = card.x() + 4;
        int maxW = card.width() - 8;
        int y = card.y() + 3;

        if (!available || info.purpose() == null) {
            TextUtil.drawScaledWrappedText(extractor, font, "MarketWatch-referensen kunde inte laddas just nu.", x, y, maxW,
                    TypographyScale.SMALL.getScale(), 2, 1, GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        y += TextUtil.drawScaledWrappedText(extractor, font, info.purpose(), x, y, maxW, TypographyScale.SMALL.getScale(), 2, 1, GZTheme.COLOR_TEXT_SECONDARY, false) + 2;

        int categoryCount = session.getSettlementCatalogStatus().isAvailable()
                ? session.getSettlementCatalog().productionCategories().size() : info.categoryCount();
        String metaLine = info.command() + " · " + categoryCount + " kategorier";

        // The button used to be a hardcoded 70px wide regardless of the command text, which
        // truncated "Kopiera /marketwatch" to "Kopiera /mark...". Size it to the actual text
        // instead, and let the meta line's own ellipsis absorb whatever space is left.
        String copyLabel = "Kopiera " + info.command();
        int copyBtnW = Math.min(maxW - 10, widestButtonWidth(s -> TextUtil.scaledWidth(font, s, TypographyScale.META.getScale()), 10, copyLabel));
        UiRect copyBtn = new UiRect(card.right() - 4 - copyBtnW, y - 1, copyBtnW, 11);
        boolean hov = copyBtn.contains(mouseX, mouseY);

        TextUtil.drawScaledEllipsizedText(extractor, font, metaLine, x, y, Math.max(10, (copyBtn.x() - 2) - x),
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        GZTheme.drawButton(extractor, font, copyBtn, copyLabel, false, hov, TypographyScale.META.getScale());
        String command = info.command();
        hitTargets.add(new ListRowHit(copyBtn, () -> copyToClipboard(command)));

        if (copyFeedbackExpiry > System.currentTimeMillis()) {
            TextUtil.drawScaledText(extractor, font, "Kopierat!", x, y + 10, TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_GREEN, false);
        }
    }

    private void renderSearchAndAdd(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        UiRect searchRect = layout.searchRect();
        UiRect clearBtnRect = layout.clearBtnRect();
        UiRect addBtnRect = layout.addBtnRect();

        int bg = searchFocused ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER;
        int border = searchFocused ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, searchRect, bg, border);
        int maxW = searchRect.width() - 8;
        if (searchText.isEmpty() && !searchFocused) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Sök anteckningar...", searchRect.x() + 4, searchRect.y() + 3, maxW,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        } else {
            String shown = searchText + (searchFocused && ((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, shown, searchRect.x() + 4, searchRect.y() + 3, maxW,
                    TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        }

        if (!searchText.isEmpty()) {
            boolean hov = clearBtnRect.contains(mouseX, mouseY);
            GZTheme.drawCard(extractor, clearBtnRect, hov ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
            TextUtil.drawCenteredText(extractor, font, "x", clearBtnRect.x() + (clearBtnRect.width() / 2), clearBtnRect.y() + 2,
                    clearBtnRect.width(), hov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED, false);
        }

        boolean addHov = addBtnRect.contains(mouseX, mouseY);
        GZTheme.drawButton(extractor, font, addBtnRect, "+ Ny", true, addHov, TypographyScale.META.getScale());
        hitTargets.add(new ListRowHit(addBtnRect, this::beginAddNote));
    }

    private void renderMessage(GuiGraphicsExtractor extractor, Font font, UiRect area, String message) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledWrappedText(extractor, font, message, area.x() + 6, area.y() + 8, area.width() - 12,
                TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_TEXT_MUTED, false);
    }

    private void beginAddNote() {
        editing = true;
        editingNoteId = null;
        editItemText = "";
        editNoteText = "";
        editCategoryId = null;
        focusedField = EditField.ITEM;
    }

    private void cancelEdit() {
        editing = false;
        editingNoteId = null;
        focusedField = null;
    }

    private void renderEditForm(GuiGraphicsExtractor extractor, Font font, CompanionSession session, int mouseX, int mouseY) {
        UiRect area = layout.listRect();
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int x = area.x() + 4;
        int maxW = area.width() - 8;
        int y = area.y() + 4;

        TextUtil.drawScaledText(extractor, font, editingNoteId == null ? "Ny anteckning" : "Redigera anteckning", x, y, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);
        y += 12;

        UiRect itemRect = new UiRect(x, y, maxW, 11);
        drawTextField(extractor, font, itemRect, "Föremål (namn eller minecraft:id)...", editItemText, focusedField == EditField.ITEM);
        hitTargets.add(new ListRowHit(itemRect, () -> focusedField = EditField.ITEM));
        y += 13;

        List<ProductionCategory> categories = session.getSettlementCatalogStatus().isAvailable()
                ? session.getSettlementCatalog().productionCategories() : List.of();
        String categoryLabel = editCategoryId == null ? "Ingen kategori"
                : categories.stream().filter(c -> c.id().equals(editCategoryId)).findFirst().map(ProductionCategory::displayName).orElse(editCategoryId);
        UiRect categoryBtn = new UiRect(x, y, maxW, 11);
        GZTheme.drawButton(extractor, font, categoryBtn, "Kategori: " + categoryLabel, false, categoryBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        hitTargets.add(new ListRowHit(categoryBtn, () -> editCategoryId = nextCategoryId(categories, editCategoryId)));
        y += 14;

        UiRect noteRect = new UiRect(x, y, maxW, 11);
        drawTextField(extractor, font, noteRect, "Anteckning (pris, efterfrågan, mängd, fritext)...", editNoteText, focusedField == EditField.NOTE);
        hitTargets.add(new ListRowHit(noteRect, () -> focusedField = EditField.NOTE));
        y += 14;

        int btnW = Math.max(40, (maxW - 4) / 2);
        UiRect saveBtn = new UiRect(x, y, btnW, 11);
        UiRect cancelBtn = new UiRect(x + btnW + 4, y, btnW, 11);
        GZTheme.drawButton(extractor, font, saveBtn, "Spara", true, saveBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        GZTheme.drawButton(extractor, font, cancelBtn, "Avbryt", false, cancelBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        hitTargets.add(new ListRowHit(saveBtn, () -> saveNote(session)));
        hitTargets.add(new ListRowHit(cancelBtn, this::cancelEdit));
    }

    private String nextCategoryId(List<ProductionCategory> categories, String currentId) {
        if (categories.isEmpty()) return null;
        if (currentId == null) return categories.get(0).id();
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id().equals(currentId)) {
                return (i + 1 < categories.size()) ? categories.get(i + 1).id() : null;
            }
        }
        return null;
    }

    private void drawTextField(GuiGraphicsExtractor extractor, Font font, UiRect rect, String placeholder, String value, boolean focused) {
        int bg = focused ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER;
        int border = focused ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, rect, bg, border);
        int maxW = rect.width() - 6;
        if (value.isEmpty() && !focused) {
            TextUtil.drawScaledEllipsizedText(extractor, font, placeholder, rect.x() + 3, rect.y() + 2, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        } else {
            String shown = value + (focused && ((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            TextUtil.drawScaledEllipsizedText(extractor, font, shown, rect.x() + 3, rect.y() + 2, maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        }
    }

    private void saveNote(CompanionSession session) {
        if (editItemText.isBlank() && editNoteText.isBlank()) {
            cancelEdit();
            return;
        }
        String contextKey = session.getCurrentStorageContext();
        MarketWatchNotesManager notes = session.getMarketWatchNotesManager();

        String itemId = editItemText.contains(":") ? editItemText.trim() : null;
        String displayName = itemId == null ? editItemText.trim() : null;

        if (editingNoteId == null) {
            notes.addNote(contextKey, itemId, displayName, editCategoryId, editNoteText, System.currentTimeMillis());
        } else {
            MarketWatchNote existing = notes.getNotes(contextKey).stream().filter(n -> n.id().equals(editingNoteId)).findFirst().orElse(null);
            if (existing != null) {
                MarketWatchNote updated = new MarketWatchNote(existing.id(), itemId, editItemText.trim(),
                        editCategoryId, editNoteText, existing.lastObservedAtMs(), existing.favorite());
                notes.updateNote(contextKey, updated);
            }
        }
        cancelEdit();
    }

    private static int calculateMaxScroll(int rowH, int rowCount, int visibleH) {
        return Math.max(0, (rowCount * rowH) - Math.max(1, visibleH));
    }

    /**
     * The exact two-step delete label - extracted so the confirmation wording is directly
     * testable without a live Font/render pass.
     */
    static String deleteButtonLabel(boolean pendingConfirm) {
        return pendingConfirm ? "Säker?" : "Ta bort";
    }

    /**
     * Computes a button width that actually fits its widest possible label, rather than a
     * hardcoded pixel width that truncates as soon as real text (a command, a longer label)
     * exceeds it. {@code textWidthMeasurer} is injected so this stays testable without a live
     * Minecraft {@code Font}.
     */
    static int widestButtonWidth(ToIntFunction<String> textWidthMeasurer, int padding, String... labels) {
        int widest = 0;
        for (String label : labels) {
            widest = Math.max(widest, textWidthMeasurer.applyAsInt(label));
        }
        return widest + padding;
    }

    private void renderNotesList(GuiGraphicsExtractor extractor, Font font, CompanionSession session, int mouseX, int mouseY) {
        UiRect listRect = layout.listRect();
        GZTheme.drawCard(extractor, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        // A fixed, non-scrolling label: these are always the player's own local notes, never
        // GameZone's actual live market/demand state - see docs/MARKETWATCH.md.
        TextUtil.drawScaledText(extractor, font, "MINA ANTECKNINGAR (LOKALT)", listRect.x() + 4, listRect.y() + 2,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        int listTop = listRect.y() + 10;

        String contextKey = session.getCurrentStorageContext();
        MarketWatchNotesManager notesManager = session.getMarketWatchNotesManager();
        List<MarketWatchNote> notes = notesManager.search(contextKey, searchText);

        if (notes.isEmpty()) {
            TextUtil.drawCenteredText(extractor, font, "Inga lokala anteckningar ännu.", listRect.x() + (listRect.width() / 2),
                    listTop + ((listRect.bottom() - listTop) / 2) - 4, listRect.width(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        int maxScroll = calculateMaxScroll(ROW_H, notes.size(), listRect.bottom() - listTop - 2);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));

        extractor.enableScissor(listRect.x() + 1, listTop, listRect.right() - 1, listRect.bottom() - 1);
        int currentY = listTop + 1 - listScroll;
        int x = listRect.x() + 3;
        int maxW = listRect.width() - 6;

        long now = System.currentTimeMillis();
        for (MarketWatchNote note : notes) {
            if (currentY + ROW_H >= listTop && currentY <= listRect.bottom()) {
                renderNoteRow(extractor, font, x, currentY, maxW, note, notesManager, contextKey, mouseX, mouseY, now);
            }
            currentY += ROW_H;
        }
        extractor.disableScissor();
    }

    private void renderNoteRow(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, MarketWatchNote note,
                                MarketWatchNotesManager notesManager, String contextKey, int mouseX, int mouseY, long now) {
        GZTheme.drawCard(extractor, new UiRect(x, y, maxW, ROW_H - 2), GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
        int iconOffset = 0;

        if (note.itemId() != null) {
            ItemStack stack = MinecraftRecipeDisplayAdapter.resolveDisplayStack(note.itemId());
            if (!stack.isEmpty()) {
                try {
                    extractor.fakeItem(stack, x + 2, y + 2);
                    iconOffset = 16;
                    itemHoverTooltips.register(new UiRect(x + 2, y + 2, 16, 16), stack, note.itemId());
                } catch (Exception ignored) {
                    // A single unbakeable item icon must never take down the whole tab.
                }
            }
        }

        UiRect favBtn = new UiRect(x + maxW - 16, y + 1, 14, 10);
        boolean favHov = favBtn.contains(mouseX, mouseY);
        String favLabel = note.favorite() ? "★" : "☆";
        TextUtil.drawScaledText(extractor, font, favLabel, favBtn.x(), favBtn.y(), TypographyScale.SMALL.getScale(),
                note.favorite() ? GZTheme.COLOR_STATUS_YELLOW : (favHov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED), false);
        String noteId = note.id();
        hitTargets.add(new ListRowHit(favBtn, () -> notesManager.toggleFavorite(contextKey, noteId)));

        TextUtil.drawScaledEllipsizedText(extractor, font, note.displayName(), x + 2 + iconOffset, y + 2, maxW - 20 - iconOffset,
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, false);
        TextUtil.drawScaledEllipsizedText(extractor, font, note.note(), x + 2 + iconOffset, y + 12, maxW - 20 - iconOffset,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);

        boolean pendingConfirm = noteId.equals(pendingDeleteId) && (now - pendingDeleteAtMs) < 3000L;
        String deleteLabel = deleteButtonLabel(pendingConfirm);

        // Both buttons used to be a hardcoded 34px, which truncated "Ta bort" to "Ta bo...".
        // Size each to whichever of its possible labels is widest ("Säker?" vs "Ta bort" for the
        // delete button) so the two-step confirmation never shifts the row layout mid-flow.
        ToIntFunction<String> measure = s -> TextUtil.scaledWidth(font, s, TypographyScale.META.getScale());
        int deleteBtnW = Math.max(34, widestButtonWidth(measure, 8, "Ta bort", "Säker?"));
        int editBtnW = Math.max(34, widestButtonWidth(measure, 8, "Ändra"));

        UiRect deleteBtn = new UiRect(x + maxW - deleteBtnW, y + 20, deleteBtnW, 10);
        UiRect editBtn = new UiRect(deleteBtn.x() - 2 - editBtnW, y + 20, editBtnW, 10);

        String lastObserved = note.lastObservedAtMs() > 0
                ? "Senast sett: " + DATE_FORMAT.format(Instant.ofEpochMilli(note.lastObservedAtMs()).atZone(ZoneId.systemDefault()))
                : "Senast sett: aldrig";
        int lastObservedMaxW = Math.max(10, (editBtn.x() - 2) - (x + 2 + iconOffset));
        TextUtil.drawScaledEllipsizedText(extractor, font, lastObserved, x + 2 + iconOffset, y + 21, lastObservedMaxW,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);

        GZTheme.drawButton(extractor, font, editBtn, "Ändra", false, editBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        GZTheme.drawButton(extractor, font, deleteBtn, deleteLabel, false, deleteBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());

        hitTargets.add(new ListRowHit(editBtn, () -> {
            editing = true;
            editingNoteId = noteId;
            editItemText = note.itemId() != null ? note.itemId() : note.displayName();
            editNoteText = note.note();
            editCategoryId = note.categoryId();
            focusedField = null;
        }));
        hitTargets.add(new ListRowHit(deleteBtn, () -> {
            if (noteId.equals(pendingDeleteId) && (System.currentTimeMillis() - pendingDeleteAtMs) < 3000L) {
                notesManager.deleteNote(contextKey, noteId);
                pendingDeleteId = null;
            } else {
                pendingDeleteId = noteId;
                pendingDeleteAtMs = System.currentTimeMillis();
            }
        }));
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
        if (layout == null) layout = MarketWatchLayout.calculate(bounds);

        boolean insideSearch = layout.searchRect().contains(mouseX, mouseY);
        boolean insideClear = layout.clearBtnRect().contains(mouseX, mouseY);

        if (!editing && insideClear) {
            searchText = "";
            return true;
        }
        if (!editing) {
            searchFocused = insideSearch;
            if (insideSearch) return true;
        }

        for (ListRowHit hit : hitTargets) {
            if (hit.rect().contains(mouseX, mouseY)) {
                hit.action().run();
                return true;
            }
        }

        return editing;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null || editing) return false;
        if (layout.listRect().contains(mouseX, mouseY)) {
            listScroll = Math.max(0, listScroll - (int) (scrollY * 14));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event == null) return false;
        String s = event.codepointAsString();
        if (s == null || s.isEmpty()) return false;

        if (editing && focusedField != null) {
            if (focusedField == EditField.ITEM && editItemText.length() < MAX_FIELD_LENGTH) {
                editItemText += s;
            } else if (focusedField == EditField.NOTE && editNoteText.length() < 96) {
                editNoteText += s;
            }
            return true;
        }
        if (!editing && searchFocused) {
            if (searchText.length() < MAX_FIELD_LENGTH) searchText += s;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event == null) return false;
        int key = event.key();

        if (editing && focusedField != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (focusedField == EditField.ITEM && !editItemText.isEmpty()) {
                    editItemText = editItemText.substring(0, editItemText.length() - 1);
                } else if (focusedField == EditField.NOTE && !editNoteText.isEmpty()) {
                    editNoteText = editNoteText.substring(0, editNoteText.length() - 1);
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                focusedField = (focusedField == EditField.ITEM) ? EditField.NOTE : EditField.ITEM;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelEdit();
                return true;
            }
            return false;
        }

        if (!editing && searchFocused) {
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
