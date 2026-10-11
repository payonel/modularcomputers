package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Item data components. */
public final class ModComponents {
	/** A battery's charge, 0..100 percent. */
	public static final DataComponentType<Integer> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("charge"),
		DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 100)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	private ModComponents() {
	}

	/** Forces the static registration above. */
	public static void init() {
	}
}
