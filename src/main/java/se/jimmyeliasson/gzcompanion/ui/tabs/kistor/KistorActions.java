package se.jimmyeliasson.gzcompanion.ui.tabs.kistor;

import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;

/**
 * Kistor actions shared by every view. Starting navigation only ever SELECTS an already-known
 * storage location as the HUD target - it never moves, turns or paths the player.
 */
public final class KistorActions {
    static final long BANNER_FLASH_MS = 4000L;

    private KistorActions() {}

    public static void startNavigation(KistorRenderContext ctx, StoredContainerId id) {
        boolean started = ctx.runtime().navigation().start(ctx.manager(), ctx.contextKey(), id, ctx.nowMs());
        ctx.state().flashBanner(started
                ? "Navigering startad • stäng med G för att se pilen"
                : "Kunde inte starta navigering - förvaringen finns inte i indexet", ctx.nowMs(), BANNER_FLASH_MS);
    }

    public static void stopNavigation(KistorRenderContext ctx) {
        ctx.runtime().navigation().stop(se.jimmyeliasson.gzcompanion.chest.nav.ChestNavigationManager.StopReason.USER);
        ctx.state().flashBanner(null, ctx.nowMs(), 0);
    }

    public static boolean isNavigationTarget(KistorRenderContext ctx, StoredContainerId id) {
        return ctx.runtime().navigation().isTarget(id);
    }
}
