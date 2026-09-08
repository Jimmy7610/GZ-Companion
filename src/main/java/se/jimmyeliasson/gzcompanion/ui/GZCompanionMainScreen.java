package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.ui.layout.MainScreenLayout;
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

    private MainScreenLayout layout;

    public GZCompanionMainScreen() {
        super(Component.literal("GZ Companion"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public MainScreenLayout getLayout() {
        return layout;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        Font font = this.font;
        this.layout = MainScreenLayout.calculate(this.width, this.height);

        UiRect modalRect = layout.modalRect();
        UiRect closeBtnRect = layout.closeBtnRect();
        UiRect sidebarRect = layout.sidebarRect();
        UiRect contentRect = layout.contentRect();
        UiRect[] tabRects = layout.tabRects();

        // 1. Full Screen Backdrop Tint
        extractor.fill(0, 0, this.width, this.height, GZTheme.COLOR_BACKDROP);

        // 2. Main Modal Canvas
        GZTheme.drawCard(extractor, modalRect, GZTheme.COLOR_PANEL_BG, GZTheme.COLOR_BORDER_MODAL);

        // Header Section
        int headerY = modalRect.y() + 4;
        GZTheme.drawIcon(extractor, IconId.LOGO, modalRect.x() + 8, headerY + 2, 12, GZTheme.COLOR_EMERALD);
        extractor.text(font, CompanionConstants.MOD_NAME.toUpperCase(), modalRect.x() + 24, headerY + 3, GZTheme.COLOR_MINT, true);

        String subtitle = "- Inofficiellt Alpha";
        int titleW = font.width(CompanionConstants.MOD_NAME.toUpperCase());
        int availHeaderW = closeBtnRect.x() - (modalRect.x() + 28 + titleW) - 8;
        if (availHeaderW > 40) {
            TextUtil.drawScaledEllipsizedText(extractor, font, subtitle, modalRect.x() + 28 + titleW, headerY + 4, availHeaderW, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        }

        // Close Button
        boolean closeHovered = closeBtnRect.contains(mouseX, mouseY);
        extractor.fill(closeBtnRect.x(), closeBtnRect.y(), closeBtnRect.right(), closeBtnRect.bottom(), closeHovered ? 0x99EF4444 : 0x221E293B);
        TextUtil.drawCenteredText(extractor, font, "x", closeBtnRect.x() + (closeBtnRect.width() / 2), closeBtnRect.y() + 1, closeBtnRect.width(),
                closeHovered ? GZTheme.COLOR_TEXT_PRIMARY : GZTheme.COLOR_TEXT_MUTED, false);

        // Header Divider
        extractor.fill(modalRect.x() + 5, modalRect.y() + 25, modalRect.right() - 5, modalRect.y() + 26, GZTheme.COLOR_BORDER_SUBTLE);

        // 3. Sidebar Rail
        GZTheme.drawCard(extractor, sidebarRect, 0x800A1017, GZTheme.COLOR_BORDER_SUBTLE);

        TabType[] tabs = TabType.values();
        float tabScale = TypographyScale.BODY.getScale();
        int tabTextH = (int) Math.ceil(8 * tabScale);
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
            GZTheme.drawIcon(extractor, tab.getIcon(), tr.x() + 3, iconY, 10, iconTint);

            int textY = tr.y() + ((tr.height() - tabTextH) / 2);
            int maxLabelW = tr.width() - 17;
            TextUtil.drawScaledEllipsizedText(extractor, font, tab.getDisplayName(), tr.x() + 15, textY, maxLabelW, tabScale, textTint, isActive);
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
        int footerY = modalRect.bottom() - 12;
        TextUtil.drawScaledEllipsizedText(extractor, font, "Fair play - Lokalt", modalRect.x() + 6, footerY, modalRect.width() / 2, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);
        String rightFooter = "v" + CompanionConstants.getModVersion();
        TextUtil.drawScaledRightAlignedText(extractor, font, rightFooter, modalRect.right() - 6, footerY, modalRect.width() / 2, TypographyScale.META.getScale(), GZTheme.COLOR_TEXT_MUTED, false);

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

        if (layout == null) {
            this.layout = MainScreenLayout.calculate(this.width, this.height);
        }

        if (layout.closeBtnRect().contains(mouseX, mouseY)) {
            this.onClose();
            return true;
        }

        TabType[] tabs = TabType.values();
        UiRect[] tabRects = layout.tabRects();
        for (int i = 0; i < tabs.length; i++) {
            if (tabRects[i] != null && tabRects[i].contains(mouseX, mouseY)) {
                this.activeTab = tabs[i];
                return true;
            }
        }

        UiRect contentRect = layout.contentRect();
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