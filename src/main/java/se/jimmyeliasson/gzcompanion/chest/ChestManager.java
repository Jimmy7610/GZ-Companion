package se.jimmyeliasson.gzcompanion.chest;

import se.jimmyeliasson.gzcompanion.chest.index.ChestSearchMatcher;
import se.jimmyeliasson.gzcompanion.chest.index.ChestSnapshotDiff;
import se.jimmyeliasson.gzcompanion.chest.model.ChestCaptureEvent;
import se.jimmyeliasson.gzcompanion.chest.model.ChestDiagnosticsSummary;
import se.jimmyeliasson.gzcompanion.chest.model.ChestGroupFilter;
import se.jimmyeliasson.gzcompanion.chest.model.ChestManagerStatus;
import se.jimmyeliasson.gzcompanion.chest.model.StorageMetadata;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.chest.storage.ChestIndexData;
import se.jimmyeliasson.gzcompanion.chest.storage.ChestIndexLoadResult;
import se.jimmyeliasson.gzcompanion.chest.storage.ChestIndexStore;
import se.jimmyeliasson.gzcompanion.chest.storage.ContextContainers;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Fair-play "last known contents" storage index.
 *
 * <p>GZ Companion knows ONLY what Minecraft legitimately showed the player. This class never
 * scans for containers; it only records a snapshot after the runtime layer (see
 * {@code chest.bridge}) proves the player physically interacted with a supported storage block
 * AND a compatible storage menu subsequently opened. Callers are responsible for that proof —
 * see {@link #tryBeginCapture} javadoc.
 *
 * <p>This class contains no Minecraft API types; it is pure domain/runtime logic driven entirely
 * by plain data supplied by the Minecraft-specific capture adapter/controller.
 */
public class ChestManager {
    /** Maximum time between a physical block interaction and a compatible menu opening. */
    public static final long PENDING_INTERACTION_WINDOW_MS = 2000L;

    /** Local label cap, shared by the Kistor UI's inline editor. */
    public static final int MAX_LABEL_LENGTH = 32;

    /** Upper bound for the display-name memo - comfortably above the vanilla item registry size. */
    private static final int MAX_DISPLAY_NAME_CACHE = 4096;

    private final ChestIndexStore store;
    private ChestIndexData indexData;
    private ChestManagerStatus status = ChestManagerStatus.UNAVAILABLE;

    /**
     * Bumped on EVERY in-memory index change (load, capture finalization, forget, clear, label or
     * metadata edits). Derived views such as the Kistor item index cache themselves per revision,
     * so they are rebuilt deterministically on mutation and never per render frame.
     */
    private long revision = 0L;

    private PendingInteraction pendingInteraction;
    private ActiveCapture activeCapture;

    private final java.util.Map<String, String> displayNameCache = new java.util.HashMap<>();

    public ChestManager(ChestIndexStore store) {
        this.store = store;
    }

    public long revision() {
        return revision;
    }

    public void initialize() {
        revision++;
        try {
            ChestIndexLoadResult result = store.load();
            if (result == null) {
                this.indexData = ChestIndexData.empty();
                this.status = ChestManagerStatus.ERROR;
                return;
            }

            switch (result.outcome()) {
                case NOT_FOUND, LOADED, CORRUPT_RECOVERED -> {
                    this.indexData = result.data();
                    this.status = ChestManagerStatus.LOADED;
                }
                case INCOMPATIBLE_SCHEMA -> {
                    // Fail closed: never treat an unrecognized future schema as loaded. The file
                    // on disk is left completely untouched by the store; no capture, mutation, or
                    // save may occur while in this state (see requireLoaded()).
                    this.indexData = ChestIndexData.empty();
                    this.status = ChestManagerStatus.INCOMPATIBLE;
                }
                case ERROR -> {
                    this.indexData = ChestIndexData.empty();
                    this.status = ChestManagerStatus.ERROR;
                }
            }
        } catch (Exception e) {
            this.indexData = ChestIndexData.empty();
            this.status = ChestManagerStatus.ERROR;
        }
    }

    public ChestManagerStatus getStatus() {
        return status;
    }

    /**
     * Every capture and mutation entry point requires this. Only in-memory queries against
     * already-loaded state remain safe (and allowed) while the manager is not LOADED.
     */
    private boolean requireLoaded() {
        return status == ChestManagerStatus.LOADED;
    }

    // ------------------------------------------------------------------
    // Capture lifecycle
    // ------------------------------------------------------------------

    /**
     * Records that the player just physically right-clicked a supported storage block.
     * Callers (the Minecraft-specific controller) must have already verified the block is on
     * the explicit storage allow-list before calling this.
     *
     * @param shape the physical shape Minecraft's already client-visible block state proved for
     *              this block ({@link StorageShape#NOT_APPLICABLE} for non-chest-family kinds).
     * @param partnerPos the double-chest partner position, required only when {@code shape} is
     *                   {@link StorageShape#DOUBLE}; ignored otherwise.
     */
    public void recordPendingInteraction(String contextKey, String dimensionKey, StorageKind kind,
                                          StoragePosition clickedPos, StoragePosition partnerPos,
                                          StorageShape shape, long nowMs) {
        if (!requireLoaded()) return;
        if (contextKey == null || dimensionKey == null || kind == null || clickedPos == null) return;
        StorageShape safeShape = shape != null ? shape : StorageShape.UNKNOWN;

        StoragePosition anchor = clickedPos;
        StoragePosition partner = null;
        if (safeShape == StorageShape.DOUBLE && partnerPos != null) {
            // Canonicalize so clicking either half of the same double chest resolves to the
            // same identity, regardless of which side was opened.
            if (clickedPos.compareOrder(partnerPos) <= 0) {
                anchor = clickedPos;
                partner = partnerPos;
            } else {
                anchor = partnerPos;
                partner = clickedPos;
            }
        }

        this.pendingInteraction = new PendingInteraction(contextKey, dimensionKey, kind, anchor, partner, safeShape, nowMs);
    }

    /**
     * Attempts to begin an opened-storage capture session for a menu that just opened.
     *
     * <p>Callers MUST have already determined, using Minecraft-specific knowledge, the full set
     * of {@link StorageKind} values whose blocks legitimately open this exact menu class (e.g. a
     * {@code ChestMenu} structurally supports {@code CHEST}, {@code TRAPPED_CHEST}, and
     * {@code BARREL}). This method then verifies, using only plain domain data, that a recent
     * matching physical block interaction exists AND that its recorded kind is one of the
     * caller-supplied compatible kinds. If not, capture is refused and NOTHING is indexed — this
     * is the core fair-play guard against plugin/virtual GUIs that reuse vanilla menu classes
     * without a real, matching block interaction.
     *
     * @return true if a capture session began.
     */
    public boolean tryBeginCapture(String contextKey, String dimensionKey, Set<StorageKind> compatibleKinds, long nowMs) {
        if (!requireLoaded()) return false;

        PendingInteraction p = this.pendingInteraction;
        if (p == null) return false;

        boolean recent = (nowMs - p.atMs()) <= PENDING_INTERACTION_WINDOW_MS && nowMs >= p.atMs();
        boolean contextMatch = Objects.equals(p.contextKey(), contextKey);
        boolean dimMatch = Objects.equals(p.dimensionKey(), dimensionKey);
        boolean kindMatch = compatibleKinds != null && compatibleKinds.contains(p.kind());

        // The pending interaction is consumed exactly once, correlated or not, to prevent a
        // stale interaction from being reused by a later, unrelated menu open.
        this.pendingInteraction = null;

        if (!recent || !contextMatch || !dimMatch || !kindMatch) {
            return false;
        }

        this.activeCapture = new ActiveCapture(p.contextKey(), p.dimensionKey(), p.kind(), p.anchor(), p.partner(), p.shape());
        return true;
    }

    /**
     * Called while a captured storage screen remains open. Recomputes a lightweight fingerprint
     * of the currently visible storage slots; if unchanged since the last call, this is a no-op.
     * Never writes to disk.
     */
    public void updateCaptureSlots(List<ChestSlotEntry> visibleSlots, long nowMs) {
        if (!requireLoaded()) return;
        if (activeCapture == null) return;
        List<ChestSlotEntry> safeSlots = visibleSlots != null ? List.copyOf(visibleSlots) : List.of();
        String fingerprint = fingerprint(safeSlots);
        if (fingerprint.equals(activeCapture.lastFingerprint)) {
            return;
        }
        activeCapture.lastFingerprint = fingerprint;
        activeCapture.lastSlots = safeSlots;
        activeCapture.lastUpdateAtMs = nowMs;
        activeCapture.hasAnyUpdate = true;
    }

    /**
     * Called when the captured storage screen closes. Finalizes and persists the last legitimate
     * visible snapshot exactly once, then clears the capture session.
     *
     * <p>Fails closed: if this capture session never received a single legitimate snapshot (see
     * {@link #updateCaptureSlots}), NOTHING is written — no empty record is created, and an
     * existing non-empty record is never overwritten with empty data. In normal operation this
     * should not happen, since the controller takes an immediate snapshot the moment the screen
     * opens and a final snapshot immediately before calling this method.
     *
     * <p>Every session that DID capture at least one legitimate snapshot always persists exactly
     * once here, even if the final contents are identical to the existing record — this is what
     * keeps "senast öppnad" (last opened) accurate on every reopen, not just on a content change.
     *
     * <p>Kistor 2.0: when a known record is replaced, its current snapshot becomes the single
     * retained {@link se.jimmyeliasson.gzcompanion.chest.model.PreviousSnapshot} (bounded to one
     * step - never an unbounded history), and its label and local metadata are preserved. This is
     * still exactly one persist per finalized session; nothing extra is written while the screen
     * is open.
     *
     * @return the finalization event, present only when a legitimate snapshot was persisted.
     */
    public Optional<ChestCaptureEvent> endCapture(long nowMs) {
        ActiveCapture cap = this.activeCapture;
        this.activeCapture = null;
        if (cap == null) return Optional.empty();
        if (!requireLoaded()) return Optional.empty();

        if (!cap.hasAnyUpdate) {
            // No legitimate snapshot was ever captured this session - fail closed.
            return Optional.empty();
        }

        StoredContainerId id = new StoredContainerId(cap.contextKey, cap.dimensionKey, cap.anchor, cap.kind);
        ContextContainers contextContainers = indexData.getContext(cap.contextKey);
        StoredContainer existing = contextContainers.containers().get(id.asStableKey());

        StoredContainer updated;
        boolean contentsChanged;
        if (existing == null) {
            updated = new StoredContainer(id, null, cap.partner, cap.shape, cap.lastUpdateAtMs, cap.lastSlots);
            contentsChanged = true;
        } else {
            updated = existing.withNewSnapshot(cap.partner, cap.shape, cap.lastUpdateAtMs, cap.lastSlots);
            contentsChanged = !ChestSnapshotDiff.compute(existing.slots(), cap.lastSlots).isEmpty();
        }

        ContextContainers updatedContext = contextContainers.withContainer(updated);
        indexData = indexData.withContext(cap.contextKey, updatedContext);
        revision++;
        store.save(indexData);
        return Optional.of(new ChestCaptureEvent(existing == null ? ChestCaptureEvent.Kind.NEW : ChestCaptureEvent.Kind.UPDATED,
                updated, contentsChanged));
    }

    public boolean isCaptureActive() {
        return activeCapture != null;
    }

    /**
     * Clears any in-memory pending interaction and/or active capture session WITHOUT persisting
     * anything, guessing, or writing a fake final snapshot. Intended for lifecycle boundaries
     * where continuing a capture would be unsafe or meaningless: leaving a world/server,
     * disconnecting, or returning to the title screen. See {@code ChestCaptureController} for
     * the verified Fabric hook this is registered against.
     */
    public void clearTransientCaptureState() {
        this.pendingInteraction = null;
        this.activeCapture = null;
    }

    // ------------------------------------------------------------------
    // Query API
    // ------------------------------------------------------------------

    public List<StoredContainer> getContainers(String contextKey) {
        if (contextKey == null) return List.of();
        List<StoredContainer> result = new ArrayList<>(indexData.getContext(contextKey).containers().values());
        result.sort(Comparator.comparingLong(StoredContainer::lastOpenedAtMs).reversed());
        return result;
    }

    public Optional<StoredContainer> getContainer(String contextKey, StoredContainerId id) {
        if (contextKey == null || id == null) return Optional.empty();
        return Optional.ofNullable(indexData.getContext(contextKey).containers().get(id.asStableKey()));
    }

    public int getIndexedCount(String contextKey) {
        if (contextKey == null) return 0;
        return indexData.getContext(contextKey).containers().size();
    }

    /**
     * Safe diagnostics summary for the given context: status, schema version, and a count only.
     * Deliberately excludes coordinates, labels, and item contents.
     */
    public ChestDiagnosticsSummary getDiagnostics(String contextKey) {
        return new ChestDiagnosticsSummary(status, indexData != null ? indexData.schemaVersion() : ChestIndexData.CURRENT_SCHEMA, getIndexedCount(contextKey));
    }

    /** Local-only search across all containers in the context, no filter, default RECENT order. */
    public List<StoredContainer> search(String contextKey, String query) {
        return search(contextKey, query, ChestTypeFilter.ALL, ChestSortMode.RECENT);
    }

    /**
     * Local-only search, restricted to the current context, combined with an optional storage
     * type filter and sort mode. No world scanning of any kind is ever performed here.
     */
    public List<StoredContainer> search(String contextKey, String query, ChestTypeFilter typeFilter, ChestSortMode sortMode) {
        return search(contextKey, query, typeFilter, sortMode, ChestGroupFilter.ALL);
    }

    /**
     * Local-only search additionally restricted by a Kistor 2.0 {@link ChestGroupFilter}. Matching
     * is delegated to {@link ChestSearchMatcher} (label, group, location note, type, dimension,
     * coordinates, item ids and display names).
     */
    public List<StoredContainer> search(String contextKey, String query, ChestTypeFilter typeFilter, ChestSortMode sortMode,
                                        ChestGroupFilter groupFilter) {
        List<StoredContainer> all = getContainers(contextKey); // already RECENT-desc by default
        String q = ChestSearchMatcher.normalizeQuery(query);
        ChestTypeFilter safeFilter = typeFilter != null ? typeFilter : ChestTypeFilter.ALL;
        ChestSortMode safeSort = sortMode != null ? sortMode : ChestSortMode.RECENT;
        ChestGroupFilter safeGroup = groupFilter != null ? groupFilter : ChestGroupFilter.ALL;

        List<StoredContainer> result = new ArrayList<>();
        for (StoredContainer container : all) {
            if (!safeFilter.matches(container.kind())) continue;
            if (!safeGroup.matches(container)) continue;
            if (q != null && !ChestSearchMatcher.containerMatches(container, q, this::itemDisplayName)) continue;
            result.add(container);
        }
        sortContainers(result, safeSort);
        return result;
    }

    /**
     * Every distinct local group name used in this context, sorted case-insensitively. Two
     * spellings differing only by case are one group (the first-sorted spelling wins).
     */
    public List<String> getGroups(String contextKey) {
        java.util.TreeMap<String, String> byLower = new java.util.TreeMap<>();
        for (StoredContainer container : getContainers(contextKey)) {
            String group = container.group();
            if (group == null) continue;
            String lower = group.toLowerCase(Locale.ROOT);
            String current = byLower.get(lower);
            if (current == null || current.compareTo(group) > 0) byLower.put(lower, group);
        }
        List<String> groups = new ArrayList<>(byLower.values());
        groups.sort(String.CASE_INSENSITIVE_ORDER);
        return groups;
    }

    private void sortContainers(List<StoredContainer> containers, ChestSortMode mode) {
        switch (mode) {
            case NAME -> containers.sort(Comparator.comparing(ChestManager::nameSortKey, String.CASE_INSENSITIVE_ORDER));
            case TYPE -> containers.sort(Comparator.comparing((StoredContainer c) -> c.kind().name())
                    .thenComparing(ChestManager::typeSortSecondaryKey, String.CASE_INSENSITIVE_ORDER));
            case RECENT -> containers.sort(Comparator.comparingLong(StoredContainer::lastOpenedAtMs).reversed());
        }
    }

    private static String nameSortKey(StoredContainer c) {
        return (c.label() != null && !c.label().isBlank()) ? c.label() : c.kind().getDisplayName();
    }

    private static String typeSortSecondaryKey(StoredContainer c) {
        return (c.label() != null && !c.label().isBlank()) ? c.label() : c.anchor().toCoordinateText();
    }

    /**
     * Resolves a human-readable display name for an item ID. Overridden at runtime by a
     * Minecraft-aware supplier where available; falls back to a readable transform of the ID.
     * Memoized (bounded) so searches and the item index never re-resolve the same id every frame.
     */
    public String itemDisplayName(String itemId) {
        if (itemId == null) return "";
        String cached = displayNameCache.get(itemId);
        if (cached != null) return cached;
        String name = null;
        if (displayNameResolver != null) {
            String resolved = displayNameResolver.resolve(itemId);
            if (resolved != null && !resolved.isBlank()) name = resolved;
        }
        if (name == null) name = fallbackDisplayName(itemId);
        if (displayNameCache.size() >= MAX_DISPLAY_NAME_CACHE) displayNameCache.clear();
        displayNameCache.put(itemId, name);
        return name;
    }

    private static String fallbackDisplayName(String itemId) {
        if (itemId == null) return "";
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private ItemDisplayNameResolver displayNameResolver;

    public void setDisplayNameResolver(ItemDisplayNameResolver resolver) {
        this.displayNameResolver = resolver;
        this.displayNameCache.clear();
        revision++;
    }

    @FunctionalInterface
    public interface ItemDisplayNameResolver {
        String resolve(String itemId);
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    /**
     * Removes one cached local entry. This only affects the local JSON index; it never touches
     * the actual Minecraft chest/container.
     */
    public boolean forgetContainer(String contextKey, StoredContainerId id) {
        if (!requireLoaded()) return false;
        if (contextKey == null || id == null) return false;
        ContextContainers contextContainers = indexData.getContext(contextKey);
        if (!contextContainers.containers().containsKey(id.asStableKey())) return false;

        ContextContainers updated = contextContainers.withoutContainer(id.asStableKey());
        indexData = indexData.withContext(contextKey, updated);
        revision++;
        store.save(indexData);
        return true;
    }

    /**
     * Clears every locally indexed container for one context, leaving other contexts untouched.
     * Used by the Settings tab's "Rensa Kistor-index" action - never touches the actual
     * Minecraft chests/containers, only this local JSON index. This also clears every Kistor 2.0
     * favorite/group/location note and previous snapshot for that context, since they live on the
     * container records themselves.
     */
    public boolean clearContext(String contextKey) {
        if (!requireLoaded() || contextKey == null) return false;
        indexData = indexData.withContext(contextKey, ContextContainers.empty());
        revision++;
        store.save(indexData);
        return true;
    }

    /**
     * Sets a local-only custom label for an indexed container. Never writes signs, blocks,
     * server state, commands, or chat — the label exists only inside chest-index.json.
     * Control characters are stripped and the label is capped at {@link #MAX_LABEL_LENGTH}.
     */
    public boolean setLabel(String contextKey, StoredContainerId id, String label) {
        String sanitized = StorageMetadata.sanitizeText(label, MAX_LABEL_LENGTH);
        return mutateContainer(contextKey, id, existing -> existing.withLabel(sanitized));
    }

    /** Local-only favorite/pinned flag. Never touches the world, chat, commands or any server. */
    public boolean setFavorite(String contextKey, StoredContainerId id, boolean favorite) {
        return mutateContainer(contextKey, id, existing -> existing.withMetadata(existing.metadata().withFavorite(favorite)));
    }

    /**
     * Assigns a simple one-level local group, or removes it with a blank/null value. If a group
     * with the same name (ignoring case) already exists in this context, its existing spelling is
     * reused so one group never splits into two by capitalization.
     */
    public boolean setGroup(String contextKey, StoredContainerId id, String group) {
        String sanitized = StorageMetadata.sanitizeGroup(group);
        if (sanitized != null) {
            for (String existingGroup : getGroups(contextKey)) {
                if (existingGroup.equalsIgnoreCase(sanitized)) {
                    sanitized = existingGroup;
                    break;
                }
            }
        }
        String finalGroup = sanitized;
        return mutateContainer(contextKey, id, existing -> existing.withMetadata(existing.metadata().withGroup(finalGroup)));
    }

    /** Local-only free-text location note (sanitized, capped); blank/null clears it. */
    public boolean setLocationNote(String contextKey, StoredContainerId id, String note) {
        return mutateContainer(contextKey, id, existing -> existing.withMetadata(existing.metadata().withLocationNote(note)));
    }

    private boolean mutateContainer(String contextKey, StoredContainerId id, java.util.function.UnaryOperator<StoredContainer> change) {
        if (!requireLoaded()) return false;
        if (contextKey == null || id == null) return false;
        ContextContainers contextContainers = indexData.getContext(contextKey);
        StoredContainer existing = contextContainers.containers().get(id.asStableKey());
        if (existing == null) return false;

        StoredContainer updated = change.apply(existing);
        ContextContainers updatedContext = contextContainers.withContainer(updated);
        indexData = indexData.withContext(contextKey, updatedContext);
        revision++;
        store.save(indexData);
        return true;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private static String fingerprint(List<ChestSlotEntry> slots) {
        StringBuilder sb = new StringBuilder();
        for (ChestSlotEntry slot : slots) {
            sb.append(slot.slotIndex()).append(':').append(slot.itemId()).append(':').append(slot.count()).append(';');
        }
        if (sb.length() == 0) return "empty";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(sb.toString().hashCode());
        }
    }

    private static final class PendingInteraction {
        private final String contextKey;
        private final String dimensionKey;
        private final StorageKind kind;
        private final StoragePosition anchor;
        private final StoragePosition partner;
        private final StorageShape shape;
        private final long atMs;

        PendingInteraction(String contextKey, String dimensionKey, StorageKind kind, StoragePosition anchor,
                            StoragePosition partner, StorageShape shape, long atMs) {
            this.contextKey = contextKey;
            this.dimensionKey = dimensionKey;
            this.kind = kind;
            this.anchor = anchor;
            this.partner = partner;
            this.shape = shape;
            this.atMs = atMs;
        }

        String contextKey() { return contextKey; }
        String dimensionKey() { return dimensionKey; }
        StorageKind kind() { return kind; }
        StoragePosition anchor() { return anchor; }
        StoragePosition partner() { return partner; }
        StorageShape shape() { return shape; }
        long atMs() { return atMs; }
    }

    private static final class ActiveCapture {
        final String contextKey;
        final String dimensionKey;
        final StorageKind kind;
        final StoragePosition anchor;
        final StoragePosition partner;
        final StorageShape shape;

        String lastFingerprint = null;
        List<ChestSlotEntry> lastSlots = List.of();
        long lastUpdateAtMs = 0L;
        boolean hasAnyUpdate = false;

        ActiveCapture(String contextKey, String dimensionKey, StorageKind kind, StoragePosition anchor,
                      StoragePosition partner, StorageShape shape) {
            this.contextKey = contextKey;
            this.dimensionKey = dimensionKey;
            this.kind = kind;
            this.anchor = anchor;
            this.partner = partner;
            this.shape = shape;
        }
    }
}
