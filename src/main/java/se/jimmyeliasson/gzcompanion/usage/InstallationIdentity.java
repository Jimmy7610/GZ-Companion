package se.jimmyeliasson.gzcompanion.usage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * This installation's anonymous identity for the active-installation counter: a random UUID plus
 * the counter's own bookkeeping. The id is generated locally from a cryptographically secure
 * random source ({@link UUID#randomUUID()}) and is never derived from anything about the player,
 * account, machine or network - see docs/PRIVACY.md.
 *
 * @param lastAttemptAt when a heartbeat was last attempted (success or failure); null if never.
 * @param lastSuccessAt when a heartbeat was last accepted by the backend; null if never.
 */
public record InstallationIdentity(UUID installationId, Instant lastAttemptAt, Instant lastSuccessAt) {
    public InstallationIdentity {
        Objects.requireNonNull(installationId, "installationId");
    }

    /** A brand-new identity with a fresh random id and no heartbeat history. */
    public static InstallationIdentity generate() {
        return new InstallationIdentity(UUID.randomUUID(), null, null);
    }

    public InstallationIdentity withAttemptAt(Instant at) {
        return new InstallationIdentity(installationId, at, lastSuccessAt);
    }

    public InstallationIdentity withSuccessAt(Instant at) {
        return new InstallationIdentity(installationId, lastAttemptAt, at);
    }
}
