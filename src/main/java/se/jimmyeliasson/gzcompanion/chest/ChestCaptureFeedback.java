package se.jimmyeliasson.gzcompanion.chest;

import se.jimmyeliasson.gzcompanion.chest.model.ChestCaptureEvent;

import java.util.Optional;

/**
 * Decides the small local Companion notification shown AFTER a legitimate capture has been
 * finalized and persisted (never while a storage screen is being read). Pure: produces text and
 * a dedupe key; the caller hands it to the existing toast manager, which respects the Companion
 * notification setting, dedupes, and keeps a bounded queue.
 *
 * <p>Anti-spam rule: a NEW storage always notifies; a known storage notifies only when its
 * last-known contents actually changed (reopening an untouched chest stays silent); finding the
 * exact navigation target always notifies.
 */
public final class ChestCaptureFeedback {
    private ChestCaptureFeedback() {}

    public record Message(String dedupeKey, String title, String body) {}

    public static Optional<Message> describe(ChestCaptureEvent event, boolean finishedNavigation) {
        if (event == null) return Optional.empty();
        String key = event.container().id().asStableKey();
        String title = event.container().displayTitle();

        if (finishedNavigation) {
            return Optional.of(new Message("kistor:found:" + key, "✓ " + title + " hittad", "Navigeringen avslutad · senast känt innehåll sparat"));
        }
        if (event.kind() == ChestCaptureEvent.Kind.NEW) {
            int distinct = event.distinctItemCount();
            String contents = distinct == 0 ? "tom" : distinct + (distinct == 1 ? " föremålstyp" : " olika föremål");
            return Optional.of(new Message("kistor:new:" + key, "Ny förvaring sparad", event.container().storageTypeText() + " • " + contents));
        }
        if (!event.contentsChanged()) {
            return Optional.empty();
        }
        String updatedTitle = event.container().hasLabel() ? title + " uppdaterad" : "Förvaring uppdaterad";
        return Optional.of(new Message("kistor:updated:" + key, updatedTitle, "Senast känt innehåll sparat"));
    }
}
