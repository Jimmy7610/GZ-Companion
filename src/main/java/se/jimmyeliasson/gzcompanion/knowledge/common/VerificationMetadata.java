package se.jimmyeliasson.gzcompanion.knowledge.common;

/**
 * Source and confidence trail for one GameZone knowledge fact. Shared by commands, crafting
 * overrides, and custom-item knowledge so the same three fields never have to be redefined per
 * knowledge type.
 *
 * <p>Enforces a hard trust rule: {@link VerificationStatus#VERIFIED} is never accepted without a
 * non-blank {@code sourceName}, {@code sourceReference}, AND {@code lastVerified} — an entry
 * claiming VERIFIED without a full trail (M4's policy requires an exact canonical source page, not
 * just a source name) is automatically downgraded to {@link VerificationStatus#UNVERIFIED} rather
 * than silently trusted. A missing/unparseable status always defaults to
 * {@link VerificationStatus#UNVERIFIED}, never {@code VERIFIED}.
 */
public record VerificationMetadata(
    VerificationStatus status,
    String sourceName,
    String sourceReference,
    String lastVerified
) {
    public static final VerificationMetadata UNVERIFIED_DEFAULT =
            new VerificationMetadata(VerificationStatus.UNVERIFIED, null, null, null);

    public VerificationMetadata {
        status = downgradeIfUnsupported(status, sourceName, sourceReference, lastVerified);
        sourceName = blankToNull(sourceName);
        sourceReference = blankToNull(sourceReference);
        lastVerified = blankToNull(lastVerified);
    }

    /**
     * Constructs metadata from raw, possibly-untrusted parsed values. A null/blank/unparseable
     * status string always becomes {@link VerificationStatus#UNVERIFIED} — never
     * {@code VERIFIED} by accident.
     */
    public static VerificationMetadata of(String rawStatus, String sourceName, String sourceReference, String lastVerified) {
        VerificationStatus status = VerificationStatus.UNVERIFIED;
        if (rawStatus != null && !rawStatus.isBlank()) {
            try {
                status = VerificationStatus.valueOf(rawStatus.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                status = VerificationStatus.UNKNOWN;
            }
        }
        return new VerificationMetadata(status, sourceName, sourceReference, lastVerified);
    }

    private static VerificationStatus downgradeIfUnsupported(VerificationStatus status, String sourceName, String sourceReference, String lastVerified) {
        VerificationStatus safeStatus = status != null ? status : VerificationStatus.UNVERIFIED;
        if (safeStatus == VerificationStatus.VERIFIED && (isBlank(sourceName) || isBlank(sourceReference) || isBlank(lastVerified))) {
            return VerificationStatus.UNVERIFIED;
        }
        return safeStatus;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s;
    }

    public boolean hasSource() {
        return sourceName != null;
    }
}
