package se.jimmyeliasson.gzcompanion.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.ui.layout.MainScreenLayout;
import se.jimmyeliasson.gzcompanion.ui.layout.TextUtil;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;
import se.jimmyeliasson.gzcompanion.ui.tabs.GuideTabComponent;
import se.jimmyeliasson.gzcompanion.ui.tabs.HomeTabComponent;
import se.jimmyeliasson.gzcompanion.ui.tabs.KistorTabComponent;
import se.jimmyeliasson.gzcompanion.ui.tabs.PlaceholderTabComponent;

/**
 * Main GZ Companion Screen interface.
 * Implements a responsive, clean floating dashboard adapting to Minecraft GUI scales.
 */
public class GZCompanionMainScreen extends Screen {
    private TabType activeTab = TabType.HEM;
    private final HomeTabComponent homeTab = new HomeTabComponent();
    private final se.jimmyeliasson.gzcompanion.ui.tabs.GuideTabComponent guideTab = new se.jimmyeliasson.gzcompanion.ui.tabs.GuideTabComponent();
    private final KistorTabComponent kistorTab = new KistorTabComponent();
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

    public GuideTabComponent getGuideTab() {
        return guideTab;
    }

    public KistorTabComponent getKistorTab() {
        return kistorTab;
    }

    public void openGuideStep(String stepId) {
        this.activeTab = TabType.GUIDE;
        if (stepId != null) {
            this.guideTab.selectStep(stepId);
        }
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

        // 4. Content Canvas (Home, Guide, or Placeholder)
        extractor.enableScissor(contentRect.x(), contentRect.y(), contentRect.right(), contentRect.bottom());
        if (activeTab == TabType.HEM) {
            homeTab.render(extractor, font, contentRect, mouseX, mouseY, this);
        } else if (activeTab == TabType.GUIDE) {
            guideTab.render(extractor, font, contentRect, mouseX, mouseY, this);
        } else if (activeTab == TabType.KISTOR) {
            kistorTab.render(extractor, font, contentRect, mouseX, mouseY, this);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (layout == null) {
            this.layout = MainScreenLayout.calculate(this.width, this.height);
        }

        if (UiInput.isPointInside(layout.closeBtnRect(), mouseX, mouseY)) {
            this.onClose();
            return true;
        }

        TabType clickedTab = UiInput.findClickedTab(layout.tabRects(), mouseX, mouseY);
        if (clickedTab != null) {
            this.activeTab = clickedTab;
            return true;
        }

        UiRect contentRect = layout.contentRect();
        if (UiInput.isPointInside(contentRect, mouseX, mouseY)) {
            if (activeTab == TabType.HEM) {
                if (homeTab.mouseClicked(mouseX, mouseY, button, contentRect, this)) {
                    return true;
                }
            } else if (activeTab == TabType.GUIDE) {
                if (guideTab.mouseClicked(mouseX, mouseY, button, contentRect, this)) {
                    return true;
                }
            } else if (activeTab == TabType.KISTOR) {
                if (kistorTab.mouseClicked(mouseX, mouseY, button, contentRect, this)) {
                    return true;
                }
            } else {
                if (placeholderTab.mouseClicked(mouseX, mouseY, button, contentRect, this)) {
                    return true;
                }
            }
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == TabType.GUIDE) {
            if (guideTab.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        } else if (activeTab == TabType.KISTOR) {
            if (kistorTab.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();

        // While a legitimate Companion text input (Kistor search or the local label editor) is
        // focused, G must type into it rather than close the Companion. charTyped still inserts
        // the character normally; only this global close action is suppressed.
        boolean textInputFocused = activeTab == TabType.KISTOR && kistorTab.isTextInputFocused();

        if (keyCode == GLFW.GLFW_KEY_G && !textInputFocused) {
            this.onClose();
            return true;
        }

        // Give the active tab's own input handling (label-edit cancel/save, search unfocus, etc.)
        // first refusal on every key while it has something focused.
        if (activeTab == TabType.KISTOR) {
            if (kistorTab.keyPressed(event)) {
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (activeTab == TabType.KISTOR) {
            if (kistorTab.charTyped(event)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    public void setActiveTab(TabType tab) {
        if (tab != null) {
            this.activeTab = tab;
        }
    }
}