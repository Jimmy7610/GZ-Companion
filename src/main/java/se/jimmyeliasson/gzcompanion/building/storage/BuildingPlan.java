package se.jimmyeliasson.gzcompanion.building.storage;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One LOCAL building plan: a chosen building, a user-given name, planning dimensions the player
 * intends to build at, and which of the fixed local checklist items the player has marked done.
 * Never represents GameZone's actual server-side license/build/approve state - see
 * {@code docs/BUILDING-PLANNER.md}.
 */
public record BuildingPlan(String id, String buildingId, String planName, int width, int depth, int height,
                            Set<BuildingRequirementKey> completed, long createdAtMs) {
    public BuildingPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(buildingId, "buildingId");
        planName = (planName != null && !planName.isBlank()) ? planName.trim() : "Namnlös plan";
        width = Math.max(0, width);
        depth = Math.max(0, depth);
        height = Math.max(0, height);
        completed = completed != null ? EnumSet.copyOf(completed) : EnumSet.noneOf(BuildingRequirementKey.class);
    }

    public boolean isCompleted(BuildingRequirementKey key) {
        return completed.contains(key);
    }

    public BuildingPlan withRenamed(String newName) {
        return new BuildingPlan(id, buildingId, newName, width, depth, height, completed, createdAtMs);
    }

    public BuildingPlan withDimensions(int newWidth, int newDepth, int newHeight) {
        return new BuildingPlan(id, buildingId, planName, newWidth, newDepth, newHeight, completed, createdAtMs);
    }

    public BuildingPlan withToggledRequirement(BuildingRequirementKey key) {
        EnumSet<BuildingRequirementKey> updated = EnumSet.copyOf(completed);
        if (!updated.remove(key)) {
            updated.add(key);
        }
        return new BuildingPlan(id, buildingId, planName, width, depth, height, updated, createdAtMs);
    }
}
