package se.jimmyeliasson.gzcompanion.knowledge.settlement;

import java.util.Objects;

/**
 * One GameZone settlement production category (e.g. Gruvdrift, Jordbruk). The taxonomy itself is
 * Rule Pack data, not a hardcoded Java enum.
 */
public record ProductionCategory(String id, String displayName, String description) {
    public ProductionCategory {
        Objects.requireNonNull(id, "id");
        displayName = (displayName != null && !displayName.isBlank()) ? displayName : id;
        description = description != null ? description : "";
    }
}
