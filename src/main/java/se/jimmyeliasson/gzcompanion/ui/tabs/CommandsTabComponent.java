package se.jimmyeliasson.gzcompanion.ui.tabs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.knowledge.commands.CommandCatalog;
import se.jimmyeliasson.gzcompanion.knowledge.commands.CommandCategory;
import se.jimmyeliasson.gzcompanion.knowledge.commands.CommandDefinition;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeModuleStatus;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.GZTheme;
import se.jimmyeliasson.gzcompanion.ui.IconId;
import se.jimmyeliasson.gzcompanion.ui.TextInputHandler;
import se.jimmyeliasson.gzcompanion.ui.TypographyScale;
import se.jimmyeliasson.gzcompanion.ui.layout.CommandsLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Kommandon tab: a searchable, category-filterable list of GameZone commands sourced
 * from the bundled Rule Pack, each with a verification badge and a "Kopiera kommando" action.
 * Never sends, runs, or auto-opens chat for any command — copy-to-clipboard only.
 */
public class CommandsTabComponent implements TextInputHandler {
    private static final int MAX_SEARCH_LENGTH = 48;

    private String searchText = "";
    private boolean searchFocused = false;
    private int categoryCycleIndex = 0;
    private String selectedCommandId = null;
    private int listScrollOffset = 0;
    private int detailScrollOffset = 0;
    private boolean compactShowingDetail = false;
    private long copyFeedbackExpiry = 0;

    private CommandsLayout layout;
    private final List<ListRowHit> listHitTargets = new ArrayList<>();

    public record ListRowHit(UiRect rect, String commandId) {}

    public CommandsLayout getLayout() {
        return layout;
    }

    @Override
    public boolean isTextInputFocused() {
        return searchFocused;
    }

    public void render(GuiGraphicsExtractor extractor, Font font, UiRect bounds, int mouseX, int mouseY, GZCompanionMainScreen mainScreen) {
        this.layout = CommandsLayout.calculate(bounds);
        listHitTargets.clear();

        CompanionSession session = CompanionSession.getInstance();
        CommandCatalog catalog = session.getCommandCatalog();
        KnowledgeModuleStatus status = session.getCommandCatalogStatus();

        if (catalog == null || !status.isAvailable()) {
            drawUnavailableState(extractor, font, bounds, status);
            return;
        }

        List<CommandCategory> categories = catalog.categoriesInUse();
        if (categoryCycleIndex > categories.size()) {
            categoryCycleIndex = 0;
        }
        String categoryFilter = categoryCycleIndex == 0 ? null : categories.get(categoryCycleIndex - 1).id();

        List<CommandDefinition> filtered = catalog.search(searchText, categoryFilter);
        boolean isFiltering = !searchText.isBlank() || categoryFilter != null;

        renderHeader(extractor, font, layout.headerRect(), catalog, filtered.size(), isFiltering);
        renderSearch(extractor, font, layout.searchRect(), layout.clearBtnRect(), mouseX, mouseY);
        renderCategoryButton(extractor, font, layout.categoryBtnRect(), categoryCycleIndex == 0 ? "Alla kategorier" : categories.get(categoryCycleIndex - 1).displayName(), mouseX, mouseY);

        if (catalog.size() == 0) {
            renderEmptyState(extractor, font, contentArea(bounds), "Inga verifierade kommandon är dokumenterade i detta Rule Pack.");
            return;
        }

        if (selectedCommandId == null || filtered.stream().noneMatch(c -> c.id().equals(selectedCommandId))) {
            selectedCommandId = filtered.isEmpty() ? null : filtered.get(0).id();
            if (selectedCommandId == null) {
                compactShowingDetail = false;
            }
        }

        if (filtered.isEmpty()) {
            renderEmptyState(extractor, font, contentArea(bounds), "Inga kommandon matchar sökningen.");
            return;
        }

        if (layout.isCompact()) {
            if (compactShowingDetail && selectedCommandId != null) {
                renderDetailPane(extractor, font, layout.detailRect(), catalog, mouseX, mouseY, true);
            } else {
                renderList(extractor, font, layout.listRect(), catalog, filtered, mouseX, mouseY);
            }
        } else {
            renderList(extractor, font, layout.listRect(), catalog, filtered, mouseX, mouseY);
            renderDetailPane(extractor, font, layout.detailRect(), catalog, mouseX, mouseY, false);
        }
    }

