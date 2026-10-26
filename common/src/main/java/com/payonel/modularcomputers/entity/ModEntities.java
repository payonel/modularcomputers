package com.payonel.modularcomputers.entity;

import com.payonel.modularcomputers.machine.GolemLayout;
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

	/** A tier 1 golem walks; a tier 2 flies too (GolemLayout's tiers). */
	public static final EntityType<BotGolem> TIER1_BOT_GOLEM = register("tier1_bot_golem",
		EntityType.Builder.<BotGolem>of((type, level) -> new BotGolem(type, level, GolemLayout.TIER_1), MobCategory.MISC).sized(GOLEM_WIDTH, GOLEM_HEIGHT)
			.clientTrackingRange(GOLEM_TRACKING));
	public static final EntityType<BotGolem> TIER2_BOT_GOLEM = register("tier2_bot_golem",
		EntityType.Builder.<BotGolem>of((type, level) -> new BotGolem(type, level, GolemLayout.TIER_2), MobCategory.MISC).sized(GOLEM_WIDTH, GOLEM_HEIGHT)
			.clientTrackingRange(GOLEM_TRACKING));

	/** A golem of a tier's type. */
	public static EntityType<BotGolem> botGolem(GolemLayout tier) {
		return tier == GolemLayout.TIER_1 ? TIER1_BOT_GOLEM : TIER2_BOT_GOLEM;
	}

	/** A field's own box: small (it's drawn round what it holds, whatever its size), never in the way. */
	private static final float FIELD_SIZE = 0.25f;
	/** How far away players see a field, in chunks: as far as what it may hold. */
	private static final int FIELD_TRACKING = 10;

	public static final EntityType<EnergyField> ENERGY_FIELD = register("energy_field",
		EntityType.Builder.<EnergyField>of(EnergyField::new, MobCategory.MISC).sized(FIELD_SIZE, FIELD_SIZE).noSave().clientTrackingRange(FIELD_TRACKING)
			.updateInterval(1));

	/** A golem's legs: as wide as they stand, three pixels high. */
	private static final float LEGS_WIDTH = 0.6f;
	private static final float LEGS_HEIGHT = BotGolem.LEG_HEIGHT;

	public static final EntityType<GolemLegs> GOLEM_LEGS = register("golem_legs",
		EntityType.Builder.<GolemLegs>of(GolemLegs::new, MobCategory.MISC).sized(LEGS_WIDTH, LEGS_HEIGHT).clientTrackingRange(GOLEM_TRACKING));

	private ModEntities() {
	}

	/** Forces the static registration above, and gives the living ones their attributes. */
	public static void init() {
		Platform.INSTANCE.entityAttributes(TIER1_BOT_GOLEM, BotGolem::attributes);
		Platform.INSTANCE.entityAttributes(TIER2_BOT_GOLEM, BotGolem::attributes);
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}
}
