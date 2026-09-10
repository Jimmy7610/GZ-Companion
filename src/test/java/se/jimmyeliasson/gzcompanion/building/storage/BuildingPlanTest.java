package se.jimmyeliasson.gzcompanion.building.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class BuildingPlanTest {

    private BuildingPlan basicPlan() {
        return new BuildingPlan("p1", "stadskarna", "Min plan", 7, 7, 5, EnumSet.noneOf(BuildingRequirementKey.class), 1000L);
    }

    @Test
    @DisplayName("A blank plan name falls back to a placeholder rather than storing an empty string")
    void blankNameFallsBack() {
        BuildingPlan plan = new BuildingPlan("p1", "stadskarna", "   ", 5, 5, 5, null, 0L);
        assertEquals("Namnlös plan", plan.planName());
    }

    @Test
    @DisplayName("Negative dimensions are clamped to zero rather than stored as negative")
    void negativeDimensionsClamped() {
        BuildingPlan plan = new BuildingPlan("p1", "stadskarna", "Plan", -3, -1, -5, null, 0L);
        assertEquals(0, plan.width());
        assertEquals(0, plan.depth());
        assertEquals(0, plan.height());
    }

    @Test
    @DisplayName("withToggledRequirement flips membership: absent becomes present, present becomes absent")
    void toggleRequirementFlipsMembership() {
        BuildingPlan plan = basicPlan();
        assertFalse(plan.isCompleted(BuildingRequirementKey.LICENSE));

        BuildingPlan toggled = plan.withToggledRequirement(BuildingRequirementKey.LICENSE);
        assertTrue(toggled.isCompleted(BuildingRequirementKey.LICENSE));

        BuildingPlan toggledBack = toggled.withToggledRequirement(BuildingRequirementKey.LICENSE);
        assertFalse(toggledBack.isCompleted(BuildingRequirementKey.LICENSE));
    }

    @Test
    @DisplayName("withRenamed and withDimensions are copy-on-write and don't affect the original")
    void copyOnWriteMutations() {
        BuildingPlan plan = basicPlan();
        BuildingPlan renamed = plan.withRenamed("Nytt namn");
        BuildingPlan resized = plan.withDimensions(10, 10, 8);

        assertEquals("Min plan", plan.planName());
        assertEquals("Nytt namn", renamed.planName());
        assertEquals(7, plan.width());
        assertEquals(10, resized.width());
    }
}
