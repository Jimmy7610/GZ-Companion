package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.chest.bridge.MinecraftPlayerPoseReader;
import se.jimmyeliasson.gzcompanion.chest.model.DimensionNames;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationManager;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationMath;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationReading;
import se.jimmyeliasson.gzcompanion.chest.nav.KistorNavigationText;
import se.jimmyeliasson.gzcompanion.chest.nav.NavigationDestination;
import se.jimmyeliasson.gzcompanion.chest.nav.NavigationPoint;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeModuleStatus;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.bridge.MinecraftRecipeDisplayAdapter;
import se.jimmyeliasson.gzcompanion.knowledge.economy.MarketWatchInfo;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.ProductionCategory;
import se.jimmyeliasson.gzcompanion.marketwatch.MarketWatchLocationInput;
import se.jimmyeliasson.gzcompanion.marketwatch.MarketWatchNavigation;
import se.jimmyeliasson.gzcompanion.marketwatch.MarketWatchNotesManager;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchNote;
import se.jimmyeliasson.gzcompanion.marketwatch.storage.MarketWatchSavedLocation;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.ItemHoverTooltips;
import se.jimmyeliasson.gzcompanion.ui.ReferenceModeBanner;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TextInputHandler;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.MarketWatchEditFormLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.MarketWatchLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Renders the MarketWatch tab: an always-available offline reference to the verified GameZone
 * MarketWatch system (resource demand for settlement upgrades - NOT an auction price list),
 * plus a purely local "Mina anteckningar" watchlist. Never runs {@code /marketwatch}
 * automatically and never presents a local note as GameZone's actual live market state.
 *
 * <p>A note can carry a place the player saved themselves: typed as X/Y/Z in the edit form, or
 * filled from their own current position with "Använd min plats". Only such notes show "Hitta",
 * which hands the place to the shared
 * Kistor navigation through {@link MarketWatchNavigation} - this tab draws no arrow and does no
 * navigation math of its own beyond reusing {@link ChestNavigationMath} for the row's distance.
 */
public class MarketWatchTabComponent implements TextInputHandler {
    private static final int MAX_FIELD_LENGTH = 48;
    /** Enough for any in-world coordinate ("-30000000") plus a little slack for stray spaces. */
    private static final int MAX_COORD_LENGTH = 12;
    private static final int ROW_H = 34;
    /** A note with a saved location gets one extra line: "Din sparade plats • ..." + Hitta/Stoppa. */
    private static final int ROW_H_WITH_LOCATION = 45;
    private static final long FEEDBACK_MS = 4000L;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    enum EditField { ITEM, NOTE, X, Y, Z }

    private record ListRowHit(UiRect rect, Runnable action) {}

    private String searchText = "";
    private boolean searchFocused = false;
    private int listScroll = 0;

    private boolean editing = false;
    private String editingNoteId = null;
    private String editItemText = "";
    private String editNoteText = "";
    private String editCategoryId = null;
    /** The X/Y/Z texts exactly as typed - kept on a validation error, never reset to 0. */
    private final String[] editCoords = {"", "", ""};
    /** Dimension to save with: the edited location's, or the one "Använd min plats" captured. */
    private String editLocationDimension = null;
    /** The note's saved location when editing began (kept as-is if nothing about it changes). */
    private MarketWatchSavedLocation editLocationOriginal = null;
    private String editLocationError = null;
    private EditField focusedField = null;
    private int editScroll = 0;
    private boolean revealFocusedField = false;
    private MarketWatchEditFormLayout formLayout;

