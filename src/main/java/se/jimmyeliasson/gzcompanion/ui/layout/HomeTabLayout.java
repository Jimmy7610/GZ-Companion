package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Immutable geometry computation for the Home ("Hem") dashboard.
 */
public record HomeTabLayout(
    UiRect welcomeRect,
    UiRect serverRect,
    UiRect versionRect,
    UiRect objectiveRect,
    UiRect moduleRect,
    UiRect primaryButtonRect,
    UiRect secondaryButton1Rect,
    UiRect secondaryButton2Rect
) {
    public static HomeTabLayout calculate(UiRect bounds) {
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        int height = bounds.height();

        int gap = 4;
        int topRowH = Math.min(50, Math.max(38, (int) (height * 0.23f)));
        int midH = Math.min(26, Math.max(20, (int) (height * 0.12f)));
        int botH = Math.max(10, height - topRowH - midH - (gap * 2));

        // Top Row: 58% Welcome / 42% Server Status
        int leftColW = (int) ((width - gap) * 0.58f);
        int rightColW = Math.max(10, width - leftColW - gap);

        UiRect welcome = new UiRect(x, y, leftColW, topRowH);
        UiRect server = new UiRect(x + leftColW + gap, y, rightColW, topRowH);

        // Middle Row: Versions Strip
        int midY = y + topRowH + gap;
        UiRect version = new UiRect(x, midY, width, midH);

        // Bottom Row: 57% Objective / 43% Module Status
        int botY = midY + midH + gap;
        int botLeftW = (int) ((width - gap) * 0.57f);
        int botRightW = Math.max(10, width - botLeftW - gap);

        UiRect objective = new UiRect(x, botY, botLeftW, botH);
        UiRect module = new UiRect(x + botLeftW + gap, botY, botRightW, botH);

        // 2-Row Action Buttons inside Objective Card
        int btnH = 13;
        int pad = 4;
        int availBtnW = Math.max(10, objective.width() - (pad * 2));

        // Row 1: Primary Action [ Öppna Guide ]
        int r1Y = objective.bottom() - (btnH * 2) - 8;
        UiRect primaryBtn = new UiRect(objective.x() + pad, r1Y, availBtnW, btnH);

        // Row 2: Secondary Actions [ Vad ska jag göra? ] [ Kompatibilitet ] (55% / 45% proportional split)
        int r2Y = r1Y + btnH + 3;
        int sBtn1W = Math.max(5, (int) ((availBtnW - pad) * 0.55f));
        int sBtn2W = Math.max(5, availBtnW - sBtn1W - pad);
        UiRect sBtn1 = new UiRect(objective.x() + pad, r2Y, sBtn1W, btnH);
        UiRect sBtn2 = new UiRect(sBtn1.right() + pad, r2Y, sBtn2W, btnH);

        return new HomeTabLayout(welcome, server, version, objective, module, primaryBtn, sBtn1, sBtn2);
    }
}