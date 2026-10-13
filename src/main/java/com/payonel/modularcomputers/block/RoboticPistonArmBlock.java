package com.payonel.modularcomputers.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A robotic piston's arm, out while it pushes ({@link RoboticPistonBlock#push}): a piston head,
 * shaped as vanilla's, facing the way it pushed, sticky on a sticky piston. It can't be pushed, and
 * breaks to nothing; its piston takes it back when the push is over.
 */
public final class RoboticPistonArmBlock extends DirectionalBlock {
	public static final BooleanProperty STICKY = BooleanProperty.create("sticky");

	public RoboticPistonArmBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STICKY, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, STICKY);
	}

	/** Vanilla's head, facing the same way: the same shape. */
	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Blocks.PISTON_HEAD.defaultBlockState().setValue(PistonHeadBlock.FACING, state.getValue(FACING))
			.setValue(BlockStateProperties.SHORT, false).getShape(level, pos, context);
	}
}
