package se.jimmyeliasson.gzcompanion.building.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonBuildingPlanStoreTest {

    @TempDir
    Path tempDir;

    private Path storeFile() {
        return tempDir.resolve("building-plans.json");
    }

    @Test
    @DisplayName("load() on a missing file returns NOT_FOUND with an empty, usable dataset")
    void loadMissingFileReturnsNotFound() {
        JsonBuildingPlanStore store = new JsonBuildingPlanStore(storeFile());
        BuildingPlanLoadResult result = store.load();
        assertEquals(BuildingPlanLoadResult.Outcome.NOT_FOUND, result.outcome());
        assertTrue(result.isUsable());
    }

    @Test
    @DisplayName("save() then load() round-trips dimensions and completed requirement keys exactly")
    void saveThenLoadRoundTrips() {
        JsonBuildingPlanStore store = new JsonBuildingPlanStore(storeFile());
        BuildingPlan plan = new BuildingPlan("p1", "stadskarna", "Min plan", 7, 7, 5,
                EnumSet.of(BuildingRequirementKey.LICENSE, BuildingRequirementKey.LEVEL), 1000L);
        BuildingPlanData data = BuildingPlanData.empty().withPlanAdded("server:gamezone.se", plan);
        store.save(data);

        BuildingPlanLoadResult result = store.load();
        assertEquals(BuildingPlanLoadResult.Outcome.LOADED, result.outcome());

        List<BuildingPlan> loaded = result.data().getPlans("server:gamezone.se");
        assertEquals(1, loaded.size());
        BuildingPlan loadedPlan = loaded.get(0);
        assertEquals(7, loadedPlan.width());
        assertTrue(loadedPlan.isCompleted(BuildingRequirementKey.LICENSE));
        assertTrue(loadedPlan.isCompleted(BuildingRequirementKey.LEVEL));
        assertFalse(loadedPlan.isCompleted(BuildingRequirementKey.WALLS));
    }

    @Test
    @DisplayName("Two different context keys are stored and loaded in isolation")
    void contextIsolation() {
        JsonBuildingPlanStore store = new JsonBuildingPlanStore(storeFile());
        BuildingPlan planA = new BuildingPlan("a", "stadskarna", "A", 5, 5, 5, null, 0L);
        BuildingPlan planB = new BuildingPlan("b", "bank", "B", 6, 6, 6, null, 0L);
        BuildingPlanData data = BuildingPlanData.empty()
                .withPlanAdded("server:a", planA)
                .withPlanAdded("singleplayer:b", planB);
        store.save(data);

        BuildingPlanData loaded = store.load().data();
        assertEquals(1, loaded.getPlans("server:a").size());
        assertEquals("stadskarna", loaded.getPlans("server:a").get(0).buildingId());
        assertEquals(1, loaded.getPlans("singleplayer:b").size());
        assertTrue(loaded.getPlans("server:other").isEmpty());
    }

    @Test
    @DisplayName("A future schema version is reported as incompatible and the file is left untouched")
    void futureSchemaIsIncompatible() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 999, \"plansByContext\": {}}");
        String before = Files.readString(file);

        JsonBuildingPlanStore store = new JsonBuildingPlanStore(file);
        BuildingPlanLoadResult result = store.load();

        assertEquals(BuildingPlanLoadResult.Outcome.INCOMPATIBLE_SCHEMA, result.outcome());
        assertEquals(before, Files.readString(file));
    }

    @Test
    @DisplayName("A corrupt file is backed up and recovered as an empty dataset")
    void corruptFileIsRecovered() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "not valid json {{{");

        JsonBuildingPlanStore store = new JsonBuildingPlanStore(file);
        BuildingPlanLoadResult result = store.load();

        assertEquals(BuildingPlanLoadResult.Outcome.CORRUPT_RECOVERED, result.outcome());
        assertTrue(result.isUsable());
    }

    @Test
    @DisplayName("An unknown checklist key value is skipped without discarding the rest of the plan")
    void unknownRequirementKeyIsSkipped() throws Exception {
        Path file = storeFile();
        Files.writeString(file, "{\"schemaVersion\": 1, \"plansByContext\": {\"ctx\": ["
                + "{\"id\":\"p1\",\"buildingId\":\"stadskarna\",\"planName\":\"Plan\",\"width\":5,\"depth\":5,\"height\":5,"
                + "\"completed\":[\"LICENSE\",\"FUTURE_UNKNOWN_KEY\"],\"createdAtMs\":0}"
                + "]}}");

        JsonBuildingPlanStore store = new JsonBuildingPlanStore(file);
        BuildingPlanLoadResult result = store.load();

        assertEquals(BuildingPlanLoadResult.Outcome.LOADED, result.outcome());
        BuildingPlan plan = result.data().getPlans("ctx").get(0);
        assertTrue(plan.isCompleted(BuildingRequirementKey.LICENSE));
    }
}
