package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.item.ModItems;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A single-block computer: screen and keyboard on the front face, parts soldered inside.
 * Right-click opens its display. On the back, the power button (or sneak + right-click
 * anywhere) turns it on or off, and the window into the case opens the case view.
 */
public final class AllInOneBlock extends HorizontalDirectionalBlock implements EntityBlock {
	/**
	 * The power light shows green. When {@link #READY} that means the machine is on; otherwise it
	 * only flashes, for {@link #ATTEMPT_TICKS}, when someone presses power and the press is refused.
	 */
	public static final BooleanProperty POWERED = BooleanProperty.create("powered");
	/** How long a refused power press shows (faded) green: about as long as the error beep. */
	public static final int ATTEMPT_TICKS = 4;
	/**
	 * The machine could run: a CPU is installed and the battery has charge. Otherwise the power
	 * button is faded and doesn't glow.
	 */
	public static final BooleanProperty READY = BooleanProperty.create("ready");

	public AllInOneBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(READY, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED, READY);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// The front faces the player who placed it.
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// End of a refused power press: the light goes back to what the machine really is.
		if (level.getBlockEntity(pos) instanceof AllInOneBlockEntity computer) {
			level.setBlock(pos, state.setValue(POWERED, computer.machine().isPowered()), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AllInOneBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != ModBlocks.ALL_IN_ONE_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<AllInOneBlockEntity>) (tickLevel, pos, tickState, computer) -> computer.serverTick();
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
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// A Bluetooth card in hand links to the one in here.
		return stack.is(ModItems.BLUETOOTH_CARD) ? Bluetooth.link(stack, level, pos, player) : super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof AllInOneBlockEntity computer)) {
			return InteractionResult.SUCCESS;
		}
		var region = BackPanel.hit(state.getValue(FACING), hit.getDirection(), hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)));
		if (region.equals(Optional.of(BackPanel.Region.POWER)) || player.isSecondaryUseActive()) {
			computer.pressPower();
		} else if (region.equals(Optional.of(BackPanel.Region.CASE))) {
			// The window into the case opens the case view.
			player.openMenu(computer);
		} else if (player instanceof ServerPlayer serverPlayer) {
			// Anywhere else: the screen wins. The client opens the display when this arrives.
			ServerPlayNetworking.send(serverPlayer, computer.openDisplayPayload());
		}
		return InteractionResult.SUCCESS;
	}
}
