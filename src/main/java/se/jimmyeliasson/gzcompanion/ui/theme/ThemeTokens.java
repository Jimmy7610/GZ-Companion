package se.jimmyeliasson.gzcompanion.ui.theme;

/**
 * Every visual token a theme controls, in one place. Tabs never branch on the theme: they keep
 * reading {@code GZTheme.COLOR_*} and drawing through {@code GZTheme}/{@code TextUtil}, which take
 * their values from the active {@code ThemeTokens}. Pure data (no Minecraft types), so the
 * palettes are unit-testable.
 *
 * <p>Layout metrics (typography scales, row heights, spacing) are deliberately NOT themed: both
 * themes share identical geometry, so switching can never move, clip or overlap a control.
 *
 * @param textShadows      whether text may draw Minecraft's drop shadow (callers still decide per
 *                         call; a theme can only turn shadows off).
 * @param chamferedCorners pixel-chamfered card corners (Minecraft) vs. plain square corners.
 * @param fontId           resource id of the font used for all Companion text, or null for
 *                         Minecraft's default font.
 */
public record ThemeTokens(
    int backdrop,
    int panelBg,
    int cardBg,
    int cardHover,
    int cardInner,
    int navActive,
    int navHover,
    int emerald,
    int mint,
    int emeraldDark,
    int borderSubtle,
    int borderModal,
    int borderEmerald,
    int textPrimary,
    int textSecondary,
    int textMuted,
    int textAccent,
    int textOnEmerald,
    int statusGreen,
    int statusYellow,
    int statusRed,
    int statusGrey,
    int buttonBg,
    int buttonHoverBg,
    int buttonHoverBorder,
    int badgeBg,
    int badgeBorder,
    boolean textShadows,
    boolean chamferedCorners,
    String fontId
) {
    /** Exactly the values GZ Companion shipped with before themes existed - no visual change for upgraders. */
    public static final ThemeTokens MINECRAFT = new ThemeTokens(
            0xB3070A0E, // backdrop: 70% dark overlay
            0xF20D141C, // panel: 95% dark navy
            0xCC131F2B, // card: 80% slate
            0xE61E2E3D, // card hover
            0x800A1017, // recessed inner container
            0x4D10B981, // active nav tab
            0x331E293B, // nav hover
            0xFF10B981, // emerald
            0xFF34D399, // mint
            0xFF059669, // emerald dark
            0x33475569, // subtle border
            0x66475569, // modal border
            0x9910B981, // emerald border
            0xFFF8FAFC, // text primary
            0xFF94A3B8, // text secondary
            0xFF64748B, // text muted
            0xFF34D399, // text accent
            0xFF04170E, // text on emerald
            0xFF22C55E, 0xFFF59E0B, 0xFFEF4444, 0xFF64748B, // status green/yellow/red/grey
            0x991E293B, // secondary button
            0xE61E2E3D, // secondary button hover
            0x8064748B, // secondary button hover border
            0x800A1017, // badge background
            0x40475569, // badge border
            true,
            true,
            null);

    /**
     * "Tydlig": the same dark GZ Companion identity, tuned for long reading - fully opaque surfaces
     * (no world bleeding through behind text), clearly brighter secondary/muted text, stronger
     * borders, square corners, no drop shadows, and a smooth sans-serif font (DejaVu Sans).
     */
    public static final ThemeTokens TYDLIG = new ThemeTokens(
            0xD9050709, // backdrop
            0xFF0B1118, // panel (opaque)
            0xFF141E29, // card (opaque)
            0xFF213040, // card hover
            0xFF0D151D, // recessed inner container
            0x6610B981, // active nav tab
            0x40334155, // nav hover
            0xFF10B981, // emerald
            0xFF6EE7B7, // mint (lighter, higher contrast)
            0xFF059669, // emerald dark
            0x6694A3B8, // subtle border (clearly visible)
            0x9994A3B8, // modal border
            0xFF34D399, // emerald border
            0xFFFFFFFF, // text primary
            0xFFD5DEE8, // text secondary
            0xFFA7B4C4, // text muted
            0xFF6EE7B7, // text accent
            0xFF02120A, // text on emerald
            0xFF4ADE80, 0xFFFBBF24, 0xFFF87171, 0xFFA7B4C4, // status green/yellow/red/grey
            0xFF1E293B, // secondary button
            0xFF2B3B4F, // secondary button hover
            0xFFA7B4C4, // secondary button hover border
            0xFF0D151D, // badge background
            0x9994A3B8, // badge border
            false,
            false,
            "gzcompanion:tydlig");
}
