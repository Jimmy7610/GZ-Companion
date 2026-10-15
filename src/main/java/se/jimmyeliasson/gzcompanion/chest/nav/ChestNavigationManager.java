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
 *
 * <p>Besides storage, the same single target slot can hold a {@link NavigationPoint}: a known
 * coordinate from another feature (e.g. a location saved on a MarketWatch note). Exactly one target
 * of either kind is active at a time - starting one replaces the other - and a point target obeys
 * the same context/disconnect rules but never depends on the Chest Manager or the Kistor index.
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
    private NavigationPoint pointTarget;
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
        if (pointTarget != null || (target != null && !target.equals(id))) {
            lastStopReason = StopReason.REPLACED;
        }
        this.pointTarget = null;
        this.target = id;
        this.startedAtMs = nowMs;
        return true;
    }

    /**
     * Starts navigating toward a known non-storage coordinate, replacing any previous target
     * (storage or point). Refused (returns false) unless the point belongs to
     * {@code currentContextKey} - a coordinate from one world/server is never guided to in another.
     */
    public boolean startPoint(String currentContextKey, NavigationPoint point, long nowMs) {
        if (point == null || currentContextKey == null) return false;
        if (!Objects.equals(point.contextStorageKey(), currentContextKey)) return false;
        if (target != null || (pointTarget != null && !pointTarget.equals(point))) {
            lastStopReason = StopReason.REPLACED;
        }
        this.target = null;
        this.pointTarget = point;
        this.startedAtMs = nowMs;
        return true;
    }

    /** O(1) fast path for the HUD: true while a target (storage or point) is set. */
    public boolean isActive() {
        return target != null || pointTarget != null;
    }

    /** The active point target, if the current target is a {@link NavigationPoint} rather than storage. */
    public Optional<NavigationPoint> pointTarget() {
        return Optional.ofNullable(pointTarget);
    }

    /** True while the active target is a point started by {@code sourceKey}. */
    public boolean isPointTarget(String sourceKey) {
        return pointTarget != null && pointTarget.sourceKey().equals(sourceKey);
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
        if (!isActive()) return;
        target = null;
        pointTarget = null;
        lastStopReason = reason != null ? reason : StopReason.USER;
    }

    /**
     * Resolves the active STORAGE target against the CURRENT local index, stopping navigation if it
     * can no longer be resolved safely. O(1): one hash lookup by stable key. Empty (without
     * stopping anything) while the active target is a point.
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
     * Resolves whatever is being navigated to - storage (with the same safety stops as
     * {@link #resolveTarget}) or a point - into what the HUD and banner draw.
     */
    public Optional<NavigationDestination> resolveDestination(ChestManager manager) {
        if (pointTarget != null) return Optional.of(NavigationDestination.of(pointTarget));
        return resolveTarget(manager).map(NavigationDestination::of);
    }

    /**
     * Stops navigation if the player's current world/server context is not the target's own
     * context. A target from one server/world is never presented as belonging to another.
     */
    public void onContextObserved(String currentContextKey) {
        if (!isActive()) return;
        String targetContext = target != null ? target.contextStorageKey() : pointTarget.contextStorageKey();
        if (!Objects.equals(targetContext, currentContextKey)) {
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
