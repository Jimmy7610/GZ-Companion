package se.jimmyeliasson.gzcompanion.knowledge.crafting;

/**
 * Pure decision logic for when the Crafting tab is allowed to re-read the client's recipe book.
 * Reading it (iterating {@code ClientRecipeBook} collections and resolving every ingredient's
 * {@code ItemStack}) is real work and must never happen on every render frame. Kept as a
 * standalone, Minecraft-type-free class specifically so it can be unit tested without a running
 * Minecraft client - the UI layer (which does need a live client to actually read the recipe
 * book) only ever asks this class "should I refresh right now?".
 */
public final class ClientRecipeCachePolicy {

    /** Never refresh more often than this while nothing else has changed. */
    public static final long MIN_REFRESH_INTERVAL_MS = 1500L;

    private ClientRecipeCachePolicy() {}

    /**
     * @param lastRefreshAtMs   epoch millis of the last successful refresh, or {@code <= 0} if
     *                          never refreshed yet (always refreshes in that case).
     * @param nowMs             current epoch millis.
     * @param contextChanged    true if the player/world/server context (and therefore the
     *                          recipe book itself) may have changed since the last refresh -
     *                          always forces an immediate refresh regardless of the interval.
     * @return true if the caller should re-read the recipe book now.
     */
    public static boolean shouldRefresh(long lastRefreshAtMs, long nowMs, boolean contextChanged) {
        if (contextChanged) {
            return true;
        }
        if (lastRefreshAtMs <= 0) {
            return true;
        }
        return (nowMs - lastRefreshAtMs) >= MIN_REFRESH_INTERVAL_MS;
    }
}
