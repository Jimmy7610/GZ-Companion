package se.jimmyeliasson.gzcompanion.guide;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContext;
import se.jimmyeliasson.gzcompanion.guide.progress.GuideContextResolver;

import static org.junit.jupiter.api.Assertions.*;

class GuideContextResolverTest {

    @Test
    @DisplayName("Should generate different context keys for different singleplayer saves")
    void testDifferentSingleplayerWorlds() {
        String world1 = GuideContextResolver.resolveSingleplayerContext("My Survival World");
        String world2 = GuideContextResolver.resolveSingleplayerContext("Creative Test World");

        assertNotEquals(world1, world2);
        assertTrue(world1.startsWith("singleplayer:"));
        assertTrue(world2.startsWith("singleplayer:"));
    }

    @Test
    @DisplayName("Should generate identical context keys across restarts for the same singleplayer world")
    void testStableSingleplayerWorldAcrossRestarts() {
        String saveDirName = "World_Alpha_2026";
        String contextBeforeRestart = GuideContextResolver.resolveSingleplayerContext(saveDirName);
        String contextAfterRestart = GuideContextResolver.resolveSingleplayerContext(saveDirName);

        assertEquals(contextBeforeRestart, contextAfterRestart);
    }

    @Test
    @DisplayName("Should normalize multiplayer server host addresses and strip default port 25565")
    void testMultiplayerServerContext() {
        String server1 = GuideContextResolver.resolveServerContext("PLAY.GAMEZONEMC.SE");
        String server2 = GuideContextResolver.resolveServerContext("play.gamezonemc.se ");
        String server3 = GuideContextResolver.resolveServerContext("play.gamezonemc.se:25565");
        String server4 = GuideContextResolver.resolveServerContext("PLAY.GAMEZONEMC.SE:25565 ");

        assertEquals("server:play.gamezonemc.se", server1);
        assertEquals("server:play.gamezonemc.se", server2);
        assertEquals("server:play.gamezonemc.se", server3);
        assertEquals("server:play.gamezonemc.se", server4);
    }

    @Test
    @DisplayName("Should construct composite storage keys containing profile UUID and world context")
    void testCompositeGuideContextStorageKey() {
        GuideContext ctxA = GuideContextResolver.create("uuid-1234", "singleplayer:world_a");
        GuideContext ctxB = GuideContextResolver.create("uuid-1234", "singleplayer:world_b");
        GuideContext ctxC = GuideContextResolver.create("uuid-5678", "singleplayer:world_a");

        assertEquals("uuid-1234@@singleplayer:world_a", ctxA.getStorageKey());
        assertEquals("uuid-1234@@singleplayer:world_b", ctxB.getStorageKey());
        assertEquals("uuid-5678@@singleplayer:world_a", ctxC.getStorageKey());

        assertNotEquals(ctxA.getStorageKey(), ctxB.getStorageKey());
        assertNotEquals(ctxA.getStorageKey(), ctxC.getStorageKey());
    }
}
