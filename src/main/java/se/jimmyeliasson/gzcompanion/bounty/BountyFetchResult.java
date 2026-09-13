package se.jimmyeliasson.gzcompanion.bounty;

import java.util.List;

/**
 * The outcome of one {@link BountySource#fetch()} call - mirrors {@code LeaderboardFetchResult}'s
 * sealed-result-not-exception design so {@link BountyManager} can update its single cached
 * snapshot unconditionally, handling every case explicitly.
 */
public sealed interface BountyFetchResult {
    /** {@code entries} may legitimately be empty - GameZone simply has no active bounties right
     * now, which is success, never an error. */
    record Success(List<BountyEntry> entries) implements BountyFetchResult {
        public Success {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    /** Network-level failure: offline, timeout, non-2xx, refused host, oversized response. */
    record Unavailable(String reason) implements BountyFetchResult {}

    /** The endpoint was reachable but its response structure no longer matches what this parser understands. */
    record Incompatible(String reason) implements BountyFetchResult {}
}
