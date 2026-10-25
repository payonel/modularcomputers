package com.payonel.modularcomputers.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * How long since a living entity last swung (its attack strength's ticker): a robot's hand (a fake
 * player, which never ticks) is given the time since the robot's own last swing, so a hit is as
 * strong as a player's at that pace.
 */
@Mixin(LivingEntity.class)
public interface AttackStrength {
	@Accessor("attackStrengthTicker")
	void modularcomputers$setAttackStrengthTicker(int ticks);
}
