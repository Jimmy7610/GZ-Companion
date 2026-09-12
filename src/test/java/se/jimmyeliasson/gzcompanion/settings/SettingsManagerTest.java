package se.jimmyeliasson.gzcompanion.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsManagerTest {

    private static final class InMemoryStore implements SettingsStore {
        private CompanionSettings settings = CompanionSettings.defaults();
        private SettingsLoadResult.Outcome loadOutcome = SettingsLoadResult.Outcome.NOT_FOUND;
        int saveCount = 0;

        @Override
        public SettingsLoadResult load() {
            return new SettingsLoadResult(loadOutcome, settings);
        }

        @Override
        public void save(CompanionSettings settings) {
            this.settings = settings;
            saveCount++;
        }
    }

    @Test
    @DisplayName("initialize() with no existing file leaves the manager LOADED with defaults")
    void initializeWithNoFileIsLoaded() {
        SettingsManager manager = new SettingsManager(new InMemoryStore());
        manager.initialize();
        assertEquals(SettingsStatus.LOADED, manager.getStatus());
        assertEquals(CompanionSettings.defaults(), manager.getSettings());
    }

    @Test
    @DisplayName("Mutations before initialize() are refused")
    void mutationsBeforeInitializeAreRefused() {
        SettingsManager manager = new SettingsManager(new InMemoryStore());
        assertFalse(manager.setShowTechnicalIds(false));
        assertEquals(CompanionSettings.defaults(), manager.getSettings());
    }

    @Test
    @DisplayName("Each setter mutates exactly its own field and persists immediately")
    void eachSetterMutatesOnlyItsOwnField() {
        InMemoryStore store = new InMemoryStore();
        SettingsManager manager = new SettingsManager(store);
        manager.initialize();

        assertTrue(manager.setShowTechnicalIds(false));
        assertFalse(manager.getSettings().showTechnicalIds());
        assertTrue(manager.getSettings().companionNotificationsEnabled(), "Unrelated fields must stay untouched.");
        assertEquals(1, store.saveCount);

        assertTrue(manager.setGameZoneToastsEnabled(false));
        assertFalse(manager.getSettings().gameZoneToastsEnabled());
        assertFalse(manager.getSettings().showTechnicalIds(), "The earlier mutation must survive a later, unrelated mutation.");
    }

    @Test
    @DisplayName("resetToDefaults() restores every field to CompanionSettings.defaults()")
    void resetToDefaultsRestoresEverything() {
        SettingsManager manager = new SettingsManager(new InMemoryStore());
        manager.initialize();
        manager.setShowTechnicalIds(false);
        manager.setCompanionNotificationsEnabled(false);

        assertTrue(manager.resetToDefaults());
        assertEquals(CompanionSettings.defaults(), manager.getSettings());
    }

    @Test
    @DisplayName("An INCOMPATIBLE_SCHEMA load result leaves the manager refusing all mutations")
    void incompatibleSchemaRefusesMutations() {
        InMemoryStore store = new InMemoryStore();
        store.loadOutcome = SettingsLoadResult.Outcome.INCOMPATIBLE_SCHEMA;
        SettingsManager manager = new SettingsManager(store);
        manager.initialize();

        assertEquals(SettingsStatus.INCOMPATIBLE, manager.getStatus());
        assertFalse(manager.setShowTechnicalIds(false));
    }

    // ------------------------------------------------------------------
    // Favorite players - local-only, case-insensitive lookup preserving original casing.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("addFavoritePlayer adds exactly the given username, and isFavoritePlayer reports it")
    void favoriteToggleAdd() {
        SettingsManager manager = new SettingsManager(new InMemoryStore());
        manager.initialize();

        assertTrue(manager.addFavoritePlayer("Kalle92"));
        assertEquals(List.of("Kalle92"), manager.getSettings().favoritePlayers());
        assertTrue(manager.isFavoritePlayer("Kalle92"));
        assertTrue(manager.isFavoritePlayer("kalle92"), "Lookup must be case-insensitive.");
    }

    @Test
    @DisplayName("removeFavoritePlayer removes a favorite case-insensitively")
    void favoriteToggleRemove() {
        SettingsManager manager = new SettingsManager(new InMemoryStore());
        manager.initialize();
        manager.addFavoritePlayer("Kalle92");

        assertTrue(manager.removeFavoritePlayer("KALLE92"));
        assertFalse(manager.isFavoritePlayer("Kalle92"));
        assertEquals(List.of(), manager.getSettings().favoritePlayers());
    }

    @Test
    @DisplayName("toggleFavoritePlayer adds when absent and removes when present")
    void toggleFavoritePlayerFlipsState() {
        SettingsManager manager = new SettingsManager(new InMemoryStore());
        manager.initialize();

        assertTrue(manager.toggleFavoritePlayer("AnnaCraft"));
        assertTrue(manager.isFavoritePlayer("AnnaCraft"));

        assertTrue(manager.toggleFavoritePlayer("AnnaCraft"));
        assertFalse(manager.isFavoritePlayer("AnnaCraft"));
    }

    @Test
    @DisplayName("Adding an already-favorited name (any casing) is a no-op, never a duplicate entry")
    void addFavoriteIsIdempotentCaseInsensitively() {
        SettingsManager manager = new SettingsManager(new InMemoryStore());
        manager.initialize();

        manager.addFavoritePlayer("Kalle92");
        assertTrue(manager.addFavoritePlayer("kalle92"));
        assertEquals(1, manager.getSettings().favoritePlayers().size());
    }

    @Test
    @DisplayName("Favorites persist through the store across a fresh SettingsManager (simulating a full restart)")
    void favoritesPersistAcrossRestart() {
        InMemoryStore store = new InMemoryStore();
        SettingsManager first = new SettingsManager(store);
        first.initialize();
        first.addFavoritePlayer("Kalle92");
        first.addFavoritePlayer("AnnaCraft");

        SettingsManager second = new SettingsManager(store);
        second.initialize();

        assertEquals(List.of("Kalle92", "AnnaCraft"), second.getSettings().favoritePlayers());
    }
}
