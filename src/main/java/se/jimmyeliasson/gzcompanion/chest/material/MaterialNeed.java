package se.jimmyeliasson.gzcompanion.chest.material;

/**
 * One required material from a planner (Settlement target range, Building plan). {@code itemId}
 * is null for a genuinely ambiguous category requirement ("any Wool") that cannot be matched to
 * one concrete item - such needs are shown but never guessed against chest contents.
 */
public record MaterialNeed(String itemId, String displayName, int needed) {
    public MaterialNeed {
        needed = Math.max(0, needed);
        displayName = (displayName != null && !displayName.isBlank()) ? displayName : (itemId != null ? itemId : "Okänt föremål");
        itemId = (itemId != null && !itemId.isBlank()) ? itemId : null;
    }

    public boolean isTrackable() {
        return itemId != null;
    }
}
