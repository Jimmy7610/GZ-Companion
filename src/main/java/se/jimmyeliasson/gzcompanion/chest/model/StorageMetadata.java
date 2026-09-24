package se.jimmyeliasson.gzcompanion.chest.model;

/**
 * Purely local, player-authored organization metadata for one known storage location (Kistor
 * 2.0): a favorite/pinned flag, a simple one-level group, and a free-text location note.
 *
 * <p>These values only ever live inside {@code chest-index.json}. They never change a sign, send
 * chat, send a command, alter the Minecraft world, or call any GameZone API.
 */
public record StorageMetadata(boolean favorite, String group, String locationNote) {
    public static final int MAX_GROUP_LENGTH = 24;
    public static final int MAX_NOTE_LENGTH = 80;
    public static final StorageMetadata EMPTY = new StorageMetadata(false, null, null);

    public StorageMetadata {
        group = sanitizeGroup(group);
        locationNote = sanitizeNote(locationNote);
    }

    public boolean hasGroup() {
        return group != null;
    }

    public boolean hasLocationNote() {
        return locationNote != null;
    }

    public boolean isDefault() {
        return !favorite && group == null && locationNote == null;
    }

    public StorageMetadata withFavorite(boolean newFavorite) {
        return new StorageMetadata(newFavorite, group, locationNote);
    }

    public StorageMetadata withGroup(String newGroup) {
        return new StorageMetadata(favorite, newGroup, locationNote);
    }

    public StorageMetadata withLocationNote(String newNote) {
        return new StorageMetadata(favorite, group, newNote);
    }

    public static String sanitizeGroup(String raw) {
        return sanitizeText(raw, MAX_GROUP_LENGTH);
    }

    public static String sanitizeNote(String raw) {
        return sanitizeText(raw, MAX_NOTE_LENGTH);
    }

    /**
     * Shared free-text sanitization for every local Kistor text field: control characters
     * (newlines, tabs, etc.) become spaces, runs of whitespace collapse to one space, the result
     * is trimmed and capped at {@code maxLength} characters. Blank input becomes {@code null},
     * meaning "not set".
     */
    public static String sanitizeText(String raw, int maxLength) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean space = Character.isWhitespace(c) || Character.isISOControl(c);
            if (space) {
                if (!lastWasSpace) sb.append(' ');
                lastWasSpace = true;
            } else {
                sb.append(c);
                lastWasSpace = false;
            }
        }
        String trimmed = sb.toString().trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > maxLength) {
            trimmed = trimmed.substring(0, maxLength).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
