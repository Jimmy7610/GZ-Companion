package se.jimmyeliasson.gzcompanion.chest.nav;

import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.model.ChestManagerStatus;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;

import java.util.Objects;
import java.util.Optional;

/**
 * In-memory, session-only state for Kistor "HITTA": at most ONE explicitly selected, already
 * known storage location to guide the player back toward. Never persisted, so a target can never
 * unexpectedly survive a restart.
 *
 * <p>The target is identified by its stable {@link StoredContainerId} (context + dimension +
 * canonical anchor + kind) - never a list index - so it resolves to the same physical storage
 * however the Kistor list is sorted or filtered. Only a container that already exists in the
 * local Chest Manager index can ever be a target; this class never discovers anything.
 *
 * <p>Fails safe: whenever the target can no longer be resolved (forgotten, context cleared, Chest
 * Manager unavailable/incompatible) or the world/server context changes, navigation STOPS rather
 * than risk presenting one context's storage in another.
 */
public final class ChestNavigationManager {

    public enum StopReason {
        /** The player pressed "Stoppa". */
        USER,
        /** The player legitimately opened the exact target storage. */
        FOUND,
        /** The target no longer exists in the local index (forgotten or Kistor data reset). */
        TARGET_MISSING,
        /** The world/server context changed. */
        CONTEXT_CHANGED,
        /** The client left the world/server. */
        DISCONNECTED,
        /** The Chest Manager is not in a usable (LOADED) state. */
        MANAGER_UNAVAILABLE,
        /** A different target replaced this one. */
        REPLACED
    }

    private StoredContainerId target;
    private long startedAtMs;
    private StopReason lastStopReason;

    /**
     * Starts navigating toward one known storage location, replacing any previous target. Refused
     * (returns false) unless the Chest Manager is LOADED and the container actually exists in the
     * local index for {@code currentContextKey}.
     */
    public boolean start(ChestManager manager, String currentContextKey, StoredContainerId id, long nowMs) {
        if (manager == null || id == null || currentContextKey == null) return false;
        if (manager.getStatus() != ChestManagerStatus.LOADED) return false;
        if (!Objects.equals(id.contextStorageKey(), currentContextKey)) return false;
        if (manager.getContainer(currentContextKey, id).isEmpty()) return false;
        if (target != null && !target.equals(id)) {
            lastStopReason = StopReason.REPLACED;
        }
        this.target = id;
        this.startedAtMs = nowMs;
        return true;
    }

    /** O(1) fast path for the HUD: true while a target is set. */
    public boolean isActive() {
        return target != null;
    }

    public Optional<StoredContainerId> target() {
        return Optional.ofNullable(target);
    }

    public boolean isTarget(StoredContainerId id) {
        return target != null && target.equals(id);
    }

    public long startedAtMs() {
        return startedAtMs;
    }

    public Optional<StopReason> lastStopReason() {
        return Optional.ofNullable(lastStopReason);
    }

    public void stop(StopReason reason) {
        if (target == null) return;
        target = null;
        lastStopReason = reason != null ? reason : StopReason.USER;
    }

    /**
     * Resolves the active target against the CURRENT local index, stopping navigation if it can no
     * longer be resolved safely. O(1): one hash lookup by stable key.
     */
    public Optional<StoredContainer> resolveTarget(ChestManager manager) {
        if (target == null) return Optional.empty();
        if (manager == null || manager.getStatus() != ChestManagerStatus.LOADED) {
            stop(StopReason.MANAGER_UNAVAILABLE);
            return Optional.empty();
        }
        Optional<StoredContainer> container = manager.getContainer(target.contextStorageKey(), target);
        if (container.isEmpty()) {
            stop(StopReason.TARGET_MISSING);
        }
        return container;
    }

    /**
     * Stops navigation if the player's current world/server context is not the target's own
     * context. A target from one server/world is never presented as belonging to another.
     */
    public void onContextObserved(String currentContextKey) {
        if (target == null) return;
        if (!Objects.equals(target.contextStorageKey(), currentContextKey)) {
            stop(StopReason.CONTEXT_CHANGED);
        }
    }

    public void onDisconnect() {
        stop(StopReason.DISCONNECTED);
    }

    /**
     * Called after a legitimate capture was finalized for {@code openedId} (same stable identity
     * rules as the Chest Manager). Ends navigation only when it is the exact target.
     *
     * @return true if this finished the active navigation.
     */
    public boolean onStorageLegitimatelyOpened(StoredContainerId openedId) {
        if (target != null && target.equals(openedId)) {
            stop(StopReason.FOUND);
            return true;
        }
        return false;
    }
}
