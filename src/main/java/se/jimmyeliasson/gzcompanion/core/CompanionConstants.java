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
    public static final String TARGET_MINECRAFT_VERSION = "26.1.2";

    private static final String FALLBACK_VERSION = "0.1.0-alpha.4";

    private CompanionConstants() {
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