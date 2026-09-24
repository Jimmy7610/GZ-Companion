package se.jimmyeliasson.gzcompanion.chest;

import se.jimmyeliasson.gzcompanion.chest.index.ChestItemIndexCache;
import se.jimmyeliasson.gzcompanion.chest.material.ChestMaterialRequest;
import se.jimmyeliasson.gzcompanion.chest.model.ChestCaptureEvent;
import se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationManager;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Session-scoped (in-memory only) Kistor 2.0 state that must outlive a single Companion screen
 * instance - the Companion screen is recreated every time G is pressed, but navigation must keep
 * running on the HUD and a Hämtningslista must survive closing the screen to go fetch things.
 *
 * <p>Holds no Minecraft types and persists nothing: the navigation target and the pickup
 * checklist are deliberately session state, and are cleared when the client leaves a
 * world/server so nothing from one context can surface in another.
 */
public final class KistorRuntime {
    /** Upper bound for checked Hämtningslista lines - a request never has anywhere near this many. */
    private static final int MAX_CHECKED_LINES = 512;

    private final ChestManager chestManager;
    private final ChestNavigationManager navigation = new ChestNavigationManager();
    private final ChestItemIndexCache itemIndexCache = new ChestItemIndexCache();

    private ChestMaterialRequest materialRequest;
    private String materialRequestContextKey;
    private final Set<String> checkedPickupLines = new HashSet<>();

    public KistorRuntime(ChestManager chestManager) {
        this.chestManager = chestManager;
    }

    public ChestManager chestManager() {
        return chestManager;
    }

    public ChestNavigationManager navigation() {
        return navigation;
    }

    public ChestItemIndexCache itemIndexCache() {
        return itemIndexCache;
    }

    // ------------------------------------------------------------------
    // Capture feedback
    // ------------------------------------------------------------------

    /**
     * Called once per legitimately finalized + persisted capture. Ends navigation only when the
     * finalized storage is the exact target (same stable identity), and returns the local
     * notification to show, if any.
     */
    public Optional<ChestCaptureFeedback.Message> onCaptureFinalized(ChestCaptureEvent event) {
        if (event == null) return Optional.empty();
        boolean finished = navigation.onStorageLegitimatelyOpened(event.container().id());
        return ChestCaptureFeedback.describe(event, finished);
    }

    /**
     * The Kistor index for {@code contextKey} was cleared (Settings reset): any navigation toward
     * storage in that context stops immediately, and pickup checkmarks (keyed by storage) reset.
     */
    public void onChestIndexCleared(String contextKey) {
        navigation.target().ifPresent(target -> {
            if (java.util.Objects.equals(target.contextStorageKey(), contextKey)) {
                navigation.stop(ChestNavigationManager.StopReason.TARGET_MISSING);
            }
        });
        checkedPickupLines.clear();
    }

    /** Safe, redacted diagnostics line: whether navigation is active - never the target or its position. */
    public String diagnosticsNavigationLine() {
        return "Kistor-navigering: " + (navigation.isActive() ? "aktiv" : "inaktiv");
    }

    /** The client left the world/server: stop navigation and drop context-bound session state. */
    public void onDisconnect() {
        navigation.onDisconnect();
        clearMaterialRequest();
    }

    // ------------------------------------------------------------------
    // Material request / Hämtningslista (session-only)
    // ------------------------------------------------------------------

    public void openMaterialRequest(String contextKey, ChestMaterialRequest request) {
        if (request == null) return;
        boolean sameAsBefore = request.equals(materialRequest) && java.util.Objects.equals(contextKey, materialRequestContextKey);
        this.materialRequest = request;
        this.materialRequestContextKey = contextKey;
        if (!sameAsBefore) checkedPickupLines.clear();
    }

    /** The active request, only if it belongs to {@code currentContextKey}. */
    public Optional<ChestMaterialRequest> materialRequest(String currentContextKey) {
        if (materialRequest == null) return Optional.empty();
        if (!java.util.Objects.equals(materialRequestContextKey, currentContextKey)) return Optional.empty();
        return Optional.of(materialRequest);
    }

    public void clearMaterialRequest() {
        materialRequest = null;
        materialRequestContextKey = null;
        checkedPickupLines.clear();
    }

    public boolean isPickupLineChecked(String checklistKey) {
        return checkedPickupLines.contains(checklistKey);
    }

    public void togglePickupLine(String checklistKey) {
        if (checklistKey == null) return;
        if (!checkedPickupLines.remove(checklistKey) && checkedPickupLines.size() < MAX_CHECKED_LINES) {
            checkedPickupLines.add(checklistKey);
        }
    }
}
