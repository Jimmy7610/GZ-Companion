package se.jimmyeliasson.gzcompanion.chest.material;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.chest.index.ChestItemIndex;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StorageMetadata;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.knowledge.building.BuildingRequirement;
import se.jimmyeliasson.gzcompanion.knowledge.settlement.ItemRequirement;
import se.jimmyeliasson.gzcompanion.settings.CompanionSettings;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChestMaterialTest {
    static final String CTX = "p@@server:gz";

    static StoredContainer storage(int x, String label, long openedAt, ChestSlotEntry... slots) {
        return new StoredContainer(new StoredContainerId(CTX, "minecraft:overworld", new StoragePosition(x, 64, 0), StorageKind.CHEST),
                label, null, StorageShape.SINGLE, openedAt, List.of(slots), StorageMetadata.EMPTY, null);
    }

    static ChestSlotEntry s(int i, String id, int c) {
        return new ChestSlotEntry(i, id, c);
    }

    /** Materiallager 812 stone + 256 iron; Gruvbas 512 stone + 32 oak; Gamla huset 162 stone + 96 oak. */
    static ChestItemIndex sampleIndex() {
        return ChestItemIndex.build(CTX, List.of(
                storage(1, "Materiallager", 3000, s(0, "minecraft:stone", 512), s(1, "minecraft:stone", 300), s(2, "minecraft:iron_ingot", 128)),
                storage(2, "Gruvbas", 2000, s(0, "minecraft:stone", 512), s(1, "minecraft:oak_log", 32)),
                storage(3, "Gamla huset", 1000, s(0, "minecraft:stone", 162), s(1, "minecraft:oak_log", 96), s(2, "minecraft:iron_ingot", 64))), id -> id);
    }

    @Test
    @DisplayName("Availability: enough, not enough (estimated missing), spread across several storage locations")
    void availability() {
        ChestMaterialRequest request = new ChestMaterialRequest("Mål", "Settlement", List.of(
                new MaterialNeed("minecraft:stone", "Stone", 1200),
                new MaterialNeed("minecraft:iron_ingot", "Iron Ingot", 256),
                new MaterialNeed("minecraft:emerald", "Emerald", 5)));
        List<MaterialAvailability> rows = ChestMaterialAvailability.compute(request, sampleIndex());

        MaterialAvailability stone = rows.get(0);
        assertEquals(1200, stone.need().needed());
        assertEquals(1486, stone.lastKnownTotal());
        assertEquals(0, stone.estimatedMissing());
        assertTrue(stone.isCoveredByEstimate());
        assertEquals(List.of("Materiallager", "Gruvbas", "Gamla huset"), stone.locations().stream().map(l -> l.title()).toList());

        MaterialAvailability iron = rows.get(1);
        assertEquals(192, iron.lastKnownTotal());
        assertEquals(64, iron.estimatedMissing());

        MaterialAvailability emerald = rows.get(2);
        assertEquals(0, emerald.lastKnownTotal());
        assertEquals(5, emerald.estimatedMissing());
        assertTrue(emerald.locations().isEmpty());

        assertEquals(1, ChestMaterialAvailability.coveredCount(rows));
    }

    @Test
    @DisplayName("Category requirements without a concrete item are never guessed against chest data")
    void untrackableNeeds() {
        ChestMaterialRequest request = new ChestMaterialRequest("Mål", "Settlement", List.of(new MaterialNeed(null, "Valfri ull", 16)));
        MaterialAvailability row = ChestMaterialAvailability.compute(request, sampleIndex()).get(0);
        assertFalse(row.isTrackable());
        assertEquals(0, row.lastKnownTotal());
        assertEquals(16, row.estimatedMissing());

        PickupPlan plan = ChestPickupPlanner.plan(request, sampleIndex());
        assertTrue(plan.groups().isEmpty());
        assertEquals(1, plan.untrackable().size());
        assertTrue(plan.shortages().isEmpty());
    }

    @Test
    @DisplayName("Duplicate needs for the same item are merged; zero needs are dropped")
    void mergesDuplicates() {
        ChestMaterialRequest request = new ChestMaterialRequest("x", "y", List.of(
                new MaterialNeed("minecraft:stone", "Stone", 100), new MaterialNeed("minecraft:stone", "Stone", 50),
                new MaterialNeed("minecraft:dirt", "Dirt", 0)));
        assertEquals(List.of(new MaterialNeed("minecraft:stone", "Stone", 150)), request.needs());
    }

    @Test
    @DisplayName("Pickup allocation: largest last-known stock first, then freshest, spread over several storage locations")
    void pickupAllocationDeterministic() {
        ChestMaterialRequest request = new ChestMaterialRequest("Mål", "Byggplaner", List.of(
                new MaterialNeed("minecraft:stone", "Stone", 1000),
                new MaterialNeed("minecraft:oak_log", "Oak Log", 100)));
        PickupPlan plan = ChestPickupPlanner.plan(request, sampleIndex());

        // Stone: Materiallager has 812 (largest) -> take 812; Gruvbas 512 -> take the remaining 188.
        // Oak: Gamla huset 96 (largest) -> 96; Gruvbas 32 -> the remaining 4.
        assertEquals(3, plan.groups().size());
        PickupPlan.Group first = plan.groups().get(0);
        assertEquals("Materiallager", first.title());
        assertEquals(List.of(new PickupPlan.Line("minecraft:stone", "Stone", 812)), first.lines());

        PickupPlan.Group gruvbas = plan.groups().stream().filter(g -> g.title().equals("Gruvbas")).findFirst().orElseThrow();
        assertEquals(List.of(new PickupPlan.Line("minecraft:stone", "Stone", 188), new PickupPlan.Line("minecraft:oak_log", "Oak Log", 4)),
                gruvbas.lines());
        PickupPlan.Group gamla = plan.groups().stream().filter(g -> g.title().equals("Gamla huset")).findFirst().orElseThrow();
        assertEquals(List.of(new PickupPlan.Line("minecraft:oak_log", "Oak Log", 96)), gamla.lines());
        assertTrue(plan.shortages().isEmpty());

        assertEquals(plan, ChestPickupPlanner.plan(request, sampleIndex()), "Same input -> identical plan");
    }

    @Test
    @DisplayName("Pickup allocation reports the remaining shortage when last-known stock is insufficient")
    void pickupShortage() {
        ChestMaterialRequest request = new ChestMaterialRequest("Mål", "Settlement", List.of(
                new MaterialNeed("minecraft:iron_ingot", "Iron Ingot", 256),
                new MaterialNeed("minecraft:emerald", "Emerald", 3)));
        PickupPlan plan = ChestPickupPlanner.plan(request, sampleIndex());
        int ironPicked = plan.groups().stream().flatMap(g -> g.lines().stream()).filter(l -> l.itemId().equals("minecraft:iron_ingot"))
                .mapToInt(PickupPlan.Line::amount).sum();
        assertEquals(192, ironPicked);
        assertEquals(List.of(new PickupPlan.Shortage("minecraft:iron_ingot", "Iron Ingot", 64), new PickupPlan.Shortage("minecraft:emerald", "Emerald", 3)),
                plan.shortages());
    }

    @Test
    @DisplayName("Ties in stock are broken by the most recently opened storage")
    void tieBreakFreshest() {
        ChestItemIndex index = ChestItemIndex.build(CTX, List.of(
                storage(1, "Äldre", 1000, s(0, "minecraft:stone", 64)),
                storage(2, "Nyare", 5000, s(0, "minecraft:stone", 64))), id -> id);
        PickupPlan plan = ChestPickupPlanner.plan(new ChestMaterialRequest("x", "y", List.of(new MaterialNeed("minecraft:stone", "Stone", 10))), index);
        assertEquals("Nyare", plan.groups().get(0).title());
    }

    @Test
    @DisplayName("Planner requests: the chest lookup is gated on the existing planner setting")
    void plannerGate() {
        CompanionSettings on = CompanionSettings.defaults();
        CompanionSettings off = new CompanionSettings(true, true, true, true, false, List.of());
        assertTrue(PlannerMaterialRequests.isChestLookupEnabled(on));
        assertFalse(PlannerMaterialRequests.isChestLookupEnabled(off));
        assertFalse(PlannerMaterialRequests.isChestLookupEnabled(null));
    }

    @Test
    @DisplayName("Planner requests translate Settlement and Building requirements, keeping category needs untrackable")
    void plannerTranslation() {
        ChestMaterialRequest settlement = PlannerMaterialRequests.fromSettlement(4, 6, List.of(
                new ItemRequirement("minecraft:stone", "Stone", 1200, null),
                new ItemRequirement(null, "Wool", 16, 3)));
        assertEquals("Settlement nivå 4 → 6", settlement.title());
        assertEquals("minecraft:stone", settlement.needs().get(0).itemId());
        assertFalse(settlement.needs().get(1).isTrackable());
        assertEquals("Wool (3 olika)", settlement.needs().get(1).displayName());

        ChestMaterialRequest building = PlannerMaterialRequests.fromBuilding("Smedja", List.of(
                new BuildingRequirement("minecraft:anvil", "Anvil", 2, null)));
        assertEquals("Smedja", building.title());
        assertEquals(PlannerMaterialRequests.BUILDING_SOURCE, building.sourceLabel());
        assertEquals(2, building.needs().get(0).needed());
    }
}
