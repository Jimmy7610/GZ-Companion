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

    private GuideManifest manifest;
    private final Map<String, GuideDefinition> guides = new ConcurrentHashMap<>();
    private String activeGuideId = "minecraft-beginner";

    // Cache of step definitions for fast lookup
    private final Map<String, GuideStep> stepIndex = new ConcurrentHashMap<>();
    private final Map<String, String> stepChapterIndex = new ConcurrentHashMap<>();

    // Cached in-memory progress data
    private GuideProgressData progressData = GuideProgressData.empty();
    private GuidePlayerSnapshot lastSnapshot = GuidePlayerSnapshot.EMPTY;

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

        for (GuideDefinition guide : result.guides()) {
            guides.put(guide.id(), guide);
            for (GuideStep step : guide.steps()) {
                stepIndex.put(step.id(), step);
                stepChapterIndex.put(step.id(), step.chapterId());
            }
        }

        this.progressData = progressStore.load();

        LOGGER.info("GuideEngine initialized with {} guides and {} steps.", guides.size(), stepIndex.size());
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

    public void setSnapshotProvider(GuideSnapshotProvider provider) {
        this.snapshotProvider = provider;
    }

    public GuideSnapshotProvider getSnapshotProvider() {
        return snapshotProvider;
    }

    public GuideManifest getManifest() {
        return manifest;
    }

    public List<GuideDefinition> getGuides() {
        return new ArrayList<>(guides.values());
    }

    public GuideDefinition getActiveGuide() {
        return guides.get(activeGuideId);
    }

    public void setActiveGuideId(String guideId) {
        if (guides.containsKey(guideId)) {
            this.activeGuideId = guideId;
        }
    }

    public GuideStep getStep(String stepId) {
        return stepIndex.get(stepId);
    }

    public GuidePlayerSnapshot getLastSnapshot() {
        return lastSnapshot;
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
     * Evaluates all steps for the current guide and context against player snapshot.
     */
    public synchronized void evaluate(GuideContext context) {
        if (context == null) return;

        GuidePlayerSnapshot snapshot = snapshotProvider != null ? snapshotProvider.createSnapshot() : GuidePlayerSnapshot.EMPTY;
        this.lastSnapshot = snapshot;

        GuideDefinition guide = getActiveGuide();
        if (guide == null) return;

        String ctxKey = context.getStorageKey();
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

        // Step 2: Progression inference (supersededBy)
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
                        changed = true;
                        break;
                    }
                }
            }
        }

        if (changed) {
            Map<String, ContextProgress> updatedContexts = new HashMap<>(progressData.contexts());
            updatedContexts.put(ctxKey, new ContextProgress(ctxProg.selectedStepId(), completed));
            this.progressData = new GuideProgressData(progressData.schemaVersion(), updatedContexts);
            progressStore.save(progressData);
        }
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
     * Finds the next actionable step (the first incomplete step whose prerequisites are met).
     */
    public GuideStep getActiveOrNextStep(GuideContext context) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null) return null;

        for (GuideStep step : guide.steps()) {
            if (isStepCompleted(context, step.id())) {
                continue;
            }

            // Check prerequisites
            boolean prereqsMet = true;
            if (step.prerequisites() != null) {
                for (String prereqId : step.prerequisites()) {
                    if (!isStepCompleted(context, prereqId)) {
                        prereqsMet = false;
                        break;
                    }
                }
            }

            if (prereqsMet) {
                return step;
            }
        }
        return null;
    }

    public void markStepCompleted(GuideContext context, String stepId, boolean manual) {
        if (context == null || stepId == null) return;
        String ctxKey = context.getStorageKey();
        ContextProgress ctxProg = progressData.getContext(ctxKey);
        Map<String, StepCompletionRecord> completed = new HashMap<>(ctxProg.completedSteps());

        GuideCompletionSource source = manual ? GuideCompletionSource.MANUAL : GuideCompletionSource.INVENTORY_EVIDENCE;
        completed.put(stepId, new StepCompletionRecord(stepId, source, System.currentTimeMillis()));

        Map<String, ContextProgress> updatedContexts = new HashMap<>(progressData.contexts());
        updatedContexts.put(ctxKey, new ContextProgress(ctxProg.selectedStepId(), completed));
        this.progressData = new GuideProgressData(progressData.schemaVersion(), updatedContexts);
        progressStore.save(progressData);

        evaluate(context);
    }

    public void undoStepCompletion(GuideContext context, String stepId) {
        if (context == null || stepId == null) return;
        String ctxKey = context.getStorageKey();
        ContextProgress ctxProg = progressData.getContext(ctxKey);
        Map<String, StepCompletionRecord> completed = new HashMap<>(ctxProg.completedSteps());
        completed.remove(stepId);

        Map<String, ContextProgress> updatedContexts = new HashMap<>(progressData.contexts());
        updatedContexts.put(ctxKey, new ContextProgress(ctxProg.selectedStepId(), completed));
        this.progressData = new GuideProgressData(progressData.schemaVersion(), updatedContexts);
        progressStore.save(progressData);
    }

    public void resetGuideProgress(GuideContext context) {
        if (context == null) return;
        progressStore.resetContext(context);
        this.progressData = progressStore.load();
    }

    public record ProgressSummary(int completedCount, int totalCount, int percent) {
    }

    public ProgressSummary getOverallProgress(GuideContext context) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null) return new ProgressSummary(0, 0, 0);

        int total = guide.steps().size();
        int completed = 0;

        for (GuideStep step : guide.steps()) {
            if (isStepCompleted(context, step.id())) {
                completed++;
            }
        }

        int percent = total > 0 ? (completed * 100) / total : 0;
        return new ProgressSummary(completed, total, percent);
    }

    public ProgressSummary getChapterProgress(GuideContext context, String chapterId) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null || chapterId == null) return new ProgressSummary(0, 0, 0);

        int total = 0;
        int completed = 0;

        for (GuideStep step : guide.steps()) {
            if (chapterId.equals(step.chapterId())) {
                total++;
                if (isStepCompleted(context, step.id())) {
                    completed++;
                }
            }
        }

        int percent = total > 0 ? (completed * 100) / total : 0;
        return new ProgressSummary(completed, total, percent);
    }

    public List<GuideStep> getStepsForChapter(String chapterId) {
        GuideDefinition guide = getActiveGuide();
        if (guide == null || chapterId == null) return Collections.emptyList();

        List<GuideStep> chapterSteps = new ArrayList<>();
        for (GuideStep step : guide.steps()) {
            if (chapterId.equals(step.chapterId())) {
                chapterSteps.add(step);
            }
        }
        return chapterSteps;
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
                Map.entry("key.attack", "Vänsterklick")
        );

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            if (result.contains(token)) {
                String val = keyTokens.getOrDefault(entry.getKey(), entry.getValue());
                result = result.replace(token, "[" + val + "]");
            }
        }

        return result;
    }
}
