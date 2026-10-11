package com.payonel.modularcomputers.cable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Follows cables out from a block to find the devices plugged into the other ends: what's
 * physically wired to a computer. Cables carry the connection; a device ends it (a screen
 * doesn't pass the cable through). A device only takes a cable on its sides that have a port
 * ({@link World#hasPort}); a screen's front, for example, is glass.
 * <p>
 * Devices come back nearest first (by how many cable blocks lead to them), so a new device's
 * default bus position follows the wiring.
 */
public final class CableWalk {
	/** Gives up past this many cable blocks, so a huge network can't stall the server. */
	public static final int MAX_CABLES = 4096;

	/** What the walk needs to know about the world. */
	public interface World {
		Kind kind(BlockPos pos);

		/** A device at {@code pos} takes a cable on {@code side}. */
		boolean hasPort(BlockPos pos, Direction side);
	}

	public enum Kind {
		/** Nothing to connect to, or a part of the world that isn't loaded. */
		NONE,
		CABLE,
		DEVICE,
	}

	/** A device found at the end of {@code cables} cable blocks. */
	public record Found(BlockPos pos, int cables) {
	}

	private CableWalk() {
	}

	/** The devices wired to the device at {@code start}, nearest first. {@code start} itself isn't one. */
	public static List<Found> from(World world, BlockPos start) {
		Map<BlockPos, Found> found = new LinkedHashMap<>();
		Set<BlockPos> visited = new HashSet<>();
		Queue<Found> cables = new ArrayDeque<>();
		visited.add(start);
		for (Direction side : Direction.values()) {
			BlockPos next = start.relative(side);
			if (world.hasPort(start, side) && world.kind(next) == Kind.CABLE && visited.add(next)) {
				cables.add(new Found(next, 1));
			}
		}
		while (!cables.isEmpty() && visited.size() <= MAX_CABLES) {
			Found cable = cables.remove();
			for (Direction side : Direction.values()) {
				BlockPos next = cable.pos().relative(side);
				if (visited.contains(next)) {
					continue;
				}
				switch (world.kind(next)) {
					case CABLE -> {
						visited.add(next);
						cables.add(new Found(next, cable.cables() + 1));
					}
					case DEVICE -> {
						if (world.hasPort(next, side.getOpposite())) {
							found.putIfAbsent(next, new Found(next, cable.cables()));
						}
					}
					case NONE -> {
					}
				}
			}
		}
		return new ArrayList<>(found.values());
	}
}
