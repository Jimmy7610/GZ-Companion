package se.jimmyeliasson.gzcompanion.ui.layout;

/**
 * Layout for the Inställningar tab: a header followed by a single scrollable content area. All
 * sections (toggles, privacy info, data management, diagnostics, keybind) render sequentially
 * inside that one scrollable area - there is no split pane, since every section is a simple
 * vertical list of rows.
 */
public record SettingsLayout(UiRect bounds, UiRect headerRect, UiRect contentRect) {
    public static SettingsLayout calculate(UiRect bounds) {
        int headerH = 13;
        UiRect header = new UiRect(bounds.x(), bounds.y(), bounds.width(), headerH);
        int contentTop = bounds.y() + headerH + 2;
        UiRect content = new UiRect(bounds.x(), contentTop, bounds.width(), bounds.bottom() - contentTop);
        return new SettingsLayout(bounds, header, content);
    }
}