    private UiRect contentArea(UiRect bounds) {
        return new UiRect(bounds.x(), layout.listRect().y(), bounds.width(),
                Math.max(10, layout.copyBtnRect().y() - 4 - layout.listRect().y()));
    }

    private void renderHeader(GuiGraphicsExtractor extractor, Font font, UiRect headerRect, CommandCatalog catalog, int filteredCount, boolean isFiltering) {
        GZTheme.drawIcon(extractor, IconId.COMMANDS, headerRect.x(), headerRect.y() + 1, 10, GZTheme.COLOR_MINT);
        TextUtil.drawScaledText(extractor, font, "Kommandon", headerRect.x() + 13, headerRect.y() + 1,
                TypographyScale.HEADING.getScale(), GZTheme.COLOR_TEXT_PRIMARY, true);

        int verifiedCount = catalog.countByStatus(VerificationStatus.VERIFIED);
        String countLabel = isFiltering ? (filteredCount + " av " + catalog.size()) : (verifiedCount + " verifierade");
        int badgeW = TextUtil.scaledWidth(font, countLabel, TypographyScale.META.getScale()) + 14;
        GZTheme.drawBadge(extractor, font, headerRect.right() - badgeW, headerRect.y(), countLabel, GZTheme.COLOR_TEXT_SECONDARY, GZTheme.COLOR_STATUS_GREEN);
    }

    private void renderSearch(GuiGraphicsExtractor extractor, Font font, UiRect searchRect, UiRect clearBtnRect, int mouseX, int mouseY) {
        int bg = searchFocused ? GZTheme.COLOR_CARD_HOVER : GZTheme.COLOR_CARD_INNER;
        int border = searchFocused ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, searchRect, bg, border);

        int textX = searchRect.x() + 4;
        int textY = searchRect.y() + 3;
        int maxW = searchRect.width() - 8;

        if (searchText.isEmpty() && !searchFocused) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Sök kommando, alias eller nyckelord...", textX, textY,
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

