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
 */
public final class GameZoneChatObserver {
    private final Supplier<GameZoneParserCatalog> catalogSupplier;
    private final GameZoneToastManager toastManager;

    private volatile GameZoneEventType lastMatchedEventType;
    private volatile long observedMessageCount;
    private volatile long matchedEventCount;

    public GameZoneChatObserver(Supplier<GameZoneParserCatalog> catalogSupplier, GameZoneToastManager toastManager) {
        this.catalogSupplier = catalogSupplier;
        this.toastManager = toastManager;
    }

    public void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, playerChatMessage, sender, boundChatType, timeStamp) -> onMessage(message.getString()));
    }

    private void onMessage(String text) {
        try {
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
