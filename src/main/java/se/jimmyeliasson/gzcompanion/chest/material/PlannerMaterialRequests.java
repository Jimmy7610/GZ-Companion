package se.jimmyeliasson.gzcompanion.chest.material;

import se.jimmyeliasson.gzcompanion.knowledge.building.BuildingRequirement;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.ItemRequirement;
import se.jimmyeliasson.gzcompanion.settings.CompanionSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds Kistor material requests from the planners' own requirement data (Settlement level
 * range, Byggplaner building). Pure translation - no chest data is read here.
 *
 * <p>Gate: "Hitta material i kistor" is only offered while the player's existing
 * {@code useLastKnownChestDataInPlanners} setting is on; with it off, planners never silently use
 * chest data.
 */
public final class PlannerMaterialRequests {
    public static final String SETTLEMENT_SOURCE = "Settlement";
    public static final String BUILDING_SOURCE = "Byggplaner";

    private PlannerMaterialRequests() {}

    public static boolean isChestLookupEnabled(CompanionSettings settings) {
        return settings != null && settings.useLastKnownChestDataInPlanners();
    }

    public static ChestMaterialRequest fromSettlement(int fromLevel, int toLevel, List<ItemRequirement> requirements) {
        List<MaterialNeed> needs = new ArrayList<>();
        if (requirements != null) {
            for (ItemRequirement req : requirements) {
                if (req == null) continue;
                String name = req.displayName() + (req.distinctVariantsRequired() != null ? " (" + req.distinctVariantsRequired() + " olika)" : "");
                needs.add(new MaterialNeed(req.hasConcreteItem() ? req.itemId() : null, name, req.count()));
            }
        }
        return new ChestMaterialRequest("Settlement nivå " + fromLevel + " → " + toLevel, SETTLEMENT_SOURCE, needs);
    }

    public static ChestMaterialRequest fromBuilding(String buildingName, List<BuildingRequirement> requirements) {
        List<MaterialNeed> needs = new ArrayList<>();
        if (requirements != null) {
            for (BuildingRequirement req : requirements) {
                if (req == null) continue;
                needs.add(new MaterialNeed(req.hasConcreteItem() ? req.itemId() : null, req.displayName(), req.count()));
            }
        }
        return new ChestMaterialRequest(buildingName != null ? buildingName : "Byggnad", BUILDING_SOURCE, needs);
    }
}
