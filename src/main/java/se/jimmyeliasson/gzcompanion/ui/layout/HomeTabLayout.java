package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Immutable geometry computation for the Home ("Hem") dashboard.
 */
public record HomeTabLayout(
    UiRect welcomeRect,
    UiRect serverRect,
    UiRect versionRect,
    UiRect[] versionBadgeRects,
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

        // Top Row: 60% Welcome / 40% Server Status
        int leftColW = (int) ((width - gap) * 0.60f);
        int rightColW = Math.max(10, width - leftColW - gap);

        UiRect welcome = new UiRect(x, y, leftColW, topRowH);
        UiRect server = new UiRect(x + leftColW + gap, y, rightColW, topRowH);

        // Middle Row: Versions Strip with Weighted Badge Allocations
        // Minecraft: ~20%, Companion: ~29%, Rule Pack: ~28%, Status: ~23%
        int midY = y + topRowH + gap;
        UiRect version = new UiRect(x, midY, width, midH);

        int vPad = 3;
        int vCardH = Math.max(8, midH - (vPad * 2));
        int availVW = Math.max(20, width - (vPad * 5));
        int w1 = (int) (availVW * 0.20f); // Minecraft (26.1.2)
        int w2 = (int) (availVW * 0.29f); // Companion (version string width - see CompanionConstants.getModVersion())
        int w3 = (int) (availVW * 0.28f); // Rule Pack (Ej laddad / version)
        int w4 = Math.max(5, availVW - w1 - w2 - w3); // Status (Overifierad / OK)

        int bx1 = x + vPad;
        int bx2 = bx1 + w1 + vPad;
        int bx3 = bx2 + w2 + vPad;
        int bx4 = bx3 + w3 + vPad;

        UiRect[] versionBadges = new UiRect[] {
            new UiRect(bx1, midY + vPad, w1, vCardH),
            new UiRect(bx2, midY + vPad, w2, vCardH),
            new UiRect(bx3, midY + vPad, w3, vCardH),
            new UiRect(bx4, midY + vPad, w4, vCardH)
        };

        // Bottom Row: 61% Objective / 39% Module Status
        int botY = midY + midH + gap;
        int botLeftW = (int) ((width - gap) * 0.61f);
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

        // Row 2: Secondary Actions [ Vad ska jag göra? ] [ Kompatibilitet ] (58% / 42% proportional split)
        int r2Y = r1Y + btnH + 3;
        int sBtn1W = Math.max(5, (int) ((availBtnW - pad) * 0.58f));
        int sBtn2W = Math.max(5, availBtnW - sBtn1W - pad);
        UiRect sBtn1 = new UiRect(objective.x() + pad, r2Y, sBtn1W, btnH);
        UiRect sBtn2 = new UiRect(sBtn1.right() + pad, r2Y, sBtn2W, btnH);

        return new HomeTabLayout(welcome, server, version, versionBadges, objective, module, primaryBtn, sBtn1, sBtn2);
    }
}