package com.haxerus.duelcraft.duel;

import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.PlayerOptions;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import java.util.UUID;

public sealed interface PreparationCommand {
    record View() implements PreparationCommand {}
    record Invite(UUID target, DuelRule rule, @Nullable Long seed, PlayerOptions options) implements PreparationCommand {
        public Invite { Objects.requireNonNull(target); new DuelSettings(rule, seed == null ? 0 : seed, options); }
    }
    record Accept(UUID invitationId) implements PreparationCommand { public Accept { Objects.requireNonNull(invitationId); } }
    record Decline(UUID invitationId) implements PreparationCommand { public Decline { Objects.requireNonNull(invitationId); } }
    record Cancel(UUID flowId) implements PreparationCommand { public Cancel { Objects.requireNonNull(flowId); } }
    record Hand(UUID flowId, UUID roundId, FirstTurnLobby.Hand hand) implements PreparationCommand {
        public Hand { Objects.requireNonNull(flowId); Objects.requireNonNull(roundId); Objects.requireNonNull(hand); }
    }
    record First(UUID flowId, boolean goFirst) implements PreparationCommand { public First { Objects.requireNonNull(flowId); } }
}
