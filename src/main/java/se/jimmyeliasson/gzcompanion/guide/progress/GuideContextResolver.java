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
     */
    public static String resolveServerContext(String serverAddress) {
        if (serverAddress == null || serverAddress.isBlank()) {
            return "server:unknown";
        }
        return "server:" + serverAddress.trim().toLowerCase();
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
