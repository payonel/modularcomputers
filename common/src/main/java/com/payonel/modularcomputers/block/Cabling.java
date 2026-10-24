package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.cable.CableWalk;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cables in the world: which blocks take a cable on which side, walking the cables out from a
 * block, and a counter that goes up whenever a cable or a cabled block is placed or removed,
 * so computers know to look again.
 * <p>
 * Screens and expansion hubs carry the connection on like a cable (a row of screens, or a
 * screen against a computer's side, is connected). Computers and robotic pistons don't.
 */
public final class Cabling {
	private static long changes;

	private Cabling() {
	}

	/** Goes up whenever a cable, screen or computer is placed or removed anywhere. */
	public static long changes() {
		return changes;
	}

	public static void changed() {
		changes++;
	}

	/** The block at {@code pos} takes a cable on {@code side}: it has a port there ({@link #hasPort(BlockState, Direction)}), open ({@link CablePorts}). */
	public static boolean hasPort(BlockGetter level, BlockPos pos, Direction side) {
		return hasPort(level.getBlockState(pos), side)
			&& !(level.getBlockEntity(pos) instanceof CablePorts.Holder holder && !holder.ports().open(side));
	}

	/**
	 * A block has a cable port on {@code side}: cables and expansion hubs on every side, screens and
	 * computers on every side but the front, robotic pistons only on their port face, Disk Stations and
	 * photomasks on every side but the front. (Whether it's open: {@link CablePorts}.)
	 */
	public static boolean hasPort(BlockState state, Direction side) {
		Block block = state.getBlock();
		if (block instanceof CableBlock || block instanceof ExpansionHubBlock) {
			return true;
		}
		if (block instanceof ScreenBlock || block instanceof AllInOneBlock) {
			return side != state.getValue(HorizontalDirectionalBlock.FACING);
		}
		if (block instanceof RoboticPistonBlock) {
			return side == state.getValue(DirectionalBlock.FACING);
		}
		if (block instanceof DiskStationBlock) {
			return side != state.getValue(HorizontalDirectionalBlock.FACING); // not its front
		}
		if (block instanceof PhotomaskBlock) {
			return side != state.getValue(HorizontalDirectionalBlock.FACING); // not the gear's face
		}
		return false;
	}

	/** The blocks connected to the one at {@code start}, by cable or Bluetooth, nearest first. Never looks into unloaded chunks. */
	public static List<CableWalk.Found> walk(Level level, BlockPos start) {
		return CableWalk.from(new CableWalk.World() {
			@Override
			public CableWalk.Kind kind(BlockPos pos) {
				if (!level.isLoaded(pos)) {
					return CableWalk.Kind.NONE;
				}
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof CableBlock) {
					return CableWalk.Kind.CABLE;
				}
				Block block = state.getBlock();
				if (block instanceof ScreenBlock || block instanceof ExpansionHubBlock) {
					return CableWalk.Kind.RELAY;
				}
				return block instanceof AllInOneBlock || block instanceof RoboticPistonBlock
					|| block instanceof PhotomaskBlock || block instanceof DiskStationBlock ? CableWalk.Kind.DEVICE : CableWalk.Kind.NONE;
			}

			@Override
			public boolean hasPort(BlockPos pos, Direction side) {
				return Cabling.hasPort(level, pos, side);
			}

			@Override
			public List<CableWalk.Link> wireless(BlockPos pos) {
				return Bluetooth.links(level, pos);
			}
		}, start);
	}
}
