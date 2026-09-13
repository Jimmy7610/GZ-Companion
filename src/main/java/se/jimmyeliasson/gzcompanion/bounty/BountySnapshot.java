package se.jimmyeliasson.gzcompanion.bounty;

import java.time.Instant;
import java.util.List;

/**
 * The single cached bounty-registry state, as held by {@link BountyManager}. Unlike Leaderboards
 * (one snapshot per board), there is exactly ONE bounty registry - see docs/BOUNTY-BOARD.md.
 *
 * <p>{@code entries} may legitimately be empty while {@code status} is {@link BountyStatus#LOADED}
 * - GameZone simply has no active bounties right now, which is a SUCCESSFUL result, never an
 * error. {@code entries} may also be non-empty while {@code status} is {@link BountyStatus#STALE}
 * - a failed refresh never clears a prior successful result; the UI decides what to show from
 * {@link BountyStatus#hasUsableData()}, never from {@code status == LOADED} alone.
 *
 * @param status       current freshness/health - see {@link BountyStatus}.
 * @param entries      cached active bounties, most-recently-successful fetch. Empty (never null)
 *                     until a fetch has ever succeeded, or when the registry is genuinely empty.
 * @param fetchedAt    when {@code entries} was captured. Null exactly when nothing has ever
 *                     successfully loaded.
 * @param errorMessage a short reason for the current non-LOADED status. Null when status is IDLE
 *                     or LOADED.
 */
public record BountySnapshot(
        BountyStatus status,
        List<BountyEntry> entries,
        Instant fetchedAt,
        String errorMessage
) {
    public BountySnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static BountySnapshot idle() {
        return new BountySnapshot(BountyStatus.IDLE, List.of(), null, null);
    }

    public boolean hasEntries() {
        return !entries.isEmpty();
    }
}
