package se.jimmyeliasson.gzcompanion.ui.tabs.kistor;

import se.jimmyeliasson.gzcompanion.chest.model.ChestGroupFilter;
import se.jimmyeliasson.gzcompanion.chest.model.ChestItemSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSortMode;
import se.jimmyeliasson.gzcompanion.chest.model.ChestTypeFilter;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainerId;
import se.jimmyeliasson.gzcompanion.ui.layout.KistorLayout;

/**
 * Local, non-persisted UI state of one Kistor tab instance. Everything here is presentation
 * state only (current mode, search text, filters, selection, scroll, inline editors).
 */
public final class KistorUiState {
    public static final int MAX_SEARCH_LENGTH = 48;

    /** Which local text field an inline editor is editing. */
    public enum EditField { LABEL, GROUP, NOTE }

    public KistorLayout.Mode mode = KistorLayout.Mode.SAKER;

    public String searchText = "";
    public boolean searchFocused = false;

    public ChestTypeFilter typeFilter = ChestTypeFilter.ALL;
    public ChestSortMode storageSort = ChestSortMode.RECENT;
    public ChestItemSortMode itemSort = ChestItemSortMode.COUNT;
    public ChestGroupFilter groupFilter = ChestGroupFilter.ALL;

    public StoredContainerId selectedStorage = null;
    public String selectedItemId = null;
    public boolean compactShowingDetail = false;

    public final ScrollState itemListScroll = new ScrollState();
    public final ScrollState itemDetailScroll = new ScrollState();
    public final ScrollState storageListScroll = new ScrollState();
    public final ScrollState storageDetailScroll = new ScrollState();
    public final ScrollState materialScroll = new ScrollState();

    public EditField editField = null;
    public String editText = "";

    public boolean confirmingForget = false;
    public long forgetConfirmExpiry = 0L;
    public long copyFeedbackExpiry = 0L;

    public boolean materialShowPickupList = false;

    /** Short-lived confirmation line in the navigation banner ("Navigering startad ..."). */
    public String bannerFlash = null;
    public long bannerFlashExpiry = 0L;

    public boolean isTextInputFocused() {
        return searchFocused || editField != null;
    }

    public int editMaxLength() {
        if (editField == null) return 0;
        return switch (editField) {
            case LABEL -> se.jimmyeliasson.gzcompanion.chest.ChestManager.MAX_LABEL_LENGTH;
            case GROUP -> se.jimmyeliasson.gzcompanion.chest.model.StorageMetadata.MAX_GROUP_LENGTH;
            case NOTE -> se.jimmyeliasson.gzcompanion.chest.model.StorageMetadata.MAX_NOTE_LENGTH;
        };
    }

    public void beginEdit(EditField field, String initial) {
        this.editField = field;
        this.editText = initial != null ? initial : "";
        this.searchFocused = false;
    }

    public void cancelEdit() {
        this.editField = null;
        this.editText = "";
    }

    public boolean isForgetConfirmActive(long nowMs) {
        return confirmingForget && nowMs < forgetConfirmExpiry;
    }

    public void flashBanner(String text, long nowMs, long durationMs) {
        this.bannerFlash = text;
        this.bannerFlashExpiry = nowMs + durationMs;
    }

    /** Opens the FÖRVARING detail for one storage location (e.g. from an item's location row). */
    public void openStorage(StoredContainerId id) {
        this.mode = KistorLayout.Mode.FORVARING;
        this.selectedStorage = id;
        this.compactShowingDetail = true;
        this.storageDetailScroll.reset();
        this.confirmingForget = false;
        cancelEdit();
    }

    public void openItem(String itemId) {
        this.mode = KistorLayout.Mode.SAKER;
        this.selectedItemId = itemId;
        this.compactShowingDetail = true;
        this.itemDetailScroll.reset();
    }

    public void switchMode(KistorLayout.Mode newMode) {
        if (newMode == null || newMode == mode) return;
        this.mode = newMode;
        this.compactShowingDetail = false;
        this.confirmingForget = false;
        cancelEdit();
    }
}
