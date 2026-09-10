package se.jimmyeliasson.gzcompanion.building;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlan;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlanData;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlanLoadResult;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlanStatus;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingPlanStore;
import se.jimmyeliasson.gzcompanion.building.storage.BuildingRequirementKey;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingPlanManagerTest {

    private static final class InMemoryStore implements BuildingPlanStore {
        private BuildingPlanData data = BuildingPlanData.empty();
        private BuildingPlanLoadResult.Outcome loadOutcome = BuildingPlanLoadResult.Outcome.NOT_FOUND;

        @Override
        public BuildingPlanLoadResult load() {
            return new BuildingPlanLoadResult(loadOutcome, data);
        }

        @Override
        public void save(BuildingPlanData data) {
            this.data = data;
        }
    }

    @Test
    @DisplayName("initialize() with no existing file leaves the manager LOADED")
    void initializeWithNoFileIsLoaded() {
        BuildingPlanManager manager = new BuildingPlanManager(new InMemoryStore());
        manager.initialize();
        assertEquals(BuildingPlanStatus.LOADED, manager.getStatus());
    }

    @Test
    @DisplayName("Mutations before initialize() are refused")
    void mutationsBeforeInitializeAreRefused() {
        BuildingPlanManager manager = new BuildingPlanManager(new InMemoryStore());
        assertNull(manager.createPlan("ctx", "stadskarna", "Plan", 5, 5, 5, 0L));
        assertFalse(manager.deletePlan("ctx", "p1"));
    }

    @Test
    @DisplayName("createPlan then getPlansForBuilding returns the created plan with the requested dimensions")
    void createPlanAndRetrieve() {
        BuildingPlanManager manager = new BuildingPlanManager(new InMemoryStore());
        manager.initialize();

        String id = manager.createPlan("server:gz", "stadskarna", "Min plan", 7, 7, 5, 1000L);
        assertNotNull(id);

        List<BuildingPlan> plans = manager.getPlansForBuilding("server:gz", "stadskarna");
        assertEquals(1, plans.size());
        assertEquals(7, plans.get(0).width());
        assertEquals("Min plan", plans.get(0).planName());
    }

    @Test
    @DisplayName("renamePlan, setDimensions, and toggleRequirement all mutate the correct plan in place")
    void mutatePlanOperations() {
        BuildingPlanManager manager = new BuildingPlanManager(new InMemoryStore());
        manager.initialize();
        String id = manager.createPlan("ctx", "bank", "Plan 1", 5, 5, 4, 0L);

        assertTrue(manager.renamePlan("ctx", id, "Nytt namn"));
        assertTrue(manager.setDimensions("ctx", id, 10, 10, 6));
        assertTrue(manager.toggleRequirement("ctx", id, BuildingRequirementKey.WALLS));

        BuildingPlan plan = manager.getPlansForBuilding("ctx", "bank").get(0);
        assertEquals("Nytt namn", plan.planName());
        assertEquals(10, plan.width());
        assertTrue(plan.isCompleted(BuildingRequirementKey.WALLS));
    }

    @Test
    @DisplayName("deletePlan removes exactly the targeted plan and leaves others in the same context untouched")
    void deletePlanRemovesOnlyTarget() {
        BuildingPlanManager manager = new BuildingPlanManager(new InMemoryStore());
        manager.initialize();
        String idA = manager.createPlan("ctx", "stadskarna", "A", 5, 5, 5, 0L);
        String idB = manager.createPlan("ctx", "stadskarna", "B", 6, 6, 6, 0L);

        assertTrue(manager.deletePlan("ctx", idA));
        List<BuildingPlan> remaining = manager.getPlansForBuilding("ctx", "stadskarna");
        assertEquals(1, remaining.size());
        assertEquals(idB, remaining.get(0).id());
    }

    @Test
    @DisplayName("clearContext deletes every plan for one context but leaves other contexts untouched")
    void clearContextIsolatedToOneContext() {
        BuildingPlanManager manager = new BuildingPlanManager(new InMemoryStore());
        manager.initialize();
        manager.createPlan("server:a", "stadskarna", "A", 5, 5, 5, 0L);
        manager.createPlan("singleplayer:b", "bank", "B", 6, 6, 6, 0L);

        assertTrue(manager.clearContext("server:a"));
        assertTrue(manager.getPlans("server:a").isEmpty());
        assertEquals(1, manager.getPlans("singleplayer:b").size(), "Clearing one context must never affect another.");
    }

    @Test
    @DisplayName("Plans in different contexts never leak into each other")
    void contextIsolation() {
        BuildingPlanManager manager = new BuildingPlanManager(new InMemoryStore());
        manager.initialize();
        manager.createPlan("server:a", "stadskarna", "A", 5, 5, 5, 0L);
        manager.createPlan("singleplayer:b", "stadskarna", "B", 6, 6, 6, 0L);

        assertEquals(1, manager.getPlans("server:a").size());
        assertEquals(1, manager.getPlans("singleplayer:b").size());
        assertTrue(manager.getPlans("server:other").isEmpty());
    }
}
