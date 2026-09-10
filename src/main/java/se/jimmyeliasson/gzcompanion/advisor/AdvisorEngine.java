package se.jimmyeliasson.gzcompanion.advisor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure, stateless rule evaluator turning an {@link AdvisorContext} into a small ranked list of
 * real, data-driven suggestions - never a hardcoded fake tip. Contains no Minecraft API types and
 * no I/O; every rule below only reads the plain data already captured in the context.
 *
 * <p>Ranking is deterministic: each rule below carries a fixed priority (lower = shown first),
 * and rules are evaluated in a fixed order regardless of input, so the same context always
 * produces the same ordering. At most {@link #MAX_SUGGESTIONS} suggestions are returned. If no
 * rule matches, a single safe, generic suggestion is returned instead of an empty list.
 */
public final class AdvisorEngine {
    public static final int MAX_SUGGESTIONS = 3;

    private AdvisorEngine() {}

    public static List<AdvisorSuggestion> generate(AdvisorContext ctx) {
        if (ctx == null) ctx = AdvisorContext.empty();
        List<AdvisorSuggestion> result = new ArrayList<>();

        if (!ctx.guideComplete() && ctx.guideObjectiveTitle() != null && !ctx.guideObjectiveTitle().isBlank()) {
            result.add(new AdvisorSuggestion("guide_continue", "Fortsätt guiden",
                    "Du har inte slutfört nybörjarguiden än.",
                    "Öppna Guide och fortsätt med: " + ctx.guideObjectiveTitle()));
        }

        if (ctx.settlementCurrentLevel() != null && ctx.settlementTargetLevel() != null
                && ctx.settlementTargetLevel() > ctx.settlementCurrentLevel() && ctx.settlementMissingMaterialsCount() > 0) {
            result.add(new AdvisorSuggestion("settlement_materials", "Samla material för nästa settlementnivå",
                    "Din valda mål-nivå kräver " + ctx.settlementMissingMaterialsCount() + " materialtyp(er) du inte har markerat som insamlade än.",
                    "Öppna Settlement -> Material och se vad som saknas."));
        }

        if (ctx.activeBuildingPlanDisplayName() != null && ctx.buildingPlanIncompleteChecklistCount() > 0) {
            result.add(new AdvisorSuggestion("building_plan_continue", "Bygg färdigt " + ctx.activeBuildingPlanDisplayName(),
                    "Du har en lokal byggplan med " + ctx.buildingPlanIncompleteChecklistCount() + " kvarstående checklistpunkt(er).",
                    "Öppna Byggplaner och fortsätt din checklista."));
        }

        if (ctx.marketWatchHasNotes()) {
            result.add(new AdvisorSuggestion("marketwatch_manual_check", "Kontrollera MarketWatch manuellt",
                    "Du bevakar resurser i MarketWatch lokalt - GameZone uppdaterar den riktiga marknaden live, inte Companion.",
                    "Öppna MarketWatch och jämför dina anteckningar.", "/marketwatch"));
        }

        if (result.isEmpty()) {
            result.add(new AdvisorSuggestion("generic_fallback", "Utforska GZ Companion",
                    "Inget specifikt hittades just nu baserat på din lokala data.",
                    "Öppna Guide, Settlement, Byggplaner eller MarketWatch för att komma igång."));
        }

        result.sort(Comparator.comparingInt(s -> priorityOf(s.id())));
        return result.size() > MAX_SUGGESTIONS ? List.copyOf(result.subList(0, MAX_SUGGESTIONS)) : List.copyOf(result);
    }

    private static int priorityOf(String suggestionId) {
        return switch (suggestionId) {
            case "guide_continue" -> 10;
            case "settlement_materials" -> 20;
            case "building_plan_continue" -> 30;
            case "marketwatch_manual_check" -> 40;
            default -> 100;
        };
    }
}
