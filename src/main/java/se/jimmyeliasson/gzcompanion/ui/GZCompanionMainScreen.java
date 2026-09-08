package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.ui.tabs.HomeTabComponent;
import se.jimmyeliasson.gzcompanion.ui.tabs.PlaceholderTabComponent;

/**
 * Main GZ Companion Screen interface.
 * Implements the full design system and tab navigation from docs/design/GZ-COMPANION-UI-REFERENCE.png.
 */
public class GZCompanionMainScreen extends Screen {
    private TabType activeTab = TabType.HEM;
    private final HomeTabComponent homeTab = new HomeTabComponent();
    private final PlaceholderTabComponent placeholderTab = new PlaceholderTabComponent();

    public GZCompanionMainScreen() {
        super(Component.literal("GZ Companion"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        Font font = this.font;

        // 1. Full Screen Backdrop Tint
        extractor.fill(0, 0, this.width, this.height, GZTheme.COLOR_BACKDROP);

        // 2. Centered Modal Dimensions
        int modalW = Math.min(this.width - 24, 640);
        int modalH = Math.min(this.height - 24, 400);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        // Draw main modal container
        GZTheme.drawCard(extractor, modalX, modalY, modalW, modalH, GZTheme.COLOR_PANEL_BG, GZTheme.COLOR_BORDER_MODAL);

        // Header
        int headerH = 36;
        int headerY = modalY + 6;
        
        extractor.text(font, "\uD83C\uDF43", modalX + 14, headerY + 4, GZTheme.COLOR_MINT, true);
        extractor.text(font, CompanionConstants.MOD_NAME.toUpperCase(), modalX + 30, headerY + 4, GZTheme.COLOR_MINT, true);
        extractor.text(font, "Unofficial community project \u2022 Alpha", modalX + 30, headerY + 16, GZTheme.COLOR_TEXT_SECONDARY, false);

        int closeBtnX = modalX + modalW - 24;
        int closeBtnY = headerY + 4;
        boolean closeHovered = mouseX >= closeBtnX && mouseX < closeBtnX + 16 && mouseY >= closeBtnY && mouseY < closeBtnY + 16;
        extractor.fill(closeBtnX, closeBtnY, closeBtnX + 16, closeBtnY + 16, closeHovered ? 0x66EF4444 : 0x221E293B);
        extractor.text(font, "\u2715", closeBtnX + 4, closeBtnY + 4, closeHovered ? 0xFFFFFFFF : GZTheme.COLOR_TEXT_SECONDARY, false);

        extractor.fill(modalX + 10, modalY + headerH, modalX + modalW - 10, modalY + headerH + 1, GZTheme.COLOR_BORDER_SUBTLE);

        // Sidebar Navigation
        int sidebarW = 120;
        int sidebarX = modalX + 10;
        int contentX = sidebarX + sidebarW + 8;
        int bodyY = modalY + headerH + 8;
        int bodyH = modalH - headerH - 32;

        GZTheme.drawCard(extractor, sidebarX, bodyY, sidebarW, bodyH, 0x4D0F172A, GZTheme.COLOR_BORDER_SUBTLE);

        TabType[] tabs = TabType.values();
        int tabBtnH = 22;
        int tabStartY = bodyY + 6;

        for (int i = 0; i < tabs.length; i++) {
            TabType tab = tabs[i];
            int ty = tabStartY + (i * (tabBtnH + 3));
            boolean isActive = (tab == activeTab);
            boolean isHov = mouseX >= sidebarX + 4 && mouseX < sidebarX + sidebarW - 4 && mouseY >= ty && mouseY < ty + tabBtnH;

            int tabBg = isActive ? GZTheme.COLOR_NAV_ACTIVE : (isHov ? GZTheme.COLOR_NAV_HOVER : 0x00000000);
            int tabBorder = isActive ? GZTheme.COLOR_BORDER_EMERALD : (isHov ? 0x33475569 : 0x00000000);

            if (tabBg != 0) {
                GZTheme.drawCard(extractor, sidebarX + 4, ty, sidebarW - 8, tabBtnH, tabBg, tabBorder);
            }

            int iconColor = isActive ? GZTheme.COLOR_MINT : (isHov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_SECONDARY);
            int textColor = isActive ? GZTheme.COLOR_TEXT_PRIMARY : (isHov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_SECONDARY);

            extractor.text(font, tab.getIconSymbol(), sidebarX + 10, ty + 6, iconColor, false);
            extractor.text(font, tab.getDisplayName(), sidebarX + 26, ty + 6, textColor, isActive);
        }

        int sFootY = bodyY + bodyH - 24;
        extractor.text(font, "GZ", sidebarX + 8, sFootY + 2, GZTheme.COLOR_TEXT_MUTED, true);
        extractor.text(font, "By the community,", sidebarX + 26, sFootY, GZTheme.COLOR_TEXT_MUTED, false);
        extractor.text(font, "for the players.", sidebarX + 26, sFootY + 10, GZTheme.COLOR_TEXT_MUTED, false);

        // Content Area
        int contentW = modalW - sidebarW - 28;
        if (activeTab == TabType.HEM) {
            homeTab.render(extractor, font, contentX, bodyY, contentW, bodyH, mouseX, mouseY, this);
        } else {
            placeholderTab.render(extractor, font, contentX, bodyY, contentW, bodyH, mouseX, mouseY, activeTab, this);
        }

        // Global Modal Footer
        int footerY = modalY + modalH - 18;
        extractor.text(font, "\uD83D\uDEE1 Client-side \u2022 Fair play \u2022 Inga cheat-funktioner", modalX + 12, footerY, GZTheme.COLOR_TEXT_MUTED, false);
        String rightFooter = "GZ Companion " + CompanionConstants.MOD_VERSION + "  \u2764 By the community";
        int rFootW = font.width(rightFooter);
        extractor.text(font, rightFooter, modalX + modalW - rFootW - 12, footerY, GZTheme.COLOR_TEXT_MUTED, false);

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

        int modalW = Math.min(this.width - 24, 640);
        int modalH = Math.min(this.height - 24, 400);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;
        int headerH = 36;
        int sidebarW = 120;
        int sidebarX = modalX + 10;
        int contentX = sidebarX + sidebarW + 8;
        int bodyY = modalY + headerH + 8;
        int bodyH = modalH - headerH - 32;
        int contentW = modalW - sidebarW - 28;

        int closeBtnX = modalX + modalW - 24;
        int closeBtnY = modalY + 10;
        if (mouseX >= closeBtnX && mouseX < closeBtnX + 16 && mouseY >= closeBtnY && mouseY < closeBtnY + 16) {
            this.onClose();
            return true;
        }

        TabType[] tabs = TabType.values();
        int tabBtnH = 22;
        int tabStartY = bodyY + 6;

        for (int i = 0; i < tabs.length; i++) {
            int ty = tabStartY + (i * (tabBtnH + 3));
            if (mouseX >= sidebarX + 4 && mouseX < sidebarX + sidebarW - 4 && mouseY >= ty && mouseY < ty + tabBtnH) {
                this.activeTab = tabs[i];
                return true;
            }
        }

        if (activeTab == TabType.HEM) {
            if (homeTab.mouseClicked(mouseX, mouseY, button, contentX, bodyY, contentW, bodyH, this)) {
                return true;
            }
        } else {
            if (placeholderTab.mouseClicked(mouseX, mouseY, button, contentX, bodyY, contentW, bodyH, this)) {
                return true;
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