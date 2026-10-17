package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.interaction.ScreenRoute;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

/** A route is navigation advice, never permission to mutate collection state. */
public final class InteractionPayloadHandler {
    private InteractionPayloadHandler() {}
    public static ScreenRoute resolve(ScreenRoute requested, boolean live, boolean preparing) {
        return live ? ScreenRoute.DUEL : preparing ? ScreenRoute.LOBBY : requested;
    }
    public static void handle(InteractionRequestPayload request, IPayloadContext context) {
        open((ServerPlayer) context.player(), request.requested(), request.requestId(), false);
    }
    public static void open(ServerPlayer player, ScreenRoute requested, UUID requestId, boolean unsolicited) {
        var view = DuelManager.get().preparation().view(player.getUUID(), System.currentTimeMillis());
        boolean preparing = view.mode() == PreparationView.Mode.RPS || view.mode() == PreparationView.Mode.FIRST_CHOICE
                || view.mode() == PreparationView.Mode.STARTING;
        PacketDistributor.sendToPlayer(player, new InteractionStatePayload(requestId,
                resolve(requested, view.mode() == PreparationView.Mode.DUEL, preparing), view, unsolicited));
    }
}
