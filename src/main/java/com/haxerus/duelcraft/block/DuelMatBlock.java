package com.haxerus.duelcraft.block;

import com.haxerus.duelcraft.interaction.ScreenRoute;
import com.haxerus.duelcraft.server.InteractionPayloadHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.UUID;

/** An entry point only: collection and preparation data belong to the player. */
public final class DuelMatBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    public DuelMatBlock(Properties properties) { super(properties.noCollission()); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        open(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                       Player player, InteractionHand hand, BlockHitResult hit) {
        open(player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    private static void open(Player player) {
        if (player instanceof ServerPlayer serverPlayer)
            InteractionPayloadHandler.open(serverPlayer, ScreenRoute.LOBBY, UUID.randomUUID(), true);
    }
}
