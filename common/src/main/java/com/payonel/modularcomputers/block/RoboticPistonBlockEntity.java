package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.device.RoboticPiston;
import com.payonel.modularcomputers.menu.CardHolderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A robotic piston: a device computers use to push on any of its faces but the one with the cable
 * port ({@link RoboticPistonBlock#push}). Instead of a cable it can take a Bluetooth card, in its one
 * slot. A push's arm goes back on its own ({@link #serverTick}); an extended one stays out until
 * retracted, saved with the block.
 */
public final class RoboticPistonBlockEntity extends CardHolderBlockEntity {
	private final RoboticPiston device = new RoboticPiston();
	/** When each arm that's out goes back, by side (game time); {@link #HELD} for an extended one. */
	private final java.util.EnumMap<net.minecraft.core.Direction, Long> armsOut = new java.util.EnumMap<>(net.minecraft.core.Direction.class);
	/** An extended arm: out until retracted. */
	private static final long HELD = Long.MAX_VALUE;
	private static final String EXTENDED = "extended";

	public RoboticPistonBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ROBOTIC_PISTON_ENTITY, pos, state, 1);
		// Its front is opposite its cable port (its block's facing).
		device.frontFrom(() -> com.payonel.modularcomputers.device.Face.of(getBlockState().getValue(RoboticPistonBlock.FACING).getOpposite().getName()));
		// Device calls come on the server thread: its arms move in the world right then.
		device.moveWith((side, motion) -> {
			net.minecraft.core.Direction direction = net.minecraft.core.Direction.byName(side);
			boolean retracting = motion == RoboticPiston.Motion.RETRACT;
			RoboticPiston.Moved failed = retracting ? RoboticPiston.Moved.NOT_EXTENDED : RoboticPiston.Moved.CANNOT_PUSH;
			if (level == null || level.isClientSide() || direction == null) {
				return failed;
			}
			if (direction == getBlockState().getValue(RoboticPistonBlock.FACING)) {
				return RoboticPiston.Moved.CABLE; // (its cable port's side: no arm there)
			}
			boolean out = armsOut.containsKey(direction) && armOut(direction);
			if (retracting) {
				if (!out) {
					return failed;
				}
				armsOut.remove(direction);
				RoboticPistonBlock.retract(level, worldPosition, direction);
				setChanged();
				return RoboticPiston.Moved.DONE;
			}
			if (out) {
				if (motion != RoboticPiston.Motion.EXTEND) {
					return failed; // a push needs its arm in
				}
				armsOut.put(direction, HELD); // out already: now it stays
				setChanged();
				return RoboticPiston.Moved.ALREADY_OUT;
			}
			if (!RoboticPistonBlock.push(level, worldPosition, direction)) {
				return failed;
			}
			armsOut.put(direction, motion == RoboticPiston.Motion.EXTEND ? HELD : level.getGameTime() + RoboticPistonBlock.ARM_TICKS);
			setChanged();
			return RoboticPiston.Moved.DONE;
		});
	}

	/** Server, every tick: arms whose push is over go back (an arm found out with no time, after a load, goes back too). */
	void serverTick() {
		device.tick(); // (a move running counts down: its command ends as its arm has moved)
		long now = level.getGameTime();
		for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
			if (armOut(side) && !armsOut.containsKey(side)) {
				armsOut.put(side, now + RoboticPistonBlock.ARM_TICKS);
			}
		}
		armsOut.entrySet().removeIf(arm -> {
			if (arm.getValue() == HELD) {
				return !armOut(arm.getKey()); // an extended arm stays, unless it's gone (broken)
			}
			if (now < arm.getValue()) {
				return false;
			}
			RoboticPistonBlock.retract(level, worldPosition, arm.getKey());
			return true;
		});
	}

	/** Its arm is out on {@code side} (an arm there facing away from it), or sliding out. */
	private boolean armOut(net.minecraft.core.Direction side) {
		BlockPos at = worldPosition.relative(side);
		BlockState arm = level.getBlockState(at);
		if (level.getBlockEntity(at) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity moving) {
			arm = moving.isExtending() ? moving.getMovedState() : arm;
		}
		return arm.is(ModBlocks.ROBOTIC_PISTON_ARM) && arm.getValue(RoboticPistonArmBlock.FACING) == side;
	}

	@Override
	protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		super.saveAdditional(output);
		output.store(EXTENDED, com.mojang.serialization.Codec.STRING.listOf(), armsOut.entrySet().stream()
			.filter(arm -> arm.getValue() == HELD).map(arm -> arm.getKey().getName()).toList());
		DeviceSave.save(output, DEVICE, device); // (its events and a move under way: a save isn't a power cut)
	}

	/** Its device's saved state's key. */
	private static final String DEVICE = "device";

	@Override
	protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
		super.loadAdditional(input);
		armsOut.clear();
		input.read(EXTENDED, com.mojang.serialization.Codec.STRING.listOf()).orElse(java.util.List.of()).forEach(side -> {
			net.minecraft.core.Direction direction = net.minecraft.core.Direction.byName(side);
			if (direction != null) {
				armsOut.put(direction, HELD);
			}
		});
		DeviceSave.load(input, DEVICE, device);
	}

	/** Its one slot is the radio slot: a Bluetooth card there links it to its computer instead of a cable. */
	@Override
	public int radioSlot() {
		return 0;
	}

	/** The piston as a bus device. */
	public RoboticPiston device() {
		return device;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return CardHolderMenu.piston(id, inventory, cards(), worldPosition, ContainerLevelAccess.create(level, worldPosition), getBlockState().getBlock());
	}
}
