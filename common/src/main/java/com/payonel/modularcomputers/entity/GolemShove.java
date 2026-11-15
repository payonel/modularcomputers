package com.payonel.modularcomputers.entity;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A golem moving shoves what's in its way (the user's, 2026-11-15): what the game lets be pushed
 * ({@link Entity#isPushable}: creatures, players, boats, minecarts; not a vehicle with a rider, nor
 * a rider, nor what a field holds) it pushes out of the space its body is going into, aside (away
 * from its way, to the side the thing is on; dead ahead, to its own right) or, with no room there,
 * ahead of it, moved by the game's own collisions as a shulker's shell pushes what's on it (never
 * into a block); a player is sent the push as knockback is (a player's moves are its game's). What
 * can't be pushed (a standing golem, an armor stand, a golem's legs), or what's pushed and has no
 * room to go, stops it, as a block does. What's only in its space and nothing to its body (an item,
 * an arrow, a marker) it passes, as the game's bodies do.
 */
final class GolemShove {
	/** Lengths this close count as the same, in blocks (floating point's). */
	private static final double EPSILON = 1e-7;
	/** How far past the space its body goes into a shove takes what it pushes, in blocks: clear of it, not touching. */
	private static final double CLEARANCE = 1e-3;
	/** The farthest a shove moves anything at once, in blocks: a body's width and more (a thing wholly in its way, pushed out of it). */
	private static final double MOST = 2;

	private GolemShove() {
	}

	/**
	 * Clears the space {@code to} that {@code golem}'s body goes into, moving {@code way}: what's
	 * there that can be pushed, pushed out of it; the first thing it meets there that can't (nearest
	 * it first), or null: its way's clear. What's behind it as it goes (it's moving away from it, or
	 * past it) never stops it.
	 */
	static @Nullable Entity clear(Entity golem, AABB to, Vec3 way) {
		List<Entity> bodies = new java.util.ArrayList<>(golem.level().getEntities(golem, to, other -> body(golem, other)));
		if (bodies.isEmpty()) {
			return null;
		}
		bodies.sort(Comparator.comparingDouble(golem::distanceToSqr));
		var pushable = EntitySelector.pushableBy(golem);
		for (Entity other : bodies) {
			if (!other.getBoundingBox().intersects(to)) {
				continue; // (pushed out of it already, by a shove of another)
			}
			if (!movable(other)) {
				if (toward(golem, other, way)) {
					return other;
				}
				continue;
			}
			if (!pushable.test(other)) {
				continue; // (its team's collision rule: the game lets them pass through each other)
			}
			if (!shove(golem, other, to, way) && toward(golem, other, way)) {
				return other; // (no room to push it to)
			}
		}
		return null;
	}

	/** Whether {@code other} is a body to {@code golem}: what it collides with, or pushes, or a creature (an armor stand too). */
	private static boolean body(Entity golem, Entity other) {
		return other.isAlive() && !other.isSpectator() && !other.noPhysics && !golem.isPassengerOfSameVehicle(other)
			&& (golem.canCollideWith(other) || other.isPushable() || other instanceof LivingEntity);
	}

	/** Whether the game lets {@code other} be pushed (its own say, as {@link Entity#push(Entity)} asks): not a vehicle with riders, nor a rider, nor held in a field. */
	private static boolean movable(Entity other) {
		return other.isPushable() && !other.isVehicle() && !other.isPassenger() && !EnergyField.held(other);
	}

	/** Whether {@code other} is ahead of {@code golem}'s middle, the way it moves. */
	private static boolean toward(Entity golem, Entity other, Vec3 way) {
		return other.getBoundingBox().getCenter().subtract(golem.getBoundingBox().getCenter()).dot(way) > EPSILON;
	}

	/** Pushes {@code other} out of {@code to}: aside, else ahead; whether it's out of it. */
	private static boolean shove(Entity golem, Entity other, AABB to, Vec3 way) {
		Vec3 across = new Vec3(way.x, 0, way.z);
		across = across.lengthSqr() > EPSILON * EPSILON ? across.normalize() : Vec3.ZERO;
		Vec3 off = other.getBoundingBox().getCenter().subtract(golem.getBoundingBox().getCenter()).multiply(1, 0, 1);
		Vec3 aside = off.subtract(across.scale(off.dot(across)));
		if (aside.lengthSqr() < EPSILON * EPSILON) {
			// (dead on its way: to its own right, as it goes; straight up or down, as it faces)
			aside = across != Vec3.ZERO ? new Vec3(-across.z, 0, across.x) : right(golem);
		}
		for (Vec3 direction : new Vec3[] {aside.normalize(), way.normalize()}) {
			if (direction.lengthSqr() < EPSILON) {
				continue;
			}
			double far = clearance(other.getBoundingBox(), to, direction);
			if (far > MOST) {
				continue;
			}
			if (!to.intersects(push(other, direction.scale(far + CLEARANCE)))) {
				return true;
			}
		}
		return false;
	}

	/** Its own right, across: as its yaw faces. */
	private static Vec3 right(Entity golem) {
		float yaw = golem.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
	}

	/**
	 * Moves {@code other} by {@code by}, by the game's collisions (never into a block, nor into a
	 * body it collides with), as a shulker pushes what's on it; a player, given it as its motion (its
	 * game moves it, by the same collisions): the space it's in (a player's, where its game will put it).
	 */
	private static AABB push(Entity other, Vec3 by) {
		if (other instanceof Player) {
			AABB box = other.getBoundingBox();
			Vec3 room = Entity.collideBoundingBox(other, by, box, other.level(), other.level().getEntityCollisions(other, box.expandTowards(by)));
			other.setDeltaMovement(room);
			other.syncVelocity = true; // (sent to its game, as knockback is)
			return box.move(room);
		}
		other.move(MoverType.SHULKER, by);
		return other.getBoundingBox();
	}

	/** How far {@code box} must go {@code direction} (a unit) to be out of {@code to}, in blocks (0: it is). */
	private static double clearance(AABB box, AABB to, Vec3 direction) {
		double far = Double.POSITIVE_INFINITY;
		far = Math.min(far, axis(direction.x, box.minX, box.maxX, to.minX, to.maxX));
		far = Math.min(far, axis(direction.y, box.minY, box.maxY, to.minY, to.maxY));
		far = Math.min(far, axis(direction.z, box.minZ, box.maxZ, to.minZ, to.maxZ));
		return Math.max(0, far);
	}

	private static double axis(double way, double min, double max, double toMin, double toMax) {
		if (Math.abs(way) < EPSILON) {
			return Double.POSITIVE_INFINITY;
		}
		return (way > 0 ? toMax - min : max - toMin) / Math.abs(way);
	}

	/** A thing's name: its type's registry id (the user's, everywhere). */
	static String name(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	/**
	 * The side of {@code to} (a body's space) that meets {@code other}, as a direction in the world:
	 * the axis they overlap least on (where they meet), toward it.
	 */
	static Vec3 side(AABB to, Entity other) {
		AABB box = other.getBoundingBox();
		Vec3 off = box.getCenter().subtract(to.getCenter());
		double x = Math.min(to.maxX - box.minX, box.maxX - to.minX);
		double y = Math.min(to.maxY - box.minY, box.maxY - to.minY);
		double z = Math.min(to.maxZ - box.minZ, box.maxZ - to.minZ);
		if (y < x && y < z) {
			return new Vec3(0, Math.signum(off.y) >= 0 ? 1 : -1, 0);
		}
		return x <= z ? new Vec3(off.x >= 0 ? 1 : -1, 0, 0) : new Vec3(0, 0, off.z >= 0 ? 1 : -1);
	}
}
