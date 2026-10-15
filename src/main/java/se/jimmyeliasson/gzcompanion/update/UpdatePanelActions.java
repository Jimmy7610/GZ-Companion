package se.jimmyeliasson.gzcompanion.update;

/**
 * Which buttons the update panel's fixed action row shows for each {@link UpdateState}. Pure, so
 * the "the right buttons are always offered" rule is unit-testable without a live client.
 *
 * @param primary   the main action, or null when the state offers no clickable action.
 * @param secondary the secondary action ("Senare"), or null.
 */
public record UpdatePanelActions(Action primary, Action secondary) {
    public enum Action {
        DOWNLOAD("Ladda ner"),
        APPLY("Stäng och uppdatera"),
        RETRY("Försök igen"),
        LATER("Senare");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final UpdatePanelActions NONE = new UpdatePanelActions(null, null);

    public static UpdatePanelActions forState(UpdateState state) {
        if (state == null) return NONE;
        return switch (state) {
            case UPDATE_AVAILABLE -> new UpdatePanelActions(Action.DOWNLOAD, Action.LATER);
            case READY_TO_INSTALL -> new UpdatePanelActions(Action.APPLY, Action.LATER);
            case ERROR -> new UpdatePanelActions(Action.RETRY, Action.LATER);
            // In-progress states: nothing sensible to click, the row shows a status line instead.
            case DOWNLOADING, VERIFYING, STARTING_INSTALLER, IDLE, CHECKING, UP_TO_DATE -> NONE;
        };
    }

    public boolean hasActions() {
        return primary != null;
    }
}
