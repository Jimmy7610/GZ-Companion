package se.jimmyeliasson.gzcompanion.usage;

/**
 * Delivers one {@link HeartbeatPayload} to the usage backend. Never throws; returns whether the
 * backend accepted it. Abstracted so the client works (and is tested) before the real endpoint
 * exists - see {@link HeartbeatEndpoint}.
 */
public interface HeartbeatTransport {
    boolean send(HeartbeatPayload payload);
}
