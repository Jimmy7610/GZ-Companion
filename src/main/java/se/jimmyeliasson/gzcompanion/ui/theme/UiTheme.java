package se.jimmyeliasson.gzcompanion.ui.theme;

import java.util.Locale;

/**
 * The selectable visual themes ("Utseende" in Inställningar). A theme only changes how the UI
 * looks - never what any feature does. {@link #id()} is the stable value persisted in
 * {@code settings.json}; never rename an existing id.
 */
public enum UiTheme {
    /** Default: the original Minecraft-styled look, unchanged from earlier versions. */
    MINECRAFT("minecraft", "Minecraft (Standard)", ThemeTokens.MINECRAFT),
    /** Readability-first: a smooth sans-serif font, opaque panels and higher-contrast text. */
    TYDLIG("tydlig", "Tydlig", ThemeTokens.TYDLIG);

    public static final UiTheme DEFAULT = MINECRAFT;

    private final String id;
    private final String displayName;
    private final ThemeTokens tokens;

    UiTheme(String id, String displayName, ThemeTokens tokens) {
        this.id = id;
        this.displayName = displayName;
        this.tokens = tokens;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ThemeTokens tokens() {
        return tokens;
    }

    /**
     * The theme for a persisted id. Missing, blank, unknown or future values all fall back to
     * {@link #DEFAULT} - an unrecognised setting must never break the UI or crash loading.
     */
    public static UiTheme fromId(String id) {
        if (id == null) return DEFAULT;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (UiTheme theme : values()) {
            if (theme.id.equals(normalized)) return theme;
        }
        return DEFAULT;
    }
}
