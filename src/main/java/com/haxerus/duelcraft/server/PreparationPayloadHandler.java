package com.haxerus.duelcraft.server;

import com.haxerus.duelcraft.duel.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.UUID;

/** Commands and packets execute this same authenticated action dispatch on MAIN. */
public final class PreparationPayloadHandler {
    private PreparationPayloadHandler() {}
    public static void handle(PreparationRequestPayload payload, IPayloadContext context) {
        var actor = ((ServerPlayer) context.player()).getUUID();
        context.reply(apply(DuelManager.get().preparation(), actor, payload, System.currentTimeMillis()));
    }
    public static PreparationStatePayload apply(DuelPreparationService service, UUID actor, PreparationRequestPayload request, long now) {
        var result = switch (request.command()) {
            case PreparationCommand.View ignored -> PreparationResult.OK;
            case PreparationCommand.Invite invite -> service.invite(actor, invite.target(), invite.rule(), invite.seed(), invite.options(), now).code();
            case PreparationCommand.Accept accept -> service.accept(actor, accept.invitationId(), now).code();
            case PreparationCommand.Decline decline -> service.decline(actor, decline.invitationId(), now).code();
            case PreparationCommand.Cancel cancel -> service.cancel(actor, cancel.flowId(), now).code();
            case PreparationCommand.Hand hand -> service.hand(actor, hand.flowId(), hand.roundId(), hand.hand(), now).code();
            case PreparationCommand.First first -> service.first(actor, first.flowId(), first.goFirst(), now).code();
        };
        return new PreparationStatePayload(request.requestId(), result, service.view(actor, now));
    }
}
