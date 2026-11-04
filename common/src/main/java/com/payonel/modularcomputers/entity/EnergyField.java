package com.payonel.modularcomputers.entity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * An energy field (user design, 2026-10-24): a sphere of translucent blue energy round a creature
 * or an item, made by an energy field card, holding it where it is. What it holds falls in place and
 * can't act (no AI: it doesn't move, attack or react; a creeper doesn't blow; a player can't attack
 * or use anything, {@link #held}); the field moves, and what it holds with it, never through blocks.
 * Its hold has one home, {@link FieldHolds} (the user's, 2026-11-04): the field is saved with its
 * chunk, and loaded, it takes hold of what it held again (as soon as that's loaded too) while its
 * hold is there, and lets go when it isn't (its card ended it, loaded or not). It acts on its card's
 * word only while its card is loaded (it ticked a moment ago): else it holds, frozen in time. It
 * moves what it holds; it's drawn round it, wherever it is.
 */
public final class EnergyField extends Entity {

	/** How fast it moves what it holds, in blocks a tick: ten a second (a golem walks four). */
	public static final double SPEED = 0.5;
	/** How near the point a move ends counts as there, in blocks. */
	private static final double THERE = 0.05;
	/** A step moved less than this part of what was asked was stopped by a block. */
	private static final double STOPPED = 0.25;
	/** How far round what it holds the sphere reaches, past its box's corners (a model reaches past its box: a pig's snout), in blocks. */
	private static final double MARGIN = 0.25;

	private static final EntityDataAccessor<Integer> HELD = SynchedEntityData.defineId(EnergyField.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(EnergyField.class, EntityDataSerializers.FLOAT);
	/** No entity (its id). */
	private static final int NONE = -1;

	/** What fields hold, server side: each held thing, its field. */
	private static final Map<Entity, EnergyField> HOLDING = Collections.synchronizedMap(new WeakHashMap<>());
	/** The fields loaded, by their hold. */
	private static final Map<java.util.UUID, EnergyField> LIVE = Collections.synchronizedMap(new java.util.HashMap<>());
	/** A card's word counts while it ticked this long ago at most, in ticks (this one, or the last: ticks come in no set order). */
	private static final long CARD_HERE = 1;

	/** Told how a field's moves go, and when it loses what it held. */
	public interface Listener {
		void arrived();

		/** A block stopped its move: the block's id (null: none to name), and the side of what it holds that met it, the world's way. */
		void blocked(String block, net.minecraft.core.Direction side);

		/** What it holds moved {@code blocks} (what it costs). */
		default void moved(double blocks) {
		}

		void lost(String why);
	}

	/** What a client does as a field comes into view: its hum, while it's there (the client's, set at its start). */
	public static java.util.function.Consumer<EnergyField> clientHum = field -> {
	};
	private boolean humming;

	private Entity held;
	/** Its hold, and what it holds by its id (what it takes hold of again after a load). */
	private java.util.UUID hold;
	private java.util.UUID heldId;
	/** Where what it holds is held (its feet), and where a move is taking it (null: none). */
	private Vec3 anchor;
	private Vec3 target;
	private Listener listener;

	public EnergyField(EntityType<? extends EnergyField> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	/** Whether something is held in a field, so it can't act (its AI, a creeper's fuse, a player's hands: the mod's mixins ask). */
	public static boolean held(Entity entity) {
		return HOLDING.containsKey(entity);
	}

	/** The field loaded for a hold, or null (not loaded: its chunk isn't, or it's gone). */
	public static EnergyField live(java.util.UUID hold) {
		return hold == null ? null : LIVE.get(hold);
	}

	/** Its card's listener, its card loaded again (a field's card is whichever object holds its hold now). */
	public void attach(Listener listener) {
		this.listener = listener;
	}

	/** The field holding {@code entity}, or null. */
	public static EnergyField fieldOf(Entity entity) {
		return HOLDING.get(entity);
	}

	/** Holds {@code entity} for {@code hold} (its record made), telling {@code listener} how it goes; false when a field holds it already. */
	public boolean hold(Entity entity, Listener listener, java.util.UUID hold, String name) {
		if (HOLDING.putIfAbsent(entity, this) != null) {
			return false;
		}
		this.hold = hold;
		heldId = entity.getUUID();
		LIVE.put(hold, this);
		this.listener = listener;
		anchor = entity.position();
		FieldHolds.of((ServerLevel) level()).put(hold, heldId, name, entity.getBoundingBox().getCenter(), level().getGameTime());
		grip(entity);
		level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), com.payonel.modularcomputers.sound.ModSounds.FIELD_ON,
			net.minecraft.sounds.SoundSource.NEUTRAL, 1, 1);
		return true;
	}

	/** It has what it holds (at its capture, or again after a load). */
	private void grip(Entity entity) {
		held = entity;
		entityData.set(HELD, entity.getId());
		double width = entity.getBbWidth();
		double height = entity.getBbHeight();
		entityData.set(RADIUS, (float) (Math.sqrt(2 * width * width + height * height) / 2 + MARGIN)); // (its box's half diagonal)
		follow();
	}

	/** What it holds. */
	public Entity holding() {
		return held;
	}

	/** Where what it holds is: its middle. */
	public Vec3 center() {
		return held.getBoundingBox().getCenter();
	}

	/** Starts moving what it holds by {@code by}, its middle to there: its listener hears when it arrives, or is blocked. */
	public void moveBy(Vec3 by) {
		target = anchor.add(by);
	}

	/** Moves where it holds what it holds by {@code by} at once, a move under way too (what it holds changed its shape: a golem's legs off). */
	public void shift(Vec3 by) {
		anchor = anchor.add(by);
		if (target != null) {
			target = target.add(by);
		}
	}

	/** Stops a move under way, holding what it holds where it is. */
	public void stop() {
		target = null;
	}

	/** Lets what it holds go (it falls), and goes. */
	public void release() {
		if (held != null) {
			Vec3 at = held.getBoundingBox().getCenter();
			level().playSound(null, at.x, at.y, at.z, com.payonel.modularcomputers.sound.ModSounds.FIELD_OFF, net.minecraft.sounds.SoundSource.NEUTRAL, 1, 1);
			HOLDING.remove(held, this);
			held.resetFallDistance(); // (it falls from here: what it fell held in place doesn't count)
			if (held instanceof ItemEntity item) {
				item.setDefaultPickUpDelay();
			}
		}
		held = null;
		listener = null;
		if (level() instanceof ServerLevel server) {
			FieldHolds.of(server).remove(hold); // (its hold ends: its one home)
		}
		discard();
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel)) {
			if (!humming) {
				humming = true;
				clientHum.accept(this); // (its hum, round it, for as long as it's there)
			}
			return;
		}
		if (hold != null && LIVE.get(hold) != this) {
			LIVE.put(hold, this); // (in the world, ticking: loaded, its card finds it; not while it's only being read in)
		}
		FieldHolds.Hold record = FieldHolds.of((ServerLevel) level()).get(hold);
		if (record == null) {
			if (held != null) {
				release(); // (its hold ended while it wasn't loaded, or its card ended it: it lets go)
			} else {
				discard();
			}
			return;
		}
		if (held == null) {
			Entity found = heldId == null ? null : ((ServerLevel) level()).getEntity(heldId);
			if (found == null || !found.isAlive()) {
				return; // (loaded before what it holds: it waits for it, its hold kept)
			}
			if (HOLDING.putIfAbsent(found, this) != null) {
				release(); // (another field took it meanwhile)
				return;
			}
			grip(found); // (loaded again: it holds it, where it held it)
			if (anchor == null) {
				anchor = found.position();
			}
		}
		if (!held.isAlive() || held.level() != level()) {
			lose(com.payonel.modularcomputers.device.Buffered.LOST); // (what it held is gone: dead, picked up, through a portal)
			return;
		}
		boolean cardHere = listener != null && record.seen() >= level().getGameTime() - CARD_HERE;
		if (target != null && cardHere) { // (its card unloaded: frozen in time, its move waiting)
			Vec3 left = target.subtract(anchor);
			double distance = left.length();
			if (distance < THERE) {
				anchor = target;
				target = null;
				place(anchor);
				listener.arrived();
			} else {
				Vec3 step = left.scale(Math.min(1, SPEED / distance));
				place(anchor);
				held.move(MoverType.SELF, step); // (blocks stop it, as anything moving)
				Vec3 moved = held.position().subtract(anchor);
				anchor = held.position();
				listener.moved(moved.length());
				if (moved.length() < step.length() * STOPPED) {
					target = null;
					Obstruction met = Obstruction.of(held, step, moved); // (the side of what it holds that met a block, and the block)
					listener.blocked(met.block(), met.side());
				}
			}
		}
		place(anchor);
		follow();
		FieldHolds.of((ServerLevel) level()).moved(hold, held.getBoundingBox().getCenter());
	}

	/** Puts what it holds at {@code at} (its feet), still: no fall, no motion of its own. */
	private void place(Vec3 at) {
		held.setDeltaMovement(Vec3.ZERO);
		held.resetFallDistance();
		if (held instanceof ServerPlayer player) {
			if (player.position().distanceToSqr(at) > 1e-6) {
				player.connection.teleport(at.x, at.y, at.z, player.getYRot(), player.getXRot()); // (a player's position is its client's)
			}
		} else {
			held.setPos(at);
		}
		if (held instanceof ItemEntity item) {
			item.setPickUpDelay(2); // (no one picks up what a field holds)
		}
	}

	/** The field goes where what it holds is (it's drawn round it). */
	private void follow() {
		Vec3 center = held.getBoundingBox().getCenter();
		setPos(center.x, center.y, center.z);
	}

	/** It lost what it held ({@code why}): its listener hears, and it goes. */
	public void lose(String why) {
		Listener was = listener;
		release();
		if (was != null) {
			was.lost(why);
		}
	}

	/** Its radius, as players are sent it. */
	public float radius() {
		return entityData.get(RADIUS);
	}

	/** What it holds, on a client (its id; -1: nothing). */
	public int heldId() {
		return entityData.get(HELD);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(HELD, NONE).define(RADIUS, 0.5f);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		hold = input.read("hold", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		heldId = input.read("held", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
		anchor = input.read("anchor", Vec3.CODEC).orElse(null);
		target = input.read("target", Vec3.CODEC).orElse(null);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.storeNullable("hold", net.minecraft.core.UUIDUtil.CODEC, hold);
		output.storeNullable("held", net.minecraft.core.UUIDUtil.CODEC, heldId);
		output.storeNullable("anchor", Vec3.CODEC, anchor);
		output.storeNullable("target", Vec3.CODEC, target);
	}

	@Override
	public void onRemoval(RemovalReason reason) {
		super.onRemoval(reason);
		if (held != null) {
			HOLDING.remove(held, this); // (its chunk unloaded, or its world stopped: what it held goes with it, its hold kept)
		}
		if (hold != null) {
			LIVE.remove(hold, this);
		}
	}
}
