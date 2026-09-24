package se.jimmyeliasson.gzcompanion.chest.model;

import java.util.List;
import java.util.Locale;

/**
 * Local-only group filter for Kistor: every storage, only storage without a group, or one named
 * group (matched case-insensitively). Pure UI state - never persisted.
 */
public record ChestGroupFilter(Kind kind, String groupName) {
    public enum Kind { ALL, UNGROUPED, NAMED }

    public static final ChestGroupFilter ALL = new ChestGroupFilter(Kind.ALL, null);
    public static final ChestGroupFilter UNGROUPED = new ChestGroupFilter(Kind.UNGROUPED, null);

    public static ChestGroupFilter named(String groupName) {
        return groupName == null ? ALL : new ChestGroupFilter(Kind.NAMED, groupName);
    }

    public boolean matches(StoredContainer container) {
        return switch (kind) {
            case ALL -> true;
            case UNGROUPED -> container.group() == null;
            case NAMED -> container.group() != null && container.group().toLowerCase(Locale.ROOT).equals(groupName.toLowerCase(Locale.ROOT));
        };
    }

    public String displayName() {
        return switch (kind) {
            case ALL -> "Alla";
            case UNGROUPED -> "Utan grupp";
            case NAMED -> groupName;
        };
    }

    /**
     * Cycles ALL -> each existing group (in the given order) -> UNGROUPED -> ALL. A NAMED filter
     * whose group no longer exists falls back to ALL.
     */
    public ChestGroupFilter next(List<String> existingGroups) {
        List<String> groups = existingGroups != null ? existingGroups : List.of();
        return switch (kind) {
            case ALL -> groups.isEmpty() ? UNGROUPED : named(groups.get(0));
            case NAMED -> {
                int idx = -1;
                for (int i = 0; i < groups.size(); i++) {
                    if (groups.get(i).equalsIgnoreCase(groupName)) {
                        idx = i;
                        break;
                    }
                }
                if (idx < 0) yield ALL;
                yield idx + 1 < groups.size() ? named(groups.get(idx + 1)) : UNGROUPED;
            }
            case UNGROUPED -> ALL;
        };
    }
}
