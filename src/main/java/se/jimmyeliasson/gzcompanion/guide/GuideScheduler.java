package se.jimmyeliasson.gzcompanion.guide;

import net.minecraft.client.Minecraft;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;

/**
 * Lightweight client tick scheduler for background Guide evaluation.
 * Evaluates progression at controlled intervals (~750ms) even when GZ Companion UI is closed.
 */
public class GuideScheduler {
    public static final int DEFAULT_INTERVAL_TICKS = 15; // 15 ticks @ 20tps = 750ms

    private final int intervalTicks;
    private int tickCounter = 0;

    public GuideScheduler() {
        this(DEFAULT_INTERVAL_TICKS);
    }

    public GuideScheduler(int intervalTicks) {
        this.intervalTicks = Math.max(1, intervalTicks);
    }

    /**
     * Called on client tick end from Fabric lifecycle events.
     */
    public void onClientTick(Minecraft client) {
        if (client == null || client.player == null) {
            return;
        }

        tickCounter++;
        if (tickCounter >= intervalTicks) {
            tickCounter = 0;
            CompanionSession.getInstance().evaluateGuide();
        }
    }

    /**
     * Pure testable tick method.
     */
    public boolean onTick(Runnable action) {
        tickCounter++;
        if (tickCounter >= intervalTicks) {
            tickCounter = 0;
            if (action != null) {
                action.run();
            }
            return true;
        }
        return false;
    }

    public int getTickCounter() {
        return tickCounter;
    }

    public int getIntervalTicks() {
        return intervalTicks;
    }
}
