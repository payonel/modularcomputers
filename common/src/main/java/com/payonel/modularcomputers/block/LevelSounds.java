package com.payonel.modularcomputers.block;

import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The sounds the game plays from level events, not as sounds: a server sends players an event's
 * number (a dispenser fired, a zombie hit a door), and each client plays its sound
 * ({@code LevelEventHandler}). Audio sensors hear them as players would, from this table: each
 * event that plays a sound, the sound it plays and its category, as the client's handler has them
 * (where an event may play one of several, its first). A block broken (2001) plays its block's own
 * break sound ({@link CardWorld#levelEvent}); events heard world-wide (a wither's spawn) aren't here.
 */
final class LevelSounds {
	/** By level event number ({@code LevelEvent}). */
	static final Map<Integer, SoundEvent> BY_EVENT = Map.ofEntries(
		Map.entry(1000, SoundEvents.DISPENSER_DISPENSE),
		Map.entry(1001, SoundEvents.DISPENSER_FAIL),
		Map.entry(1002, SoundEvents.DISPENSER_LAUNCH),
		Map.entry(1004, SoundEvents.FIREWORK_ROCKET_SHOOT),
		Map.entry(1009, SoundEvents.FIRE_EXTINGUISH),
		Map.entry(1015, SoundEvents.GHAST_WARN),
		Map.entry(1016, SoundEvents.GHAST_SHOOT),
		Map.entry(1017, SoundEvents.ENDER_DRAGON_SHOOT),
		Map.entry(1018, SoundEvents.BLAZE_SHOOT),
		Map.entry(1019, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR),
		Map.entry(1020, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR),
		Map.entry(1021, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR),
		Map.entry(1022, SoundEvents.WITHER_BREAK_BLOCK),
		Map.entry(1024, SoundEvents.WITHER_SHOOT),
		Map.entry(1025, SoundEvents.BAT_TAKEOFF),
		Map.entry(1026, SoundEvents.ZOMBIE_INFECT),
		Map.entry(1027, SoundEvents.ZOMBIE_VILLAGER_CONVERTED),
		Map.entry(1029, SoundEvents.ANVIL_DESTROY),
		Map.entry(1030, SoundEvents.ANVIL_USE),
		Map.entry(1031, SoundEvents.ANVIL_LAND),
		Map.entry(1033, SoundEvents.CHORUS_FLOWER_GROW),
		Map.entry(1034, SoundEvents.CHORUS_FLOWER_DEATH),
		Map.entry(1035, SoundEvents.BREWING_STAND_BREW),
		Map.entry(1039, SoundEvents.PHANTOM_BITE),
		Map.entry(1040, SoundEvents.ZOMBIE_CONVERTED_TO_DROWNED),
		Map.entry(1041, SoundEvents.HUSK_CONVERTED_TO_ZOMBIE),
		Map.entry(1042, SoundEvents.GRINDSTONE_USE),
		Map.entry(1043, SoundEvents.BOOK_PAGE_TURN),
		Map.entry(1044, SoundEvents.SMITHING_TABLE_USE),
		Map.entry(1045, SoundEvents.POINTED_DRIPSTONE_LAND),
		Map.entry(1046, SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA_INTO_CAULDRON),
		Map.entry(1047, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON),
		Map.entry(1048, SoundEvents.SKELETON_CONVERTED_TO_STRAY),
		Map.entry(1049, SoundEvents.CRAFTER_CRAFT),
		Map.entry(1050, SoundEvents.CRAFTER_FAIL),
		Map.entry(1051, SoundEvents.WIND_CHARGE_THROW),
		Map.entry(1052, SoundEvents.SULFUR_SPIKE_LAND),
		Map.entry(1054, SoundEvents.SPLASH_POTION_BREAK),
		Map.entry(1501, SoundEvents.LAVA_EXTINGUISH),
		Map.entry(1502, SoundEvents.REDSTONE_TORCH_BURNOUT),
		Map.entry(1503, SoundEvents.END_PORTAL_FRAME_FILL),
		Map.entry(1505, SoundEvents.BONE_MEAL_USE),
		Map.entry(2006, SoundEvents.DRAGON_FIREBALL_EXPLODE),
		Map.entry(3000, SoundEvents.END_GATEWAY_SPAWN),
		Map.entry(3001, SoundEvents.ENDER_DRAGON_GROWL),
		Map.entry(3006, SoundEvents.SCULK_BLOCK_CHARGE),
		Map.entry(3007, SoundEvents.SCULK_SHRIEKER_SHRIEK),
		Map.entry(3012, SoundEvents.TRIAL_SPAWNER_SPAWN_MOB),
		Map.entry(3013, SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER),
		Map.entry(3014, SoundEvents.TRIAL_SPAWNER_EJECT_ITEM),
		Map.entry(3015, SoundEvents.VAULT_ACTIVATE),
		Map.entry(3016, SoundEvents.VAULT_DEACTIVATE),
		Map.entry(3018, SoundEvents.COBWEB_PLACE),
		Map.entry(3019, SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER),
		Map.entry(3020, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE),
		Map.entry(3021, SoundEvents.TRIAL_SPAWNER_SPAWN_ITEM));

	private LevelSounds() {
	}
}
