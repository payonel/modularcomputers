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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A single-block computer: screen and keyboard on the front face, parts soldered inside.
 * Right-click opens its display. The power button on the back (or sneak + right-click
 * anywhere) turns it on or off.
 */
public final class AllInOneBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** Mirrors the machine's power so the model can light the power button. */
	public static final BooleanProperty RUNNING = BooleanProperty.create("running");

	public AllInOneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RUNNING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, RUNNING);
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
		if (PowerButton.isHit(state.getValue(FACING), hit.getDirection(), hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)))
			|| player.isSecondaryUseActive()) {
			computer.togglePower();
		} else if (player instanceof ServerPlayer serverPlayer) {
			// Right-click: the client opens the display when this arrives.
			ServerPlayNetworking.send(serverPlayer, new OpenDisplayPayload(pos, computer.display().buffer().resolution()));
		}
		return InteractionResult.SUCCESS;
	}
}
