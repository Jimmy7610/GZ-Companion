package se.jimmyeliasson.gzcompanion.chest.bridge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import se.jimmyeliasson.gzcompanion.chest.nav.PlayerPose;

import java.util.Optional;

/**
 * Reads ONLY the local player's own position, dimension and camera yaw - the same values the
 * vanilla F3 screen shows the player. Nothing else about the world (blocks, block entities,
 * chunks, other entities) is ever read here.
 */
public final class MinecraftPlayerPoseReader {
    private MinecraftPlayerPoseReader() {}

    /**
     * @param partialTick render partial tick, so position and yaw interpolate smoothly between
     *                    client ticks (the arrow rotates smoothly while the camera turns).
     * @return empty when there is no playable world/player right now.
     */
    public static Optional<PlayerPose> read(float partialTick) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) return Optional.empty();
            LocalPlayer player = client.player;
            if (player == null || client.level == null) return Optional.empty();
            Vec3 pos = player.getPosition(partialTick);
            float yaw = player.getViewYRot(partialTick);
            String dimension = client.level.dimension().identifier().toString();
            return Optional.of(new PlayerPose(dimension, pos.x, pos.y, pos.z, yaw));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
