package se.jimmyeliasson.gzcompanion.bounty;

import java.time.Instant;

/**
 * One published PvE bounty, exactly as GameZone's own public {@code /api/bounties} contract
 * exposes it - see docs/BOUNTY-BOARD.md for the discovery process and the exact JSON shape this is
 * mapped from. A bounty targets one UNIQUE named entity, never "any mob of this type" (see
 * https://www.gamezonemc.se/wiki/bounties/bounties) - {@code name} is that entity's given name
 * (e.g. GameZone's own wiki example "Gorgash"), not a generic mob-type label.
 *
 * <p>Only fields the source actually publishes are modeled here - {@code entityType} and {@code
 * hint} are both legitimately nullable (a bounty may have no public clue). Companion never guesses
 * a value for an absent field - see {@link BountyJsonParser}.
 *
 * <p>{@code expiry} is never null - it is always one of {@link BountyExpiry}'s three explicit
 * states ({@code NoLimit}/{@code ExpiresAt}/{@code Unknown}), because "the source didn't say" and
 * "the source said there is no limit" are different facts that must never be collapsed into each
 * other. See {@link BountyExpiry}'s own doc comment.
 *
 * @param name        the bounty target's given name - GameZone shows this in red above the mob
 *                    in-game. Never blank.
 * @param entityType  the target's entity type as GameZone's own API names it (e.g.
 *                    {@code "WITHER_SKELETON"}) - null if the source did not publish one. Never
 *                    reformatted into a guessed Minecraft entity id; only cosmetically
 *                    underscore-to-space converted for display by {@link BountyFormatter}.
 * @param rewardCoins the published Coin reward. Never negative.
 * @param hint        the optional PUBLIC clue GameZone published - exactly as published, never
 *                    used to infer a coordinate or location Companion wasn't told. Null if absent.
 * @param status      the source's own status string for this entry (e.g. {@code "ACTIVE"}) -
 *                    preserved as-delivered rather than re-derived, so a future status value this
 *                    Companion version doesn't specifically know about is still shown honestly
 *                    rather than silently reinterpreted.
 * @param createdAt   when the bounty was published, if the source provided a parseable timestamp.
 * @param expiry      the bounty's expiry status - see {@link BountyExpiry}. Never null.
 */
public record BountyEntry(
        String name,
        String entityType,
        long rewardCoins,
        String hint,
        String status,
        Instant createdAt,
        BountyExpiry expiry
) {
    public BountyEntry {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (rewardCoins < 0) {
            throw new IllegalArgumentException("rewardCoins must not be negative, was " + rewardCoins);
        }
        if (expiry == null) {
            throw new IllegalArgumentException("expiry must not be null - use BountyExpiry.UNKNOWN instead");
        }
    }

    public boolean hasHint() {
        return hint != null && !hint.isBlank();
    }

    /** True only when the source published a real, parseable expiry timestamp - false for both
     * {@code NoLimit} and {@code Unknown}. Use {@code expiry()} directly (with {@link
     * BountyFormatter#formatRemainingTime}) to distinguish those two remaining cases for display. */
    public boolean hasExpiry() {
        return expiry instanceof BountyExpiry.ExpiresAt;
    }

    public boolean hasEntityType() {
        return entityType != null && !entityType.isBlank();
    }
}
