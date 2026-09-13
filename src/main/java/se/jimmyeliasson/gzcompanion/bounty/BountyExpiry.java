package se.jimmyeliasson.gzcompanion.bounty;

import java.time.Instant;

/**
 * Explicit, non-lossy representation of one bounty's public expiry status - see
 * docs/BOUNTY-BOARD.md's "Expiry semantics" section.
 *
 * <p>GameZone's real {@code /api/bounties} contract distinguishes an EXPLICIT JSON {@code null}
 * (GameZone itself says the bounty has no time limit - "kan sakna tidsgräns helt") from a real
 * ISO-8601 timestamp. A field that is simply ABSENT, or present but not a valid non-null string,
 * carries no such guarantee from the source - collapsing that case into {@link NoLimit} would be a
 * guess Companion has no right to make (see {@link BountyJsonParser#parseActiveBounties}, which is
 * the only place these three states are ever decided).
 */
public sealed interface BountyExpiry {
    /** The source explicitly published {@code "expiresAt": null} - a genuine, source-confirmed "no
     * time limit," never inferred from an absent or malformed field. */
    record NoLimit() implements BountyExpiry {}

    /** The source published a real, parseable ISO-8601 expiry timestamp. */
    record ExpiresAt(Instant instant) implements BountyExpiry {
        public ExpiresAt {
            if (instant == null) {
                throw new IllegalArgumentException("instant must not be null - use BountyExpiry.UNKNOWN instead");
            }
        }
    }

    /** The field was absent, or present but not JSON null and not a parseable timestamp - the
     * source did not tell us anything definite. Companion must not guess "no limit" here, and must
     * not guess a specific time either. */
    record Unknown() implements BountyExpiry {}

    /** Shared singleton for the (data-free) "no limit" state - use this rather than {@code new
     * NoLimit()} at call sites. */
    BountyExpiry NO_LIMIT = new NoLimit();
    /** Shared singleton for the (data-free) "unknown" state - use this rather than {@code new
     * Unknown()} at call sites. */
    BountyExpiry UNKNOWN = new Unknown();
}
