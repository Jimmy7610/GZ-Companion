package se.jimmyeliasson.gzcompanion.settlement.storage;

import java.util.Objects;

/**
 * A purely local, user-authored note about a settlement "member" - a name plus a free-text note
 * the player typed themselves (e.g. role/responsibility). This is NEVER the live GameZone server
 * roster and must always be labeled "Lokala anteckningar" in the UI - see docs/SETTLEMENT-COMPANION.md.
 */
public record MemberNote(String id, String playerName, String note) {
    public MemberNote {
        Objects.requireNonNull(id, "id");
        playerName = (playerName != null && !playerName.isBlank()) ? playerName.trim() : "Okänd spelare";
        note = note != null ? note.trim() : "";
    }
}
