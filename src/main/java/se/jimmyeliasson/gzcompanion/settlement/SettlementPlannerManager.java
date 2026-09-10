package se.jimmyeliasson.gzcompanion.settlement;

import se.jimmyeliasson.gzcompanion.settlement.storage.MemberNote;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerData;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerProfile;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerStatus;
import se.jimmyeliasson.gzcompanion.settlement.storage.SettlementPlannerStore;

import java.util.List;

/**
 * Runtime coordinator for local Settlement planning state (chosen current/target level, manually
 * entered owned-material counts, and free-text local member notes), isolated per
 * GameZone-server-vs-singleplayer context exactly like {@code ChestManager}. Contains no
 * Minecraft API types and never claims to know the player's actual, server-observed settlement
 * state - only what the player locally chose to plan around.
 */
public class SettlementPlannerManager {
    private final SettlementPlannerStore store;
    private SettlementPlannerData data;
    private SettlementPlannerStatus status = SettlementPlannerStatus.UNAVAILABLE;

    public SettlementPlannerManager(SettlementPlannerStore store) {
        this.store = store;
    }

    public void initialize() {
        try {
            var result = store.load();
            if (result == null) {
                this.data = SettlementPlannerData.empty();
                this.status = SettlementPlannerStatus.ERROR;
                return;
            }
            switch (result.outcome()) {
                case NOT_FOUND, LOADED, CORRUPT_RECOVERED -> {
                    this.data = result.data();
                    this.status = SettlementPlannerStatus.LOADED;
                }
                case INCOMPATIBLE_SCHEMA -> {
                    this.data = SettlementPlannerData.empty();
                    this.status = SettlementPlannerStatus.INCOMPATIBLE;
                }
                case ERROR -> {
                    this.data = SettlementPlannerData.empty();
                    this.status = SettlementPlannerStatus.ERROR;
                }
            }
        } catch (Exception e) {
            this.data = SettlementPlannerData.empty();
            this.status = SettlementPlannerStatus.ERROR;
        }
    }

    public SettlementPlannerStatus getStatus() {
        return status;
    }

    private boolean requireLoaded() {
        return status == SettlementPlannerStatus.LOADED;
    }

    public SettlementPlannerProfile getProfile(String contextKey) {
        if (contextKey == null || data == null) return SettlementPlannerProfile.empty();
        return data.getProfile(contextKey);
    }

    public boolean setCurrentLevel(String contextKey, Integer level) {
        if (!requireLoaded() || contextKey == null) return false;
        SettlementPlannerProfile updated = data.getProfile(contextKey).withCurrentLevel(level);
        persist(contextKey, updated);
        return true;
    }

    public boolean setTargetLevel(String contextKey, Integer level) {
        if (!requireLoaded() || contextKey == null) return false;
        SettlementPlannerProfile updated = data.getProfile(contextKey).withTargetLevel(level);
        persist(contextKey, updated);
        return true;
    }

    public boolean setOwnedAmount(String contextKey, String itemKey, int amount) {
        if (!requireLoaded() || contextKey == null || itemKey == null) return false;
        SettlementPlannerProfile updated = data.getProfile(contextKey).withOwnedAmount(itemKey, amount);
        persist(contextKey, updated);
        return true;
    }

    public boolean addOrUpdateMember(String contextKey, MemberNote member) {
        if (!requireLoaded() || contextKey == null || member == null) return false;
        SettlementPlannerProfile updated = data.getProfile(contextKey).withMember(member);
        persist(contextKey, updated);
        return true;
    }

    public boolean removeMember(String contextKey, String memberId) {
        if (!requireLoaded() || contextKey == null || memberId == null) return false;
        SettlementPlannerProfile updated = data.getProfile(contextKey).withoutMember(memberId);
        persist(contextKey, updated);
        return true;
    }

    public List<MemberNote> getMembers(String contextKey) {
        return getProfile(contextKey).members();
    }

    private void persist(String contextKey, SettlementPlannerProfile updated) {
        data = data.withProfile(contextKey, updated);
        store.save(data);
    }
}
