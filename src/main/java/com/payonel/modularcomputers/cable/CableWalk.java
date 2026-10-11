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
 * Follows cables out from a block to find the devices on the other ends: what's physically
 * wired to a computer. Cables carry the connection. Some devices carry it too, like a cable
 * ({@link Kind#RELAY}: screens, so a row of screens is one network); others end it
 * ({@link Kind#DEVICE}). Each step needs a port on both sides ({@link World#hasPort}): cables
 * have them everywhere, while a screen's front, for example, is glass.
 * <p>
 * Devices come back nearest first (by how many blocks lie between), so a new device's default
 * bus position follows the wiring.
 */
public final class CableWalk {
	/** Gives up past this many blocks carrying the connection, so a huge network can't stall the server. */
	public static final int MAX_CABLES = 4096;

	/** What the walk needs to know about the world. */
	public interface World {
		Kind kind(BlockPos pos);

		/** The block at {@code pos} takes a connection on {@code side}. */
		boolean hasPort(BlockPos pos, Direction side);
	}

	public enum Kind {
		/** Nothing to connect to, or a part of the world that isn't loaded. */
		NONE,
		CABLE,
		/** A device that ends the connection. */
		DEVICE,
		/** A device that also carries the connection on, like a cable. */
		RELAY,
	}

	/** A device found with {@code cables} blocks carrying the connection between. */
	public record Found(BlockPos pos, int cables) {
	}

	private CableWalk() {
	}

	/** The devices connected to the block at {@code start}, nearest first. {@code start} itself isn't one. */
	public static List<Found> from(World world, BlockPos start) {
		Map<BlockPos, Found> found = new LinkedHashMap<>();
		Set<BlockPos> visited = new HashSet<>();
		Queue<Found> carriers = new ArrayDeque<>();
		visited.add(start);
		carriers.add(new Found(start, 0));
		// Two devices that don't carry the connection don't connect by touching: something
		// between them has to (or the start has to be a relay itself).
		boolean startCarries = world.kind(start) == Kind.RELAY;
		while (!carriers.isEmpty() && visited.size() <= MAX_CABLES) {
			Found carrier = carriers.remove();
			// The start doesn't count toward the distance; every block carrying it on does.
			int between = carrier.pos().equals(start) ? 0 : carrier.cables() + 1;
			for (Direction side : Direction.values()) {
				BlockPos next = carrier.pos().relative(side);
				if (visited.contains(next) || !world.hasPort(carrier.pos(), side) || !world.hasPort(next, side.getOpposite())) {
					continue;
				}
				switch (world.kind(next)) {
					case CABLE -> {
						visited.add(next);
						carriers.add(new Found(next, between));
					}
					case RELAY -> {
						visited.add(next);
						found.putIfAbsent(next, new Found(next, between));
						carriers.add(new Found(next, between));
					}
					case DEVICE -> {
						if (!carrier.pos().equals(start) || startCarries) {
							found.putIfAbsent(next, new Found(next, between));
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
