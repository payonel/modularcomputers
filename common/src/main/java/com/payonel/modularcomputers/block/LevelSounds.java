package com.payonel.modularcomputers.block;

import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The sounds the game plays from level events, not as sounds: a server sends players an event's
 * number (a dispenser fired, a zombie hit a door), and each client plays its sound
 * ({@code LevelEventHandler}). Audio sensors hear them as players would, from this table: each
 * event that plays a sound, the sound it plays and its category, as the client's handler has them
 * (where an event may play one of several, its first). A block broken (2001) plays its block's own
 * break sound ({@link CardWorld#levelEvent}); events heard world-wide (a wither's spawn) aren't here.
 */
final class LevelSounds {
	/** A level event's sound and its category. */
	record LevelSound(SoundEvent sound, SoundSource source) {
	}

	/** By level event number ({@code LevelEvent}). */
	static final Map<Integer, LevelSound> BY_EVENT = Map.ofEntries(
		Map.entry(1000, new LevelSound(SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS)),
		Map.entry(1001, new LevelSound(SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS)),
		Map.entry(1002, new LevelSound(SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS)),
		Map.entry(1004, new LevelSound(SoundEvents.FIREWORK_ROCKET_SHOOT, SoundSource.NEUTRAL)),
		Map.entry(1009, new LevelSound(SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS)),
		Map.entry(1015, new LevelSound(SoundEvents.GHAST_WARN, SoundSource.HOSTILE)),
		Map.entry(1016, new LevelSound(SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE)),
		Map.entry(1017, new LevelSound(SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE)),
		Map.entry(1018, new LevelSound(SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE)),
		Map.entry(1019, new LevelSound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE)),
		Map.entry(1020, new LevelSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE)),
		Map.entry(1021, new LevelSound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE)),
		Map.entry(1022, new LevelSound(SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE)),
		Map.entry(1024, new LevelSound(SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE)),
		Map.entry(1025, new LevelSound(SoundEvents.BAT_TAKEOFF, SoundSource.NEUTRAL)),
		Map.entry(1026, new LevelSound(SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE)),
		Map.entry(1027, new LevelSound(SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE)),
		Map.entry(1029, new LevelSound(SoundEvents.ANVIL_DESTROY, SoundSource.BLOCKS)),
		Map.entry(1030, new LevelSound(SoundEvents.ANVIL_USE, SoundSource.BLOCKS)),
		Map.entry(1031, new LevelSound(SoundEvents.ANVIL_LAND, SoundSource.BLOCKS)),
		Map.entry(1033, new LevelSound(SoundEvents.CHORUS_FLOWER_GROW, SoundSource.BLOCKS)),
		Map.entry(1034, new LevelSound(SoundEvents.CHORUS_FLOWER_DEATH, SoundSource.BLOCKS)),
		Map.entry(1035, new LevelSound(SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS)),
		Map.entry(1039, new LevelSound(SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE)),
		Map.entry(1040, new LevelSound(SoundEvents.ZOMBIE_CONVERTED_TO_DROWNED, SoundSource.HOSTILE)),
		Map.entry(1041, new LevelSound(SoundEvents.HUSK_CONVERTED_TO_ZOMBIE, SoundSource.HOSTILE)),
		Map.entry(1042, new LevelSound(SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS)),
		Map.entry(1043, new LevelSound(SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS)),
		Map.entry(1044, new LevelSound(SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS)),
		Map.entry(1045, new LevelSound(SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.BLOCKS)),
		Map.entry(1046, new LevelSound(SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA_INTO_CAULDRON, SoundSource.BLOCKS)),
		Map.entry(1047, new LevelSound(SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, SoundSource.BLOCKS)),
		Map.entry(1048, new LevelSound(SoundEvents.SKELETON_CONVERTED_TO_STRAY, SoundSource.HOSTILE)),
		Map.entry(1049, new LevelSound(SoundEvents.CRAFTER_CRAFT, SoundSource.BLOCKS)),
		Map.entry(1050, new LevelSound(SoundEvents.CRAFTER_FAIL, SoundSource.BLOCKS)),
		Map.entry(1051, new LevelSound(SoundEvents.WIND_CHARGE_THROW, SoundSource.BLOCKS)),
		Map.entry(1052, new LevelSound(SoundEvents.SULFUR_SPIKE_LAND, SoundSource.BLOCKS)),
		Map.entry(1054, new LevelSound(SoundEvents.SPLASH_POTION_BREAK, SoundSource.NEUTRAL)),
		Map.entry(1501, new LevelSound(SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS)),
		Map.entry(1502, new LevelSound(SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS)),
		Map.entry(1503, new LevelSound(SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS)),
		Map.entry(1505, new LevelSound(SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS)),
		Map.entry(2006, new LevelSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE)),
		Map.entry(3000, new LevelSound(SoundEvents.END_GATEWAY_SPAWN, SoundSource.BLOCKS)),
		Map.entry(3001, new LevelSound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE)),
		Map.entry(3006, new LevelSound(SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.BLOCKS)),
		Map.entry(3007, new LevelSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS)),
		Map.entry(3012, new LevelSound(SoundEvents.TRIAL_SPAWNER_SPAWN_MOB, SoundSource.BLOCKS)),
		Map.entry(3013, new LevelSound(SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER, SoundSource.BLOCKS)),
		Map.entry(3014, new LevelSound(SoundEvents.TRIAL_SPAWNER_EJECT_ITEM, SoundSource.BLOCKS)),
		Map.entry(3015, new LevelSound(SoundEvents.VAULT_ACTIVATE, SoundSource.BLOCKS)),
		Map.entry(3016, new LevelSound(SoundEvents.VAULT_DEACTIVATE, SoundSource.BLOCKS)),
		Map.entry(3018, new LevelSound(SoundEvents.COBWEB_PLACE, SoundSource.BLOCKS)),
		Map.entry(3019, new LevelSound(SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER, SoundSource.BLOCKS)),
		Map.entry(3020, new LevelSound(SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.BLOCKS)),
		Map.entry(3021, new LevelSound(SoundEvents.TRIAL_SPAWNER_SPAWN_ITEM, SoundSource.BLOCKS)));

	private LevelSounds() {
	}
}
