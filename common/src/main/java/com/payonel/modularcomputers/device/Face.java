package com.payonel.modularcomputers.device;

import java.util.List;
import java.util.Locale;

/**
 * A block's face, as every device's methods name one (user design): by the world's name
 * ({@code north}, {@code south}, {@code east}, {@code west}, {@code up}, {@code down}: Minecraft's
 * own), or relative to the block's front ({@code front}, {@code back}, {@code left},
 * {@code right}), so a program written for a block works however it's turned. Left and right are the
 * block's own, looking out of its front (ComputerCraft's). A block with no front (an expansion hub)
 * takes only the world's names. Devices answer with the world's names.
 */
public enum Face {
	NORTH, SOUTH, EAST, WEST, UP, DOWN;

	/** The names relative to a block's front. */
	public static final List<String> RELATIVE = List.of("front", "back", "left", "right");

	/** Its name, as programs see it. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Face opposite() {
		return switch (this) {
			case NORTH -> SOUTH;
			case SOUTH -> NORTH;
			case EAST -> WEST;
			case WEST -> EAST;
			case UP -> DOWN;
			case DOWN -> UP;
		};
	}

	/** A quarter turn clockwise, seen from above (north to east); up and down stay. */
	public Face clockwise() {
		return switch (this) {
			case NORTH -> EAST;
			case EAST -> SOUTH;
			case SOUTH -> WEST;
			case WEST -> NORTH;
			default -> this;
		};
	}

	/** The face a world direction's name is ({@code Direction.name()}: NORTH...). */
	public static Face of(String direction) {
		return valueOf(direction.toUpperCase(Locale.ROOT));
	}

	/**
	 * The face {@code name} means, on a block whose front is {@code front} (null: it has none). Fails
	 * (an {@link IllegalArgumentException}, with why) for a name that's no face's, or a relative one
	 * the block can't mean: it has no front, or (left, right) its front is up or down.
	 */
	public static Face named(String name, Face front) {
		String key = name.toLowerCase(Locale.ROOT);
		for (Face face : values()) {
			if (face.id().equals(key)) {
				return face;
			}
		}
		if (!RELATIVE.contains(key)) {
			throw new IllegalArgumentException("no such face: " + name + " (north, south, east, west, up, down, or front, back, left, right)");
		}
		if (front == null) {
			throw new IllegalArgumentException(key + ": this block has no front (name a face by north, south, east, west, up or down)");
		}
		return switch (key) {
			case "front" -> front;
			case "back" -> front.opposite();
			default -> {
				if (front == UP || front == DOWN) {
					throw new IllegalArgumentException(key + ": this block faces " + front.id() + ", so it has no " + key);
				}
				yield key.equals("right") ? front.clockwise() : front.clockwise().opposite();
			}
		};
	}
}
