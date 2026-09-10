package se.jimmyeliasson.gzcompanion.knowledge.settlement;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

/**
 * Verified foundational settlement facts (creation command/cost, start level, rename/category
 * change costs). Deliberately does NOT model a "maxMembersInitial" or any other placeholder fact
 * that is not actually backed by a current official source.
 */
public record SettlementFoundation(
    String creationCommand,
    long creationCostCoins,
    int startLevel,
    String startLevelName,
    long renameCostCoins,
    long categoryChangeCostCoins,
    String note,
    VerificationMetadata verification
) {
    public SettlementFoundation {
        verification = verification != null ? verification : VerificationMetadata.UNVERIFIED_DEFAULT;
    }
}
