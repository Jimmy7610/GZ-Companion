package se.jimmyeliasson.gzcompanion.knowledge.commands;

import java.util.Objects;

/**
 * A GameZone-specific command category. The taxonomy itself (which categories exist, their
 * display names, and their order) is Rule Pack data, not a hardcoded Java enum — only the
 * generic notion of "a category has an id, a display name, and a sort order" lives in Java.
 */
public record CommandCategory(String id, String displayName, int sortOrder) {
    public static final String FALLBACK_ID = "okategoriserad";
    public static final CommandCategory FALLBACK = new CommandCategory(FALLBACK_ID, "Okategoriserad", Integer.MAX_VALUE);

    public CommandCategory {
        Objects.requireNonNull(id, "id");
        displayName = displayName != null && !displayName.isBlank() ? displayName : id;
    }
}
