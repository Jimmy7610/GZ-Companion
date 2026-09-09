package se.jimmyeliasson.gzcompanion.chest.storage;

/**
 * Local-first persistence boundary for the Chest Manager index.
 */
public interface ChestIndexStore {
    ChestIndexLoadResult load();

    void save(ChestIndexData data);
}
