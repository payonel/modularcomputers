package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import java.util.Optional;
import com.payonel.modularcomputers.network.ModNetwork;
import com.payonel.modularcomputers.device.TerminalTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.BlockHitResult;

/**
 * A screen on its own: a monitor for computers to show their console on, joined to them by
 * cable (it takes one on any side but the glass). Right-click opens its screen view: just the
 * display and a toggle to its radio slot (a Bluetooth card there can pair it with a computer or hub),
 * which right-clicking its back opens directly. The power button is on the computer's case,
 * not on every screen.
 */
public final class ScreenBlock extends HorizontalDirectionalBlock implements EntityBlock {
	private final TerminalTier tier;

	public ScreenBlock(Properties properties, TerminalTier tier) {
		super(properties);
		this.tier = tier;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// The glass faces the player who placed it.
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	/** What its terminal shows: see {@link TerminalTier}. */
	public TerminalTier tier() {
		return tier;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ScreenBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != ModBlocks.SCREEN_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<ScreenBlockEntity>) (tickLevel, pos, tickState, screen) -> screen.serverTick();
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// A Bluetooth card in hand links to the one in here.
		return stack.is(ModItems.BLUETOOTH_CARD) ? Bluetooth.link(stack, level, pos, player) : super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ScreenBlockEntity screen)) {
			return InteractionResult.SUCCESS;
		}
		if (hit.getDirection() == state.getValue(FACING).getOpposite()) {
			com.payonel.modularcomputers.menu.ModMenus.open(player, screen); // the back: its radio slot
		} else if (player instanceof ServerPlayer serverPlayer) {
			ModNetwork.send(serverPlayer, openDisplayPayload(screen));
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * What the server sends to open a screen's view: the display and the toggle to its slot,
	 * with no power button (that belongs to the computer's case).
	 */
	public static OpenDisplayPayload openDisplayPayload(ScreenBlockEntity screen) {
		return new OpenDisplayPayload(com.payonel.modularcomputers.network.HostRef.of(screen.getBlockPos()), screen.terminal().buffer().resolution(), Optional.empty(), true);
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
