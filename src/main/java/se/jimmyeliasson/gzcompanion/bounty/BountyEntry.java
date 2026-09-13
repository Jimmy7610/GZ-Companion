package se.jimmyeliasson.gzcompanion.bounty;

import java.time.Instant;

/**
 * One published PvE bounty, exactly as GameZone's own public {@code /api/bounties} contract
 * exposes it - see docs/BOUNTY-BOARD.md for the discovery process and the exact JSON shape this is
 * mapped from. A bounty targets one UNIQUE named entity, never "any mob of this type" (see
 * https://www.gamezonemc.se/wiki/bounties/bounties) - {@code name} is that entity's given name
 * (e.g. GameZone's own wiki example "Gorgash"), not a generic mob-type label.
 *
 * <p>Only fields the source actually publishes are modeled here - {@code entityType}, {@code hint},
 * and {@code expiresAt} are all legitimately nullable (a bounty may have no public clue, and may
 * have no expiry at all per GameZone's own documented "kan sakna tidsgräns helt"). Companion never
 * guesses a value for an absent field - see {@link BountyJsonParser}.
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
 * @param expiresAt   when the bounty expires, if it has an expiry the source published as a
 *                    parseable timestamp - null when the bounty has no expiry (never invented).
 */
public record BountyEntry(
        String name,
        String entityType,
        long rewardCoins,
        String hint,
        String status,
        Instant createdAt,
        Instant expiresAt
) {
    public BountyEntry {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (rewardCoins < 0) {
            throw new IllegalArgumentException("rewardCoins must not be negative, was " + rewardCoins);
        }
    }

    public boolean hasHint() {
        return hint != null && !hint.isBlank();
    }

    public boolean hasExpiry() {
        return expiresAt != null;
    }

    public boolean hasEntityType() {
        return entityType != null && !entityType.isBlank();
    }
}
