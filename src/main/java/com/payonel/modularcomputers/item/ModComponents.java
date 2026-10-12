package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.payonel.modularcomputers.power.BatteryCharge;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Item data components. */
public final class ModComponents {
	/** A battery's charge: ticks of running a CPU, and when it was last worked out. */
	public static final DataComponentType<BatteryCharge> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("battery_charge"),
		DataComponentType.<BatteryCharge>builder()
			.persistent(RecordCodecBuilder.create(charge -> charge.group(
				Codec.intRange(0, Integer.MAX_VALUE).fieldOf("units").forGetter(BatteryCharge::units),
				Codec.LONG.optionalFieldOf("settled_at", BatteryCharge.UNSETTLED).forGetter(BatteryCharge::settledAt)
			).apply(charge, BatteryCharge::new)))
			.networkSynchronized(StreamCodec.composite(ByteBufCodecs.VAR_INT, BatteryCharge::units, ByteBufCodecs.VAR_LONG, BatteryCharge::settledAt, BatteryCharge::new))
			.build());

	/**
	 * A card's serial number, stamped the first time it goes into a computer or an expansion hub.
	 * Programs can address it as {@code id:<serial>} wherever it is (like Linux's
	 * {@code /dev/disk/by-id}), and a hub's computers keep its place by it, whichever slot it's in.
	 */
	public static final DataComponentType<UUID> SERIAL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("serial"),
		DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	/** The board (a computer's, a hub's, ...) this Bluetooth card links to. */
	public static final DataComponentType<UUID> BLUETOOTH_LINK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("bluetooth_link"),
		DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	/** What an EEPROM holds (its image), written by programs; up to {@code Eeprom.SIZE} bytes. Not sent to clients. */
	public static final DataComponentType<String> EEPROM_IMAGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("eeprom_image"),
		DataComponentType.<String>builder().persistent(Codec.STRING).build());

	private ModComponents() {
	}

	/** Forces the static registration above. */
	public static void init() {
	}

	/** What the player named the item (on an anvil), if anything: its device's label. */
	public static String label(ItemStack stack) {
		net.minecraft.network.chat.Component name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
		return name == null ? null : name.getString();
	}

	/** The card's serial number, stamping a new one on if it has none yet. Server side only. */
	public static UUID serial(ItemStack card) {
		UUID serial = card.get(SERIAL);
		if (serial == null) {
			serial = UUID.randomUUID();
			card.set(SERIAL, serial);
		}
		return serial;
	}
}
