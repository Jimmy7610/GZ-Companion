package se.jimmyeliasson.gzcompanion.gamezone.events;

/**
 * The kinds of legitimately-observable GameZone events the parser engine can recognize. This is
 * a generic taxonomy, not tied to any specific chat pattern - a {@code GameZoneParserDefinition}
 * maps a verified pattern to one of these types.
 */
public enum GameZoneEventType {
    SETTLEMENT_INVITE("Settlementinbjudan"),
    WHISPER("Whisper"),
    BALANCE_CHANGE("Saldoförändring"),
    SYSTEM_MESSAGE("Systemmeddelande"),
    UNKNOWN("Okänd");

    private final String displayName;

    GameZoneEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
