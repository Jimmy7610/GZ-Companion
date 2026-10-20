package se.jimmyeliasson.gzcompanion.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.TextInputManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.jimmyeliasson.gzcompanion.chest.ChestManager;
import se.jimmyeliasson.gzcompanion.chest.model.ChestSlotEntry;
import se.jimmyeliasson.gzcompanion.chest.model.StorageKind;
import se.jimmyeliasson.gzcompanion.chest.model.StorageShape;
import se.jimmyeliasson.gzcompanion.chest.model.StoragePosition;
import se.jimmyeliasson.gzcompanion.chest.model.StoredContainer;
import se.jimmyeliasson.gzcompanion.core.CompanionConstants;
import se.jimmyeliasson.gzcompanion.core.CompanionSession;
import se.jimmyeliasson.gzcompanion.diagnostics.CompatibilityStatus;
import se.jimmyeliasson.gzcompanion.diagnostics.ModuleReport;
import se.jimmyeliasson.gzcompanion.guide.bridge.GuidePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.guide.bridge.MinecraftGuideSnapshotProvider;
import se.jimmyeliasson.gzcompanion.guide.model.GuideLoadStatus;
import se.jimmyeliasson.gzcompanion.keybind.KeybindHandler;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.ClientRecipeSnapshot;
import se.jimmyeliasson.gzcompanion.knowledge.crafting.bridge.MinecraftRecipeDisplayAdapter;
import se.jimmyeliasson.gzcompanion.minecraft.OnlinePlayerSnapshot;
import se.jimmyeliasson.gzcompanion.minecraft.VanillaMinecraftBridge;
import se.jimmyeliasson.gzcompanion.mixin.BossHealthOverlayAccessor;
import se.jimmyeliasson.gzcompanion.ui.GZCompanionMainScreen;
import se.jimmyeliasson.gzcompanion.ui.TabType;
import se.jimmyeliasson.gzcompanion.ui.layout.UiRect;
import se.jimmyeliasson.gzcompanion.ui.theme.UiTheme;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Runtime verification of GZ Companion on the real Minecraft client (client game test API).
 *
 * <p>Run {@code gradlew runClientGameTest} twice. The first run plays through every Minecraft-facing
 * feature in a fresh singleplayer world with real simulated input and leaves a marker; the second run
 * (a new JVM = a real game restart) verifies that settings and captured storage data were persisted
 * and load again, then removes the marker. Only fair-play paths are used: storages are opened by a
 * real "use" key press on a block the player looks at, exactly like a player would.
 */
