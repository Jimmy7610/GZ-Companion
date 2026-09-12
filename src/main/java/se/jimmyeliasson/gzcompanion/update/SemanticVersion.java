package se.jimmyeliasson.gzcompanion.update;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A real Semantic Versioning 2.0.0 (https://semver.org) value, with correct prerelease precedence -
 * deliberately NOT a plain string comparison (that would incorrectly rank {@code "alpha.10"} before
 * {@code "alpha.2"}, and would not know that a release without a prerelease tag outranks one with).
 *
 * <p>Required ordering this class guarantees:
 * {@code 0.1.0-alpha.1 < 0.1.0-alpha.2 < 0.1.0-alpha.10 < 0.1.0-beta.1 < 0.1.0}.
 *
 * <p>Build metadata (a trailing {@code +...} segment) is parsed but never affects precedence, per
 * the SemVer spec - two versions differing only in build metadata compare equal.
 */
public record SemanticVersion(int major, int minor, int patch, List<String> prereleaseIdentifiers) implements Comparable<SemanticVersion> {

    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    public SemanticVersion {
        prereleaseIdentifiers = prereleaseIdentifiers != null ? List.copyOf(prereleaseIdentifiers) : List.of();
    }

    /**
     * Parses a version string, tolerating an optional leading {@code v} (GitHub release tags are
     * {@code v0.1.0-alpha.2}). Returns empty (never throws) for anything that doesn't strictly
     * match SemVer - a malformed version must never be silently treated as "current" or "newer".
     */
    public static Optional<SemanticVersion> parse(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        Matcher m = SEMVER_PATTERN.matcher(trimmed);
        if (!m.matches()) return Optional.empty();

        try {
            int major = Integer.parseInt(m.group(1));
            int minor = Integer.parseInt(m.group(2));
            int patch = Integer.parseInt(m.group(3));
            String prereleaseRaw = m.group(4);
            List<String> prerelease = prereleaseRaw == null || prereleaseRaw.isEmpty()
                    ? List.of()
                    : List.of(prereleaseRaw.split("\\.", -1));
            return Optional.of(new SemanticVersion(major, minor, patch, prerelease));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public boolean isPrerelease() {
        return !prereleaseIdentifiers.isEmpty();
    }

    public String toDisplayString() {
        String core = major + "." + minor + "." + patch;
        return isPrerelease() ? core + "-" + String.join(".", prereleaseIdentifiers) : core;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        if (major != other.major) return Integer.compare(major, other.major);
        if (minor != other.minor) return Integer.compare(minor, other.minor);
        if (patch != other.patch) return Integer.compare(patch, other.patch);

        // A version WITHOUT a prerelease tag always outranks one with, per the SemVer spec.
        if (!isPrerelease() && !other.isPrerelease()) return 0;
        if (!isPrerelease()) return 1;
        if (!other.isPrerelease()) return -1;

        int len = Math.min(prereleaseIdentifiers.size(), other.prereleaseIdentifiers.size());
        for (int i = 0; i < len; i++) {
            int cmp = compareIdentifier(prereleaseIdentifiers.get(i), other.prereleaseIdentifiers.get(i));
            if (cmp != 0) return cmp;
        }
        // All shared identifiers equal - the longer identifier list has higher precedence.
        return Integer.compare(prereleaseIdentifiers.size(), other.prereleaseIdentifiers.size());
    }

    /** Numeric identifiers compare numerically; a numeric identifier always has LOWER precedence than an alphanumeric one. */
    private static int compareIdentifier(String a, String b) {
        boolean aNumeric = isNumeric(a);
        boolean bNumeric = isNumeric(b);
        if (aNumeric && bNumeric) {
            return Long.compare(Long.parseLong(a), Long.parseLong(b));
        }
        if (aNumeric) return -1;
        if (bNumeric) return 1;
        return a.compareTo(b);
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}
