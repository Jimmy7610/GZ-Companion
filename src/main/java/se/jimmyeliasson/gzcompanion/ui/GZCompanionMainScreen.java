package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.ui.layout.ResponsiveBreakpoint;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;
import se.jimmyeliasson.gzcompanion.ui.tabs.HomeTabComponent;
import se.jimmyeliasson.gzcompanion.ui.tabs.PlaceholderTabComponent;

/**
 * Main GZ Companion Screen interface.
 * Implements a responsive, clean floating dashboard adapting to Minecraft GUI scales.
 */
public class GZCompanionMainScreen extends Screen {
    private TabType activeTab = TabType.HEM;
    private final HomeTabComponent homeTab = new HomeTabComponent();
    private final PlaceholderTabComponent placeholderTab = new PlaceholderTabComponent();

    // Cached Layout Rectangles
    private UiRect modalRect = new UiRect(0, 0, 0, 0);
    private UiRect closeBtnRect = new UiRect(0, 0, 0, 0);
    private UiRect sidebarRect = new UiRect(0, 0, 0, 0);
    private UiRect contentRect = new UiRect(0, 0, 0, 0);
    private UiRect[] tabRects = new UiRect[TabType.values().length];

    public GZCompanionMainScreen() {
        super(Component.literal("GZ Companion"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Precalculates layout geometry based on current viewport dimensions.
     */
    private void calculateLayout(int screenWidth, int screenHeight) {
        ResponsiveBreakpoint bp = ResponsiveBreakpoint.fromScreen(screenWidth, screenHeight);

        int modalW;
        int modalH;
        if (bp == ResponsiveBreakpoint.COMPACT) {
            modalW = Math.max(280, screenWidth - 16);
            modalH = Math.max(200, screenHeight - 16);
        } else if (bp == ResponsiveBreakpoint.LARGE) {
            modalW = Math.min(screenWidth - 48, 560);
            modalH = Math.min(screenHeight - 48, 340);
        } else {
            modalW = Math.min(screenWidth - 32, 500);
            modalH = Math.min(screenHeight - 32, 310);
        }

        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;
        this.modalRect = new UiRect(modalX, modalY, modalW, modalH);

        int headerH = 28;
        int footerH = 16;
        int sidebarW = (bp == ResponsiveBreakpoint.COMPACT) ? 88 : 104;

        this.closeBtnRect = new UiRect(modalX + modalW - 18, modalY + 6, 12, 12);
        this.sidebarRect = new UiRect(modalX + 6, modalY + headerH + 4, sidebarW, modalH - headerH - footerH - 8);
        this.contentRect = new UiRect(sidebarRect.right() + 6, modalY + headerH + 4, modalW - sidebarW - 18, sidebarRect.height());

        TabType[] tabs = TabType.values();
        int tabCount = tabs.length;
        int availableH = sidebarRect.height() - 8;
        int tabH = Math.max(15, Math.min(18, availableH / tabCount));
        int tabSpacing = Math.max(1, (availableH - (tabH * tabCount)) / (tabCount - 1));

        for (int i = 0; i < tabCount; i++) {
            int ty = sidebarRect.y() + 4 + (i * (tabH + tabSpacing));
            tabRects[i] = new UiRect(sidebarRect.x() + 2, ty, sidebarRect.width() - 4, tabH);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        Font font = this.font;
        calculateLayout(this.width, this.height);

        // 1. Full Screen Backdrop Tint
        extractor.fill(0, 0, this.width, this.height, GZTheme.COLOR_BACKDROP);

        // 2. Main Modal Canvas
        GZTheme.drawCard(extractor, modalRect, GZTheme.COLOR_PANEL_BG, GZTheme.COLOR_BORDER_MODAL);

        // Header Section
        int headerY = modalRect.y() + 4;
        GZTheme.drawIcon(extractor, IconId.LOGO, modalRect.x() + 8, headerY + 2, 12, GZTheme.COLOR_EMERALD);
        extractor.text(font, CompanionConstants.MOD_NAME.toUpperCase(), modalRect.x() + 24, headerY + 3, GZTheme.COLOR_MINT, true);
        extractor.text(font, "- Alpha", modalRect.x() + 28 + font.width(CompanionConstants.MOD_NAME.toUpperCase()), headerY + 3, GZTheme.COLOR_TEXT_MUTED, false);

        // Close Button
        boolean closeHovered = closeBtnRect.contains(mouseX, mouseY);
        extractor.fill(closeBtnRect.x(), closeBtnRect.y(), closeBtnRect.right(), closeBtnRect.bottom(), closeHovered ? 0x99EF4444 : 0x221E293B);
        TextUtil.drawCenteredText(extractor, font, "x", closeBtnRect.x() + (closeBtnRect.width() / 2), closeBtnRect.y() + 2, closeBtnRect.width(),
                closeHovered ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED, false);

        // Header Divider
        extractor.fill(modalRect.x() + 6, modalRect.y() + 27, modalRect.right() - 6, modalRect.y() + 28, GZTheme.COLOR_BORDER_SUBTLE);

        // 3. Sidebar Rail
        GZTheme.drawCard(extractor, sidebarRect, 0x800A1017, GZTheme.COLOR_BORDER_SUBTLE);

        TabType[] tabs = TabType.values();
        for (int i = 0; i < tabs.length; i++) {
            TabType tab = tabs[i];
            UiRect tr = tabRects[i];
            boolean isActive = (tab == activeTab);
            boolean isHov = tr.contains(mouseX, mouseY);

            int tabBg = isActive ? GZTheme.COLOR_NAV_ACTIVE : (isHov ? GZTheme.COLOR_NAV_HOVER : 0);
            int tabBorder = isActive ? GZTheme.COLOR_BORDER_EMERALD : (isHov ? 0x33475569 : 0);

            if (tabBg != 0) {
                GZTheme.drawCard(extractor, tr, tabBg, tabBorder);
            }

            int iconTint = isActive ? GZTheme.COLOR_MINT : (isHov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED);
            int textTint = isActive ? GZTheme.COLOR_TEXT_PRIMARY : (isHov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_SECONDARY);

            int iconY = tr.y() + ((tr.height() - 10) / 2);
            GZTheme.drawIcon(extractor, tab.getIcon(), tr.x() + 4, iconY, 10, iconTint);

            int textY = tr.y() + ((tr.height() - 8) / 2);
            int maxLabelW = tr.width() - 20;
            TextUtil.drawEllipsizedText(extractor, font, tab.getDisplayName(), tr.x() + 18, textY, maxLabelW, textTint, isActive);
        }

        // 4. Content Canvas (Home or Placeholder)
        extractor.enableScissor(contentRect.x(), contentRect.y(), contentRect.right(), contentRect.bottom());
        if (activeTab == TabType.HEM) {
            homeTab.render(extractor, font, contentRect, mouseX, mouseY, this);
        } else {
            placeholderTab.render(extractor, font, contentRect, mouseX, mouseY, activeTab, this);
        }
        extractor.disableScissor();

        // 5. Global Modal Footer
        int footerY = modalRect.bottom() - 13;
        TextUtil.drawEllipsizedText(extractor, font, "Client-side - Fair play", modalRect.x() + 8, footerY, modalRect.width() / 2, GZTheme.COLOR_TEXT_MUTED, false);
        String rightFooter = "GZ Companion " + CompanionConstants.getModVersion();
        TextUtil.drawRightAlignedText(extractor, font, rightFooter, modalRect.right() - 8, footerY, modalRect.width() / 2, GZTheme.COLOR_TEXT_MUTED, false);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDown) {
        if (!isDown) {
            return super.mouseClicked(event, false);
        }

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (closeBtnRect.contains(mouseX, mouseY)) {
            this.onClose();
            return true;
        }

        TabType[] tabs = TabType.values();
        for (int i = 0; i < tabs.length; i++) {
            if (tabRects[i] != null && tabRects[i].contains(mouseX, mouseY)) {
                this.activeTab = tabs[i];
                return true;
            }
        }

        if (contentRect.contains(mouseX, mouseY)) {
            if (activeTab == TabType.HEM) {
                if (homeTab.mouseClicked(mouseX, mouseY, button, contentRect, this)) {
                    return true;
                }
            } else {
                if (placeholderTab.mouseClicked(mouseX, mouseY, button, contentRect, this)) {
                    return true;
                }
            }
        }

        return super.mouseClicked(event, isDown);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_G) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    public void setActiveTab(TabType tab) {
        if (tab != null) {
            this.activeTab = tab;
        }
    }
}