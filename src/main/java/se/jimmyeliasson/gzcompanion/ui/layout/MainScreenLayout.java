package se.jimmyeliasson.gzcompanion.ui.layout;

import se.jimmyeliasson.gzcompanion.ui.TabType;

/**
 * Immutable geometry computation for GZ Companion Main Screen.
 */
public record MainScreenLayout(
    ResponsiveBreakpoint breakpoint,
    UiRect modalRect,
    UiRect headerRect,
    UiRect closeBtnRect,
    UiRect sidebarRect,
    UiRect[] tabRects,
    UiRect contentRect,
    UiRect footerRect
) {
    public static MainScreenLayout calculate(int screenWidth, int screenHeight) {
        ResponsiveBreakpoint bp = ResponsiveBreakpoint.fromScreen(screenWidth, screenHeight);

        int marginX = Math.max(8, (int) (screenWidth * 0.04f));
        int marginY = Math.max(8, (int) (screenHeight * 0.04f));

        // Normal mode target: ~460-475 x ~280-295 logical px
        int maxW = (bp == ResponsiveBreakpoint.LARGE) ? 520 : ((bp == ResponsiveBreakpoint.COMPACT) ? 380 : 465);
        int maxH = (bp == ResponsiveBreakpoint.LARGE) ? 320 : ((bp == ResponsiveBreakpoint.COMPACT) ? 240 : 285);

        int modalW = Math.min(screenWidth - (marginX * 2), maxW);
        int modalH = Math.min(screenHeight - (marginY * 2), maxH);

        // Clamping edge case: never exceed screen bounds minus minimal 4px safe margin
        modalW = Math.min(modalW, Math.max(10, screenWidth - 8));
        modalH = Math.min(modalH, Math.max(10, screenHeight - 8));

        int modalX = (screenWidth - modalW) / 2;
        int modalY = (screenHeight - modalH) / 2;
        UiRect modal = new UiRect(modalX, modalY, modalW, modalH);

        int headerH = 26;
        int footerH = 15;
        int sidebarW = (bp == ResponsiveBreakpoint.COMPACT) ? 82 : 98;

        UiRect header = new UiRect(modalX, modalY, modalW, headerH);
        UiRect closeBtn = new UiRect(modalX + modalW - 16, modalY + 6, 11, 11);
        UiRect footer = new UiRect(modalX + 6, modal.bottom() - footerH, modalW - 12, footerH);

        int bodyY = modalY + headerH + 3;
        int bodyH = Math.max(10, modalH - headerH - footerH - 6);

        UiRect sidebar = new UiRect(modalX + 5, bodyY, sidebarW, bodyH);
        int contentX = sidebar.right() + 5;
        int contentW = Math.max(10, modalW - sidebarW - 15);
        UiRect content = new UiRect(contentX, bodyY, contentW, bodyH);

        TabType[] tabs = TabType.values();
        int tabCount = tabs.length;
        UiRect[] tabRects = new UiRect[tabCount];

        int availTabH = Math.max(1, sidebar.height() - 6);
        int tabH = Math.max(12, Math.min(16, availTabH / tabCount));
        int tabSpacing = Math.max(0, (availTabH - (tabH * tabCount)) / Math.max(1, tabCount - 1));

        for (int i = 0; i < tabCount; i++) {
            int ty = sidebar.y() + 3 + (i * (tabH + tabSpacing));
            tabRects[i] = new UiRect(sidebar.x() + 2, ty, sidebar.width() - 4, tabH);
        }

        return new MainScreenLayout(bp, modal, header, closeBtn, sidebar, tabRects, content, footer);
    }
}