package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.network.OpenDisplayPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A single-block computer: screen and keyboard on the front face, parts soldered inside.
 * Right-click opens its display; sneak + right-click turns it on or off.
 */
public final class AllInOneBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public AllInOneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// The front faces the player who placed it.
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AllInOneBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof AllInOneBlockEntity computer)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			// Sneak + right-click: power switch.
			computer.togglePower();
		} else if (player instanceof ServerPlayer serverPlayer) {
			// Right-click: the client opens the display when this arrives.
			ServerPlayNetworking.send(serverPlayer, new OpenDisplayPayload(pos, computer.machine().resolution()));
		}
		return InteractionResult.SUCCESS;
	}
}
