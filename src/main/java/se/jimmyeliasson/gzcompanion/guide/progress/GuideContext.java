package se.jimmyeliasson.gzcompanion.guide.progress;

import java.util.Objects;

/**
 * Identifies the player profile and world/server session context for isolated guide progress.
 */
public record GuideContext(
    String profileId,
    String contextKey
) {
    public static final GuideContext DEFAULT = new GuideContext("offline_profile", "singleplayer:default");

    public GuideContext {
        profileId = Objects.requireNonNullElse(profileId, "offline_profile");
        contextKey = Objects.requireNonNullElse(contextKey, "singleplayer:default");
    }

    public String getStorageKey() {
        return profileId + "@@" + contextKey;
    }
}