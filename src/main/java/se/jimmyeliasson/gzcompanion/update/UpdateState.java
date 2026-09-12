package se.jimmyeliasson.gzcompanion.update;

/**
 * Deterministic update state machine. Only {@link UpdateManager} ever transitions this; the UI
 * only ever reads an immutable snapshot ({@code UpdateManager.Snapshot}) - rendering never
 * performs networking or file I/O itself.
 */
public enum UpdateState {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    VERIFYING,
    READY_TO_INSTALL,
    STARTING_INSTALLER,
    ERROR
}
