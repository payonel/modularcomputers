package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.mojang.serialization.Codec;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Item data components. */
public final class ModComponents {
	/** A battery's charge, 0..100 percent. */
	public static final DataComponentType<Integer> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("charge"),
		DataComponentType.<Integer>builder().persistent(Codec.intRange(0, 100)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	/** A Bluetooth card's own address: other cards link to it. Set when something first links to it, and by a reset. */
	public static final DataComponentType<UUID> BLUETOOTH_ID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("bluetooth_id"),
		DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	/** The address of the one Bluetooth card this card links to. */
	public static final DataComponentType<UUID> BLUETOOTH_LINK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("bluetooth_link"),
		DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	private ModComponents() {
	}

	/** Forces the static registration above. */
	public static void init() {
	}
}
