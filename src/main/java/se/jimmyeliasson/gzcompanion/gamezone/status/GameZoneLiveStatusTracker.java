package se.jimmyeliasson.gzcompanion.gamezone.status;

import java.util.Objects;

/**
 * Holds the local player's LIVE GameZone status across frames - the "modest cache" this feature
 * needs so the (cheap, but non-zero) header parse only reruns when the raw TAB header text
 * actually changed. Deliberately holds NO history: disconnecting immediately discards the current
 * status ({@link GameZoneLiveStatus#UNKNOWN}) rather than keeping "last known" values around, and a
 * fresh connection always reparses from scratch - a previous session's numbers must never be shown
 * as current. Pure (no Minecraft dependency), fully unit-testable with synthetic header strings.
 */
public final class GameZoneLiveStatusTracker {
    private boolean lastConnected = false;
    private String lastHeaderText;
    private GameZoneLiveStatus status = GameZoneLiveStatus.UNKNOWN;

    /**
     * Refreshes the tracked status from the current raw TAB header text. Must be called with
     * {@code connected = false} the moment GameZone disconnects - this immediately clears the
     * tracked status rather than leaving stale values active.
     */
    public GameZoneLiveStatus update(boolean connected, String headerText) {
        if (!connected) {
            reset();
            return status;
        }

        boolean unchanged = lastConnected && Objects.equals(lastHeaderText, headerText);
        if (!unchanged) {
            status = GameZoneTabStatusParser.parse(headerText);
            lastConnected = true;
            lastHeaderText = headerText;
        }
        return status;
    }

    /** The status as of the most recent {@link #update}. */
    public GameZoneLiveStatus current() {
        return status;
    }

    private void reset() {
        status = GameZoneLiveStatus.UNKNOWN;
        lastConnected = false;
        lastHeaderText = null;
    }
}
