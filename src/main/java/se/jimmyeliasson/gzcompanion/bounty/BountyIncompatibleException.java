package se.jimmyeliasson.gzcompanion.bounty;

/**
 * Thrown by {@link BountyJsonParser} when the fetched response's TOP-LEVEL structure doesn't match
 * what this Companion version knows how to read at all (e.g. not a JSON object, missing the
 * {@code data.active} array entirely, or an unrecognized top-level {@code status} value) - as
 * opposed to a single malformed bounty entry within an otherwise-valid array, which is silently
 * skipped rather than failing the whole registry. Mirrors {@code LeaderboardIncompatibleException}.
 */
public final class BountyIncompatibleException extends Exception {
    public BountyIncompatibleException(String message) {
        super(message);
    }
}
