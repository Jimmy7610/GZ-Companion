package se.jimmyeliasson.gzcompanion.bounty;

/**
 * Fetches GameZone's current active-bounty registry from wherever it legitimately comes from.
 * Never throws - every failure mode is represented in {@link BountyFetchResult}, mirroring
 * {@code LeaderboardSource}'s convention.
 */
public interface BountySource {
    BountyFetchResult fetch();
}
