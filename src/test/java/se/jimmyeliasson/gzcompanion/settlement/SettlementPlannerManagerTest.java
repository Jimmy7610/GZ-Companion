package se.jimmyeliasson.gzcompanion.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.settlement.storage.MemberNote;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerData;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerLoadResult;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerStatus;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerStore;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettlementPlannerManagerTest {

    /** In-memory test double - avoids touching disk for pure manager-logic tests. */
    private static final class InMemoryStore implements SettlementPlannerStore {
        private SettlementPlannerData data = SettlementPlannerData.empty();
        private SettlementPlannerLoadResult.Outcome loadOutcome = SettlementPlannerLoadResult.Outcome.NOT_FOUND;
        final List<SettlementPlannerData> savedSnapshots = new ArrayList<>();

        @Override
        public SettlementPlannerLoadResult load() {
            return new SettlementPlannerLoadResult(loadOutcome, data);
        }

        @Override
        public void save(SettlementPlannerData data) {
            this.data = data;
            savedSnapshots.add(data);
        }
    }

    @Test
    @DisplayName("initialize() with no existing file leaves the manager LOADED and usable")
    void initializeWithNoFileIsLoaded() {
        SettlementPlannerManager manager = new SettlementPlannerManager(new InMemoryStore());
        manager.initialize();
        assertEquals(SettlementPlannerStatus.LOADED, manager.getStatus());
    }

    @Test
    @DisplayName("Mutations before initialize() are refused (fail closed) and never crash")
    void mutationsBeforeInitializeAreRefused() {
        SettlementPlannerManager manager = new SettlementPlannerManager(new InMemoryStore());
        assertFalse(manager.setCurrentLevel("ctx", 5));
        assertFalse(manager.setOwnedAmount("ctx", "minecraft:oak_log", 3));
        assertFalse(manager.addOrUpdateMember("ctx", new MemberNote("m1", "Alice", "note")));
    }

    @Test
    @DisplayName("setCurrentLevel/setTargetLevel persist and are readable back through getProfile")
    void levelMutationsPersist() {
        InMemoryStore store = new InMemoryStore();
        SettlementPlannerManager manager = new SettlementPlannerManager(store);
        manager.initialize();

        assertTrue(manager.setCurrentLevel("server:gz", 4));
        assertTrue(manager.setTargetLevel("server:gz", 12));

        assertEquals(4, manager.getProfile("server:gz").currentLevel());
        assertEquals(12, manager.getProfile("server:gz").targetLevel());
        assertFalse(store.savedSnapshots.isEmpty());
    }

    @Test
    @DisplayName("Two different contexts never see each other's planner state")
    void contextsAreIsolated() {
        SettlementPlannerManager manager = new SettlementPlannerManager(new InMemoryStore());
        manager.initialize();

        manager.setCurrentLevel("server:a", 1);
        manager.setCurrentLevel("singleplayer:b", 30);

        assertEquals(1, manager.getProfile("server:a").currentLevel());
        assertEquals(30, manager.getProfile("singleplayer:b").currentLevel());
    }

    @Test
    @DisplayName("addOrUpdateMember then removeMember round-trips through getMembers")
    void memberLifecycle() {
        SettlementPlannerManager manager = new SettlementPlannerManager(new InMemoryStore());
        manager.initialize();

        manager.addOrUpdateMember("ctx", new MemberNote("m1", "Alice", "Byggare"));
        assertEquals(1, manager.getMembers("ctx").size());

        manager.removeMember("ctx", "m1");
        assertTrue(manager.getMembers("ctx").isEmpty());
    }

    @Test
    @DisplayName("An INCOMPATIBLE_SCHEMA load result leaves the manager refusing all mutations")
    void incompatibleSchemaRefusesMutations() {
        InMemoryStore store = new InMemoryStore();
        store.loadOutcome = SettlementPlannerLoadResult.Outcome.INCOMPATIBLE_SCHEMA;
        SettlementPlannerManager manager = new SettlementPlannerManager(store);
        manager.initialize();

        assertEquals(SettlementPlannerStatus.INCOMPATIBLE, manager.getStatus());
        assertFalse(manager.setCurrentLevel("ctx", 5));
    }
}
