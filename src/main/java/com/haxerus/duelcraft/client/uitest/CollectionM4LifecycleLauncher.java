package com.haxerus.duelcraft.client.uitest;

import com.google.gson.*;
import com.haxerus.duelcraft.Duelcraft;
import com.lowdragmc.lowdraglib2.uitest.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.io.*;
import java.nio.file.*;
import java.util.List;

/** Separate M4 retained world: never opens any M2/M3 historical save. */
@EventBusSubscriber(modid = Duelcraft.MODID, value = Dist.CLIENT)
public final class CollectionM4LifecycleLauncher {
    static final String WORLD = "collection_m4_login_20260919";
    static final List<String> PHASES = List.of("default", "ownership", "throwing", "restricted", "removed");
    private static int step;
    private static long started;
    static String phase() { return System.getProperty("duelcraft.uitest.collectionM4Lifecycle"); }
    static Path directory() { return Minecraft.getInstance().gameDirectory.toPath(); }
    static JsonObject manifest() {
        try { return JsonParser.parseString(Files.readString(directory().resolve("collection-m4-phase.json"))).getAsJsonObject(); }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }
    static void manifest(JsonObject value) {
        try { Files.writeString(directory().resolve("collection-m4-phase.json"), new GsonBuilder().setPrettyPrinting().create().toJson(value)); }
        catch (IOException error) { throw new UncheckedIOException(error); }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (FMLEnvironment.production || phase() == null || step == 9) return;
        var mc = Minecraft.getInstance(); var root = directory(); var report = root.resolve("ldlib2-uitest/report.json");
        try {
            if (started == 0) {
                started = System.currentTimeMillis();
                if (!root.toAbsolutePath().normalize().getFileName().toString().equals("run-collection-m4-lifecycle")
                        || !PHASES.contains(phase()) || System.getProperty("ldlib2.uitest.run") != null) throw new IllegalStateException("Requires isolated M4 lifecycle launcher");
                if (Files.exists(report) || Files.exists(root.resolve("collection-m4-failure.txt"))) throw new IllegalStateException("Archive previous report/failure first");
                boolean fresh = phase().equals("default");
                if (fresh) {
                    if (mc.getLevelSource().levelExists(WORLD) || Files.exists(root.resolve("collection-m4-phase.json")) || Files.exists(root.resolve("config/duelcraft-server.toml"))) throw new IllegalStateException("Default requires new world and absent SERVER config");
                } else {
                    String previous = PHASES.get(PHASES.indexOf(phase()) - 1); var saved = manifest();
                    var prior = JsonParser.parseString(Files.readString(root.resolve("lifecycle-evidence/" + previous + "/report.json"))).getAsJsonObject();
                    if (!saved.get("phase").getAsString().equals(previous) || !saved.get("world").getAsString().equals(WORLD)
                            || !prior.get("status").getAsString().equals("PASS") || !prior.has("finishedAt")
                            || ProcessHandle.of(saved.get("pid").getAsLong()).map(ProcessHandle::isAlive).orElse(false)) throw new IllegalStateException("Requires passed previous phase and stopped PID");
                }
            }
            if (System.currentTimeMillis() - started > 240000) throw new IllegalStateException("M4 lifecycle timed out");
            if (step == 0) {
                if (mc.getOverlay() == null && mc.screen instanceof AccessibilityOnboardingScreen onboarding) { onboarding.onClose(); return; }
                if (phase().equals("default")) {
                    if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                    step = 1; WorldBootstrap.createFreshLevel(mc, WORLD);
                } else step = 1;
            }
            if (step == 1 && mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.getOverlay() == null) {
                step = 2;
                String error = UITestRunner.runInteractive("collection_m4_login");
                if (error != null) throw new IllegalStateException(error);
            } else if (step == 2 && !UITestRunner.isRunning() && Files.exists(report)) {
                var result = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
                if (!result.has("finishedAt") || result.get("finishedAt").isJsonNull()) return;
                if (!result.get("status").getAsString().equals("PASS")) throw new IllegalStateException("M4 login assertions failed");
                var target = root.resolve("lifecycle-evidence/" + phase());
                try (var paths = Files.walk(report.getParent())) {
                    for (var source : paths.toList()) {
                        var destination = target.resolve(report.getParent().relativize(source));
                        if (Files.isDirectory(source)) Files.createDirectories(destination); else Files.copy(source, destination);
                    }
                }
                Files.copy(root.resolve("collection-m4-phase.json"), target.resolve("collection-m4-phase.json"));
                Files.copy(root.resolve("config/duelcraft-server.toml"), target.resolve("duelcraft-server.toml"));
                Files.delete(report); step = 9; mc.stop();
            }
        } catch (Exception error) {
            step = 9; Duelcraft.LOGGER.error("M4 retained login failed", error);
            try { Files.writeString(root.resolve("collection-m4-failure.txt"), error.toString()); } catch (IOException ignored) {}
            mc.stop();
        }
    }
}
