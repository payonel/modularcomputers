package com.payonel.modularcomputers.entity;

import com.payonel.modularcomputers.mixin.MobGoals;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;

/**
 * Which mobs fear bot golems (the user's): spiders (and so a skeleton riding one) keep away from any
 * golem, as they keep away from armadillos; chickens and cows (mooshrooms too) from a flying one.
 * Sheep don't care. Each runs from it as from its other fears: within {@link #NEAR} blocks, walking,
 * then faster as it closes. The mixins (SpiderFear, ChickenFear, CowFear) add the goal as each mob
 * registers its goals.
 */
public final class GolemFear {
	/** How near a golem a mob runs from it, in blocks, and how fast it goes (its walk, then its sprint: a spider's from an armadillo). */
	public static final float NEAR = 6;
	private static final double WALK = 1.0;
	private static final double SPRINT = 1.2;
	/** The goal's place among a mob's goals: with its other fears (a spider's armadillo; a farm animal's panic). */
	public static final int SPIDER_PRIORITY = 2;
	public static final int ANIMAL_PRIORITY = 1;

	private GolemFear() {
	}

	/** Makes {@code mob} run from the golems {@code which} picks. */
	public static void fear(PathfinderMob mob, int priority, java.util.function.Predicate<BotGolem> which) {
		((MobGoals) mob).modularcomputers$goals().addGoal(priority,
			new AvoidEntityGoal<>(mob, BotGolem.class, NEAR, WALK, SPRINT, entity -> which.test((BotGolem) entity)));
	}
}
