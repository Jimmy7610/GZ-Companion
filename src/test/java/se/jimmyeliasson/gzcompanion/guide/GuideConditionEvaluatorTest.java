package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.guide.bridge.GuidePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.guide.condition.GuideConditionEvaluator;
import se.jimmyeliasson.gzcompanion.guide.model.GuideCondition;
import se.jimmyeliasson.gzcompanion.guide.model.GuideConditionResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GuideConditionEvaluatorTest {

    @Test
    @DisplayName("Should evaluate HAS_ITEM condition accurately")
    void testHasItemCondition() {
        GuideCondition cond = GuideCondition.hasItem("minecraft:oak_log", 4, "4 logs");

        GuidePlayerSnapshot snapFail = new GuidePlayerSnapshot(
                Map.of("minecraft:oak_log", 3), Map.of(), false, "f1", Map.of()
        );
        GuideConditionResult resFail = GuideConditionEvaluator.evaluate(cond, snapFail);
        assertFalse(resFail.satisfied());
        assertEquals(3, resFail.currentCount());
        assertEquals(4, resFail.requiredCount());

        GuidePlayerSnapshot snapPass = new GuidePlayerSnapshot(
                Map.of("minecraft:oak_log", 10), Map.of(), false, "f2", Map.of()
        );
        GuideConditionResult resPass = GuideConditionEvaluator.evaluate(cond, snapPass);
        assertTrue(resPass.satisfied());
        assertEquals(10, resPass.currentCount());
    }

    @Test
    @DisplayName("Should evaluate HAS_ITEM_TAG condition accurately")
    void testHasItemTagCondition() {
        GuideCondition cond = GuideCondition.hasTag("minecraft:logs", 4, "4 logs of any type");

        GuidePlayerSnapshot snapFail = new GuidePlayerSnapshot(
                Map.of(), Map.of("minecraft:logs", 2), false, "f1", Map.of()
        );
        assertFalse(GuideConditionEvaluator.evaluate(cond, snapFail).satisfied());

        GuidePlayerSnapshot snapPass = new GuidePlayerSnapshot(
                Map.of(), Map.of("minecraft:logs", 5), false, "f2", Map.of()
        );
        assertTrue(GuideConditionEvaluator.evaluate(cond, snapPass).satisfied());
    }

    @Test
    @DisplayName("Should evaluate HAS_EDIBLE_ITEM condition accurately")
    void testHasEdibleItemCondition() {
        GuideCondition cond = GuideCondition.hasEdible(1, "Any edible item");

        GuidePlayerSnapshot snapNoFood = new GuidePlayerSnapshot(
                Map.of(), Map.of(), false, "f1", Map.of()
        );
        assertFalse(GuideConditionEvaluator.evaluate(cond, snapNoFood).satisfied());

        GuidePlayerSnapshot snapFood = new GuidePlayerSnapshot(
                Map.of(), Map.of(), true, "f2", Map.of()
        );
        assertTrue(GuideConditionEvaluator.evaluate(cond, snapFood).satisfied());
    }

    @Test
    @DisplayName("Should evaluate ALL_OF and ANY_OF compound conditions")
    void testCompoundConditions() {
        GuideCondition cond1 = GuideCondition.hasItem("minecraft:iron_ingot", 3, "3 Iron");
        GuideCondition cond2 = GuideCondition.hasItem("minecraft:stick", 2, "2 Sticks");
        GuideCondition allOf = GuideCondition.allOf(List.of(cond1, cond2), "Iron Pickaxe materials");

        GuidePlayerSnapshot snapOnlyIron = new GuidePlayerSnapshot(
                Map.of("minecraft:iron_ingot", 5, "minecraft:stick", 0), Map.of(), false, "f1", Map.of()
        );
        assertFalse(GuideConditionEvaluator.evaluate(allOf, snapOnlyIron).satisfied());

        GuidePlayerSnapshot snapBoth = new GuidePlayerSnapshot(
                Map.of("minecraft:iron_ingot", 5, "minecraft:stick", 4), Map.of(), false, "f2", Map.of()
        );
        assertTrue(GuideConditionEvaluator.evaluate(allOf, snapBoth).satisfied());

        GuideCondition anyOf = GuideCondition.anyOf(List.of(
                GuideCondition.hasItem("minecraft:coal", 1, "Coal"),
                GuideCondition.hasItem("minecraft:charcoal", 1, "Charcoal")
        ), "Any fuel");

        GuidePlayerSnapshot snapCharcoal = new GuidePlayerSnapshot(
                Map.of("minecraft:charcoal", 2), Map.of(), false, "f3", Map.of()
        );
        assertTrue(GuideConditionEvaluator.evaluate(anyOf, snapCharcoal).satisfied());
    }
}
