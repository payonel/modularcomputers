package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A photomask (user design, 2026-10-12): the stencil chips are printed through, in a hand-cranked
 * lithography frame. Silicon goes in, chips come out. Right-click turns its crank (its gear turns
 * on the front); a charged battery in it turns it on its own ({@link #WORKING}). Sneak and
 * right-click to open its slots. Right-click holding silicon or a battery puts it in.
 */
public final class PhotomaskBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/** Running, on the bus's power or its battery (idling or working): its gear turns by itself. */
	public static final BooleanProperty WORKING = BooleanProperty.create("working");

	public PhotomaskBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(WORKING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WORKING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PhotomaskBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != ModBlocks.PHOTOMASK_ENTITY) {
			return null;
		}
		return level.isClientSide()
			? (BlockEntityTicker<T>) (BlockEntityTicker<PhotomaskBlockEntity>) (tickLevel, pos, tickState, mask) -> mask.clientTick()
			: (BlockEntityTicker<T>) (BlockEntityTicker<PhotomaskBlockEntity>) (tickLevel, pos, tickState, mask) -> mask.serverTick();
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		boolean feeds = stack.is(ModItems.SILICON) || stack.getItem() instanceof BatteryItem;
		if (!feeds || player.isSecondaryUseActive()) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PhotomaskBlockEntity mask) {
			mask.insert(stack);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PhotomaskBlockEntity mask) {
			if (player.isShiftKeyDown()) {
				player.openMenu(mask);
			} else {
				mask.crank();
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
		BlockEntity entity = level.getBlockEntity(pos);
		return entity != null && entity.triggerEvent(id, param);
	}
}
