package se.jimmyeliasson.gzcompanion.gamezone.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.jimmyeliasson.gzcompanion.gamezone.events.GameZoneEventType;
import se.jimmyeliasson.gzcompanion.gamezone.parsing.GameZoneParserCatalog;
import se.jimmyeliasson.gzcompanion.gamezone.parsing.GameZoneParserDefinition;
import se.jimmyeliasson.gzcompanion.gamezone.parsing.ParserMatchType;
import se.jimmyeliasson.gzcompanion.gamezone.toast.GameZoneToastManager;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationMetadata;
import se.jimmyeliasson.gzcompanion.knowledge.common.VerificationStatus;
import se.jimmyeliasson.gzcompanion.profile.ServerProfile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies GameZoneChatObserver's profile gate: GameZone parser logic must only ever run while
 * connected to a verified GameZone profile - never on singleplayer or another server, regardless
 * of the Companion notification/toast settings.
 */
class GameZoneChatObserverTest {
    private static final VerificationMetadata TEST_ONLY_VERIFICATION =
            new VerificationMetadata(VerificationStatus.VERIFIED, "TEST ONLY fixture", "test://fixture", "2026-09-10");

    private GameZoneParserCatalog activeCatalog() {
        GameZoneParserDefinition parser = new GameZoneParserDefinition("p1", GameZoneEventType.SYSTEM_MESSAGE, ParserMatchType.CONTAINS,
                "TEST_ONLY_MARKER", List.of(), true, TEST_ONLY_VERIFICATION);
        return new GameZoneParserCatalog(List.of(parser), List.of());
    }

    @Test
    @DisplayName("On a GameZone profile (supplier true), an eligible message is observed and matched")
    void gameZoneProfileEligible() {
        GameZoneToastManager toastManager = new GameZoneToastManager();
        GameZoneChatObserver observer = new GameZoneChatObserver(this::activeCatalog, toastManager, () -> true);

        observer.onMessage("prefix TEST_ONLY_MARKER suffix");

        assertEquals(1, observer.getObservedMessageCount());
        assertEquals(1, observer.getMatchedEventCount());
        assertEquals(GameZoneEventType.SYSTEM_MESSAGE, observer.getLastMatchedEventType());
    }

    @Test
    @DisplayName("On a generic/other server (supplier false), the same eligible message is never observed or matched")
    void genericServerProfileIneligible() {
        GameZoneToastManager toastManager = new GameZoneToastManager();
        GameZoneChatObserver observer = new GameZoneChatObserver(this::activeCatalog, toastManager, () -> false);

        observer.onMessage("prefix TEST_ONLY_MARKER suffix");

        assertEquals(0, observer.getObservedMessageCount(), "A message received on a non-GameZone server must not even be counted as observed.");
        assertEquals(0, observer.getMatchedEventCount());
        assertNull(observer.getLastMatchedEventType());
    }

    @Test
    @DisplayName("On singleplayer (supplier false), GameZone parsing is equally ineligible")
    void singleplayerProfileIneligible() {
        GameZoneToastManager toastManager = new GameZoneToastManager();
        GameZoneChatObserver observer = new GameZoneChatObserver(this::activeCatalog, toastManager, () -> false);

        observer.onMessage("TEST_ONLY_MARKER");

        assertEquals(0, observer.getObservedMessageCount());
        assertTrue(toastManager.currentToast(System.currentTimeMillis()).isEmpty(), "No toast may ever be offered outside a verified GameZone profile.");
    }

    @Test
    @DisplayName("A null profile supplier fails closed (treated as ineligible), never NPEs")
    void nullProfileSupplierFailsClosed() {
        GameZoneToastManager toastManager = new GameZoneToastManager();
        GameZoneChatObserver observer = new GameZoneChatObserver(this::activeCatalog, toastManager, null);

        assertDoesNotThrow(() -> observer.onMessage("TEST_ONLY_MARKER"));
        assertEquals(0, observer.getObservedMessageCount());
    }

    @Test
    @DisplayName("The profile gate wins even when a toast setting would otherwise allow the toast")
    void profileGateWinsOverEnabledToastSettings() {
        GameZoneToastManager toastManager = new GameZoneToastManager();
        toastManager.setNotificationsEnabledSupplier(() -> true);
        toastManager.setGameZoneToastsEnabledSupplier(() -> true);
        GameZoneChatObserver observer = new GameZoneChatObserver(this::activeCatalog, toastManager, () -> false);

        observer.onMessage("TEST_ONLY_MARKER");

        assertEquals(0, observer.getMatchedEventCount(), "A setting cannot override fair server identity - the profile gate always wins.");
        assertTrue(toastManager.currentToast(System.currentTimeMillis()).isEmpty());
    }

    @Test
    @DisplayName("A message that doesn't match any parser is still safely handled once eligible")
    void eligibleButNonMatchingMessage() {
        GameZoneToastManager toastManager = new GameZoneToastManager();
        GameZoneChatObserver observer = new GameZoneChatObserver(this::activeCatalog, toastManager, () -> true);

        observer.onMessage("completely unrelated text");

        assertEquals(1, observer.getObservedMessageCount());
        assertEquals(0, observer.getMatchedEventCount());
    }

    @Test
    @DisplayName("A null message never throws, regardless of profile eligibility")
    void nullMessageNeverThrows() {
        GameZoneToastManager toastManager = new GameZoneToastManager();
        GameZoneChatObserver observer = new GameZoneChatObserver(this::activeCatalog, toastManager, () -> true);
        assertDoesNotThrow(() -> observer.onMessage(null));
    }

    @Test
    @DisplayName("Portability: wiring the observer's gate directly to ServerProfile.GAMEZONE/GENERIC reproduces the same eligible/ineligible behavior")
    void wiredDirectlyToServerProfileGameZoneIsEligible() {
        GameZoneToastManager toastManager = new GameZoneToastManager();
        ServerProfile currentProfile = ServerProfile.GAMEZONE;
        GameZoneChatObserver observer = new GameZoneChatObserver(this::activeCatalog, toastManager, currentProfile::isGameZone);

        observer.onMessage("TEST_ONLY_MARKER");

        assertEquals(1, observer.getMatchedEventCount());
    }

    @Test
    @DisplayName("Portability: ServerProfile.GENERIC (covers both 'another server' and singleplayer in this codebase) is ineligible")
    void wiredDirectlyToServerProfileGenericIsIneligible() {
        GameZoneToastManager toastManager = new GameZoneToastManager();
        ServerProfile currentProfile = ServerProfile.GENERIC;
        GameZoneChatObserver observer = new GameZoneChatObserver(this::activeCatalog, toastManager, currentProfile::isGameZone);

        observer.onMessage("TEST_ONLY_MARKER");

        assertEquals(0, observer.getMatchedEventCount(), "ServerProfile.GENERIC must never allow GameZone parsing - it is the profile used for both other servers and singleplayer.");
    }
}
