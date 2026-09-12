package se.jimmyeliasson.gzcompanion.update;

import java.io.IOException;
import java.util.List;

/**
 * The network seam for release discovery - the ONLY place this feature talks to GitHub. Kept as a
 * one-method interface so {@link UpdateChecker}'s selection logic can be unit-tested against fixed,
 * deterministic fixtures without any live network access. See {@link GitHubReleaseSource} for the
 * real implementation.
 */
public interface UpdateReleaseSource {
    /** Every release known to the repository (drafts included) - filtering/selection happens in {@link UpdateChecker}. */
    List<GitHubRelease> fetchReleases() throws IOException;

    /** Fetches the raw text of one URL - used to download {@code update-manifest.json} itself (a small text file, not the installer). */
    String fetchText(String url) throws IOException;
}
