package se.jimmyeliasson.gzcompanion.guide.progress;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Resolves stable, isolated context keys for singleplayer worlds and multiplayer servers.
 */
public final class GuideContextResolver {

    private GuideContextResolver() {}

    /**
     * Resolves a stable singleplayer world context identifier based on save folder name / level ID.
     */
    public static String resolveSingleplayerContext(String saveDirName) {
        if (saveDirName == null || saveDirName.isBlank()) {
            return "singleplayer:default";
        }
        String sanitized = saveDirName.trim().replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
        return "singleplayer:" + sanitized;
    }

    /**
     * Resolves a normalized multiplayer server context identifier.
     * Normalizes case and strips default Minecraft port :25565.
     */
    public static String resolveServerContext(String serverAddress) {
        if (serverAddress == null || serverAddress.isBlank()) {
            return "server:unknown";
        }
        String normalized = serverAddress.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.endsWith(":25565")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }
        return "server:" + normalized;
    }

    /**
     * Generates a stable GuideContext for a player in a specific world or server.
     */
    public static GuideContext create(String profileId, String rawContext) {
        String safeProfile = (profileId != null && !profileId.isBlank()) ? profileId.trim() : "offline_profile";
        String safeContext = (rawContext != null && !rawContext.isBlank()) ? rawContext.trim() : "singleplayer:default";
        return new GuideContext(safeProfile, safeContext);
    }
}
