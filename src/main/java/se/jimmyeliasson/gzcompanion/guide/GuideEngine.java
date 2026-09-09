package se.jimmyeliasson.gzcompanion.guide;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.guide.bridge.GuidePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.guide.bridge.GuideSnapshotProvider;
import se.jimmyeliasson.gzcompanion.guide.condition.GuideConditionEvaluator;
import se.jimmyeliasson.gzcompanion.guide.model.*;
import se.jimmyeliasson.gzcompanion.guide.progress.ContextProgress;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideProgressData;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideProgressStore;
import se.jimmyeliasson.gzcompanion.guide.progress.StepCompletionRecord;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core progression engine orchestrating guide definitions, player state snapshots,
 * condition evaluation, progression inference (supersededBy), and local persistence.
 */
public class GuideEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuideEngine.class);

    private final GuideLoader guideLoader;
    private final GuideProgressStore progressStore;
    private GuideSnapshotProvider snapshotProvider;

    private GuideLoadStatus loadStatus = GuideLoadStatus.UNAVAILABLE;
    private GuideManifest manifest;
    private final Map<String, GuideDefinition> guides = new ConcurrentHashMap<>();
    private String activeGuideId = "minecraft-beginner";

    // Cache of step definitions for fast lookup
    private final Map<String, GuideStep> stepIndex = new ConcurrentHashMap<>();
    private final Map<String, String> stepChapterIndex = new ConcurrentHashMap<>();

    // Cached in-memory progress data
    private GuideProgressData progressData = GuideProgressData.empty();
    private GuidePlayerSnapshot lastSnapshot = GuidePlayerSnapshot.EMPTY;

    // Fingerprint caching to avoid redundant evaluations and disk writes
    private String lastEvaluatedContextKey = null;
    private String lastEvaluatedFingerprint = null;
    private int lastEvaluatedKeyTokensHash = 0;

    public GuideEngine(GuideLoader guideLoader, GuideProgressStore progressStore, GuideSnapshotProvider snapshotProvider) {
        this.guideLoader = guideLoader;
        this.progressStore = progressStore;
        this.snapshotProvider = snapshotProvider;
    }

    public void initialize() {
        GuideLoader.LoadResult result = guideLoader.loadBundled();
        if (result.manifest() != null) {
            this.manifest = result.manifest();
        }

        guides.clear();
        stepIndex.clear();
        stepChapterIndex.clear();

        if (result.isSuccess()) {
            for (GuideDefinition guide : result.guides()) {
                guides.put(guide.id(), guide);
                for (GuideStep step : guide.steps()) {
                    stepIndex.put(step.id(), step);
                    stepChapterIndex.put(step.id(), step.chapterId());
                }
            }

            // Check Minecraft version compatibility against manifest tested versions
            String currentMcVersion = se.jimmyeliasson.gzcompanion.core.CompanionConstants.TARGET_MINECRAFT_VERSION;
            if (manifest != null && manifest.testedMinecraftVersions() != null && !manifest.testedMinecraftVersions().isEmpty()) {
                if (!manifest.testedMinecraftVersions().contains(currentMcVersion)) {
                    this.loadStatus = GuideLoadStatus.INCOMPATIBLE;
                    LOGGER.warn("Guide manifest tested versions {} do not contain current Minecraft version {}",
                            manifest.testedMinecraftVersions(), currentMcVersion);
                    this.progressData = progressStore.load();
                    return;
                }
            }

            // Runtime registry validation of item IDs and tags
            for (GuideDefinition guide : result.guides()) {
                GuideValidator.ValidationResult regResult = GuideValidator.validateRegistry(guide);
                if (!regResult.isValid()) {
                    this.loadStatus = GuideLoadStatus.ERROR;
                    for (String err : regResult.errors()) {
                        LOGGER.error("Guide registry validation error: {}", err);
                    }
                    this.progressData = progressStore.load();
                    return;
                }
            }

            this.loadStatus = GuideLoadStatus.LOADED;
        } else if (!result.errors().isEmpty()) {
            this.loadStatus = GuideLoadStatus.ERROR;
        } else {
            this.loadStatus = GuideLoadStatus.UNAVAILABLE;
        }

        this.progressData = progressStore.load();

        LOGGER.info("GuideEngine initialized with status: {}, {} guides, {} steps.",
                loadStatus, guides.size(), stepIndex.size());

        if (!result.warnings().isEmpty()) {
            for (String w : result.warnings()) {
                LOGGER.warn("Guide warning: {}", w);
            }
        }
        if (!result.errors().isEmpty()) {
            for (String e : result.errors()) {
                LOGGER.error("Guide error: {}", e);
            }
        }

        if (!guides.containsKey(activeGuideId) && !guides.isEmpty()) {
            activeGuideId = guides.keySet().iterator().next();
        }
    }

    public GuideLoadStatus getLoadStatus() {
        return loadStatus;
    }

    public boolean isGuideLoaded() {
        return loadStatus == GuideLoadStatus.LOADED && !guides.isEmpty();
    }

    public GuideManifest getManifest() {
        return manifest;
    }

    public GuideDefinition getActiveGuide() {
        return guides.get(activeGuideId);
    }

    public void setActiveGuide(String guideId) {
        if (guides.containsKey(guideId)) {
            this.activeGuideId = guideId;
        }
    }

    public GuideSnapshotProvider getSnapshotProvider() {
        return snapshotProvider;
    }

    public void setSnapshotProvider(GuideSnapshotProvider provider) {
        this.snapshotProvider = provider;
    }

    public GuidePlayerSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public List<GuideChapter> getChapters() {
        GuideDefinition guide = getActiveGuide();
        return guide != null ? guide.chapters() : Collections.emptyList();
    }

    public List<GuideStep> getStepsForChapter(String chapterId) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null || chapterId == null) return Collections.emptyList();
        List<GuideStep> result = new ArrayList<>();
        for (GuideStep s : guide.steps()) {
            if (chapterId.equals(s.chapterId())) {
                result.add(s);
            }
        }
        return result;
    }

    public GuideStep getStep(String stepId) {
        if (stepId == null) return null;
        return stepIndex.get(stepId);
    }

    public boolean isStepCompleted(GuideContext context, String stepId) {
        if (context == null || stepId == null) return false;
        return progressData.getContext(context.getStorageKey()).isStepCompleted(stepId);
    }

    public Optional<StepCompletionRecord> getStepCompletion(GuideContext context, String stepId) {
        if (context == null || stepId == null) return Optional.empty();
        return Optional.ofNullable(progressData.getContext(context.getStorageKey()).getCompletionRecord(stepId));
    }

    /**
     * Periodic progression evaluation.
     * Evaluates automatic inventory conditions and applies transitive progression inference (fixed-point).
     *
     * @return true if progression state changed and was saved, false otherwise.
     */
    public boolean evaluate(GuideContext context) {
        return evaluate(context, false);
    }

    /**
     * Periodic or forced progression evaluation.
     */
    public boolean evaluate(GuideContext context, boolean force) {
        if (loadStatus != GuideLoadStatus.LOADED) {
            return false;
        }
        if (context == null || snapshotProvider == null) {
            return false;
        }

        GuidePlayerSnapshot snapshot = snapshotProvider.createSnapshot();
        if (snapshot == null) {
            snapshot = GuidePlayerSnapshot.EMPTY;
        }
        this.lastSnapshot = snapshot;

        String ctxKey = context.getStorageKey();
        String currentFingerprint = snapshot.inventoryFingerprint();
        int currentKeysHash = snapshot.keyTokens() != null ? snapshot.keyTokens().hashCode() : 0;

        // Optimization: Skip evaluation if neither context nor inventory/keys changed
        if (!force
                && Objects.equals(ctxKey, lastEvaluatedContextKey)
                && Objects.equals(currentFingerprint, lastEvaluatedFingerprint)
                && currentKeysHash == lastEvaluatedKeyTokensHash) {
            return false;
        }

        this.lastEvaluatedContextKey = ctxKey;
        this.lastEvaluatedFingerprint = currentFingerprint;
        this.lastEvaluatedKeyTokensHash = currentKeysHash;

        GuideDefinition guide = getActiveGuide();
        if (guide == null) return false;

        ContextProgress ctxProg = progressData.getContext(ctxKey);
        Map<String, StepCompletionRecord> completed = new HashMap<>(ctxProg.completedSteps());
        boolean changed = false;

        // Step 1: Automatic condition evaluation for incomplete steps
        for (GuideStep step : guide.steps()) {
            if (completed.containsKey(step.id())) {
                continue;
            }

            if (step.conditions() != null && !step.conditions().isEmpty()) {
                boolean allSatisfied = true;
                boolean hasNonManual = false;

                for (GuideCondition cond : step.conditions()) {
                    if (cond.type() != GuideConditionType.MANUAL) {
                        hasNonManual = true;
                        GuideConditionResult res = GuideConditionEvaluator.evaluate(cond, snapshot);
                        if (!res.satisfied()) {
                            allSatisfied = false;
                            break;
                        }
                    } else {
                        allSatisfied = false; // Manual requires explicit toggle unless superseded
                    }
                }

                if (hasNonManual && allSatisfied) {
                    completed.put(step.id(), new StepCompletionRecord(
                            step.id(),
                            GuideCompletionSource.INVENTORY_EVIDENCE,
                            System.currentTimeMillis()
                    ));
                    changed = true;
                }
            }
        }

        // Step 2: Transitive Progression inference (supersededBy) via fixed-point iteration
        boolean inferenceChanged;
        int maxIterations = guide.steps().size() + 1;
        int iterations = 0;
        do {
            inferenceChanged = false;
            iterations++;
            for (GuideStep step : guide.steps()) {
                if (completed.containsKey(step.id())) {
                    continue;
                }

                if (step.supersededBy() != null && !step.supersededBy().isEmpty()) {
                    for (String laterStepId : step.supersededBy()) {
                        if (completed.containsKey(laterStepId)) {
                            completed.put(step.id(), new StepCompletionRecord(
                                    step.id(),
                                    GuideCompletionSource.PROGRESSION_INFERENCE,
                                    System.currentTimeMillis()
                            ));
                            inferenceChanged = true;
                            changed = true;
                            break;
                        }
                    }
                }
            }
        } while (inferenceChanged && iterations < maxIterations);

        if (changed) {
            Map<String, ContextProgress> updatedContexts = new HashMap<>(progressData.contexts());
            updatedContexts.put(ctxKey, new ContextProgress(ctxProg.selectedStepId(), completed));
            this.progressData = new GuideProgressData(progressData.schemaVersion(), updatedContexts);
            progressStore.save(progressData);
        }

        return changed;
    }

    /**
     * Computes the current state for a given step.
     */
    public GuideStepState getStepState(GuideContext context, String stepId) {
        GuideStep step = stepIndex.get(stepId);
        if (step == null) return GuideStepState.LOCKED;

        if (context != null) {
            Optional<StepCompletionRecord> recordOpt = getStepCompletion(context, stepId);
            if (recordOpt.isPresent()) {
                return switch (recordOpt.get().source()) {
                    case MANUAL -> GuideStepState.COMPLETED_MANUAL;
                    case INVENTORY_EVIDENCE -> GuideStepState.COMPLETED_AUTO;
                    case PROGRESSION_INFERENCE -> GuideStepState.SATISFIED_BY_LATER_PROGRESS;
                };
            }
        }

        // Check prerequisites
        if (step.prerequisites() != null && !step.prerequisites().isEmpty()) {
            for (String prereqId : step.prerequisites()) {
                if (!isStepCompleted(context, prereqId)) {
                    return GuideStepState.LOCKED;
                }
            }
        }

        // Check if this step is the first incomplete available step in its guide
        GuideStep activeStep = getActiveOrNextStep(context);
        if (activeStep != null && activeStep.id().equals(stepId)) {
            return GuideStepState.ACTIVE;
        }

        return GuideStepState.AVAILABLE;
    }

    /**
     * Checks if all required (non-optional) steps in the active guide are completed.
     */
    public boolean isRequiredGuideComplete(GuideContext context) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null || guide.steps().isEmpty()) return false;
        for (GuideStep step : guide.steps()) {
            if (!step.optional() && !isStepCompleted(context, step.id())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the next incomplete required step whose prerequisites are met,
     * or null if all required steps are complete.
     */
    public GuideStep getNextRequiredStep(GuideContext context) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null) return null;

        for (GuideStep step : guide.steps()) {
            if (step.optional()) continue;
            if (isStepCompleted(context, step.id())) continue;

            if (arePrerequisitesMet(context, step)) {
                return step;
            }
        }
        return null;
    }

    /**
     * Returns the next incomplete optional step whose prerequisites are met,
     * or null if no optional steps are available.
     */
    public GuideStep getNextOptionalStep(GuideContext context) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null) return null;

        for (GuideStep step : guide.steps()) {
            if (!step.optional()) continue;
            if (isStepCompleted(context, step.id())) continue;

            if (arePrerequisitesMet(context, step)) {
                return step;
            }
        }
        return null;
    }

    /**
     * Returns the number of incomplete optional steps remaining in the active guide.
     */
    public int getIncompleteOptionalStepsCount(GuideContext context) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null) return 0;
        int count = 0;
        for (GuideStep step : guide.steps()) {
            if (step.optional() && !isStepCompleted(context, step.id())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Finds the next actionable step (first incomplete REQUIRED step whose prerequisites are met).
     * Returns null if all required steps are completed.
     */
    public GuideStep getActiveOrNextStep(GuideContext context) {
        return getNextRequiredStep(context);
    }

    private boolean arePrerequisitesMet(GuideContext context, GuideStep step) {
        if (step.prerequisites() != null) {
            for (String prereqId : step.prerequisites()) {
                if (!isStepCompleted(context, prereqId)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Manually marks a step as completed.
     * Enforces manualCompletionAllowed and prerequisite checks.
     *
     * @return true if completion was recorded, false if rejected.
     */
    public boolean markStepCompleted(GuideContext context, String stepId, boolean manual) {
        if (loadStatus != GuideLoadStatus.LOADED) return false;
        if (context == null || stepId == null) return false;

        GuideStep step = stepIndex.get(stepId);
        if (step == null) {
            LOGGER.warn("Attempted to mark unknown step '{}' as completed", stepId);
            return false;
        }

        if (manual && !step.manualCompletionAllowed()) {
            LOGGER.warn("Step '{}' does not allow manual completion", stepId);
            return false;
        }

        if (getStepState(context, stepId) == GuideStepState.LOCKED) {
            LOGGER.warn("Cannot mark locked step '{}' as completed", stepId);
            return false;
        }

        String ctxKey = context.getStorageKey();
        ContextProgress ctxProg = progressData.getContext(ctxKey);
        Map<String, StepCompletionRecord> completed = new HashMap<>(ctxProg.completedSteps());

        GuideCompletionSource source = manual ? GuideCompletionSource.MANUAL : GuideCompletionSource.INVENTORY_EVIDENCE;
        completed.put(stepId, new StepCompletionRecord(stepId, source, System.currentTimeMillis()));

        Map<String, ContextProgress> updatedContexts = new HashMap<>(progressData.contexts());
        updatedContexts.put(ctxKey, new ContextProgress(ctxProg.selectedStepId(), completed));
        this.progressData = new GuideProgressData(progressData.schemaVersion(), updatedContexts);
        progressStore.save(progressData);

        evaluate(context, true);
        return true;
    }

    public boolean undoStepCompletion(GuideContext context, String stepId) {
        if (loadStatus != GuideLoadStatus.LOADED) return false;
        if (context == null || stepId == null) return false;

        String ctxKey = context.getStorageKey();
        ContextProgress ctxProg = progressData.getContext(ctxKey);
        if (!ctxProg.isStepCompleted(stepId)) {
            return false;
        }

        Map<String, StepCompletionRecord> completed = new HashMap<>(ctxProg.completedSteps());
        completed.remove(stepId);

        Map<String, ContextProgress> updatedContexts = new HashMap<>(progressData.contexts());
        updatedContexts.put(ctxKey, new ContextProgress(ctxProg.selectedStepId(), completed));
        this.progressData = new GuideProgressData(progressData.schemaVersion(), updatedContexts);
        progressStore.save(progressData);

        evaluate(context, true);
        return true;
    }

    public void resetGuideProgress(GuideContext context) {
        if (loadStatus != GuideLoadStatus.LOADED || context == null) return;
        progressStore.resetContext(context);
        this.progressData = progressStore.load();
        this.lastEvaluatedFingerprint = null;
        this.lastEvaluatedContextKey = null;
    }

    public record ProgressSummary(int completedCount, int totalCount, int percent, int completedOptional, int totalOptional) {
        public ProgressSummary(int completedCount, int totalCount, int percent) {
            this(completedCount, totalCount, percent, 0, 0);
        }
    }

    /**
     * Returns overall progress.
     * Note: totalCount and completedCount count REQUIRED steps only.
     * Optional steps (e.g. craft_bed) do NOT prevent 100% completion.
     */
    public ProgressSummary getOverallProgress(GuideContext context) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null) return new ProgressSummary(0, 0, 0);

        int totalRequired = 0;
        int completedRequired = 0;
        int totalOptional = 0;
        int completedOptional = 0;

        for (GuideStep step : guide.steps()) {
            boolean completed = isStepCompleted(context, step.id());
            if (step.optional()) {
                totalOptional++;
                if (completed) completedOptional++;
            } else {
                totalRequired++;
                if (completed) completedRequired++;
            }
        }

        int percent = totalRequired > 0 ? (completedRequired * 100) / totalRequired : 0;
        return new ProgressSummary(completedRequired, totalRequired, percent, completedOptional, totalOptional);
    }

    /**
     * Returns chapter progress.
     * Counts REQUIRED steps for total and percentage.
     */
    public ProgressSummary getChapterProgress(GuideContext context, String chapterId) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null || chapterId == null) return new ProgressSummary(0, 0, 0);

        int totalRequired = 0;
        int completedRequired = 0;
        int totalOptional = 0;
        int completedOptional = 0;

        for (GuideStep step : guide.steps()) {
            if (chapterId.equals(step.chapterId())) {
                boolean completed = isStepCompleted(context, step.id());
                if (step.optional()) {
                    totalOptional++;
                    if (completed) completedOptional++;
                } else {
                    totalRequired++;
                    if (completed) completedRequired++;
                }
            }
        }

        int percent = totalRequired > 0 ? (completedRequired * 100) / totalRequired : 0;
        return new ProgressSummary(completedRequired, totalRequired, percent, completedOptional, totalOptional);
    }

    /**
     * Resolves dynamic keybinding tokens such as {key.inventory} or {key.forward}.
     */
    public String resolveTokens(String text) {
        if (text == null || !text.contains("{key.")) {
            return text;
        }

        Map<String, String> keyTokens = lastSnapshot != null ? lastSnapshot.keyTokens() : Collections.emptyMap();

        String result = text;
        Map<String, String> defaults = Map.ofEntries(
                Map.entry("key.inventory", "E"),
                Map.entry("key.forward", "W"),
                Map.entry("key.left", "A"),
                Map.entry("key.back", "S"),
                Map.entry("key.right", "D"),
                Map.entry("key.jump", "Mellanslag"),
                Map.entry("key.sneak", "Skift"),
                Map.entry("key.sprint", "Ctrl"),
                Map.entry("key.drop", "Q"),
                Map.entry("key.use", "Högerklick"),
                Map.entry("key.attack", "Vänsterklick"),
                Map.entry("key.swapOffhand", "F")
        );

        if (keyTokens != null) {
            for (Map.Entry<String, String> entry : keyTokens.entrySet()) {
                String token = "{" + entry.getKey() + "}";
                if (result.contains(token)) {
                    result = result.replace(token, "[" + entry.getValue() + "]");
                }
            }
        }

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            if (result.contains(token)) {
                result = result.replace(token, "[" + entry.getValue() + "]");
            }
        }

        return result;
    }
}
