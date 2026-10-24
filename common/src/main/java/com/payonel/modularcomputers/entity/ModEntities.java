package com.payonel.modularcomputers.entity;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.payonel.modularcomputers.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The mod's entities. */
public final class ModEntities {
	/** A block in size: through a one-block gap, under a slab. */
	private static final float GOLEM_WIDTH = 0.8f;
	private static final float GOLEM_HEIGHT = 0.95f;
	/** How far away players see it move, in chunks (an iron golem's). */
	private static final int GOLEM_TRACKING = 10;

	public static final EntityType<BotGolem> BOT_GOLEM = register("bot_golem",
		EntityType.Builder.<BotGolem>of(BotGolem::new, MobCategory.MISC).sized(GOLEM_WIDTH, GOLEM_HEIGHT).clientTrackingRange(GOLEM_TRACKING));

	private ModEntities() {
	}

	/** Forces the static registration above, and gives the living ones their attributes. */
	public static void init() {
		Platform.INSTANCE.entityAttributes(BOT_GOLEM, BotGolem::attributes);
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