    private void renderCategoryButton(GuiGraphicsExtractor extractor, Font font, UiRect rect, String label, int mouseX, int mouseY) {
        boolean hov = rect.contains(mouseX, mouseY);
        boolean active = categoryCycleIndex != 0;
        GZTheme.drawCard(extractor, rect, active ? GZTheme.COLOR_NAV_ACTIVE : (hov ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER),
                active ? GZTheme.COLOR_BORDER_EMERALD : GZTheme.COLOR_BORDER_SUBTLE);
        TextUtil.drawScaledEllipsizedText(extractor, font, "Kategori: " + label, rect.x() + 3, rect.y() + 2,
                rect.width() - 6, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    private void renderEmptyState(GuiGraphicsExtractor extractor, Font font, UiRect area, String message) {
        GZTheme.drawCard(extractor, area, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        int centerX = area.x() + (area.width() / 2);
        int maxW = area.width() - 20;
        int y = area.y() + Math.max(6, (area.height() / 2) - 12);
        GZTheme.drawIcon(extractor, IconId.COMMANDS, centerX - 8, y, 16, GZTheme.COLOR_MINT);
        y += 20;
        TextUtil.drawScaledWrappedText(extractor, font, message, area.x() + 10, y, maxW,
                TypographyScale.SMALL.getScale(), 3, 1, GZTheme.COLOR_TEXT_SECONDARY, false);
    }

    public static int calculateMaxListScroll(UiRect listRect, int rowCount) {
        int rowH = 24;
        int totalH = 15 + (rowCount * (rowH + 1)) + 4;
        int visibleH = Math.max(1, listRect.height() - 4);
        return Math.max(0, totalH - visibleH);
    }

    private void renderList(GuiGraphicsExtractor extractor, Font font, UiRect listRect, CommandCatalog catalog, List<CommandDefinition> commands, int mouseX, int mouseY) {
        GZTheme.drawCard(extractor, listRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        int rowH = 24;
        int maxScroll = calculateMaxListScroll(listRect, commands.size());
        this.listScrollOffset = Math.max(0, Math.min(listScrollOffset, maxScroll));

        extractor.enableScissor(listRect.x() + 1, listRect.y() + 1, listRect.right() - 1, listRect.bottom() - 1);

        int currentY = listRect.y() + 3 - listScrollOffset;
        TextUtil.drawScaledText(extractor, font, "KOMMANDON", listRect.x() + 4, currentY,
                TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        currentY += 12;

        for (CommandDefinition command : commands) {
            UiRect rowRect = new UiRect(listRect.x() + 2, currentY, listRect.width() - 4, rowH);
            if (rowRect.bottom() >= listRect.y() + 2 && rowRect.y() <= listRect.bottom() - 2) {
                listHitTargets.add(new ListRowHit(rowRect, command.id()));
            }

            if (currentY + rowH >= listRect.y() && currentY <= listRect.bottom()) {
                boolean isSelected = command.id().equals(selectedCommandId);
                boolean isHovered = rowRect.contains(mouseX, mouseY);

                int bg = isSelected ? GZTheme.COLOR_NAV_ACTIVE : (isHovered ? GZTheme.COLOR_NAV_HOVER : 0);
                int border = isSelected ? GZTheme.COLOR_BORDER_EMERALD : 0;
                if (bg != 0) GZTheme.drawCard(extractor, rowRect, bg, border);

                GZTheme.drawStatusDot(extractor, rowRect.x() + 4, rowRect.y() + 5, command.verification().status().getArgbColor());

                TextUtil.drawScaledEllipsizedText(extractor, font, command.primaryCommand(), rowRect.x() + 11, rowRect.y() + 2,
                        rowRect.width() - 15, TypographyScale.SMALL.getScale(), GZTheme.COLOR_TEXT_PRIMARY, isSelected);
                TextUtil.drawScaledEllipsizedText(extractor, font, catalog.resolveCategory(command.categoryId()).displayName(),
                        rowRect.x() + 11, rowRect.y() + 12, rowRect.width() - 15, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            }

            currentY += rowH + 1;
        }

        extractor.disableScissor();
    }

    private int calculateMaxDetailScroll(Font font, UiRect contentArea, CommandDefinition command) {
        if (font == null || contentArea == null || command == null) return 0;
        int maxW = Math.max(10, contentArea.width() - 8);
        int totalH = 4;
        totalH += 11; // primaryCommand heading
        totalH += TextUtil.measureWrappedHeight(font, command.syntax(), maxW, TypographyScale.SMALL.getScale(), 1) + 9;
        totalH += TextUtil.measureWrappedHeight(font, command.description(), maxW, TypographyScale.SMALL.getScale(), 4) + 9;
        if (command.requirements() != null && !command.requirements().isBlank()) totalH += 10;
        if (!command.aliases().isEmpty()) totalH += 10;
        for (String example : command.examples()) {
            totalH += TextUtil.measureWrappedHeight(font, example, maxW, TypographyScale.SMALL.getScale(), 1) + 2;
        }
        totalH += 11; // verification badge row
        return Math.max(0, totalH - contentArea.height());
    }

    private void renderDetailPane(GuiGraphicsExtractor extractor, Font font, UiRect detailRect, CommandCatalog catalog, int mouseX, int mouseY, boolean isCompact) {
        GZTheme.drawCard(extractor, detailRect, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);

        CommandDefinition command = selectedCommandId != null
                ? catalog.commands().stream().filter(c -> c.id().equals(selectedCommandId)).findFirst().orElse(null)
                : null;
        if (command == null) {
            TextUtil.drawCenteredText(extractor, font, "Välj ett kommando i listan", detailRect.x() + (detailRect.width() / 2),
                    detailRect.y() + (detailRect.height() / 2) - 4, detailRect.width(), GZTheme.COLOR_TEXT_MUTED, false);
            return;
        }

        int pad = 5;
        int contentTop = detailRect.y() + (isCompact ? 16 : 4);
        UiRect contentArea = new UiRect(detailRect.x() + 1, contentTop, detailRect.width() - 2, detailRect.bottom() - contentTop - 1);

        int maxScroll = calculateMaxDetailScroll(font, contentArea, command);
        this.detailScrollOffset = Math.max(0, Math.min(detailScrollOffset, maxScroll));

        if (isCompact) {
            UiRect backBtn = layout.backBtnRect();
            boolean backHover = backBtn.contains(mouseX, mouseY);
            GZTheme.drawButton(extractor, font, backBtn, "< Lista", false, backHover, TypographyScale.SMALL.getScale());
        }

        extractor.enableScissor(contentArea.x(), contentArea.y(), contentArea.right(), contentArea.bottom());
        int currY = contentArea.y() + 2 - detailScrollOffset;
        int maxW = contentArea.width() - (pad * 2);

        TextUtil.drawScaledEllipsizedText(extractor, font, command.primaryCommand(), contentArea.x() + pad, currY,
                maxW, TypographyScale.HEADING.getScale(), GZTheme.COLOR_MINT, true);
        currY += 11;

        currY += TextUtil.drawScaledWrappedText(extractor, font, command.syntax(), contentArea.x() + pad, currY, maxW,
                TypographyScale.SMALL.getScale(), 1, 1, GZTheme.COLOR_TEXT_SECONDARY, false) + 2;

        currY += TextUtil.drawScaledWrappedText(extractor, font, command.description(), contentArea.x() + pad, currY, maxW,
                TypographyScale.SMALL.getScale(), 4, 1, GZTheme.COLOR_TEXT_PRIMARY, false) + 5;

        if (command.requirements() != null && !command.requirements().isBlank()) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Krav: " + command.requirements(), contentArea.x() + pad, currY,
                    maxW, TypographyScale.META.getScale(), GZTheme.COLOR_STATUS_YELLOW, false);
            currY += 10;
        }

        if (!command.aliases().isEmpty()) {
            TextUtil.drawScaledEllipsizedText(extractor, font, "Alias: " + String.join(", ", command.aliases()), contentArea.x() + pad, currY,
                    maxW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
            currY += 10;
        }

        for (String example : command.examples()) {
            currY += TextUtil.drawScaledWrappedText(extractor, font, example, contentArea.x() + pad, currY, maxW,
                    TypographyScale.SMALL.getScale(), 1, 1, GZTheme.COLOR_TEXT_SECONDARY, false) + 2;
        }

        currY += 3;
        VerificationStatus vs = command.verification().status();
        String badgeLabel = vs.getDisplayName();
        int badgeW = TextUtil.scaledWidth(font, badgeLabel, TypographyScale.META.getScale()) + 14;
        GZTheme.drawBadge(extractor, font, contentArea.x() + pad, currY, badgeLabel, GZTheme.COLOR_TEXT_SECONDARY, vs.getArgbColor());

        extractor.disableScissor();

        renderActionRow(extractor, font, mouseX, mouseY);
    }

    private void renderActionRow(GuiGraphicsExtractor extractor, Font font, int mouseX, int mouseY) {
        UiRect copyBtn = layout.copyBtnRect();
        boolean copyFeedbackActive = System.currentTimeMillis() < copyFeedbackExpiry;
        String copyLabel = copyFeedbackActive ? "Kopierat!" : "Kopiera kommando";
        int copyBg = copyFeedbackActive ? 0x4010B981 : (copyBtn.contains(mouseX, mouseY) ? GZTheme.COLOR_NAV_HOVER : GZTheme.COLOR_CARD_INNER);
        int copyBorder = copyFeedbackActive ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_BORDER_SUBTLE;
        GZTheme.drawCard(extractor, copyBtn, copyBg, copyBorder);
        TextUtil.drawScaledCenteredText(extractor, font, copyLabel, copyBtn.x() + (copyBtn.width() / 2),
                copyBtn.y() + ((copyBtn.height() - 7) / 2), copyBtn.width() - 4,
                TypographyScale.META.getScale(), copyFeedbackActive ? GZTheme.COLOR_STATUS_GREEN : GZTheme.COLOR_TEXT_MUTED, false);
    }

    private void drawUnavailableState(GuiGraphicsExtractor extractor, Font font, UiRect bounds, KnowledgeModuleStatus status) {
        GZTheme.drawCard(extractor, bounds, GZTheme.COLOR_CARD_BG, GZTheme.COLOR_BORDER_SUBTLE);
        String msg = switch (status) {
            case ERROR -> "Fel inträffade vid inläsning av kommandodatan.";
            case UNAVAILABLE -> "Kommandon är inte tillgängligt just nu.";
            case INCOMPATIBLE -> "Kommandodatan är sparad med ett schema som inte stöds av denna version.";
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
        if (layout == null) layout = CommandsLayout.calculate(bounds);

        CompanionSession session = CompanionSession.getInstance();
        CommandCatalog catalog = session.getCommandCatalog();

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

        if (layout.categoryBtnRect().contains(mouseX, mouseY) && catalog != null) {
            int categoryCount = catalog.categoriesInUse().size();
            categoryCycleIndex = (categoryCycleIndex + 1) % (categoryCount + 1);
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
                    selectedCommandId = hit.commandId;
                    compactShowingDetail = true;
                    detailScrollOffset = 0;
                    return true;
                }
            }
        }

        if (selectedCommandId != null && catalog != null && layout.copyBtnRect().contains(mouseX, mouseY)) {
            catalog.commands().stream().filter(c -> c.id().equals(selectedCommandId)).findFirst()
                    .ifPresent(this::copyCommand);
            return true;
        }

        return false;
    }

    private void copyCommand(CommandDefinition command) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.keyboardHandler != null) {
                client.keyboardHandler.setClipboard(command.primaryCommand());
                copyFeedbackExpiry = System.currentTimeMillis() + 2000;
            }
        } catch (Exception ignored) {
            // Clipboard access is a pure local OS convenience - never let a failure here affect anything else.
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout == null) return false;

        if (layout.listRect().contains(mouseX, mouseY)) {
            CompanionSession session = CompanionSession.getInstance();
            CommandCatalog catalog = session.getCommandCatalog();
            List<CommandCategory> categories = catalog != null ? catalog.categoriesInUse() : List.of();
            String categoryFilter = (catalog == null || categoryCycleIndex == 0 || categoryCycleIndex > categories.size())
                    ? null : categories.get(categoryCycleIndex - 1).id();
            int count = catalog != null ? catalog.search(searchText, categoryFilter).size() : 0;
            int maxScroll = calculateMaxListScroll(layout.listRect(), count);
            this.listScrollOffset = Math.max(0, Math.min(listScrollOffset - (int) (scrollY * 14), maxScroll));
            return true;
        }

        if (layout.detailRect().contains(mouseX, mouseY) && selectedCommandId != null) {
            CompanionSession session = CompanionSession.getInstance();
            CommandCatalog catalog = session.getCommandCatalog();
            CommandDefinition command = catalog != null
                    ? catalog.commands().stream().filter(c -> c.id().equals(selectedCommandId)).findFirst().orElse(null)
                    : null;
            if (command != null) {
                Font font = Minecraft.getInstance().font;
                int contentTop = layout.detailRect().y() + (layout.isCompact() ? 16 : 4);
                UiRect contentArea = new UiRect(layout.detailRect().x() + 1, contentTop, layout.detailRect().width() - 2, layout.detailRect().bottom() - contentTop - 1);
                int maxScroll = calculateMaxDetailScroll(font, contentArea, command);
                this.detailScrollOffset = Math.max(0, Math.min(detailScrollOffset - (int) (scrollY * 14), maxScroll));
                return true;
            }
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
