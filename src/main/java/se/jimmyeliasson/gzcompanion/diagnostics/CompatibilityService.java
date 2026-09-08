package se.jimmyeliasson.gzcompanion.diagnostics;

import se.jimmyeliasson.gzcompanion.gamezone.RulePack;
import se.jimmyeliasson.gzcompanion.gamezone.RulePackManifest;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates compatibility across Minecraft version, Companion version, and Rule Pack modules.
 */
public class CompatibilityService {

    public CompatibilityResult evaluate(RulePack rulePack, String runningMinecraftVersion) {
        List<ModuleReport> reports = new ArrayList<>();
        CompatibilityStatus overall = CompatibilityStatus.VERIFIED;

        if (rulePack == null || rulePack.manifest() == null) {
            reports.add(new ModuleReport("rulepack", "GameZone Rule Pack", CompatibilityStatus.INCOMPATIBLE, "Inget Rule Pack laddat"));
            return new CompatibilityResult(CompatibilityStatus.INCOMPATIBLE, "Kunde inte ladda GameZone Rule Pack", reports);
        }

        RulePackManifest manifest = rulePack.manifest();

        // 1. Check Minecraft Version Compatibility
        boolean mcTested = runningMinecraftVersion != null && manifest.testedMinecraftVersions().contains(runningMinecraftVersion);
        if (!mcTested) {
            reports.add(new ModuleReport("minecraft", "Minecraft-version", CompatibilityStatus.WARNING,
                    "K\u00F6rs p\u00E5 " + runningMinecraftVersion + ", testad f\u00F6r " + manifest.testedMinecraftVersions()));
            overall = CompatibilityStatus.WARNING;
        } else {
            reports.add(new ModuleReport("minecraft", "Minecraft-version", CompatibilityStatus.VERIFIED,
                    "K\u00F6rs p\u00E5 verifierad version " + runningMinecraftVersion));
        }

        // 2. Check Pack Manifest Verification
        if (manifest.verification() != null) {
            CompatibilityStatus packStatus = manifest.verification().status();
            if (packStatus == CompatibilityStatus.INCOMPATIBLE) {
                overall = CompatibilityStatus.INCOMPATIBLE;
            } else if (packStatus == CompatibilityStatus.WARNING || packStatus == CompatibilityStatus.UNVERIFIED || packStatus == CompatibilityStatus.STALE) {
                if (overall == CompatibilityStatus.VERIFIED) {
                    overall = CompatibilityStatus.WARNING;
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

        String summary = overall.isGood() ? "Alla aktiva moduler \u00E4r verifierade och kompatibla."
                : (overall.isWarning() ? "Vissa moduler beh\u00F6ver verifiering." : "Inkompatibilitet uppt\u00E4ckt.");

        return new CompatibilityResult(overall, summary, reports);
    }
}