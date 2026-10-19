package se.jimmyeliasson.gzcompanion.usage;

import java.util.Optional;

/** Persists the {@link InstallationIdentity}. Implementations never throw. */
public interface InstallationIdentityStore {
    /** The stored identity, or empty if none exists yet or the stored one is unreadable. */
    Optional<InstallationIdentity> load();

    void save(InstallationIdentity identity);
}