    private String navFeedbackNoteId = null;
    private long navFeedbackExpiry = 0L;

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
        setEditLocation(null);
        editLocationOriginal = null;
        focusedField = EditField.ITEM;
        editScroll = 0;
    }

    private void beginEditNote(MarketWatchNote note) {
        editing = true;
        editingNoteId = note.id();
        editItemText = note.itemId() != null ? note.itemId() : note.displayName();
        editNoteText = note.note();
        editCategoryId = note.categoryId();
        setEditLocation(note.location());
        editLocationOriginal = note.location();
        focusedField = null;
        editScroll = 0;
    }

    private void cancelEdit() {
        editing = false;
        editingNoteId = null;
        setEditLocation(null);
        editLocationOriginal = null;
        focusedField = null;
        editScroll = 0;
    }

    /** Fills (or with null, clears) the form's X/Y/Z fields and the dimension they will be saved in. */
    private void setEditLocation(MarketWatchSavedLocation location) {
        String[] fields = MarketWatchLocationInput.fieldsFor(location);
        System.arraycopy(fields, 0, editCoords, 0, 3);
        editLocationDimension = location != null ? location.dimensionKey() : null;
        editLocationError = null;
    }

    /**
     * "Använd min plats": fills X/Y/Z and the dimension from the player's OWN current position
     * (persisted on "Spara", discarded on "Avbryt"). Without a live position nothing changes.
     */
    private void useMyPosition() {
        Optional<MarketWatchSavedLocation> captured = MarketWatchNavigation.locationFromPose(
                MinecraftPlayerPoseReader.read(1.0f), System.currentTimeMillis());
        if (captured.isPresent()) {
            setEditLocation(captured.get());
        } else {
            editLocationError = "Kunde inte läsa din position just nu.";
        }
    }

    private boolean hasAnyCoordinate() {
        return !(editCoords[0].isBlank() && editCoords[1].isBlank() && editCoords[2].isBlank());
    }

    private void renderEditForm(GuiGraphicsExtractor extractor, Font font, CompanionSession session, int mouseX, int mouseY) {
        UiRect area = layout.listRect();
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        ToIntFunction<String> measure = s -> TextUtil.scaledWidth(font, s, TypographyScale.META.getScale());
        int useMyPosW = widestButtonWidth(measure, 10, "Använd min plats");
        int removeW = hasAnyCoordinate() ? widestButtonWidth(measure, 10, "Ta bort plats") : 0;
        MarketWatchEditFormLayout f = MarketWatchEditFormLayout.calculate(area, editScroll, useMyPosW, removeW);
        if (revealFocusedField && focusedField != null) {
            editScroll = f.scrollToReveal(fieldRect(f, focusedField));
            f = MarketWatchEditFormLayout.calculate(area, editScroll, useMyPosW, removeW);
        }
        revealFocusedField = false;
        editScroll = f.scroll();
        formLayout = f;

        UiRect vp = f.viewport();
        extractor.enableScissor(vp.x(), vp.y(), vp.right(), vp.bottom());

        TextUtil.drawScaledText(extractor, font, editingNoteId == null ? "Ny anteckning" : "Redigera anteckning", f.titleRect().x(), f.titleRect().y(),
                TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        drawTextField(extractor, font, f.itemRect(), "Föremål (namn eller minecraft:id)...", editItemText, focusedField == EditField.ITEM);
        addFormHit(f.itemRect(), () -> focusedField = EditField.ITEM);

        List<ProductionCategory> categories = session.getSettlementCatalogStatus().isAvailable()
                ? session.getSettlementCatalog().productionCategories() : List.of();
        String categoryLabel = editCategoryId == null ? "Ingen kategori"
                : categories.stream().filter(c -> c.id().equals(editCategoryId)).findFirst().map(ProductionCategory::displayName).orElse(editCategoryId);
        GZTheme.drawButton(extractor, font, f.categoryRect(), "Kategori: " + categoryLabel, false, f.categoryRect().contains(mouseX, mouseY), TypographyScale.META.getScale());
        addFormHit(f.categoryRect(), () -> editCategoryId = nextCategoryId(categories, editCategoryId));

        drawTextField(extractor, font, f.noteRect(), "Anteckning (pris, efterfrågan, mängd, fritext)...", editNoteText, focusedField == EditField.NOTE);
        addFormHit(f.noteRect(), () -> focusedField = EditField.NOTE);

        renderEditLocation(extractor, font, f, mouseX, mouseY);

        GZTheme.drawButton(extractor, font, f.saveBtnRect(), "Spara", true, f.saveBtnRect().contains(mouseX, mouseY), TypographyScale.META.getScale());
        GZTheme.drawButton(extractor, font, f.cancelBtnRect(), "Avbryt", false, f.cancelBtnRect().contains(mouseX, mouseY), TypographyScale.META.getScale());
        addFormHit(f.saveBtnRect(), () -> saveNote(session));
        addFormHit(f.cancelBtnRect(), this::cancelEdit);

        extractor.disableScissor();
    }

    /** Registers a form control's click target, clipped to the visible part of the (possibly scrolled) form. */
    private void addFormHit(UiRect rect, Runnable action) {
        UiRect visible = formLayout.visiblePart(rect);
        if (visible != null) hitTargets.add(new ListRowHit(visible, action));
    }

    private static UiRect fieldRect(MarketWatchEditFormLayout f, EditField field) {
        return switch (field) {
            case ITEM -> f.itemRect();
            case NOTE -> f.noteRect();
            case X -> f.xFieldRect();
            case Y -> f.yFieldRect();
            case Z -> f.zFieldRect();
        };
    }

    /**
     * The optional location section: {@code X [..] Y [..] Z [..] [Använd min plats]}, then a status
     * line. The wording always says the place is the player's OWN, so it can never be mistaken for
     * shop data from MarketWatch.
     */
    private void renderEditLocation(GuiGraphicsExtractor extractor, Font font, MarketWatchEditFormLayout f, int mouseX, int mouseY) {
        UiRect[] labels = {f.xLabelRect(), f.yLabelRect(), f.zLabelRect()};
        UiRect[] fields = {f.xFieldRect(), f.yFieldRect(), f.zFieldRect()};
        EditField[] ids = {EditField.X, EditField.Y, EditField.Z};
        String[] names = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            TextUtil.drawScaledCenteredText(extractor, font, names[i], labels[i].x() + labels[i].width() / 2, labels[i].y() + 2, labels[i].width() + 2,
                    TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            drawCoordinateField(extractor, font, fields[i], editCoords[i], focusedField == ids[i]);
            EditField id = ids[i];
            addFormHit(fields[i], () -> focusedField = id);
        }
        GZTheme.drawButton(extractor, font, f.useMyPositionBtnRect(), "Använd min plats", false, f.useMyPositionBtnRect().contains(mouseX, mouseY), TypographyScale.META.getScale());
        addFormHit(f.useMyPositionBtnRect(), this::useMyPosition);

        String status;
        int statusColor;
        if (editLocationError != null) {
            status = editLocationError;
            statusColor = GZTheme.COLOR_STATUS_YELLOW;
        } else {
            status = editLocationStatusText(editCoords, editLocationDimension, editLocationOriginal);
            statusColor = hasAnyCoordinate() ? GZTheme.COLOR_TEXT_SECONDARY : GZTheme.COLOR_TEXT_MUTED;
        }
        UiRect st = f.statusRect();
        TextUtil.drawScaledEllipsizedText(extractor, font, status, st.x(), st.y() + 2, st.width(), TypographyScale.META.getScale(), statusColor, false);

        if (f.removeLocationBtnRect().width() > 0) {
            GZTheme.drawButton(extractor, font, f.removeLocationBtnRect(), "Ta bort plats", false, f.removeLocationBtnRect().contains(mouseX, mouseY), TypographyScale.META.getScale());
            addFormHit(f.removeLocationBtnRect(), () -> setEditLocation(null));
        }
    }

    /**
     * Edit-form status line for the location fields. {@code dimension} is the one the location
     * will be saved in, or null when it will be the player's current dimension at save time.
     */
    static String editLocationStatusText(String[] coords, String dimension, MarketWatchSavedLocation original) {
        boolean blank = coords[0].isBlank() && coords[1].isBlank() && coords[2].isBlank();
        if (blank) return "Ingen plats. Skriv X, Y, Z eller tryck Använd min plats.";
        if (dimension == null) return "Din plats sparas i din nuvarande dimension.";
        String text = "Din plats i " + DimensionNames.shortName(dimension);
        boolean unchanged = original != null && original.dimensionKey().equals(dimension)
                && java.util.Arrays.equals(MarketWatchLocationInput.fieldsFor(original), new String[]{coords[0].strip(), coords[1].strip(), coords[2].strip()});
        if (unchanged && original.savedAtMs() > 0) {
            text += " • sparad " + DATE_FORMAT.format(Instant.ofEpochMilli(original.savedAtMs()).atZone(ZoneId.systemDefault()));
        }
        return text;
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

    /** A small coordinate field: while the text is wider than the field, its END stays visible so typing can be followed. */
    private void drawCoordinateField(GuiGraphicsExtractor extractor, Font font, UiRect rect, String value, boolean focused) {
        int bg = focused ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER;
        int border = focused ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, rect, bg, border);
        String shown = value + (focused && ((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
        int maxW = rect.width() - 4;
        float scale = TypographyScale.META.getScale();
        while (shown.length() > 1 && TextUtil.scaledWidth(font, shown, scale) > maxW) {
            shown = shown.substring(1);
        }
        TextUtil.drawScaledText(extractor, font, shown, rect.x() + 2, rect.y() + 2, scale, GZTheme.COLOR_TEXT_PRIMARY, false);
    }

    private void saveNote(CompanionSession session) {
        // Resolve the location first: an invalid or incomplete location keeps the form open with the
        // typed values intact and saves nothing - never a note with a guessed or dropped location.
        MarketWatchLocationInput.Outcome location = MarketWatchLocationInput.resolve(editCoords[0], editCoords[1], editCoords[2],
                editLocationDimension, editLocationDimension == null ? MinecraftPlayerPoseReader.read(1.0f) : Optional.empty(),
                editLocationOriginal, System.currentTimeMillis());
        if (!location.isValid()) {
            editLocationError = location.error();
            return;
        }
        MarketWatchSavedLocation editLocation = location.location();
        if (editItemText.isBlank() && editNoteText.isBlank() && editLocation == null) {
            cancelEdit();
            return;
        }
        String contextKey = session.getCurrentStorageContext();
        MarketWatchNotesManager notes = session.getMarketWatchNotesManager();

        String itemId = editItemText.contains(":") ? editItemText.trim() : null;
        String displayName = itemId == null ? editItemText.trim() : null;

        if (editingNoteId == null) {
            String newId = notes.addNote(contextKey, itemId, displayName, editCategoryId, editNoteText, System.currentTimeMillis());
            if (newId != null && editLocation != null) {
                notes.setLocation(contextKey, newId, editLocation);
            }
        } else {
            MarketWatchNote existing = notes.getNotes(contextKey).stream().filter(n -> n.id().equals(editingNoteId)).findFirst().orElse(null);
            if (existing != null) {
                MarketWatchNote updated = new MarketWatchNote(existing.id(), itemId, editItemText.trim(),
                        editCategoryId, editNoteText, existing.lastObservedAtMs(), existing.favorite(), editLocation);
                if (notes.updateNote(contextKey, updated)) {
                    syncActiveNavigation(session.getKistorRuntime().navigation(), contextKey, updated);
                }
            }
        }
        cancelEdit();
    }

    /**
     * If {@code note} is the active navigation target, keep the target in step with the edit: a
     * moved place or renamed note restarts toward the new point; a removed place stops navigation
     * (never keeps guiding toward a place the note no longer has).
     */
    static void syncActiveNavigation(ChestNavigationManager navigation, String contextKey, MarketWatchNote note) {
        if (!MarketWatchNavigation.isNavigatingTo(navigation, note)) return;
        if (!MarketWatchNavigation.startNavigation(navigation, contextKey, note, System.currentTimeMillis())) {
            MarketWatchNavigation.stopIfNavigatingTo(navigation, note);
        }
    }

    private static int calculateMaxScroll(int contentH, int visibleH) {
        return Math.max(0, contentH - Math.max(1, visibleH));
    }

    /** Notes without a saved location keep their original height; only located notes grow. */
    static int rowHeight(MarketWatchNote note) {
        return note.hasLocation() ? ROW_H_WITH_LOCATION : ROW_H;
    }

    /**
     * Text of a located note's extra line. Always starts from "Din sparade plats" / "NAVIGERAR"
     * so the player knows it is a place THEY saved; distance wording comes from the shared Kistor
     * navigation copy. {@code reading} is null when there is no live position to measure from.
     */
    static String locationLineText(boolean navigating, ChestNavigationReading reading, boolean showStartedFeedback) {
        if (navigating && showStartedFeedback) return KistorNavigationText.NAVIGATION_STARTED;
        String summary = reading != null ? KistorNavigationText.bannerSummary(reading) : "";
        String prefix = navigating ? "● NAVIGERAR HIT" : "Din sparade plats";
        return summary.isEmpty() ? prefix : prefix + " • " + summary;
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

        int contentH = 0;
        for (MarketWatchNote note : notes) contentH += rowHeight(note);
        int maxScroll = calculateMaxScroll(contentH, listRect.bottom() - listTop - 2);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));

        // Read once per frame and only when a row actually needs a distance.
        // Same per-frame context validation as the Kistor tab: a target from another world/server
        // stops here too instead of waiting for the HUD's once-per-second check.
        ChestNavigationManager navigation = session.getKistorRuntime().navigation();
        navigation.onContextObserved(contextKey);
        Optional<PlayerPose> pose = notes.stream().anyMatch(MarketWatchNote::hasLocation)
                ? MinecraftPlayerPoseReader.read(1.0f) : Optional.empty();
        UiRect viewport = new UiRect(listRect.x() + 1, listTop, listRect.width() - 2, listRect.bottom() - 1 - listTop);

        extractor.enableScissor(listRect.x() + 1, listTop, listRect.right() - 1, listRect.bottom() - 1);
        int currentY = listTop + 1 - listScroll;
        int x = listRect.x() + 3;
        int maxW = listRect.width() - 6;

        long now = System.currentTimeMillis();
        for (MarketWatchNote note : notes) {
            int rowH = rowHeight(note);
            if (currentY + rowH >= listTop && currentY <= listRect.bottom()) {
                renderNoteRow(extractor, font, x, currentY, maxW, note, notesManager, contextKey, mouseX, mouseY, now);
                if (note.hasLocation()) {
                    renderLocationLine(extractor, font, x, currentY + ROW_H - 2, maxW, note, navigation, contextKey, pose, viewport, mouseX, mouseY, now);
                }
            }
            currentY += rowH;
        }
        extractor.disableScissor();
    }

    private void renderNoteRow(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, MarketWatchNote note,
                                MarketWatchNotesManager notesManager, String contextKey, int mouseX, int mouseY, long now) {
        GZTheme.drawCard(extractor, new UiRect(x, y, maxW, rowHeight(note) - 2), GZTheme.COLOR_CARD_INNER, GZTheme.COLOR_BORDER_SUBTLE);
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

        hitTargets.add(new ListRowHit(editBtn, () -> beginEditNote(note)));
        hitTargets.add(new ListRowHit(deleteBtn, () -> {
            if (noteId.equals(pendingDeleteId) && (System.currentTimeMillis() - pendingDeleteAtMs) < 3000L) {
                if (notesManager.deleteNote(contextKey, noteId)) {
                    MarketWatchNavigation.stopIfNavigatingTo(CompanionSession.getInstance().getKistorRuntime().navigation(), note);
                }
                pendingDeleteId = null;
            } else {
                pendingDeleteId = noteId;
                pendingDeleteAtMs = System.currentTimeMillis();
            }
        }));
    }

    /**
     * The extra line of a note with a saved location: where it is relative to the player (shared
     * Kistor distance wording) and Hitta/Stoppa. Hitta is only drawn when the note's location
     * resolves to a valid navigation point for the CURRENT context.
     */
    private void renderLocationLine(GuiGraphicsExtractor extractor, Font font, int x, int y, int maxW, MarketWatchNote note,
                                    ChestNavigationManager navigation, String contextKey, Optional<PlayerPose> pose,
                                    UiRect viewport, int mouseX, int mouseY, long now) {
        Optional<NavigationPoint> point = MarketWatchNavigation.toNavigationPoint(contextKey, note);
        if (point.isEmpty()) return;

        boolean navigating = MarketWatchNavigation.isNavigatingTo(navigation, note);
        ToIntFunction<String> measure = s -> TextUtil.scaledWidth(font, s, TypographyScale.META.getScale());
        int btnW = Math.max(34, widestButtonWidth(measure, 8, "Hitta", "Stoppa"));
        UiRect navBtn = new UiRect(x + maxW - btnW, y, btnW, 10);

        ChestNavigationReading reading = pose.map(p -> ChestNavigationMath.evaluate(p, NavigationDestination.of(point.get()))).orElse(null);
        boolean feedback = note.id().equals(navFeedbackNoteId) && now < navFeedbackExpiry;
        String text = locationLineText(navigating, reading, feedback);
        int textX = x + 2;
        TextUtil.drawScaledEllipsizedText(extractor, font, text, textX, y + 1, Math.max(10, navBtn.x() - 2 - textX),
                TypographyScale.META.getScale(), navigating ? GZTheme.COLOR_MINT : GZTheme.COLOR_TEXT_SECONDARY, false);

        GZTheme.drawButton(extractor, font, navBtn, navigating ? "Stoppa" : "Hitta", !navigating, navBtn.contains(mouseX, mouseY), TypographyScale.META.getScale());
        // Clipped to the scrolled viewport so a half-hidden row is never clickable outside the list.
        int top = Math.max(navBtn.y(), viewport.y());
        int bottom = Math.min(navBtn.bottom(), viewport.bottom());
        if (bottom > top) {
            hitTargets.add(new ListRowHit(new UiRect(navBtn.x(), top, navBtn.width(), bottom - top),
                    () -> toggleNavigation(navigation, contextKey, note)));
        }
    }

    private void toggleNavigation(ChestNavigationManager navigation, String contextKey, MarketWatchNote note) {
        if (MarketWatchNavigation.isNavigatingTo(navigation, note)) {
            navigation.stop(ChestNavigationManager.StopReason.USER);
            navFeedbackNoteId = null;
            return;
        }
        if (MarketWatchNavigation.startNavigation(navigation, contextKey, note, System.currentTimeMillis())) {
            navFeedbackNoteId = note.id();
            navFeedbackExpiry = System.currentTimeMillis() + FEEDBACK_MS;
        }
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
        if (layout == null) return false;
        if (editing) {
            // Only the smallest Companion sizes need it: the form is taller than the card there.
            if (!layout.listRect().contains(mouseX, mouseY)) return false;
            editScroll = Math.max(0, editScroll - (int) (scrollY * 14));
            return true;
        }
        if (layout.listRect().contains(mouseX, mouseY)) {
            listScroll = Math.max(0, listScroll - (int) (scrollY * 14));
            return true;
        }
        return false;
    }

    private static int coordIndex(EditField field) {
        return field == EditField.X ? 0 : field == EditField.Y ? 1 : field == EditField.Z ? 2 : -1;
    }

    /** Tab order through the form's text fields: item, note, X, Y, Z, then back to item. */
    static EditField nextField(EditField current) {
        EditField[] order = EditField.values();
        return current == null ? EditField.ITEM : order[(current.ordinal() + 1) % order.length];
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
            } else {
                int i = coordIndex(focusedField);
                if (i >= 0 && editCoords[i].length() < MAX_COORD_LENGTH) {
                    editCoords[i] += s;
                    editLocationError = null;
                }
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
                } else {
                    int i = coordIndex(focusedField);
                    if (i >= 0 && !editCoords[i].isEmpty()) {
                        editCoords[i] = editCoords[i].substring(0, editCoords[i].length() - 1);
                        editLocationError = null;
                    }
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                focusedField = nextField(focusedField);
                revealFocusedField = true;
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
