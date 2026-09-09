package se.jimmyeliasson.gzcompanion.knowledge.common;

/**
 * Runtime readiness status shared by the three M4 knowledge modules (commands, crafting,
 * items). Intentionally new and scoped to {@code knowledge.*} — the already human-QA-approved
 * {@code GuideLoadStatus} and {@code ChestManagerStatus} are deliberately left untouched rather
 * than mechanically merged into this, per this milestone's explicit "no regression-risk
 * refactor" instruction. The three shapes happen to look alike; that duplication is accepted
 * debt, not fixed here.
 */
public enum KnowledgeModuleStatus {
    LOADED("Laddad", true),
    UNAVAILABLE("Ej tillgänglig", false),
    ERROR("Fel vid inläsning", false),
    INCOMPATIBLE("Inkompatibelt schema", false);

    private final String displayName;
    private final boolean available;

    KnowledgeModuleStatus(String displayName, boolean available) {
        this.displayName = displayName;
        this.available = available;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isAvailable() {
        return available;
    }
}
