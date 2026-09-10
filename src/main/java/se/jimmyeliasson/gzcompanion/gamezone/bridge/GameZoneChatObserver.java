package se.jimmyeliasson.gzcompanion.gamezone.bridge;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import se.jimmyeliasson.gzcompanion.gamezone.events.GameZoneEventType;
import se.jimmyeliasson.gzcompanion.gamezone.events.GameZoneObservedEvent;
import se.jimmyeliasson.gzcompanion.gamezone.parsing.GameZoneParserCatalog;
import se.jimmyeliasson.gzcompanion.gamezone.parsing.GameZoneParserEngine;
import se.jimmyeliasson.gzcompanion.gamezone.toast.GameZoneToastManager;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Registers GZ Companion's ONLY chat/message observation hooks: the non-cancellable
 * {@code ClientReceiveMessageEvents.GAME} and {@code ClientReceiveMessageEvents.CHAT} events from
 * {@code fabric-message-api-v1}. Deliberately never touches {@code ALLOW_GAME}/{@code ALLOW_CHAT}
 * (the cancel/rewrite-capable variants) - this class can only ever look at a message the client
 * was already going to show, never change whether or how it's shown.
 *
 * <p>Never sends a message, never issues a command, never stores raw message text anywhere
 * persistent - only {@link GameZoneParserEngine#match} results (parser id + declared capture
 * values) ever leave this method, and even those live only in memory in
 * {@link GameZoneToastManager}.
 *
 * <p><b>Profile gating.</b> The registration hooks above fire for EVERY message the client
 * receives, on any server - singleplayer, GameZoneMC, or any other Minecraft server. GameZone
 * parser logic must only ever run while the client is actually connected to a verified GameZone
 * profile: {@code gameZoneProfileActiveSupplier} is checked FIRST, before anything else, and a
 * message is not even counted as "observed" when it fails this check - {@link
 * #getObservedMessageCount()} deliberately represents "messages that were actually eligible for
 * GameZone parsing," not every chat line the client happened to receive on an unrelated server.
 * This gate is a pure read - it never cancels, mutates, or delays the message itself; the message
 * still renders in chat exactly as Minecraft/the server intended either way.
 */
public final class GameZoneChatObserver {
    private final Supplier<GameZoneParserCatalog> catalogSupplier;
    private final GameZoneToastManager toastManager;
    private final Supplier<Boolean> gameZoneProfileActiveSupplier;

    private volatile GameZoneEventType lastMatchedEventType;
    private volatile long observedMessageCount;
    private volatile long matchedEventCount;

    /**
     * @param gameZoneProfileActiveSupplier returns {@code true} only when the client is currently
     *                                       connected to a server {@link
     *                                       se.jimmyeliasson.gzcompanion.profile.ServerDetection}
     *                                       recognizes as GameZoneMC - never {@code true} for
     *                                       singleplayer or any other server.
     */
    public GameZoneChatObserver(Supplier<GameZoneParserCatalog> catalogSupplier, GameZoneToastManager toastManager,
                                 Supplier<Boolean> gameZoneProfileActiveSupplier) {
        this.catalogSupplier = catalogSupplier;
        this.toastManager = toastManager;
        this.gameZoneProfileActiveSupplier = gameZoneProfileActiveSupplier != null ? gameZoneProfileActiveSupplier : () -> false;
    }

    public void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) -> onMessage(message.getString()));
    }

    /** Package-private (not private) so tests can exercise the profile gate directly without a live Fabric event bus. */
    void onMessage(String text) {
        try {
            if (!Boolean.TRUE.equals(gameZoneProfileActiveSupplier.get())) {
                // Not connected to a verified GameZone profile - no GameZone parsing occurs at
                // all, and this message is not even counted as "observed." A setting that enables
                // GameZone toasts can never override this: the profile gate always wins.
                return;
            }

            observedMessageCount++;
            GameZoneParserCatalog catalog = catalogSupplier.get();
            if (catalog == null || catalog.activeCount() == 0) {
                return;
            }
            long now = System.currentTimeMillis();
            Optional<GameZoneObservedEvent> matched = GameZoneParserEngine.match(catalog.activeParsers(), text, now);
            if (matched.isEmpty()) {
                return;
            }
            GameZoneObservedEvent event = matched.get();
            matchedEventCount++;
            lastMatchedEventType = event.eventType();
            toastManager.offer(event.dedupeKey(), event.eventType().getDisplayName(), summarize(event), now);
        } catch (Exception ignored) {
            // Observation must never break chat rendering - fail silently and keep listening.
        }
    }

    /** Builds a short toast body purely from the parser's own declared capture values - never the raw message. */
    private static String summarize(GameZoneObservedEvent event) {
        if (event.capturedValues().isEmpty()) {
            return event.eventType().getDisplayName();
        }
        return String.join(" · ", event.capturedValues().values());
    }

    public GameZoneEventType getLastMatchedEventType() {
        return lastMatchedEventType;
    }

    public long getObservedMessageCount() {
        return observedMessageCount;
    }

    public long getMatchedEventCount() {
        return matchedEventCount;
    }
}
