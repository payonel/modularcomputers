package com.payonel.modularcomputers.block;

import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A cabled block's closed cable ports (user design, 2026-10-20): a side whose port is closed takes
 * no cable, so a cable against it doesn't join there (and shows it), and the block isn't on that
 * cable's network. Every port is open at first. Kept by the block's entity, saved with it, synced to
 * clients (who draw the cables).
 */
public final class CablePorts {
	private static final String CLOSED = "closed_ports";

	/** A block entity with ports. */
	public interface Holder {
		CablePorts ports();
	}

	private final Set<Direction> closed = EnumSet.noneOf(Direction.class);

	public boolean open(Direction side) {
		return !closed.contains(side);
	}

	/**
	 * Opens or closes a side's port, in the world: the cable against it (if any) joins or lets go,
	 * computers look along their cables again, and the block's neighbours and clients hear of it.
	 * Returns whether it changed.
	 */
	public static boolean set(Level level, BlockPos pos, Direction side, boolean open) {
		if (!(level.getBlockEntity(pos) instanceof Holder holder) || holder.ports().open(side) == open) {
			return false;
		}
		if (open) {
			holder.ports().closed.remove(side);
		} else {
			holder.ports().closed.add(side);
		}
		BlockState state = level.getBlockState(pos);
		var block = level.getBlockEntity(pos);
		block.setChanged();
		BlockPos neighbor = pos.relative(side);
		BlockState cable = level.getBlockState(neighbor);
		if (cable.getBlock() instanceof CableBlock) {
			level.setBlock(neighbor, CableBlock.withConnections(level, neighbor, cable), Block.UPDATE_ALL);
		}
		Cabling.changed();
		level.updateNeighborsAt(pos, state.getBlock());
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
		return true;
	}

	public void save(ValueOutput output) {
		if (!closed.isEmpty()) {
			output.store(CLOSED, Codec.STRING.listOf(), closed.stream().map(Direction::getName).toList());
		}
	}

	public void load(ValueInput input) {
		closed.clear();
		for (String name : input.read(CLOSED, Codec.STRING.listOf()).orElse(List.of())) {
			Direction side = Direction.byName(name);
			if (side != null) {
				closed.add(side);
			}
		}
	}
}
