package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.network.OpenDisplayPayload;
import java.util.Optional;
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
 * Right-click opens its display. On the back, the power button (or sneak + right-click
 * anywhere) turns it on or off, and the window into the case opens the case view.
 */
public final class AllInOneBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** The power switch is on. Picks red or green for the power button. */
	public static final BooleanProperty POWERED = BooleanProperty.create("powered");
	/** A CPU is installed. Without one the power button is faded and doesn't glow. */
	public static final BooleanProperty CPU = BooleanProperty.create("cpu");

	public AllInOneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(CPU, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, CPU);
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
		var region = BackPanel.hit(state.getValue(FACING), hit.getDirection(), hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)));
		if (region.equals(Optional.of(BackPanel.Region.POWER)) || player.isSecondaryUseActive()) {
			computer.togglePower();
		} else if (region.equals(Optional.of(BackPanel.Region.CASE))) {
			// The window into the case opens the case view.
			player.openMenu(computer);
		} else if (player instanceof ServerPlayer serverPlayer) {
			// Anywhere else: the screen wins. The client opens the display when this arrives.
			ServerPlayNetworking.send(serverPlayer, new OpenDisplayPayload(pos, computer.display().buffer().resolution()));
		}
		return InteractionResult.SUCCESS;
	}
}
