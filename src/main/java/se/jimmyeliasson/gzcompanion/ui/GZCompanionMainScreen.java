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
 * Implements the compact, centered card dashboard from docs/design/GZ-COMPANION-UI-REFERENCE.png.
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

        // 2. Compact Centered Modal Sizing
        // At 1600x900 / scale 2-3, maintains deliberate padding and crisp proportions
        int modalW = Math.min(this.width - 32, 540);
        int modalH = Math.min(this.height - 32, 330);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        // Draw main modal container
        GZTheme.drawCard(extractor, modalX, modalY, modalW, modalH, GZTheme.COLOR_PANEL_BG, GZTheme.COLOR_BORDER_MODAL);

        // Header Section (Height 32px)
        int headerH = 30;
        int headerY = modalY + 5;

        // Brand Icon & Title
        extractor.text(font, "*", modalX + 12, headerY + 4, GZTheme.COLOR_EMERALD, true);
        extractor.text(font, CompanionConstants.MOD_NAME.toUpperCase(), modalX + 22, headerY + 4, GZTheme.COLOR_MINT, true);
        extractor.text(font, "Unofficial community project - Alpha", modalX + 22, headerY + 14, GZTheme.COLOR_TEXT_SECONDARY, false);

        // Close Button
        int closeBtnX = modalX + modalW - 20;
        int closeBtnY = headerY + 2;
        boolean closeHovered = mouseX >= closeBtnX && mouseX < closeBtnX + 14 && mouseY >= closeBtnY && mouseY < closeBtnY + 14;
        extractor.fill(closeBtnX, closeBtnY, closeBtnX + 14, closeBtnY + 14, closeHovered ? 0x99EF4444 : 0x331E293B);
        extractor.text(font, "x", closeBtnX + 4, closeBtnY + 2, closeHovered ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_SECONDARY, false);

        // Header Divider Line
        extractor.fill(modalX + 8, modalY + headerH, modalX + modalW - 8, modalY + headerH + 1, GZTheme.COLOR_BORDER_SUBTLE);

        // Sidebar Navigation (Width 108px)
        int sidebarW = 108;
        int sidebarX = modalX + 8;
        int bodyY = modalY + headerH + 6;
        int footerH = 20;
        int bodyH = modalH - headerH - footerH - 10;
        int contentX = sidebarX + sidebarW + 6;
        int contentW = modalW - sidebarW - 20;

        // Sidebar Container
        GZTheme.drawCard(extractor, sidebarX, bodyY, sidebarW, bodyH, 0x800A1017, GZTheme.COLOR_BORDER_SUBTLE);

        TabType[] tabs = TabType.values();
        int tabBtnH = 18;
        int tabStartY = bodyY + 4;

        for (int i = 0; i < tabs.length; i++) {
            TabType tab = tabs[i];
            int ty = tabStartY + (i * (tabBtnH + 2));
            boolean isActive = (tab == activeTab);
            boolean isHov = mouseX >= sidebarX + 3 && mouseX < sidebarX + sidebarW - 3 && mouseY >= ty && mouseY < ty + tabBtnH;

            int tabBg = isActive ? GZTheme.COLOR_NAV_ACTIVE : (isHov ? GZTheme.COLOR_NAV_HOVER : 0x00000000);
            int tabBorder = isActive ? GZTheme.COLOR_BORDER_EMERALD : (isHov ? 0x40475569 : 0x00000000);

            if (tabBg != 0) {
                GZTheme.drawCard(extractor, sidebarX + 3, ty, sidebarW - 6, tabBtnH, tabBg, tabBorder);
            }

            int iconColor = isActive ? GZTheme.COLOR_MINT : (isHov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED);
            int textColor = isActive ? GZTheme.COLOR_TEXT_PRIMARY : (isHov ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_SECONDARY);

            extractor.text(font, tab.getIconSymbol(), sidebarX + 7, ty + 5, iconColor, false);
            extractor.text(font, tab.getDisplayName(), sidebarX + 18, ty + 5, textColor, isActive);
        }

        // Sidebar Community Tagline
        int sFootY = bodyY + bodyH - 18;
        extractor.text(font, "By the community,", sidebarX + 8, sFootY, GZTheme.COLOR_TEXT_MUTED, false);
        extractor.text(font, "for the players.", sidebarX + 8, sFootY + 9, GZTheme.COLOR_TEXT_MUTED, false);

        // Content Area Rendering
        if (activeTab == TabType.HEM) {
            homeTab.render(extractor, font, contentX, bodyY, contentW, bodyH, mouseX, mouseY, this);
        } else {
            placeholderTab.render(extractor, font, contentX, bodyY, contentW, bodyH, mouseX, mouseY, activeTab, this);
        }

        // Global Modal Footer
        int footerY = modalY + modalH - 16;
        extractor.text(font, "[!] Client-side - Fair play - Inga cheat-funktioner", modalX + 10, footerY, GZTheme.COLOR_TEXT_MUTED, false);
        String rightFooter = "GZ Companion " + CompanionConstants.getModVersion();
        int rFootW = font.width(rightFooter);
        extractor.text(font, rightFooter, modalX + modalW - rFootW - 10, footerY, GZTheme.COLOR_TEXT_MUTED, false);

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

        int modalW = Math.min(this.width - 32, 540);
        int modalH = Math.min(this.height - 32, 330);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;
        int headerH = 30;
        int sidebarW = 108;
        int sidebarX = modalX + 8;
        int contentX = sidebarX + sidebarW + 6;
        int bodyY = modalY + headerH + 6;
        int footerH = 20;
        int bodyH = modalH - headerH - footerH - 10;
        int contentW = modalW - sidebarW - 20;

        // Close button click
        int closeBtnX = modalX + modalW - 20;
        int closeBtnY = modalY + 7;
        if (mouseX >= closeBtnX && mouseX < closeBtnX + 14 && mouseY >= closeBtnY && mouseY < closeBtnY + 14) {
            this.onClose();
            return true;
        }

        // Sidebar tab clicks
        TabType[] tabs = TabType.values();
        int tabBtnH = 18;
        int tabStartY = bodyY + 4;

        for (int i = 0; i < tabs.length; i++) {
            int ty = tabStartY + (i * (tabBtnH + 2));
            if (mouseX >= sidebarX + 3 && mouseX < sidebarX + sidebarW - 3 && mouseY >= ty && mouseY < ty + tabBtnH) {
                this.activeTab = tabs[i];
                return true;
            }
        }

        // Subtab component clicks
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