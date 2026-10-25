package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.mixin.MobGoals;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;

/**
 * Which mobs fear bot golems (the user's): spiders run from any golem; chickens and cows from a
 * flying one, not one on its legs; sheep from none. A mob's fear is its avoid goal starting
 * (AvoidEntityGoal.canUse: a golem near that it fears, and somewhere away to go).
 */
public class GolemFearGameTest {
	/** Where the golem stands, and where the mob is: two blocks off. */
	private static final BlockPos GOLEM = new BlockPos(1, 1, 1);
	private static final BlockPos MOB = new BlockPos(3, 1, 1);
	/** How long a mob that doesn't fear it is watched, in ticks. */
	private static final int WATCH = 40;

	/** Whether any of a mob's avoid goals would start now. */
	private static boolean afraid(PathfinderMob mob) {
		return ((MobGoals) mob).modularcomputers$goals().getAvailableGoals().stream()
			.anyMatch(goal -> goal.getGoal() instanceof AvoidEntityGoal<?> avoid && avoid.canUse());
	}

	private static BotGolem golem(GameTestHelper helper, boolean flying) {
		BotGolem golem = BotGolemGameTest.golem(helper, board -> {
		});
		if (flying) {
			golem.legs(false);
			golem.setNoGravity(true);
			golem.setPos(golem.getX(), golem.getY() + 1, golem.getZ()); // (in the air, its legs off: as a field holds it)
		}
		return golem;
	}

	private static void afraid(GameTestHelper helper, EntityType<? extends PathfinderMob> type, boolean flying) {
		golem(helper, flying);
		PathfinderMob mob = helper.spawn(type, MOB);
		helper.succeedWhen(() -> helper.assertTrue(afraid(mob), type + " runs from the golem"));
	}

	private static void unafraid(GameTestHelper helper, EntityType<? extends PathfinderMob> type, boolean flying) {
		golem(helper, flying);
		PathfinderMob mob = helper.spawn(type, MOB);
		helper.onEachTick(() -> helper.assertTrue(!afraid(mob), type + " doesn't mind the golem"));
		helper.runAfterDelay(WATCH, helper::succeed);
	}

	@GameTest(maxTicks = 100)
	public void aSpiderRunsFromAGolem(GameTestHelper helper) {
		afraid(helper, EntityTypes.SPIDER, false);
	}

	@GameTest(maxTicks = 100)
	public void aCowRunsFromAFlyingGolem(GameTestHelper helper) {
		afraid(helper, EntityTypes.COW, true);
	}

	@GameTest(maxTicks = 100)
	public void aChickenRunsFromAFlyingGolem(GameTestHelper helper) {
		afraid(helper, EntityTypes.CHICKEN, true);
	}

	@GameTest(maxTicks = 100)
	public void aCowDoesntMindAGolemOnItsLegs(GameTestHelper helper) {
		unafraid(helper, EntityTypes.COW, false);
	}

	@GameTest(maxTicks = 100)
	public void aSheepDoesntMindAFlyingGolem(GameTestHelper helper) {
		unafraid(helper, EntityTypes.SHEEP, true);
	}
}
