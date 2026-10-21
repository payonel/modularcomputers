package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An expansion hub: card slots on the cable network. It takes a cable on every side and passes
 * the connection on, like a cable. Right-click opens its slots.
 */
public final class ExpansionHubBlock extends Block implements EntityBlock {
	public ExpansionHubBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ExpansionHubBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// A Bluetooth card in hand links to the one in here.
		return stack.is(ModItems.BLUETOOTH_CARD) ? Bluetooth.link(stack, level, pos, player) : super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ExpansionHubBlockEntity hub) {
			com.payonel.modularcomputers.menu.ModMenus.open(player, hub);
		}
		return InteractionResult.SUCCESS;
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

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
		net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
		return level.isClientSide() || type != ModBlocks.EXPANSION_HUB_ENTITY ? null
			: (net.minecraft.world.level.block.entity.BlockEntityTicker<T>) (net.minecraft.world.level.block.entity.BlockEntityTicker<ExpansionHubBlockEntity>)
				(tickLevel, pos, tickState, hub) -> hub.serverTick();
	}

	/** Its redstone cards put out redstone (user, 2026-10-13). */
	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.core.Direction direction) {
		return level.getBlockEntity(pos) instanceof ExpansionHubBlockEntity hub ? hub.redstoneOut(direction) : 0;
	}
}
