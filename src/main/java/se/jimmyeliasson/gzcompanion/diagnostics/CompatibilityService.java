package se.jimmyeliasson.gzcompanion.diagnostics;

import se.jimmyeliasson.gzcompanion.gamezone.RulePack;
import se.jimmyeliasson.gzcompanion.gamezone.RulePackManifest;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates compatibility across Minecraft version, Companion version, and Rule Pack modules.
 *
 * Verification status definitions:
 * - COMPATIBLE: Schema and module are technically valid and loadable.
 * - VERIFIED: Server-specific data has been confirmed against live GameZoneMC behavior.
 * - UNVERIFIED: Structure is valid, but server-specific values are not yet confirmed.
 * - UNAVAILABLE: Module is not yet implemented in the current milestone.
 * - INCOMPATIBLE: Version mismatch or structural loading error.
 */
public class CompatibilityService {

    public CompatibilityResult evaluate(RulePack rulePack, String runningMinecraftVersion) {
        List<ModuleReport> reports = new ArrayList<>();
        CompatibilityStatus overall = CompatibilityStatus.COMPATIBLE;

        if (rulePack == null || rulePack.manifest() == null) {
            reports.add(new ModuleReport("rulepack", "GameZone Rule Pack", CompatibilityStatus.INCOMPATIBLE, "Inget Rule Pack laddat"));
            return new CompatibilityResult(CompatibilityStatus.INCOMPATIBLE, "Kunde inte ladda GameZone Rule Pack", reports);
        }

        RulePackManifest manifest = rulePack.manifest();

        // 1. Check Minecraft Version Compatibility
        boolean mcTested = runningMinecraftVersion != null && manifest.testedMinecraftVersions().contains(runningMinecraftVersion);
        if (!mcTested) {
            reports.add(new ModuleReport("minecraft", "Minecraft-version", CompatibilityStatus.WARNING,
                    "Körs på " + runningMinecraftVersion + ", testad för " + manifest.testedMinecraftVersions()));
            overall = CompatibilityStatus.WARNING;
        } else {
            reports.add(new ModuleReport("minecraft", "Minecraft-version", CompatibilityStatus.COMPATIBLE,
                    "Körs på kompatibel version " + runningMinecraftVersion));
        }

        // 2. Check Pack Manifest Verification
        if (manifest.verification() != null) {
            CompatibilityStatus packStatus = manifest.verification().status();
            if (packStatus == CompatibilityStatus.INCOMPATIBLE) {
                overall = CompatibilityStatus.INCOMPATIBLE;
            } else if (packStatus == CompatibilityStatus.WARNING || packStatus == CompatibilityStatus.UNVERIFIED || packStatus == CompatibilityStatus.STALE) {
                if (overall == CompatibilityStatus.COMPATIBLE || overall == CompatibilityStatus.VERIFIED) {
                    overall = CompatibilityStatus.UNVERIFIED;
                }
            }
        }

        // 3. Module Statuses
        if (manifest.modules() != null) {
            manifest.modules().forEach((key, info) -> {
                CompatibilityStatus modStatus = info.status() != null ? info.status() : CompatibilityStatus.UNKNOWN;
                reports.add(new ModuleReport(key, info.file(), modStatus, "Status: " + modStatus.getDisplayName()));
            });
        }

        String summary = overall == CompatibilityStatus.VERIFIED
                ? "Alla aktiva moduler är verifierade mot live-servern."
                : (overall == CompatibilityStatus.COMPATIBLE
                ? "Alla moduler är tekniskt kompatibla (schemavaliderade)."
                : (overall == CompatibilityStatus.UNVERIFIED
                ? "Moduler är kompatibla. Serverdata väntar på liveverifiering."
                : (overall.isWarning() ? "Vissa moduler behöver verifiering." : "Inkompatibilitet upptäckt.")));

        return new CompatibilityResult(overall, summary, reports);
    }
}