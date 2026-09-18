package com.haxerus.duelcraft.client.uitest;

import com.google.gson.*;
import com.haxerus.duelcraft.Duelcraft;
import com.lowdragmc.lowdraglib2.uitest.UITestRunner;
import com.lowdragmc.lowdraglib2.uitest.WorldBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;

/** Opt-in disposable-world evidence only; ordinary development and production clients do nothing. */
@EventBusSubscriber(modid = Duelcraft.MODID, value = Dist.CLIENT)
public final class CollectionLifecycleLauncher {
    static final String WORLD = "collection_m2_retained_20260917";
    static String stage;
    private static int step;
    private static long started;
    private CollectionLifecycleLauncher() {}

    static Path manifest() { return Minecraft.getInstance().gameDirectory.toPath().resolve("collection-phase.json"); }
    static JsonObject readManifest() {
        try { return JsonParser.parseString(Files.readString(manifest())).getAsJsonObject(); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    static void writeManifest(JsonObject value) {
        try { Files.writeString(manifest(), new GsonBuilder().setPrettyPrinting().create().toJson(value)); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        String phase = System.getProperty("duelcraft.uitest.collectionLifecycle");
        if (FMLEnvironment.production || phase == null || step == 9) return;
        var mc = Minecraft.getInstance();
        try {
            if (started == 0) {
                started = System.currentTimeMillis();
                if (!phase.equals("seed") && !phase.equals("verify")) throw new IllegalStateException("Invalid lifecycle phase");
                if (!mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run-collection-lifecycle")) {
                    throw new IllegalStateException("Requires isolated lifecycle gameDirectory");
                }
                if (System.getProperty("ldlib2.uitest.run") != null) throw new IllegalStateException("Automatic fresh-world runner forbidden");
                boolean exists = mc.getLevelSource().levelExists(WORLD);
                if (phase.equals("seed") ? exists || Files.exists(manifest()) : !exists || !Files.exists(manifest())) {
                    throw new IllegalStateException("Seed requires absent save/manifest; verify requires both existing");
                }
                if (Files.exists(report())) throw new IllegalStateException("Archive old interactive report before launching");
                if (phase.equals("verify") && !readManifest().get("phase").getAsString().equals("rejoined")) {
                    throw new IllegalStateException("Verify requires completed seed and same-JVM rejoin");
                }
            }
            if (System.currentTimeMillis() - started > 240000) {
                throw new IllegalStateException("Lifecycle launch exceeded 240 seconds");
            }
            if (step == 0) {
                if (mc.getOverlay() == null && mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
                    onboarding.onClose(); // Continue only this disposable client's first-launch onboarding.
                    return;
                }
                if (phase.equals("seed")) {
                    if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                    step = 1; // World creation pumps nested client frames.
                    WorldBootstrap.createFreshLevel(mc, WORLD);
                } else step = 1;
            }
            if (step == 1 && ready(mc)) {
                step = 2;
                stage = phase;
                launch();
            } else if (step == 2 && finished()) {
                archive(stage);
                if (phase.equals("seed")) {
                    step = 3; // Disconnect pumps frames while the integrated server saves/stops.
                    mc.level.disconnect();
                    mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
                    mc.setScreen(new TitleScreen());
                } else { step = 9; mc.stop(); }
            } else if (step == 3 && mc.level == null && mc.getSingleplayerServer() == null && mc.getOverlay() == null) {
                if (com.haxerus.duelcraft.DuelcraftClient.getCollectionClient().state().view() != null) {
                    throw new IllegalStateException("Logout did not clear private client snapshot");
                }
                step = 4;
                mc.createWorldOpenFlows().openWorld(WORLD, () -> fail(mc, new IllegalStateException("Retained rejoin failed")));
            } else if (step == 4 && ready(mc)) {
                step = 5;
                stage = "rejoin";
                launch();
            } else if (step == 5 && finished()) {
                archive(stage);
                step = 9;
                mc.stop();
            }
        } catch (Exception exception) { fail(mc, exception); }
    }

    private static boolean ready(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && mc.getOverlay() == null;
    }
    private static Path report() { return Minecraft.getInstance().gameDirectory.toPath().resolve("ldlib2-uitest/report.json"); }
    private static void launch() {
        String error = UITestRunner.runInteractive("collection_retained_world");
        if (error != null) throw new IllegalStateException(error);
    }
    private static boolean finished() throws IOException {
        if (UITestRunner.isRunning() || !Files.exists(report())) return false;
        var report = JsonParser.parseString(Files.readString(report())).getAsJsonObject();
        if (!report.has("finishedAt") || report.get("finishedAt").isJsonNull()) return false;
        if (!report.get("status").getAsString().equals("PASS")) throw new IllegalStateException("Lifecycle scenario failed; inspect interactive report");
        return true;
    }
    private static void archive(String name) throws IOException {
        Path source = report().getParent();
        Path target = source.getParent().resolve("lifecycle-evidence").resolve(name);
        try (var paths = Files.walk(source)) {
            for (var path : paths.toList()) {
                var destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.copy(manifest(), target.resolve("collection-phase.json"), StandardCopyOption.REPLACE_EXISTING);
        Files.delete(report()); // A previous completed report must never finish the next phase.
    }
    private static void fail(Minecraft mc, Exception exception) {
        step = 9;
        Duelcraft.LOGGER.error("Retained collection lifecycle failed", exception);
        try { Files.writeString(mc.gameDirectory.toPath().resolve("collection-lifecycle-failure.txt"), exception.toString()); }
        catch (IOException writeFailure) { Duelcraft.LOGGER.error("Cannot write lifecycle failure marker", writeFailure); }
        mc.stop();
    }
}
