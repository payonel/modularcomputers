package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A robotic piston (user design, 2026-10-13). {@link #FACING} is its one cable port; it pushes on
 * every other face, when a computer says (its device's {@code push}), as a real piston does: the
 * blocks in front move (vanilla's piston mechanics, slide and all), its arm comes out, then goes back
 * after {@link #ARM_TICKS}; a sticky one pulls back what's in front of its arm. It can take a
 * Bluetooth card instead of a cable. Right-click opens its card slot.
 */
public final class RoboticPistonBlock extends DirectionalBlock implements EntityBlock {
	/** How long its arm stays out after a push, in ticks (vanilla's slide takes 2). */
	public static final int ARM_TICKS = 6;
	private final boolean sticky;

	public RoboticPistonBlock(Properties properties, boolean sticky) {
		super(properties);
		this.sticky = sticky;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
	}

	public boolean sticky() {
		return sticky;
	}

	/**
	 * Pushes on {@code side}, as a piston extending: false on its cable's side, when its arm is out
	 * there already, or when what's in front won't move (obsidian, too many blocks, the world's edge).
	 */
	public static boolean push(Level level, BlockPos pos, Direction side) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof RoboticPistonBlock piston) || side == state.getValue(FACING)) {
			return false;
		}
		PistonStructureResolver resolver = new PistonStructureResolver(level, pos, side, true);
		if (!resolver.resolve()) {
			return false;
		}
		BlockState arm = ModBlocks.ROBOTIC_PISTON_ARM.defaultBlockState().setValue(RoboticPistonArmBlock.FACING, side)
			.setValue(RoboticPistonArmBlock.STICKY, piston.sticky);
		move(level, pos, side, true, resolver, arm);
		level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f, level.getRandom().nextFloat() * 0.25f + 0.6f);
		return true;
	}

	/** Takes its arm on {@code side} back; a sticky one pulls back what's in front of it. */
	static void retract(Level level, BlockPos pos, Direction side) {
		BlockState state = level.getBlockState(pos);
		BlockPos armPos = pos.relative(side);
		if (level.getBlockEntity(armPos) instanceof PistonMovingBlockEntity sliding && sliding.isExtending()
			&& sliding.getMovedState().is(ModBlocks.ROBOTIC_PISTON_ARM)) {
			sliding.finalTick(); // still sliding out: it's out now, then back
		}
		if (!(state.getBlock() instanceof RoboticPistonBlock piston) || !level.getBlockState(armPos).is(ModBlocks.ROBOTIC_PISTON_ARM)) {
			return;
		}
		level.setBlock(armPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		if (piston.sticky) {
			PistonStructureResolver resolver = new PistonStructureResolver(level, pos, side, false);
			BlockState pulled = level.getBlockState(armPos.relative(side));
			if (!pulled.isAir() && !pulled.is(Blocks.MOVING_PISTON) && resolver.resolve()) {
				move(level, pos, side, false, resolver, null);
			}
		}
		level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5f, level.getRandom().nextFloat() * 0.15f + 0.6f);
	}

	/** Moves the blocks the resolver found, as vanilla's piston does ({@code PistonBaseBlock.moveBlocks}), with our arm as its head. */
	private static void move(Level level, BlockPos pos, Direction side, boolean extending, PistonStructureResolver resolver, BlockState arm) {
		BlockPos armPos = pos.relative(side);
		Map<BlockPos, BlockState> deleteAfterMove = new HashMap<>();
		List<BlockPos> toPush = resolver.getToPush();
		List<BlockState> shapes = new ArrayList<>();
		for (BlockPos at : toPush) {
			BlockState moving = level.getBlockState(at);
			shapes.add(moving);
			deleteAfterMove.put(at, moving);
		}
		List<BlockPos> toDestroy = resolver.getToDestroy();
		BlockState[] toUpdate = new BlockState[toPush.size() + toDestroy.size()];
		Direction pushDirection = extending ? side : side.getOpposite();
		int index = 0;
		for (int i = toDestroy.size() - 1; i >= 0; i--) {
			BlockPos at = toDestroy.get(i);
			BlockState broken = level.getBlockState(at);
			dropResources(broken, level, at, broken.hasBlockEntity() ? level.getBlockEntity(at) : null);
			level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			level.gameEvent(GameEvent.BLOCK_DESTROY, at, GameEvent.Context.of(broken));
			toUpdate[index++] = broken;
		}
		for (int i = toPush.size() - 1; i >= 0; i--) {
			BlockPos from = toPush.get(i);
			BlockState moving = level.getBlockState(from);
			BlockPos to = from.relative(pushDirection);
			deleteAfterMove.remove(to);
			BlockState slot = Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, side);
			level.setBlock(to, slot, Block.UPDATE_INVISIBLE | Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_KNOWN_SHAPE);
			level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(to, slot, shapes.get(i), side, extending, false));
			toUpdate[index++] = moving;
		}
		if (extending) {
			BlockState slot = Blocks.MOVING_PISTON.defaultBlockState().setValue(MovingPistonBlock.FACING, side);
			deleteAfterMove.remove(armPos);
			level.setBlock(armPos, slot, Block.UPDATE_INVISIBLE | Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_KNOWN_SHAPE);
			level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(armPos, slot, arm, side, true, true)); // the arm slides out
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		for (BlockPos at : deleteAfterMove.keySet()) {
			level.setBlock(at, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON);
		}
		for (Map.Entry<BlockPos, BlockState> entry : deleteAfterMove.entrySet()) {
			entry.getValue().updateIndirectNeighbourShapes(level, entry.getKey(), Block.UPDATE_CLIENTS);
			air.updateNeighbourShapes(level, entry.getKey(), Block.UPDATE_CLIENTS);
			air.updateIndirectNeighbourShapes(level, entry.getKey(), Block.UPDATE_CLIENTS);
		}
		index = 0;
		for (int i = toDestroy.size() - 1; i >= 0; i--) {
			BlockState broken = toUpdate[index++];
			BlockPos at = toDestroy.get(i);
			if (level instanceof ServerLevel server) {
				broken.affectNeighborsAfterRemoval(server, at, false);
			}
			broken.updateIndirectNeighbourShapes(level, at, Block.UPDATE_CLIENTS);
			level.updateNeighborsAt(at, broken.getBlock());
		}
		for (int i = toPush.size() - 1; i >= 0; i--) {
			level.updateNeighborsAt(toPush.get(i), toUpdate[index++].getBlock());
		}
		if (extending) {
			level.updateNeighborsAt(armPos, ModBlocks.ROBOTIC_PISTON_ARM);
		}
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != ModBlocks.ROBOTIC_PISTON_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<RoboticPistonBlockEntity>) (tickLevel, pos, tickState, piston) -> piston.serverTick();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// The port faces the block it was placed against: put it on a cable and it's plugged in.
		return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RoboticPistonBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		// A Bluetooth card in hand links to the one in here.
		return stack.is(ModItems.BLUETOOTH_CARD) ? Bluetooth.link(stack, level, pos, player) : super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RoboticPistonBlockEntity piston) {
			com.payonel.modularcomputers.menu.ModMenus.open(player, piston);
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
}
