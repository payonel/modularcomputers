package com.payonel.modularcomputers.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A cable: joins computers to the devices they use (screens, for now). It reaches out to
 * every neighbor that takes a cable on that side (other cables, and screens and computers
 * anywhere but their front), and its model shows each of those connections.
 */
public final class CableBlock extends PipeBlock {
	/** Thickness, in pixels. */
	private static final float THICKNESS = 4.0f;

	public CableBlock(Properties properties) {
		super(THICKNESS, properties);
		BlockState state = stateDefinition.any();
		for (var property : PROPERTY_BY_DIRECTION.values()) {
			state = state.setValue(property, false);
		}
		registerDefaultState(state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withConnections(context.getLevel(), context.getClickedPos(), defaultBlockState());
	}

	/** Joins every neighbor that takes a cable. */
	public static BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
		for (Direction side : Direction.values()) {
			state = state.setValue(PROPERTY_BY_DIRECTION.get(side), Cabling.hasPort(level.getBlockState(pos.relative(side)), side.getOpposite()));
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
		BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		return state.setValue(PROPERTY_BY_DIRECTION.get(side), Cabling.hasPort(neighbor, side.getOpposite()));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(this)) {
			Cabling.changed();
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		Cabling.changed();
	}
}
