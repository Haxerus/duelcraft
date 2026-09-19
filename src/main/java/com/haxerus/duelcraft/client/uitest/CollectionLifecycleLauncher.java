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
    static final String POLICY_WORLD = "collection_policy_followup_20260919";
    static final String TRANSFER_WORLD = "collection_m3_transfers_20260919";
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
        boolean policy = System.getProperty("duelcraft.uitest.collectionPolicyLifecycle") != null;
        boolean transfer = System.getProperty("duelcraft.uitest.collectionTransferLifecycle") != null;
        if (transfer) phase = System.getProperty("duelcraft.uitest.collectionTransferLifecycle");
        if (policy) phase = System.getProperty("duelcraft.uitest.collectionPolicyLifecycle");
        if (FMLEnvironment.production || phase == null || step == 9) return;
        String world = transfer ? TRANSFER_WORLD : policy ? POLICY_WORLD : WORLD;
        boolean fresh = phase.equals(policy ? "default" : "seed");
        var mc = Minecraft.getInstance();
        try {
            if (started == 0) {
                started = System.currentTimeMillis();
                if (!(policy ? java.util.List.of("default", "ownership", "restricted", "removed") : java.util.List.of("seed", "verify")).contains(phase)) throw new IllegalStateException("Invalid lifecycle phase");
                Path expectedDirectory = Path.of(System.getProperty("duelcraft.uitest.lifecycleDirectory", mc.gameDirectory.toString())).toAbsolutePath().normalize();
                if (!mc.gameDirectory.toPath().toAbsolutePath().normalize().equals(expectedDirectory)
                        || !expectedDirectory.getFileName().toString().equals(transfer ? "run-collection-transfer-lifecycle"
                        : policy ? "run-collection-policy-lifecycle" : "run-collection-lifecycle")) {
                    throw new IllegalStateException("Requires isolated lifecycle gameDirectory");
                }
                if (System.getProperty("ldlib2.uitest.run") != null) throw new IllegalStateException("Automatic fresh-world runner forbidden");
                boolean exists = mc.getLevelSource().levelExists(world);
                if (fresh ? exists || Files.exists(manifest()) : !exists || !Files.exists(manifest())) {
                    throw new IllegalStateException("Seed requires absent save/manifest; verify requires both existing");
                }
                if (Files.exists(report())) throw new IllegalStateException("Archive old interactive report before launching");
                if (Files.exists(mc.gameDirectory.toPath().resolve("collection-lifecycle-failure.txt"))) throw new IllegalStateException("Archive failed lifecycle attempt before launching");
                if (phase.equals("verify") && !readManifest().get("phase").getAsString().equals("rejoined")) {
                    throw new IllegalStateException("Verify requires completed seed and same-JVM rejoin");
                }
                if (policy && !fresh) {
                    String previous = switch (phase) { case "ownership" -> "default"; case "restricted" -> "ownership"; default -> "restricted"; };
                    var manifest = readManifest();
                    var priorReport = JsonParser.parseString(Files.readString(mc.gameDirectory.toPath()
                            .resolve("lifecycle-evidence").resolve(previous).resolve("report.json"))).getAsJsonObject();
                    if (!manifest.get("phase").getAsString().equals(previous)
                            || !manifest.get("world").getAsString().equals(world)
                            || !priorReport.get("status").getAsString().equals("PASS") || !priorReport.has("finishedAt")
                            || ProcessHandle.of(manifest.get("pid").getAsLong()).map(ProcessHandle::isAlive).orElse(false)) {
                        throw new IllegalStateException("Policy stage requires passed previous phase and stopped PID");
                    }
                }
                if (policy && fresh && (Files.exists(mc.gameDirectory.toPath().resolve("config/duelcraft-server.toml"))
                        || Files.exists(mc.gameDirectory.toPath().resolve("saves").resolve(world).resolve("serverconfig/duelcraft-server.toml")))) {
                    throw new IllegalStateException("Default policy requires absent SERVER config");
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
                if (fresh) {
                    if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                    step = 1; // World creation pumps nested client frames.
                    WorldBootstrap.createFreshLevel(mc, world);
                } else step = 1;
            }
            if (step == 1 && ready(mc)) {
                step = 2;
                stage = phase;
                launch(policy);
            } else if (step == 2 && finished()) {
                archive(stage);
                if (!policy && phase.equals("seed")) {
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
                mc.createWorldOpenFlows().openWorld(world, () -> fail(mc, new IllegalStateException("Retained rejoin failed")));
            } else if (step == 4 && ready(mc)) {
                step = 5;
                stage = "rejoin";
                launch(false);
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
    private static void launch(boolean policy) {
        String error = UITestRunner.runInteractive(System.getProperty("duelcraft.uitest.collectionTransferLifecycle") != null
                ? "collection_transfer_restart" : policy ? "collection_policy_lifecycle" : "collection_retained_world");
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
        if (System.getProperty("duelcraft.uitest.collectionPolicyLifecycle") != null) {
            Files.copy(source.getParent().resolve("config/duelcraft-server.toml"), target.resolve("duelcraft-server.toml"));
        }
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
