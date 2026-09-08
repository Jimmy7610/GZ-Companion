package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Breakpoints adapting modal scaling and density to player viewport dimensions.
 */
public enum ResponsiveBreakpoint {
    COMPACT,  // Screen width < 520 or height < 340 (high GUI scale on smaller displays)
    NORMAL,   // Typical desktop gameplay scale (width 520-750)
    LARGE;    // Wide / 4K displays with lower GUI scale (width > 750)

    public static ResponsiveBreakpoint fromScreen(int screenWidth, int screenHeight) {
        if (screenWidth < 520 || screenHeight < 340) {
            return COMPACT;
        } else if (screenWidth > 750 && screenHeight > 480) {
            return LARGE;
        }
        return NORMAL;
    }
}