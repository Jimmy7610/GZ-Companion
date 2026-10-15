package se.jimmyeliasson.gzcompanion.minecraft;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.Toast;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VanillaToastFilterTest {

    @Test
    @DisplayName("Setting OFF (default): the unverified-chat warning is shown exactly like vanilla")
    void offShowsWarning() {
        assertFalse(VanillaToastFilter.shouldSuppress(SystemToast.SystemToastId.UNSECURE_SERVER_WARNING, false));
    }

    @Test
    @DisplayName("Setting ON: only the unverified-chat warning is hidden")
    void onHidesOnlyThatWarning() {
        assertTrue(VanillaToastFilter.shouldSuppress(SystemToast.SystemToastId.UNSECURE_SERVER_WARNING, true));
    }

    @Test
    @DisplayName("Setting ON: every other system toast id, the no-token marker and null are never hidden")
    void onNeverHidesAnythingElse() {
        for (SystemToast.SystemToastId id : new SystemToast.SystemToastId[]{
                SystemToast.SystemToastId.NARRATOR_TOGGLE, SystemToast.SystemToastId.WORLD_BACKUP,
                SystemToast.SystemToastId.PACK_LOAD_FAILURE, SystemToast.SystemToastId.WORLD_ACCESS_FAILURE,
                SystemToast.SystemToastId.PACK_COPY_FAILURE, SystemToast.SystemToastId.FILE_DROP_FAILURE,
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION, SystemToast.SystemToastId.LOW_DISK_SPACE,
                SystemToast.SystemToastId.CHUNK_LOAD_FAILURE, SystemToast.SystemToastId.CHUNK_SAVE_FAILURE}) {
            assertFalse(VanillaToastFilter.shouldSuppress(id, true), id.toString());
        }
        assertFalse(VanillaToastFilter.shouldSuppress(Toast.NO_TOKEN, true));
        assertFalse(VanillaToastFilter.shouldSuppress(null, true));
        assertFalse(VanillaToastFilter.shouldSuppress("Chat messages can't be verified", true), "Never matched by text");
    }
}
