package com.payonel.modularcomputers.entity;

import com.payonel.modularcomputers.device.Face;
import com.payonel.modularcomputers.device.FieldCard;
import com.payonel.modularcomputers.runtime.Result;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An energy field card's work in the world: what's round its computer, in its frame, and the field
 * ({@link EnergyField}) it makes round what it captures. Its computer is a block (its center, its
 * front: right as someone facing it sees it, as every face is) or a robot (its middle, its heading:
 * its own right).
 */
public final class FieldProjector implements FieldCard.Projector {
	/** Where positions are given from, and which way: forward and right (up is up), unit vectors. */
	public record Frame(Vec3 origin, Vec3 forward, Vec3 right) {
		/** A point in the frame, in the world. */
		Vec3 world(double forward, double right, double up) {
			return origin.add(offset(forward, right, up));
		}

		/** A move in the frame, in the world. */
		Vec3 offset(double forward, double right, double up) {
			return this.forward.scale(forward).add(this.right.scale(right)).add(0, up, 0);
		}

		/** Where a point in the world is in the frame: forward, right, up, to a tenth. */
		public Map<String, Object> local(Vec3 at) {
			Vec3 d = at.subtract(origin);
			Map<String, Object> where = new LinkedHashMap<>();
			where.put("forward", tenth(d.dot(forward)));
			where.put("right", tenth(d.dot(right)));
			where.put("up", tenth(d.y));
			return where;
		}

		/** A block's frame: its center, its front, its right as someone facing its front sees it. */
		public static Frame of(Vec3 center, Direction front) {
			Direction right = Direction.valueOf(Face.named("right", Face.of(front.getName())).name());
			return new Frame(center, Vec3.atLowerCornerOf(front.getUnitVec3i()), Vec3.atLowerCornerOf(right.getUnitVec3i()));
		}

		/** A robot's frame: its middle, its heading, its own right. */
		public static Frame of(Entity robot) {
			float yaw = robot.getYRot() * Mth.DEG_TO_RAD;
			Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
			Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
			return new Frame(robot.getBoundingBox().getCenter(), forward, right);
		}
	}

	private static double tenth(double value) {
		return Math.round(value * 10) / 10.0;
	}

	private final FieldCard card;
	private final Supplier<ServerLevel> level;
	private final Supplier<Frame> frame;
	/** The robot it's in (null: a block's card): what "self" holds, and what carries the card. */
	private final Supplier<Entity> self;
	private EnergyField field;

	public FieldProjector(FieldCard card, Supplier<ServerLevel> level, Supplier<Frame> frame, Supplier<Entity> self) {
		this.card = card;
		this.level = level;
		this.frame = frame;
		this.self = self;
	}

	/** What a field can hold: creatures (players too) and items, not blocks, nor other fields. */
	private static boolean holdable(Entity entity) {
		return entity.isAlive() && !entity.isSpectator() && (entity instanceof LivingEntity || entity instanceof ItemEntity);
	}

	private static String name(Entity entity) {
		if (entity instanceof ItemEntity item) {
			return BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString();
		}
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	@Override
	public Result scan(double range) {
		Frame at = frame.get();
		List<Entity> found = new ArrayList<>(level.get().getEntities((Entity) null, new AABB(at.origin(), at.origin()).inflate(range),
			entity -> holdable(entity) && entity.getBoundingBox().getCenter().distanceTo(at.origin()) <= range && entity != self.get()));
		found.sort(Comparator.comparingDouble(entity -> entity.getBoundingBox().getCenter().distanceTo(at.origin())));
		List<Object> list = new ArrayList<>();
		for (Entity entity : found) {
			Map<String, Object> seen = new LinkedHashMap<>();
			seen.put("id", entity.getStringUUID());
			seen.put("name", name(entity));
			seen.putAll(at.local(entity.getBoundingBox().getCenter()));
			seen.put("distance", tenth(entity.getBoundingBox().getCenter().distanceTo(at.origin())));
			list.add(seen);
		}
		return Result.ok(list);
	}

	@Override
	public Result capture(String id, double[] point) {
		Frame at = frame.get();
		Entity target;
		if (FieldCard.SELF.equals(id)) {
			target = self.get();
			if (target == null) {
				return Result.fail("not a robot");
			}
		} else if (id != null) {
			try {
				target = level.get().getEntity(UUID.fromString(id));
			} catch (IllegalArgumentException e) {
				return Result.fail("not an id: " + id);
			}
			if (target == null || !holdable(target)) {
				return Result.fail("no entity: " + id);
			}
		} else {
			Vec3 there = at.world(point[0], point[1], point[2]);
			if (there.distanceTo(at.origin()) > FieldCard.RANGE + FieldCard.REACH) {
				return Result.fail("too far: " + (int) FieldCard.RANGE); // (it senses no farther than it reaches)
			}
			target = level.get().getEntities((Entity) null, new AABB(there, there).inflate(FieldCard.REACH),
					entity -> holdable(entity) && entity.getBoundingBox().getCenter().distanceTo(there) <= FieldCard.REACH)
				.stream().min(Comparator.comparingDouble(entity -> entity.getBoundingBox().getCenter().distanceTo(there))).orElse(null);
			if (target == null) {
				return Result.fail("no entity");
			}
		}
		if (target != self.get() && target.getBoundingBox().getCenter().distanceTo(at.origin()) > FieldCard.RANGE) {
			return Result.fail("too far: " + (int) FieldCard.RANGE);
		}
		EnergyField made = ModEntities.ENERGY_FIELD.create(level.get(), EntitySpawnReason.TRIGGERED);
		if (!made.hold(target, new EnergyField.Listener() {
			@Override
			public void arrived() {
				card.arrived();
			}

			@Override
			public void blocked(String why) {
				card.blocked(why);
			}

			@Override
			public void moved(double blocks) {
				card.movedBy(blocks);
			}

			@Override
			public void lost(String why) {
				field = null;
				card.lost(why);
			}
		})) {
			return Result.fail("held");
		}
		level.get().addFreshEntity(made);
		field = made;
		return Result.ok(name(target));
	}

	@Override
	public Result move(double forward, double right, double up) {
		if (field == null) {
			return Result.fail("not holding");
		}
		Frame at = frame.get();
		Vec3 by = at.offset(forward, right, up);
		if (field.holding() != self.get() && field.center().add(by).distanceTo(at.origin()) > FieldCard.RANGE) {
			return Result.fail("too far: " + (int) FieldCard.RANGE);
		}
		field.moveBy(by);
		return Result.ok(true);
	}

	@Override
	public void release() {
		if (field != null) {
			EnergyField was = field;
			field = null;
			was.release();
		}
	}

	@Override
	public Map<String, Object> where() {
		if (field == null) {
			return null;
		}
		Map<String, Object> where = new LinkedHashMap<>();
		where.put("name", name(field.holding()));
		where.putAll(frame.get().local(field.center()));
		return where;
	}

	@Override
	public String check() {
		if (field == null) {
			return null;
		}
		if (field.isRemoved()) {
			field = null;
			return FieldCard.INTERRUPTED; // (its chunk unloaded, its world stopped)
		}
		if (field.holding() != self.get() && field.center().distanceTo(frame.get().origin()) > FieldCard.RANGE + FieldCard.REACH) {
			return "too far: " + (int) FieldCard.RANGE;
		}
		return null;
	}
}
