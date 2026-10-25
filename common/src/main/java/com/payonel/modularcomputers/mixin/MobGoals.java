package com.payonel.modularcomputers.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's goals, for the mixins that add to them (which mobs fear golems: GolemFear). */
@Mixin(Mob.class)
public interface MobGoals {
	@Accessor("goalSelector")
	GoalSelector modularcomputers$goals();
}
