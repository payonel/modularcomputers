package com.payonel.modularcomputers.entity;

import com.payonel.modularcomputers.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A bot golem's legs, left standing where it detached them (user design, 2026-10-24): an object in
 * the world, as a boat is (solid, falling, pushed by nothing on its own). A right click pops them up
 * into the clicker's hands (a golem's too: its hand's use; no tool needed); a player's hit picks
 * them up as well. A golem attaches them again from close by, and their item placed stands them up.
 */
public final class GolemLegs extends Entity {
	/** How a fall slows, a tick, in the air and on the ground (an item's). */
	private static final double DRAG = 0.98;
	private static final double GROUND_FRICTION = 0.7;
	/** The gravity a tick, in blocks a tick a tick (an item's). */
	private static final double GRAVITY = 0.04;

	public GolemLegs(EntityType<? extends GolemLegs> type, Level level) {
		super(type, level);
	}

	@Override
	protected double getDefaultGravity() {
		return GRAVITY;
	}

	@Override
	public void tick() {
		super.tick();
		applyGravity();
		move(MoverType.SELF, getDeltaMovement());
		Vec3 motion = getDeltaMovement().scale(DRAG);
		if (onGround()) {
			motion = motion.multiply(GROUND_FRICTION, 0, GROUND_FRICTION);
		}
		setDeltaMovement(motion);
	}

	/** Solid, as a boat is: walked into, stood on. */
	@Override
	public boolean canBeCollidedWith(Entity other) {
		return isAlive();
	}

	@Override
	public boolean isPickable() {
		return isAlive();
	}

	/** A right click pops them up: into the clicker's inventory (a golem's storage), or at its feet with no room. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (level() instanceof ServerLevel && isAlive()) {
			ItemStack legs = new ItemStack(ModItems.TIER2_BOT_GOLEM_LEGS);
			if (!player.getInventory().add(legs) && level() instanceof ServerLevel server) {
				spawnAtLocation(server, legs);
			}
			discard();
		}
		return InteractionResult.SUCCESS;
	}

	/** A player's hit picks them up too; nothing else harms them. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof Player && isAlive()) {
			spawnAtLocation(level, new ItemStack(ModItems.TIER2_BOT_GOLEM_LEGS));
			discard();
			return true;
		}
		return false;
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
