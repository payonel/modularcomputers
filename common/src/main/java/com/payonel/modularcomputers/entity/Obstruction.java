package com.payonel.modularcomputers.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * What stopped a move the game's collisions cut short (the user's): the side of the mover's box that
 * met a block first, the world's way, and that block's id (null: none to name, the world's border).
 * The side is the one whose axis was cut short most, never the move's main direction: a diagonal
 * move along a wall meets it on one face, the wall's.
 */
public record Obstruction(Direction side, String block) {
	/** How far past its box's face it looks for the block it met, in blocks. */
	private static final double PROBE = 0.1;
	/** What's left off the box's other sides as it looks, so a block only touching an edge isn't taken. */
	private static final double EDGE = 1.0E-4;

	/** What stopped {@code mover}, which was to move {@code step} and moved {@code moved}. */
	public static Obstruction of(Entity mover, Vec3 step, Vec3 moved) {
		Direction.Axis cut = Direction.Axis.Y;
		double most = -1;
		for (Direction.Axis axis : Direction.Axis.values()) {
			double wanted = step.get(axis);
			double shortBy = Math.abs(wanted - moved.get(axis));
			if (wanted != 0 && shortBy > most) {
				most = shortBy;
				cut = axis;
			}
		}
		Direction side = Direction.fromAxisAndDirection(cut, step.get(cut) >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
		return new Obstruction(side, blockAt(mover, side));
	}

	/** The block just past {@code mover}'s box on {@code side} that it touches there, nearest its middle (null: none). */
	private static String blockAt(Entity mover, Direction side) {
		AABB box = mover.getBoundingBox();
		AABB past = beyond(box, side);
		Vec3 middle = past.getCenter();
		CollisionContext context = CollisionContext.of(mover);
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(past.minX, past.minY, past.minZ), BlockPos.containing(past.maxX, past.maxY, past.maxZ))) {
			var shape = mover.level().getBlockState(pos).getCollisionShape(mover.level(), pos, context);
			if (shape.isEmpty()) {
				continue;
			}
			boolean meets = shape.toAabbs().stream().anyMatch(part -> part.move(pos).intersects(past));
			double distance = Vec3.atCenterOf(pos).distanceToSqr(middle);
			if (meets && distance < nearestDistance) {
				nearest = pos.immutable();
				nearestDistance = distance;
			}
		}
		return nearest == null ? null : BuiltInRegistries.BLOCK.getKey(mover.level().getBlockState(nearest).getBlock()).toString();
	}

	/** A thin slab just past {@code box}'s face on {@code side}, a little short of its other edges. */
	private static AABB beyond(AABB box, Direction side) {
		AABB inner = box.deflate(EDGE);
		return switch (side) {
			case EAST -> new AABB(box.maxX, inner.minY, inner.minZ, box.maxX + PROBE, inner.maxY, inner.maxZ);
			case WEST -> new AABB(box.minX - PROBE, inner.minY, inner.minZ, box.minX, inner.maxY, inner.maxZ);
			case UP -> new AABB(inner.minX, box.maxY, inner.minZ, inner.maxX, box.maxY + PROBE, inner.maxZ);
			case DOWN -> new AABB(inner.minX, box.minY - PROBE, inner.minZ, inner.maxX, box.minY, inner.maxZ);
			case SOUTH -> new AABB(inner.minX, inner.minY, box.maxZ, inner.maxX, inner.maxY, box.maxZ + PROBE);
			case NORTH -> new AABB(inner.minX, inner.minY, box.minZ - PROBE, inner.maxX, inner.maxY, box.minZ);
		};
	}
}
