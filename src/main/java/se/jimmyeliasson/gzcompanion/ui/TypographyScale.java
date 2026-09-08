package se.jimmyeliasson.gzcompanion.ui;

/**
 * Design system typography scale tokens for hierarchy and density.
 */
public enum TypographyScale {
    DISPLAY(1.0f),
    HEADING(0.95f),
    BODY(0.88f),
    SMALL(0.84f),
    META(0.80f);

    private final float scale;

    TypographyScale(float scale) {
        this.scale = scale;
    }

    public float getScale() {
        return scale;
    }
}