@SuppressWarnings("UnstableApiUsage")
public class GZCompanionClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("gzcompanion-gametest");
    private static final String PLAYER = "GZTester";
    private static final String MARKER = "gzcompanion-gametest-restart.properties";

    private final List<String> failures = new ArrayList<>();
    private int passed;

    @Override
    public void runTest(ClientGameTestContext context) {
        Path marker = FabricLoader.getInstance().getGameDir().resolve(MARKER);
        try {
            if (Files.exists(marker)) {
                verifyAfterRestart(context, marker);
            } else {
                firstRun(context, marker);
            }
        } catch (IOException e) {
            fail("I/O: " + e);
        }
        LOG.info("[GZ-GAMETEST] SUMMARY passed={} failed={}", passed, failures.size());
        if (!failures.isEmpty()) {
            throw new AssertionError("GZ Companion runtime checks failed:\n - " + String.join("\n - ", failures));
        }
    }

    // ------------------------------------------------------------------------------------------
    private void firstRun(ClientGameTestContext context, Path marker) throws IOException {
        TestInput input = context.getInput();

        // 1. Startup: real versions, data packs compatible, mixins applied (launch would fail otherwise).
        context.runOnClient(mc -> {
            CompanionSession session = CompanionSession.getInstance();
            check("26.3".equals(CompanionConstants.runningMinecraftVersion()), "running Minecraft version is 26.3 (got " + CompanionConstants.runningMinecraftVersion() + ")");
            check(session.getGuideEngine().getLoadStatus() == GuideLoadStatus.LOADED, "Guide loads on 26.3, incl. item/tag registry validation (got " + session.getGuideEngine().getLoadStatus() + ")");
            Optional<ModuleReport> mcReport = session.getCompatibilityResult().moduleReports().stream().filter(r -> r.moduleId().equals("minecraft")).findFirst();
            check(mcReport.isPresent() && mcReport.get().status() == CompatibilityStatus.COMPATIBLE, "rule pack reports Minecraft 26.3 as compatible (" + mcReport.map(ModuleReport::details).orElse("missing") + ")");
            check(!session.getUsageHeartbeatService().isActive(), "usage counter is inactive in the development environment (no production heartbeat from test runs)");
            check(KeybindHandler.getOpenMenuKey().getDefaultKey().getValue() == InputConstants.KEY_G, "Companion key defaults to G (SDL KEY_G)");
            session.getSettingsManager().setUiTheme(UiTheme.MINECRAFT);
        });
        context.takeScreenshot("gz-00-title-screen");

        // 2. World: a cleared platform so every storage is reachable and visible.
        TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create();
        TestServerContext server = sp.getServer();
        server.runCommand("gamemode creative " + PLAYER);
        server.runCommand("fill -6 99 -6 6 99 6 minecraft:stone");
        server.runCommand("fill -6 100 -6 6 106 6 minecraft:air");
        server.runCommand("tp " + PLAYER + " 0.5 100 0.5 0 0");
        sp.getConnection().waitForChunksRender();
        context.waitTicks(10);
        String contextKey = context.computeOnClient(mc -> CompanionSession.getInstance().getCurrentStorageContext());
        check(contextKey != null && contextKey.contains("@@singleplayer:"), "singleplayer storage context resolved (" + contextKey + ")");
        // Start from an empty index so no data from an earlier run can make a capture check pass.
        context.runOnClient(mc -> CompanionSession.getInstance().getChestManager().clearContext(contextKey));
        check(context.computeOnClient(mc -> CompanionSession.getInstance().getChestManager().getContainers(contextKey).isEmpty()), "storage index starts empty for this run");

        // 3. G opens / G closes / Esc closes - through the real key path.
        openCompanionWithKey(context);
        input.pressKey(InputConstants.KEY_G);
        context.waitTicks(3);
        check(context.computeOnClient(mc -> mc.gui.screen() == null), "G closes the Companion");
        openCompanionWithKey(context);
        input.pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        check(context.computeOnClient(mc -> mc.gui.screen() == null), "Esc closes the Companion");

        // 4. Every tab in both themes (screenshots in build/run/clientGameTest/screenshots).
        openCompanionWithKey(context);
        for (UiTheme theme : List.of(UiTheme.MINECRAFT, UiTheme.TYDLIG)) {
            context.runOnClient(mc -> CompanionSession.getInstance().getSettingsManager().setUiTheme(theme));
            for (TabType tab : TabType.values()) {
                context.runOnClient(mc -> ((GZCompanionMainScreen) mc.gui.screen()).setActiveTab(tab));
                context.waitTicks(3);
                context.takeScreenshot("gz-tab-" + theme.id() + "-" + tab.name().toLowerCase());
            }
        }
        check(context.computeOnClient(mc -> mc.gui.screen() instanceof GZCompanionMainScreen), "all " + TabType.values().length + " tabs rendered in both themes without closing/crashing");

        // 5. Real mouse input (26.3: left = 1) + SDL text input focus.
        context.runOnClient(mc -> ((GZCompanionMainScreen) mc.gui.screen()).setActiveTab(TabType.HEM));
        context.waitTicks(2);
        UiRect kistorTabRect = context.computeOnClient(mc -> ((GZCompanionMainScreen) mc.gui.screen()).getLayout().tabRects()[TabType.KISTOR.ordinal()]);
        clickGui(context, kistorTabRect);
        check(context.computeOnClient(mc -> ((GZCompanionMainScreen) mc.gui.screen()).getActiveTab() == TabType.KISTOR), "a real left click on the Kistor tab switches tabs");
        context.waitTicks(2);
        UiRect searchRect = context.computeOnClient(mc -> ((GZCompanionMainScreen) mc.gui.screen()).getKistorTab().getLayout().searchRect());
        clickGui(context, searchRect);
        check(context.computeOnClient(mc -> ((GZCompanionMainScreen) mc.gui.screen()).getKistorTab().isSearchFocused()), "a real left click focuses the Kistor search field (tab left-click guard works with SDL buttons)");
        check(context.computeOnClient(GZCompanionClientGameTest::textInputEnabled), "focusing a Companion text field starts Minecraft's SDL text input");
        input.pressKey(InputConstants.KEY_G);
        context.waitTicks(2);
        check(context.computeOnClient(mc -> mc.gui.screen() instanceof GZCompanionMainScreen), "G does not close the Companion while a text field is focused");
        input.typeChars("guld");
        context.waitTicks(2);
        check("guld".equals(context.computeOnClient(mc -> ((GZCompanionMainScreen) mc.gui.screen()).getKistorTab().getSearchText())), "typed characters arrive in the search field (Å/Ä/Ö-capable char path)");
        context.takeScreenshot("gz-kistor-search-typed");
        input.pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(2);
        check(!context.computeOnClient(mc -> ((GZCompanionMainScreen) mc.gui.screen()).getKistorTab().isSearchFocused()), "Esc unfocuses the search field");
        check(!context.computeOnClient(GZCompanionClientGameTest::textInputEnabled), "unfocusing stops SDL text input again");
        input.pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        check(context.computeOnClient(mc -> mc.gui.screen() == null), "second Esc closes the Companion");

        // 6. Kistor capture of every supported storage, opened by a real "use" press.
        record Storage(StorageKind kind, BlockPos pos, String block, String item, int count, int slot) {}
        List<Storage> storages = List.of(
                new Storage(StorageKind.CHEST, new BlockPos(-2, 100, 2), "minecraft:chest[facing=north]", "minecraft:gold_ingot", 5, 0),
                new Storage(StorageKind.TRAPPED_CHEST, new BlockPos(0, 100, 2), "minecraft:trapped_chest[facing=north]", "minecraft:iron_ingot", 7, 1),
                new Storage(StorageKind.BARREL, new BlockPos(2, 100, 2), "minecraft:barrel[facing=up]", "minecraft:oak_log", 12, 2),
                new Storage(StorageKind.SHULKER_BOX, new BlockPos(-2, 100, -2), "minecraft:shulker_box[facing=up]", "minecraft:diamond", 3, 3),
                new Storage(StorageKind.HOPPER, new BlockPos(0, 100, -2), "minecraft:hopper", "minecraft:redstone", 9, 4),
                new Storage(StorageKind.DISPENSER, new BlockPos(2, 100, -2), "minecraft:dispenser[facing=up]", "minecraft:arrow", 16, 5),
                new Storage(StorageKind.DROPPER, new BlockPos(-3, 100, 0), "minecraft:dropper[facing=up]", "minecraft:coal", 4, 6));
        for (Storage s : storages) {
            setBlock(server, s.pos(), s.block());
            server.runCommand("item replace block " + s.pos().getX() + " " + s.pos().getY() + " " + s.pos().getZ() + " container." + s.slot() + " with " + s.item() + " " + s.count());
        }
        setBlock(server, new BlockPos(3, 100, 0), "minecraft:furnace[facing=west]");
        server.runCommand("item replace block 3 100 0 container.0 with minecraft:raw_iron 8");
        context.waitTicks(5);

        for (Storage s : storages) {
            openAndCloseStorage(context, s.pos());
            Optional<StoredContainer> stored = findStored(context, contextKey, s.kind(), s.pos());
            check(stored.isPresent(), s.kind() + " opened by the player is indexed");
            stored.ifPresent(c -> check(hasItem(c, s.item(), s.count(), s.slot()), s.kind() + " captured " + s.count() + "x " + s.item() + " in slot " + s.slot() + " (got " + c.slots() + ")"));
        }
        int countBeforeFurnace = context.computeOnClient(mc -> CompanionSession.getInstance().getChestManager().getContainers(contextKey).size());
        openAndCloseStorage(context, new BlockPos(3, 100, 0));
        int countAfterFurnace = context.computeOnClient(mc -> CompanionSession.getInstance().getChestManager().getContainers(contextKey).size());
        check(countAfterFurnace == countBeforeFurnace, "a furnace (not on the storage allow-list) is never indexed");

        // Double chest: both halves, slot in the second half (index > 26).
        server.runCommand("fill -2 100 2 2 100 2 minecraft:air");
        setBlock(server, new BlockPos(0, 100, 2), "minecraft:chest[facing=north,type=left]");
        setBlock(server, new BlockPos(1, 100, 2), "minecraft:chest[facing=north,type=right]");
        server.runCommand("item replace block 1 100 2 container.3 with minecraft:emerald 2");
        server.runCommand("item replace block 0 100 2 container.0 with minecraft:lapis_lazuli 6");
        context.waitTicks(5);
        openAndCloseStorage(context, new BlockPos(0, 100, 2));
        Optional<StoredContainer> dbl = findStored(context, contextKey, StorageKind.CHEST, new BlockPos(0, 100, 2));
        check(dbl.isPresent() && dbl.get().shape() == StorageShape.DOUBLE && new StoragePosition(1, 100, 2).equals(dbl.get().partner()),
                "double chest captured as DOUBLE with its partner (" + dbl.map(c -> c.shape() + " " + c.partner()).orElse("missing") + ")");
        dbl.ifPresent(c -> check(c.slots().stream().anyMatch(e -> e.itemId().equals("minecraft:emerald") && e.count() == 2)
                && c.slots().stream().anyMatch(e -> e.itemId().equals("minecraft:lapis_lazuli") && e.count() == 6), "double chest captured items from both halves"));

        // Search over captured data + the Kistor tab with real data in both themes.
        check(context.computeOnClient(mc -> CompanionSession.getInstance().getChestManager().search(contextKey, "diamond").size()) >= 1, "Kistor search finds a captured item");
        openCompanionWithKey(context);
        context.runOnClient(mc -> ((GZCompanionMainScreen) mc.gui.screen()).setActiveTab(TabType.KISTOR));
        context.waitTicks(3);
        context.takeScreenshot("gz-kistor-with-data-tydlig");
        input.pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);

        // 7. Hitta: navigation toward a captured storage, HUD drawn, stops when the target is opened.
        // The shulker box still stands (the chest row was rebuilt for the double chest).
        StoredContainer target = findStored(context, contextKey, StorageKind.SHULKER_BOX, new BlockPos(-2, 100, -2)).orElse(null);
        if (target != null) {
            boolean started = context.computeOnClient(mc -> {
                CompanionSession session = CompanionSession.getInstance();
                return session.getKistorRuntime().navigation().start(session.getChestManager(), contextKey, target.id(), System.currentTimeMillis());
            });
            check(started, "Hitta navigation starts toward a captured storage");
            context.waitTicks(10);
            context.takeScreenshot("gz-hitta-hud");
            check(context.computeOnClient(mc -> CompanionSession.getInstance().getKistorRuntime().navigation().isActive()), "Hitta stays active while walking/looking around");
            BlockPos tp = new BlockPos(target.id().anchor().x(), target.id().anchor().y(), target.id().anchor().z());
            openAndCloseStorage(context, tp);
            check(!context.computeOnClient(mc -> CompanionSession.getInstance().getKistorRuntime().navigation().isActive()), "Hitta stops when the target storage is legitimately opened");
        } else {
            fail("no captured storage available for Hitta");
        }

        // 8. Mixin accessors at runtime: boss bars (nav HUD offset) and the TAB header (settlement/online data).
        server.runCommand("bossbar add gzcompanion:test \"GZ test\"");
        server.runCommand("bossbar set gzcompanion:test players " + PLAYER);
        context.waitTicks(10);
        int bossBars = context.computeOnClient(mc -> ((BossHealthOverlayAccessor) mc.gui.hud.getBossOverlay()).gzcompanion$getEvents().size());
        check(bossBars == 1, "BossHealthOverlayAccessor reads the client's boss bars (got " + bossBars + ")");
        server.runCommand("bossbar remove gzcompanion:test");
        Optional<String> header = context.computeOnClient(mc -> {
            mc.gui.hud.getTabList().setHeader(Component.literal("Settlement: Testby"));
            return new VanillaMinecraftBridge().getTabHeaderText();
        });
        check(header.isPresent() && header.get().equals("Settlement: Testby"), "PlayerTabOverlayAccessor reads the TAB header (" + header + ")");

        // 9. Online list (only the vanilla listed players) and server context.
        List<OnlinePlayerSnapshot> online = context.computeOnClient(mc -> new VanillaMinecraftBridge().getOnlinePlayers());
        check(online.stream().anyMatch(p -> p.username().equals(PLAYER) && p.localPlayer()), "online list shows the listed local player (" + online + ")");
        check(context.computeOnClient(mc -> new VanillaMinecraftBridge().getCurrentServerAddress()).isPresent(), "server/world context is detected");

        // 10. Toast mixin: the optional "hide unverified chat warning" hides exactly that toast.
        boolean hiddenWhenOn = context.computeOnClient(mc -> {
            CompanionSession.getInstance().getSettingsManager().setHideUnverifiedChatWarning(true);
            mc.gui.toastManager().clear();
            SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.UNSECURE_SERVER_WARNING, Component.literal("t"), Component.literal("m"));
            return mc.gui.toastManager().getToast(SystemToast.class, SystemToast.SystemToastId.UNSECURE_SERVER_WARNING) == null;
        });
        boolean shownWhenOff = context.computeOnClient(mc -> {
            CompanionSession.getInstance().getSettingsManager().setHideUnverifiedChatWarning(false);
            mc.gui.toastManager().clear();
            SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.UNSECURE_SERVER_WARNING, Component.literal("t"), Component.literal("m"));
            boolean present = mc.gui.toastManager().getToast(SystemToast.class, SystemToast.SystemToastId.UNSECURE_SERVER_WARNING) != null;
            mc.gui.toastManager().clear();
            return present;
        });
        check(hiddenWhenOn && shownWhenOff, "ToastManagerMixin hides only while the setting is on (on=" + hiddenWhenOn + ", off-shown=" + shownWhenOff + ")");

        // 11. Guide: legitimate inventory-based detection (items + tags).
        server.runCommand("give " + PLAYER + " minecraft:oak_log 4");
        server.runCommand("give " + PLAYER + " minecraft:bread 1");
        context.waitTicks(20);
        GuidePlayerSnapshot snapshot = context.computeOnClient(mc -> new MinecraftGuideSnapshotProvider().createSnapshot());
        check(snapshot.itemCounts().getOrDefault("minecraft:oak_log", 0) >= 4, "Guide snapshot counts inventory items (" + snapshot.itemCounts() + ")");
        check(snapshot.tagCounts().getOrDefault("minecraft:logs", 0) >= 4, "Guide snapshot resolves item tags (" + snapshot.tagCounts() + ")");
        check(snapshot.hasEdibleItem(), "Guide snapshot detects food via DataComponents.FOOD");
        context.runOnClient(mc -> CompanionSession.getInstance().evaluateGuide());

        // 12. Crafting: the client's own recipe book.
        server.runCommand("recipe give " + PLAYER + " *");
        context.waitTicks(20);
        List<ClientRecipeSnapshot> recipes = context.computeOnClient(mc -> MinecraftRecipeDisplayAdapter.readClientRecipeBook());
        check(recipes.size() > 100, "Crafting reads the client recipe book (" + recipes.size() + " recipes)");

        // 13. Reconnect: leave and rejoin the world - transient state cleared, persisted data still there.
        int capturedCount = context.computeOnClient(mc -> CompanionSession.getInstance().getChestManager().getContainers(contextKey).size());
        context.runOnClient(mc -> CompanionSession.getInstance().getSettingsManager().setUiTheme(UiTheme.TYDLIG));
        TestWorldSave save = sp.getWorldSave();
        sp.close();
        context.waitTicks(5);
        check(!context.computeOnClient(mc -> CompanionSession.getInstance().getChestManager().isCaptureActive()), "disconnect clears transient capture state");
        TestSingleplayerContext rejoined = save.open();
        rejoined.getConnection().waitForChunksRender();
        context.waitTicks(10);
        check(contextKey.equals(context.computeOnClient(mc -> CompanionSession.getInstance().getCurrentStorageContext())), "rejoin resolves the same storage context");
        check(context.computeOnClient(mc -> CompanionSession.getInstance().getChestManager().getContainers(contextKey).size()) == capturedCount, "captured storages are still known after reconnect (" + capturedCount + ")");
        openCompanionWithKey(context);
        context.takeScreenshot("gz-after-reconnect");
        input.pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(3);
        rejoined.close();

        Properties props = new Properties();
        props.setProperty("contextKey", contextKey);
        props.setProperty("capturedCount", Integer.toString(capturedCount));
        props.setProperty("theme", UiTheme.TYDLIG.id());
        try (Writer w = Files.newBufferedWriter(marker)) {
            props.store(w, "GZ Companion client game test - verified on the next run (game restart)");
        }
        LOG.info("[GZ-GAMETEST] first run complete - run runClientGameTest again to verify persistence after a restart");
    }

    private void verifyAfterRestart(ClientGameTestContext context, Path marker) throws IOException {
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(marker)) {
            props.load(r);
        }
        String contextKey = props.getProperty("contextKey");
        int expected = Integer.parseInt(props.getProperty("capturedCount"));
        context.runOnClient(mc -> {
            CompanionSession session = CompanionSession.getInstance();
            check(session.getSettingsManager().getSettings().uiTheme().id().equals(props.getProperty("theme")), "theme persisted across a game restart (" + session.getSettingsManager().getSettings().uiTheme() + ")");
            int count = session.getChestManager().getContainers(contextKey).size();
            check(count == expected, "captured storages load again after a game restart (" + count + "/" + expected + ")");
            check(session.getGuideEngine().getLoadStatus() == GuideLoadStatus.LOADED, "Guide loads after restart");
            session.getSettingsManager().setUiTheme(UiTheme.MINECRAFT);
        });
        context.setScreen(GZCompanionMainScreen::new);
        context.waitTicks(5);
        context.takeScreenshot("gz-after-restart");
        context.setScreen(() -> null);
        Files.deleteIfExists(marker);
        LOG.info("[GZ-GAMETEST] restart verification complete");
    }

    // ------------------------------------------------------------------------------------------
    private void openCompanionWithKey(ClientGameTestContext context) {
        context.getInput().pressKey(InputConstants.KEY_G);
        context.waitFor(mc -> mc.gui.screen() instanceof GZCompanionMainScreen, 100);
        check(context.computeOnClient(mc -> mc.gui.screen() instanceof GZCompanionMainScreen), "G opens the Companion");
    }

    /** A real left click at the centre of a GUI-space rectangle. */
    private static void clickGui(ClientGameTestContext context, UiRect rect) {
        double[] window = context.computeOnClient(mc -> {
            double sx = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            double sy = (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
            return new double[]{(rect.x() + rect.width() / 2.0) * sx, (rect.y() + rect.height() / 2.0) * sy};
        });
        context.getInput().setCursorPos(window[0], window[1]);
        context.waitTick();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private void openAndCloseStorage(ClientGameTestContext context, BlockPos pos) {
        context.getInput().lookAt(pos);
        context.waitTicks(2);
        context.getInput().pressKey(options -> options.keyUse);
        int waited;
        try {
            waited = context.waitFor(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>, 100);
        } catch (AssertionError timeout) {
            fail("storage at " + pos + " did not open a container screen");
            return;
        }
        context.waitTicks(3);
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitFor(mc -> mc.gui.screen() == null, 100);
        context.waitTicks(3);
        LOG.info("[GZ-GAMETEST] opened storage at {} after {} ticks", pos, waited);
    }

    private static Optional<StoredContainer> findStored(ClientGameTestContext context, String contextKey, StorageKind kind, BlockPos pos) {
        return context.computeOnClient(mc -> {
            ChestManager manager = CompanionSession.getInstance().getChestManager();
            StoragePosition p = new StoragePosition(pos.getX(), pos.getY(), pos.getZ());
            return manager.getContainers(contextKey).stream()
                    .filter(c -> c.id().kind() == kind && c.id().anchor().equals(p))
                    .findFirst();
        });
    }

    private static boolean hasItem(StoredContainer container, String itemId, int count, int slot) {
        for (ChestSlotEntry e : container.slots()) {
            if (e.itemId().equals(itemId) && e.count() == count && e.slotIndex() == slot) return true;
        }
        return false;
    }

    private static void setBlock(TestServerContext server, BlockPos pos, String block) {
        server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
    }

    /** Minecraft's own record of whether SDL text input is running (private; read for verification only). */
    private static boolean textInputEnabled(Minecraft mc) {
        try {
            Field field = TextInputManager.class.getDeclaredField("textInputEnabled");
            field.setAccessible(true);
            return field.getBoolean(mc.textInputManager());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read TextInputManager.textInputEnabled", e);
        }
    }

    private void check(boolean condition, String description) {
        if (condition) {
            passed++;
            LOG.info("[GZ-GAMETEST] PASS {}", description);
        } else {
            fail(description);
        }
    }

    private void fail(String description) {
        failures.add(description);
        LOG.error("[GZ-GAMETEST] FAIL {}", description);
    }
}
