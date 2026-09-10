package se.jimmyeliasson.gzcompanion.advisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdvisorEngineTest {

    @Test
    @DisplayName("An empty context produces exactly one safe, generic suggestion - never zero")
    void noContextProducesSafeGenericGuidance() {
        List<AdvisorSuggestion> result = AdvisorEngine.generate(AdvisorContext.empty());
        assertEquals(1, result.size());
        assertEquals("generic_fallback", result.get(0).id());
    }

    @Test
    @DisplayName("A null context is treated the same as an empty one, never throwing")
    void nullContextIsSafe() {
        List<AdvisorSuggestion> result = AdvisorEngine.generate(null);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("An incomplete guide objective outranks the generic fallback and appears first")
    void guideObjectiveOutranksGeneric() {
        AdvisorContext ctx = new AdvisorContext("Bygg ett skydd", false, null, null, 0, null, 0, false, false);
        List<AdvisorSuggestion> result = AdvisorEngine.generate(ctx);
        assertEquals("guide_continue", result.get(0).id());
        assertTrue(result.get(0).nextStep().contains("Bygg ett skydd"));
    }

    @Test
    @DisplayName("A chosen settlement level range with missing materials produces the settlement-materials suggestion")
    void settlementMissingMaterialsSuggestion() {
        AdvisorContext ctx = new AdvisorContext(null, true, 5, 10, 3, null, 0, false, false);
        List<AdvisorSuggestion> result = AdvisorEngine.generate(ctx);
        assertTrue(result.stream().anyMatch(s -> s.id().equals("settlement_materials")));
    }

    @Test
    @DisplayName("A settlement level range with zero missing materials does NOT trigger the settlement-materials suggestion")
    void settlementNoMissingMaterialsSuppressesSuggestion() {
        AdvisorContext ctx = new AdvisorContext(null, true, 5, 10, 0, null, 0, false, false);
        List<AdvisorSuggestion> result = AdvisorEngine.generate(ctx);
        assertTrue(result.stream().noneMatch(s -> s.id().equals("settlement_materials")));
    }

    @Test
    @DisplayName("An active building plan with incomplete checklist items produces the building-plan suggestion, naming the building")
    void activeBuildingPlanSuggestion() {
        AdvisorContext ctx = new AdvisorContext(null, true, null, null, 0, "Stadskärna", 4, false, false);
        List<AdvisorSuggestion> result = AdvisorEngine.generate(ctx);
        AdvisorSuggestion suggestion = result.stream().filter(s -> s.id().equals("building_plan_continue")).findFirst().orElseThrow();
        assertTrue(suggestion.title().contains("Stadskärna"));
    }

    @Test
    @DisplayName("MarketWatch local notes produce a manual-check reminder with a copy-only verified command")
    void marketWatchManualNoteReminder() {
        AdvisorContext ctx = new AdvisorContext(null, true, null, null, 0, null, 0, true, true);
        List<AdvisorSuggestion> result = AdvisorEngine.generate(ctx);
        AdvisorSuggestion suggestion = result.stream().filter(s -> s.id().equals("marketwatch_manual_check")).findFirst().orElseThrow();
        assertEquals("/marketwatch", suggestion.optionalCommand());
    }

    @Test
    @DisplayName("Ordering is deterministic: the same context always produces the same suggestion order")
    void deterministicOrdering() {
        AdvisorContext ctx = new AdvisorContext("Objective", false, 1, 10, 2, "Bank", 3, true, true);
        List<AdvisorSuggestion> first = AdvisorEngine.generate(ctx);
        List<AdvisorSuggestion> second = AdvisorEngine.generate(ctx);
        List<String> firstIds = first.stream().map(AdvisorSuggestion::id).toList();
        List<String> secondIds = second.stream().map(AdvisorSuggestion::id).toList();
        assertEquals(firstIds, secondIds);
        assertEquals(List.of("guide_continue", "settlement_materials", "building_plan_continue"), firstIds);
    }

    @Test
    @DisplayName("At most MAX_SUGGESTIONS are ever returned, even when every rule matches")
    void resultIsCappedAtMaxSuggestions() {
        AdvisorContext ctx = new AdvisorContext("Objective", false, 1, 10, 2, "Bank", 3, true, true);
        List<AdvisorSuggestion> result = AdvisorEngine.generate(ctx);
        assertEquals(AdvisorEngine.MAX_SUGGESTIONS, result.size());
    }
}
