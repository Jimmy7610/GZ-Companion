package se.jimmyeliasson.gzcompanion.usage;

import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.UUID;

/**
 * The ENTIRE content of a heartbeat: the anonymous installation id and the GZ Companion version.
 * There are deliberately no other fields - nothing about the player, account, world, server,
 * settings, gameplay or machine can be added without changing this record (and its tests).
 *
 * <pre>{"installationId":"<random UUID>","version":"0.1.0-alpha.12"}</pre>
 */
public record HeartbeatPayload(UUID installationId, String version) {
    static final int MAX_VERSION_LENGTH = 64;

    public HeartbeatPayload {
        Objects.requireNonNull(installationId, "installationId");
        if (version == null || version.isBlank() || version.length() > MAX_VERSION_LENGTH) {
            throw new IllegalArgumentException("Invalid version: " + version);
        }
        version = version.trim();
    }

    /** The exact JSON body sent - two fields, nothing else. */
    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("installationId", installationId.toString());
        obj.addProperty("version", version);
        return obj.toString();
    }
}
