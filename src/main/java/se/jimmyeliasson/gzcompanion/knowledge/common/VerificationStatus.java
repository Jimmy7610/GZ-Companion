package se.jimmyeliasson.gzcompanion.knowledge.common;

/**
 * Fact-level verification state for one GameZone knowledge entry (a command, a crafting
 * override, a custom item). Deliberately separate from
 * {@code se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus}, which answers a
 * different question — "is this file/module schema-valid and loadable" — not "has this specific
 * fact been confirmed against live GameZone behavior." A schema can be perfectly
 * {@code COMPATIBLE} while every fact inside it is still {@code UNVERIFIED}; the two axes must
 * never collapse into one type. No M4 entry may hold a {@code CompatibilityStatus} value here.
 */
public enum VerificationStatus {
    /** Confirmed against an explicitly canonical, current official GameZone source. */
    VERIFIED("Verifierad", 0xFF22C55E),
    /** Schema-valid, loadable, but not yet confirmed against a live/official source. Default. */
    UNVERIFIED("Overifierad", 0xFFF59E0B),
    /** Was once verified but the source has since changed or the fact is suspected outdated. */
    STALE("Inaktuell", 0xFFF59E0B),
    /** No usable verification information at all (e.g. a malformed status value). */
    UNKNOWN("Okänd", 0xFF64748B),
    /**
     * Two current, canonical official sources make directly contradictory claims about this
     * exact fact (not merely "not yet checked" - actively checked and found inconsistent).
     * Deliberately distinct from {@link #UNVERIFIED} (never checked) and {@link #STALE} (was
     * correct once, source moved on): a fact must never be silently presented as one settled
     * VERIFIED number when the canonical sources themselves disagree. See
     * {@code se.jimmyeliasson.gzcompanion.knowledge.building.SettlementBuilding#hasLevelRequirementConflict()}
     * for the first concrete use of this status.
     */
    CONFLICT("Motstridiga källor", 0xFFEF4444);

    private final String displayName;
    private final int argbColor;

    VerificationStatus(String displayName, int argbColor) {
        this.displayName = displayName;
        this.argbColor = argbColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getArgbColor() {
        return argbColor;
    }
}
