package se.jimmyeliasson.gzcompanion.chest.storage;

/**
 * Local-first persistence boundary for the Chest Manager index.
 */
public interface ChestIndexStore {
    ChestIndexData load();

    void save(ChestIndexData data);
}
