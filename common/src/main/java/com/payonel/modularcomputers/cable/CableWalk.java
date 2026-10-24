package com.payonel.modularcomputers.cable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Follows the connections out from a block to find the devices on the other ends: what's
 * wired to a computer. Cables carry the connection. Some devices carry it too, like a cable
 * ({@link Kind#RELAY}: screens and expansion hubs); others end it ({@link Kind#DEVICE}). Each
 * cable step needs a port on both sides ({@link World#hasPort}): cables have them everywhere,
 * while a screen's front, for example, is glass.
 * <p>
 * Bluetooth links ({@link World#wireless}) join blocks without a cable. The start and relays
 * use theirs; a device at the end of one is found but carries nothing on.
 * <p>
 * Devices come back nearest first, by how far the connection runs to them in meters: one per
 * block along a cable (a device right against the start is 1m away), and the straight-line
 * distance across a Bluetooth link. A new device's default bus position follows the wiring.
 * <p>
 * A device right against the start, port to port, is connected without a cable (the user's call:
 * a block pushed against a computer is plugged in, as a card in a dock).
 */
public final class CableWalk {
	/** Gives up past this many blocks, so a huge network can't stall the server. */
	public static final int MAX_CABLES = 4096;

	/** What the walk needs to know about the world. */
	public interface World {
		Kind kind(BlockPos pos);

		/** The block at {@code pos} takes a connection on {@code side}. */
		boolean hasPort(BlockPos pos, Direction side);

		/** The blocks the one at {@code pos} reaches by Bluetooth, and how far each is. */
		default List<Link> wireless(BlockPos pos) {
			return List.of();
		}
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

	/** A Bluetooth link to the block at {@code to}, {@code meters} away. */
	public record Link(BlockPos to, int meters) {
	}

	/** A device found {@code meters} along the connection; {@code wireless} when the way there crosses a Bluetooth link. */
	public record Found(BlockPos pos, int meters, boolean wireless) {
		public Found(BlockPos pos, int meters) {
			this(pos, meters, false);
		}
	}

	private record Step(BlockPos pos, Kind kind, int meters, boolean wireless, long order) {
	}

	private CableWalk() {
	}

	/** The devices connected to the block at {@code start}, nearest first. {@code start} itself isn't one. */
	public static List<Found> from(World world, BlockPos start) {
		List<Found> found = new ArrayList<>();
		Set<BlockPos> settled = new HashSet<>();
		PriorityQueue<Step> queue = new PriorityQueue<>(Comparator.comparingInt(Step::meters).thenComparingLong(Step::order));
		long[] order = {0};
		queue.add(new Step(start, world.kind(start), 0, false, order[0]++));
		while (!queue.isEmpty() && settled.size() <= MAX_CABLES) {
			Step step = queue.remove();
			if (!settled.add(step.pos())) {
				continue;
			}
			boolean isStart = step.pos().equals(start);
			if (!isStart && (step.kind() == Kind.DEVICE || step.kind() == Kind.RELAY)) {
				found.add(new Found(step.pos(), step.meters(), step.wireless()));
			}
			if (!isStart && step.kind() == Kind.DEVICE) {
				continue; // a device ends the connection
			}
			for (Direction side : Direction.values()) {
				BlockPos next = step.pos().relative(side);
				if (settled.contains(next) || !world.hasPort(step.pos(), side) || !world.hasPort(next, side.getOpposite())) {
					continue;
				}
				Kind kind = world.kind(next);
				if (kind == Kind.NONE) {
					continue;
				}
				queue.add(new Step(next, kind, step.meters() + 1, step.wireless(), order[0]++));
			}
			if (isStart || step.kind() == Kind.RELAY) {
				for (Link link : world.wireless(step.pos())) {
					Kind kind = world.kind(link.to());
					if (kind != Kind.NONE && !settled.contains(link.to())) {
						queue.add(new Step(link.to(), kind, step.meters() + link.meters(), true, order[0]++));
					}
				}
			}
		}
		return found;
	}
}
