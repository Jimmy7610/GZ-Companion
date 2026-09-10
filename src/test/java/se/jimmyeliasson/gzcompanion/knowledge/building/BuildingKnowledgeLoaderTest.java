package se.jimmyeliasson.gzcompanion.knowledge.building;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.knowledge.common.KnowledgeLoadResult;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildingKnowledgeLoaderTest {
    private static final String BASIC = "/building-fixtures/buildings-basic.json";
    private static final String MALFORMED = "/building-fixtures/buildings-malformed.json";
    private static final String FUTURE_SCHEMA = "/building-fixtures/buildings-future-schema.json";
    private static final String MISSING = "/building-fixtures/does-not-exist.json";

    @Test
    @DisplayName("Loads buildings and global rules from a valid fixture")
    void loadsBasicFixture() {
        KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader(BASIC).load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        BuildingKnowledgeBase base = result.data();
        assertEquals(2, base.size());
        assertEquals(40, base.globalRules().minWallCoveragePercent());
        assertEquals(75, base.globalRules().minRoofCoveragePercent());
        assertTrue(base.byId("bank").isPresent());
        assertTrue(base.byId("bank").get().hasPublishedMinimumFootprint());
        assertFalse(base.byId("stadskarna").get().hasPublishedMinimumFootprint());
    }

    @Test
    @DisplayName("A missing resource is a hard error")
    void missingResourceIsError() {
        KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader(MISSING).load();
        assertEquals(KnowledgeLoadResult.Outcome.ERROR, result.outcome());
        assertEquals(0, result.data().size());
    }

    @Test
    @DisplayName("A future schema version is reported as incompatible")
    void futureSchemaIsIncompatible() {
        KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader(FUTURE_SCHEMA).load();
        assertEquals(KnowledgeLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
    }

    @Test
    @DisplayName("A malformed building entry (missing id, duplicate id) is skipped without breaking the others")
    void malformedEntriesAreSkipped() {
        KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader(MALFORMED).load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        BuildingKnowledgeBase base = result.data();
        assertEquals(1, base.size());
        assertTrue(base.byId("stadskarna").isPresent());
        assertEquals(1, base.byId("stadskarna").get().levelRequirement());
        assertFalse(base.loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("search() matches by name, id, and bonus text, case-insensitively")
    void searchMatchesAcrossFields() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader(BASIC).load().data();
        assertEquals(1, base.search("bank").size());
        assertEquals(1, base.search("RÄNTA").size());
        assertEquals(2, base.search("").size());
        assertEquals(2, base.search(null).size());
    }

    @Test
    @DisplayName("The bundled Rule Pack building data loads with the current Building System 1.0 global rules and at least one building")
    void bundledRulePackLoadsCurrentEngine() {
        KnowledgeLoadResult<BuildingKnowledgeBase> result = new BuildingKnowledgeLoader().load();
        assertEquals(KnowledgeLoadResult.Outcome.LOADED, result.outcome());
        BuildingKnowledgeBase base = result.data();
        assertEquals(19, base.size(), "Bundled building catalog must be the current 19-building Building System 1.0 list.");
        assertEquals(40, base.globalRules().minWallCoveragePercent());
        assertEquals(75, base.globalRules().minRoofCoveragePercent());
        assertTrue(base.globalRules().mustBeFullyInsideTerritory());
    }

    @Test
    @DisplayName("Every bundled building id is unique - no duplicate survives loading")
    void bundledBuildingsHaveUniqueIds() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        Set<String> seen = new HashSet<>();
        for (SettlementBuilding building : base.buildings()) {
            assertTrue(seen.add(building.id()), "Duplicate building id: " + building.id());
        }
        assertEquals(19, seen.size());
    }

    @Test
    @DisplayName("Every bundled building now publishes a non-null minimum width and depth - the per-building footprint gap is closed")
    void everyBundledBuildingHasPublishedFootprint() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        for (SettlementBuilding building : base.buildings()) {
            assertTrue(building.hasPublishedMinimumFootprint(), building.id() + " is missing its published minimum footprint.");
            assertTrue(building.minWidth() > 0, building.id() + " minWidth must be positive.");
            assertTrue(building.minDepth() > 0, building.id() + " minDepth must be positive.");
        }
    }

    @Test
    @DisplayName("Stall's published minimum footprint is exactly 19x19, per its individual Wiki detail page")
    void stallIsExactly19By19() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        SettlementBuilding stall = base.byId("stall").orElseThrow();
        assertEquals(19, stall.minWidth());
        assertEquals(19, stall.minDepth());
        assertEquals(14, stall.levelRequirement());
        assertEquals(500000, stall.licenseCost());
    }

    @Test
    @DisplayName("Exactly 4 of the 19 buildings publish a separate minimum height; the rest correctly have none")
    void exactlyFourBuildingsPublishMinimumHeight() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        long withHeight = base.buildings().stream().filter(b -> b.minHeight() != null).count();
        assertEquals(4, withHeight, "Vindhamn, Kyrka, Rådhus, and Slott are the only buildings with a separately published minimum height.");
        assertEquals(18, base.byId("vindhamn").orElseThrow().minHeight());
        assertEquals(15, base.byId("kyrka").orElseThrow().minHeight());
        assertEquals(16, base.byId("radhus").orElseThrow().minHeight());
        assertEquals(20, base.byId("slott").orElseThrow().minHeight());
    }

    @Test
    @DisplayName("Every building's level requirement is positive and its license cost is non-negative")
    void levelRequirementAndCostAreValid() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        for (SettlementBuilding building : base.buildings()) {
            assertTrue(building.levelRequirement() > 0, building.id() + " levelRequirement must be positive.");
            assertTrue(building.licenseCost() >= 0, building.id() + " licenseCost must be non-negative.");
        }
    }

    @Test
    @DisplayName("Every VERIFIED building entry carries a non-blank sourceReference pointing at its own individual Wiki page")
    void everyVerifiedBuildingHasItsOwnSourceReference() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        for (SettlementBuilding building : base.buildings()) {
            if (building.verification().status() != VerificationStatus.VERIFIED) continue;
            assertNotNull(building.verification().sourceReference(), building.id() + " is VERIFIED but has no sourceReference.");
            assertTrue(building.verification().sourceReference().contains("/wiki/buildings/" + building.id()),
                    building.id() + "'s sourceReference should point at its own individual building page, not a shared summary page.");
        }
    }

    // ------------------------------------------------------------------
    // Source-conflict handling: Stadskärna and Handelscentrum's individual pages state a
    // levelRequirement that directly contradicts the Settlement Upgrade progression page's own
    // "krävs för nivå N" cards for the exact same building. Neither number is asserted here as
    // the uniquely correct interpretation - only that the conflict itself is represented honestly.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Stadskärna's level-requirement conflict is represented honestly: CONFLICT status, not a silently-chosen VERIFIED number")
    void stadskarnaConflictIsRepresentedHonestly() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        SettlementBuilding stadskarna = base.byId("stadskarna").orElseThrow();

        assertTrue(stadskarna.hasLevelRequirementConflict(),
                "Stadskärna's own page (NIVÅKRAV 2) directly conflicts with the Settlement Upgrade page (krävs för nivå 2, i.e. must already exist at level 1).");
        assertEquals(VerificationStatus.CONFLICT, stadskarna.levelRequirementVerification().status());
        assertFalse(stadskarna.isLevelRequirementVerified());
        // The raw, disputed numbers themselves are still preserved (not deleted/nulled) so the
        // Byggplaner UI can show both sides of the conflict honestly.
        assertEquals(2, stadskarna.levelRequirement());
        assertEquals(2, stadskarna.progressionRequiredForUpgradeToLevel());
    }

    @Test
    @DisplayName("Handelscentrum's level-requirement conflict is represented honestly: CONFLICT status, not a silently-chosen VERIFIED number")
    void handelscentrumConflictIsRepresentedHonestly() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        SettlementBuilding handelscentrum = base.byId("handelscentrum").orElseThrow();

        assertTrue(handelscentrum.hasLevelRequirementConflict(),
                "Handelscentrum's own page (NIVÅKRAV 4) directly conflicts with the Settlement Upgrade page (available at level 3, per 'BYGGNAD PÅ NIVÅ 3').");
        assertEquals(VerificationStatus.CONFLICT, handelscentrum.levelRequirementVerification().status());
        assertFalse(handelscentrum.isLevelRequirementVerified());
        assertEquals(4, handelscentrum.levelRequirement());
        assertEquals(4, handelscentrum.progressionRequiredForUpgradeToLevel());
    }

    @Test
    @DisplayName("Stadskärna and Handelscentrum's footprint/cost/special requirements remain fully VERIFIED despite the level conflict")
    void conflictedBuildingsKeepTheirFootprintAndCostVerified() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        SettlementBuilding stadskarna = base.byId("stadskarna").orElseThrow();
        SettlementBuilding handelscentrum = base.byId("handelscentrum").orElseThrow();

        assertEquals(VerificationStatus.VERIFIED, stadskarna.verification().status(), "A disputed level field must never downgrade the building's confirmed footprint/cost.");
        assertEquals(11, stadskarna.minWidth());
        assertEquals(11, stadskarna.minDepth());
        assertEquals(5000, stadskarna.licenseCost());

        assertEquals(VerificationStatus.VERIFIED, handelscentrum.verification().status());
        assertEquals(15, handelscentrum.minWidth());
        assertEquals(15, handelscentrum.minDepth());
        assertEquals(20000, handelscentrum.licenseCost());
    }

    @Test
    @DisplayName("Exactly 2 of the 19 bundled buildings have a level-requirement conflict - scanning all 19 with the general rule finds no others")
    void exactlyTwoBundledBuildingsHaveALevelConflict() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        List<String> conflicted = base.buildings().stream()
                .filter(SettlementBuilding::hasLevelRequirementConflict)
                .map(SettlementBuilding::id)
                .toList();
        assertEquals(List.of("stadskarna", "handelscentrum"), conflicted,
                "The general circularity rule, scanned across all 19 bundled buildings, must find exactly these two - no more, no fewer.");
    }

    @Test
    @DisplayName("All 17 unaffected buildings preserve their existing levelRequirement and are confirmed non-conflicting")
    void unaffectedBuildingsPreserveExistingValuesAndHaveNoConflict() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        var expectedLevels = java.util.Map.ofEntries(
                java.util.Map.entry("kategoribyggnad", 2), java.util.Map.entry("laboratorium", 5),
                java.util.Map.entry("bank", 6), java.util.Map.entry("reliktempel", 7),
                java.util.Map.entry("vindhamn", 8), java.util.Map.entry("gatukontor", 10),
                java.util.Map.entry("turistbyra", 12), java.util.Map.entry("stall", 14),
                java.util.Map.entry("kontor", 16), java.util.Map.entry("kyrka", 18),
                java.util.Map.entry("marknadsplats", 20), java.util.Map.entry("myntforvaring", 22),
                java.util.Map.entry("radhus", 25), java.util.Map.entry("slott", 30),
                java.util.Map.entry("museum", 35), java.util.Map.entry("rustkammare", 40),
                java.util.Map.entry("myntverk", 45)
        );
        assertEquals(17, expectedLevels.size());
        for (var entry : expectedLevels.entrySet()) {
            SettlementBuilding building = base.byId(entry.getKey()).orElseThrow(() -> new AssertionError("Missing building: " + entry.getKey()));
            assertEquals(entry.getValue(), building.levelRequirement(), entry.getKey() + "'s levelRequirement must be unchanged.");
            assertFalse(building.hasLevelRequirementConflict(), entry.getKey() + " must not be flagged as conflicting.");
            assertEquals(VerificationStatus.VERIFIED, building.verification().status());
        }
    }

    @Test
    @DisplayName("Laboratorium is never presented as a general upgrade gate (Alkemi-only, conditional) and therefore never conflicts")
    void laboratoriumHasNoGeneralUpgradeGate() {
        BuildingKnowledgeBase base = new BuildingKnowledgeLoader().load().data();
        SettlementBuilding laboratorium = base.byId("laboratorium").orElseThrow();
        assertNull(laboratorium.progressionRequiredForUpgradeToLevel());
        assertFalse(laboratorium.hasLevelRequirementConflict());
    }
}
