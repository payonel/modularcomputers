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
 * front: its own right, as every face is) or a robot (its middle, its heading:
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

		/** A side, the world's way, as a face in the frame: front, back, left, right, up or down. */
		public String face(Direction side) {
			Vec3 way = Vec3.atLowerCornerOf(side.getUnitVec3i());
			double f = way.dot(forward);
			double r = way.dot(right);
			double u = way.y;
			if (Math.abs(u) >= Math.abs(f) && Math.abs(u) >= Math.abs(r)) {
				return u >= 0 ? "up" : "down";
			}
			if (Math.abs(f) >= Math.abs(r)) {
				return f >= 0 ? "front" : "back";
			}
			return r >= 0 ? "right" : "left";
		}

		/** A block's frame: its center, its front, its own right (as every face is). */
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
	/** Its card's ear on its field (its field, loaded again, is given this one: whichever object holds its card now). */
	private final EnergyField.Listener listener = new EnergyField.Listener() {
		@Override
		public void arrived() {
			card.arrived();
		}

		@Override
		public void blocked(String block, Direction side) {
			card.blocked(block, frame.get().face(side)); // (its face in its computer's frame, as its positions are)
		}

		@Override
		public void moved(double blocks) {
			card.movedBy(blocks);
		}

		@Override
		public void lost(String why) {
			card.lost(why);
		}
	};

	public FieldProjector(FieldCard card, Supplier<ServerLevel> level, Supplier<Frame> frame, Supplier<Entity> self) {
		this.card = card;
		this.level = level;
		this.frame = frame;
		this.self = self;
	}

	/** Its hold's field, loaded (null: its chunk isn't, or none), its ear given it. */
	private EnergyField field() {
		EnergyField field = EnergyField.live(card.hold());
		if (field != null) {
			field.attach(listener);
		}
		return field;
	}

	/** Its hold's record (null: none, or it ended). */
	private FieldHolds.Hold record() {
		return card.hold() == null ? null : FieldHolds.of(level.get()).get(card.hold());
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
			Map<String, Object> seen = new LinkedHashMap<>(); // (a thing, as a robot's scan gives it: the user's, one shape)
			seen.put("kind", "entity");
			seen.put("name", name(entity));
			seen.put("id", entity.getStringUUID());
			seen.put("at", at.local(entity.getBoundingBox().getCenter()));
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
				return Result.fail(com.payonel.modularcomputers.runtime.Failure.denied("not_a_robot", FieldCard.SELF)); // (its computer is a block: no robot to hold)
			}
		} else if (id != null) {
			try {
				target = level.get().getEntity(UUID.fromString(id));
			} catch (IllegalArgumentException e) {
				return Result.fail(com.payonel.modularcomputers.runtime.Failure.badArgument(1, id, "id"));
			}
			if (target == null || !holdable(target)) {
				return Result.fail(com.payonel.modularcomputers.runtime.Failure.notFound("entity", id));
			}
		} else {
			Vec3 there = at.world(point[0], point[1], point[2]);
			if (there.distanceTo(at.origin()) > FieldCard.RANGE + FieldCard.REACH) {
				return FieldCard.CAPTURE_TOO_FAR; // (it senses no farther than it reaches)
			}
			target = level.get().getEntities((Entity) null, new AABB(there, there).inflate(FieldCard.REACH),
					entity -> holdable(entity) && entity.getBoundingBox().getCenter().distanceTo(there) <= FieldCard.REACH)
				.stream().min(Comparator.comparingDouble(entity -> entity.getBoundingBox().getCenter().distanceTo(there))).orElse(null);
			if (target == null) {
				return Result.fail(com.payonel.modularcomputers.runtime.Failure.notFound("entity", java.util.Map.of("forward", point[0], "right", point[1], "up", point[2]))); // (none near the point given)
			}
		}
		if (target != self.get() && target.getBoundingBox().getCenter().distanceTo(at.origin()) > FieldCard.RANGE) {
			return FieldCard.CAPTURE_TOO_FAR;
		}
		EnergyField made = ModEntities.ENERGY_FIELD.create(level.get(), EntitySpawnReason.TRIGGERED);
		UUID hold = UUID.randomUUID();
		if (!made.hold(target, listener, hold, name(target))) {
			return Result.fail(com.payonel.modularcomputers.runtime.Failure.badState("entity", target.getStringUUID(), "free")); // (another field holds it: not "held", the robot's wait)
		}
		card.hold(hold);
		level.get().addFreshEntity(made);
		return Result.ok(name(target));
	}

	@Override
	public Result move(double forward, double right, double up) {
		FieldHolds.Hold record = record();
		if (record == null) {
			return FieldCard.NOT_HOLDING;
		}
		Vec3 by = frame.get().offset(forward, right, up);
		EnergyField field = field();
		if (field != null) {
			field.moveBy(by);
		}
		return Result.ok(true);
	}

	@Override
	public String beyondReach(double forward, double right, double up) {
		FieldHolds.Hold record = record();
		if (record == null) {
			return null;
		}
		Frame at = frame.get();
		EnergyField field = field();
		Vec3 to = (field != null ? field.center() : record.at()).add(at.offset(forward, right, up));
		return itself(field) || to.distanceTo(at.origin()) <= FieldCard.RANGE ? null : outward(at, to);
	}

	@Override
	public String outOfReach() {
		FieldHolds.Hold record = record();
		if (record == null) {
			return null;
		}
		Frame at = frame.get();
		EnergyField field = field();
		Vec3 now = field != null && field.holding() != null ? field.center() : record.at(); // (not loaded: where its field was, kept)
		return itself(field) || now.distanceTo(at.origin()) <= FieldCard.RANGE + FieldCard.REACH ? null : outward(at, now);
	}

	/** Whether it holds its own robot (which carries the card: never out of its reach). */
	private boolean itself(EnergyField field) {
		return field != null && field.holding() == self.get();
	}

	/** The face of what it holds at a point past its reach that meets its reach's edge: the way out from its computer there, in the frame. */
	private static String outward(Frame at, Vec3 point) {
		Vec3 out = point.subtract(at.origin());
		return at.face(Direction.getApproximateNearest(out.x, out.y, out.z));
	}

	@Override
	public String unloadedFace(double forward, double right, double up) {
		if (record() == null || field() != null) {
			return null;
		}
		Frame at = frame.get();
		Vec3 by = at.offset(forward, right, up);
		return at.face(Direction.getApproximateNearest(by.x, by.y, by.z)); // (the way it was to go: nothing met, what it holds isn't loaded)
	}

	@Override
	public void stop() {
		EnergyField field = field();
		if (field != null) {
			field.stop();
		}
	}

	@Override
	public void release() {
		EnergyField field = field();
		if (field != null) {
			field.release(); // (its record goes with it)
		} else if (card.hold() != null) {
			FieldHolds.of(level.get()).remove(card.hold()); // (not loaded: its hold ends all the same, and loaded, its field lets go)
		}
		card.hold(null);
	}

	@Override
	public Map<String, Object> lastKnown() {
		FieldHolds.Hold record = FieldHolds.of(level.get()).get(card.hold());
		if (record != null) {
			EnergyField field = field();
			Vec3 at = field != null && field.holding() != null ? field.center() : record.at();
			Map<String, Object> last = new LinkedHashMap<>();
			last.put("name", record.name());
			last.put("id", record.held().toString());
			last.put("at", frame.get().local(at));
			lastKnown = last;
		}
		return lastKnown;
	}

	/** What it held, last seen (its record gone with it: a loss says it from here). */
	private Map<String, Object> lastKnown;

	@Override
	public Map<String, Object> where() {
		FieldHolds.Hold record = record();
		if (record == null) {
			return null;
		}
		EnergyField field = field();
		Map<String, Object> where = new LinkedHashMap<>();
		where.put("name", record.name());
		where.putAll(frame.get().local(field != null && field.holding() != null ? field.center() : record.at()));
		return where;
	}

	@Override
	public String check() {
		if (card.hold() == null) {
			return null;
		}
		FieldHolds.Hold record = record();
		if (record == null) {
			return FieldCard.LOST; // (its hold ended where it was: what it held went, its field said so while this card wasn't here to hear)
		}
		FieldHolds.of(level.get()).seen(card.hold(), level.get().getGameTime()); // (it's loaded: its field may act on its word)
		lastKnown(); // (what it holds and where, kept for a loss to say)
		return outOfReach() != null ? FieldCard.BLOCKED : null; // (out of its reach: blocked, the user's)
	}
}
