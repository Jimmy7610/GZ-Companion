package se.jimmyeliasson.gzcompanion.settlement.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local planning state for one context (a specific singleplayer world or a specific server),
 * isolated the same way {@code ContextContainers} isolates Chest Manager data per context. Never
 * claims to be the player's actual, server-observed settlement state - only what the player
 * locally chose to plan around.
 */
public record SettlementPlannerProfile(Integer currentLevel, Integer targetLevel, Map<String, Integer> ownedItemAmounts, List<MemberNote> members) {
    public SettlementPlannerProfile {
        ownedItemAmounts = ownedItemAmounts != null ? Map.copyOf(ownedItemAmounts) : Map.of();
        members = members != null ? List.copyOf(members) : List.of();
    }

    public static SettlementPlannerProfile empty() {
        return new SettlementPlannerProfile(null, null, Map.of(), List.of());
    }

    public SettlementPlannerProfile withCurrentLevel(Integer level) {
        return new SettlementPlannerProfile(level, targetLevel, ownedItemAmounts, members);
    }

    public SettlementPlannerProfile withTargetLevel(Integer level) {
        return new SettlementPlannerProfile(currentLevel, level, ownedItemAmounts, members);
    }

    public SettlementPlannerProfile withOwnedAmount(String itemKey, int amount) {
        Map<String, Integer> copy = new LinkedHashMap<>(ownedItemAmounts);
        if (amount <= 0) {
            copy.remove(itemKey);
        } else {
            copy.put(itemKey, amount);
        }
        return new SettlementPlannerProfile(currentLevel, targetLevel, copy, members);
    }

    public int ownedAmount(String itemKey) {
        return ownedItemAmounts.getOrDefault(itemKey, 0);
    }

    public SettlementPlannerProfile withMember(MemberNote member) {
        List<MemberNote> copy = new ArrayList<>();
        boolean replaced = false;
        for (MemberNote existing : members) {
            if (existing.id().equals(member.id())) {
                copy.add(member);
                replaced = true;
            } else {
                copy.add(existing);
            }
        }
        if (!replaced) copy.add(member);
        return new SettlementPlannerProfile(currentLevel, targetLevel, ownedItemAmounts, copy);
    }

    public SettlementPlannerProfile withoutMember(String memberId) {
        List<MemberNote> copy = new ArrayList<>();
        for (MemberNote existing : members) {
            if (!existing.id().equals(memberId)) copy.add(existing);
        }
        return new SettlementPlannerProfile(currentLevel, targetLevel, ownedItemAmounts, copy);
    }
}
