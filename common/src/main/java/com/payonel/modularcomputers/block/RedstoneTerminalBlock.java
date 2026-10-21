package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A redstone terminal, tier 1 (user design, 2026-10-20): a block on the cable network,
 * with a port on every side. A side with a cable against it joins the network; every other side is
 * a redstone side, put out and read by programs ({@link com.payonel.modularcomputers.device.RedstoneTerminal}).
 * Right-click opens its slots: a radio (a Bluetooth card) and a battery, which pays for its signal
 * changes before the bus does.
 */
public final class RedstoneTerminalBlock extends Block implements EntityBlock {
	public RedstoneTerminalBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RedstoneTerminalBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// A Bluetooth card in hand links to the one in here.
		return stack.is(ModItems.BLUETOOTH_CARD) ? Bluetooth.link(stack, level, pos, player) : super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RedstoneTerminalBlockEntity terminal) {
			ModMenus.open(player, terminal);
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

	/** Something next to it changed: a signal coming in, or a cable come or gone. */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RedstoneTerminalBlockEntity terminal) {
			terminal.sense();
		}
	}

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	/** What it puts out toward a neighbour ({@code direction}: from the neighbour to the terminal). */
	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return level.getBlockEntity(pos) instanceof RedstoneTerminalBlockEntity terminal ? terminal.signal(direction.getOpposite()) : 0;
	}
}
