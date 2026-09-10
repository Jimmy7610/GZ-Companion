package se.jimmyeliasson.gzcompanion.knowledge.economy;

import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;

import java.util.List;

/**
 * Verified GameZone MarketWatch system facts: MarketWatch represents resource DEMAND for
 * upcoming settlement upgrades (compared against each settlement's registered inventory) - it is
 * NOT an auction price list. The related-but-distinct {@code /market} command family (shop price
 * lookup, company buy listings) is a separate GameZone system and is intentionally not modeled
 * here - see docs/MARKETWATCH.md.
 */
public record MarketWatchInfo(String command, int categoryCount, String purpose, List<String> usageSteps, VerificationMetadata verification) {
    public MarketWatchInfo {
        usageSteps = usageSteps != null ? List.copyOf(usageSteps) : List.of();
        verification = verification != null ? verification : VerificationMetadata.UNVERIFIED_DEFAULT;
    }

    public static MarketWatchInfo empty() {
        return new MarketWatchInfo(null, 0, null, List.of(), VerificationMetadata.UNVERIFIED_DEFAULT);
    }
}
