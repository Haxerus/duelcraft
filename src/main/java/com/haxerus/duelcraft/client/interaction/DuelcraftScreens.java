package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.DuelScreen;
import com.haxerus.duelcraft.client.LDLibDuelScreen;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.duel.PreparationResult;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.interaction.ScreenRoute;
import com.haxerus.duelcraft.server.InteractionRequestPayload;
import com.haxerus.duelcraft.server.InteractionStatePayload;
import com.haxerus.duelcraft.server.PreparationStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Connection-scoped navigation and the one suspended editor session. */
public final class DuelcraftScreens {
    private static final ScreenRequests requests = new ScreenRequests();
    private static CollectionScreen suspended;
    private static ScreenRoute collectionParent = ScreenRoute.HOME;
    private DuelcraftScreens() {}

    public static void request(ScreenRoute route) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Runnable send = () -> PacketDistributor.sendToServer(new InteractionRequestPayload(requests.begin(), route));
        if (mc.screen instanceof CollectionScreen editor) editor.navigate(send);
        else send.run();
    }

    public static void apply(InteractionStatePayload reply) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var previous = DuelcraftClient.preparation().view();
        if (previous != null && reply.preparation().revision() < previous.revision()) return;
        if (!requests.accept(reply.requestId(), reply.unsolicited(), mc.screen == null)) return;
        DuelcraftClient.preparation().receive(new PreparationStatePayload(null, PreparationResult.OK, reply.preparation()));
        if (reply.resolved() == ScreenRoute.DUEL) { openDuel(); return; }
        if (preparing(reply.preparation())) { forcePreparation(); return; }
        navigate(requests.generation(), () -> { switch (reply.resolved()) {
            case HOME -> mc.setScreen(HomeScreen.create());
            case LOBBY -> mc.setScreen(DuelLobbyScreen.create());
            case COLLECTION -> {
                if (mc.screen instanceof DuelLobbyScreen) collectionParent = ScreenRoute.LOBBY;
                else collectionParent = ScreenRoute.HOME;
                openCollection();
            }
            case DUEL -> throw new IllegalStateException("Handled above");
        } });
    }

    /** Recheck at replacement time: a route/refresh round trip can outlive further edits. */
    private static void navigate(long generation, Runnable action) {
        Runnable current = () -> {
            if (generation == requests.generation() && !managementBlocked()) action.run();
        };
        if (Minecraft.getInstance().screen instanceof CollectionScreen editor) editor.navigate(current);
        else current.run();
    }

    private static void openCollection() {
        var mc = Minecraft.getInstance();
        long generation = requests.generation();
        var player = mc.player;
        DuelcraftClient.getCollectionClient().refresh().whenCompleteAsync((view, error) -> {
            if (player == null || mc.player != player || generation != requests.generation() || managementBlocked()) return;
            if (error != null) { message("Could not open your collection. Try again."); return; }
            navigate(generation, () -> {
                if (!restoreEditor()) {
                    var editor = CollectionScreen.create(view);
                    editor.setBackAction(() -> request(collectionParent));
                    mc.setScreen(editor);
                }
            });
        }, mc::execute);
    }

    public static boolean preparing(PreparationView view) {
        return view != null && (view.mode() == PreparationView.Mode.RPS
                || view.mode() == PreparationView.Mode.FIRST_CHOICE || view.mode() == PreparationView.Mode.STARTING);
    }
    public static boolean managementBlocked() {
        var view = DuelcraftClient.preparation().view();
        return LDLibDuelScreen.isDuelLive() || preparing(view) || view != null && view.mode() == PreparationView.Mode.DUEL;
    }
    public static long generation() { return requests.generation(); }
    public static boolean hasDraft() { return suspended != null; }
    public static void suspendEditor() {
        requests.invalidate();
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof CollectionScreen editor) {
            if (suspended != null && suspended != editor) suspended.disconnect();
            suspended = editor;
            editor.suspend();
        }
    }
    public static boolean restoreEditor() {
        if (managementBlocked() || suspended == null) return false;
        var editor = suspended; suspended = null;
        editor.resume();
        Minecraft.getInstance().setScreen(editor);
        return true;
    }
    private static void forcePreparation() {
        suspendEditor();
        var mc = Minecraft.getInstance();
        if (!(mc.screen instanceof DuelLobbyScreen)) mc.setScreen(DuelLobbyScreen.create());
    }
    private static void openDuel() {
        suspendEditor();
        var screen = LDLibDuelScreen.reopen();
        if (screen != null) Minecraft.getInstance().setScreen(screen);
        else {
            Minecraft.getInstance().setScreen(DuelLobbyScreen.create());
            message("Your duel is active, but its screen state is unavailable. Reconnect or cancel the duel.");
        }
    }
    public static void preparationChanged(PreparationStatePayload state) {
        var mc = Minecraft.getInstance();
        if (preparing(state.view())) forcePreparation();
        if (state.result() == PreparationResult.START_FAILED) {
            requests.invalidate();
            LDLibDuelScreen.close();
            if (mc.screen instanceof DuelScreen) mc.setScreen(DuelLobbyScreen.create());
        }
    }
    public static void disconnect() {
        requests.invalidate();
        if (suspended != null) suspended.disconnect();
        suspended = null;
        collectionParent = ScreenRoute.HOME;
        DuelLobbyScreen.clearForm();
    }
    private static void message(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal(message));
    }
}
