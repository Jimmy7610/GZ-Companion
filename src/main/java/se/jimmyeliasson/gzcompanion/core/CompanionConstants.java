package se.jimmyeliasson.gzcompanion.core;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Fundamental constants and runtime metadata for GZ Companion.
 *
 * GameZone server host/domain specifics reside in the profile/ and gamezone/ layers.
 * Mod version is resolved dynamically from Fabric Loader metadata at runtime with
 * a fallback to the build-time version string.
 */
public final class CompanionConstants {
    public static final String MOD_ID = "gzcompanion";
    public static final String MOD_NAME = "GZ Companion";
    public static final String AUTHOR = "Jimmy Eliasson";
    /** The Minecraft version this build is compiled and verified for (fabric.mod.json requires it). */
    public static final String TARGET_MINECRAFT_VERSION = "26.3";

    private static final String FALLBACK_VERSION = "0.1.0-alpha.12";

    private CompanionConstants() {
    }

    /**
     * The Minecraft version actually running, as reported by Fabric Loader - shown on Home and in
     * diagnostics and checked against the data packs' tested versions. Falls back to
     * {@link #TARGET_MINECRAFT_VERSION} when no loader is present (unit tests).
     */
    public static String runningMinecraftVersion() {
        String cached = runningMinecraftVersion;
        if (cached == null) {
            // Cannot change while the game runs - resolved once (Home reads it every frame).
            cached = resolveRunningMinecraftVersion();
            runningMinecraftVersion = cached;
        }
        return cached;
    }

    private static volatile String runningMinecraftVersion;

    private static String resolveRunningMinecraftVersion() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer("minecraft")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .filter(version -> !version.isBlank())
                    .orElse(TARGET_MINECRAFT_VERSION);
        } catch (Throwable ignored) {
            return TARGET_MINECRAFT_VERSION;
        }
    }

    /**
     * Obtains the single source of truth for the mod version from Fabric Loader.
     */
    public static String getModVersion() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer(MOD_ID)
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse(FALLBACK_VERSION);
        } catch (Throwable ignored) {
            return FALLBACK_VERSION;
        }
    }
}