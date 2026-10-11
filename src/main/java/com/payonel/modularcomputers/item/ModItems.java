package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Parts that go into computers. Only the CPU and EEPROM do anything yet; the others fill their
 * slots (and the bus, for drives and cards) so the case view can be built out.
 */
public final class ModItems {
	public static final Item CPU = register("cpu");
	/** Every EEPROM holds the stand-in program for now; programmable contents come with the Lua runtime. */
	public static final Item EEPROM = register("eeprom");
	public static final Item RAM = register("ram");
	public static final Item HARD_DISK = register("hard_disk");
	/** An expansion card. */
	public static final Item WLAN_CARD = register("wlan_card");
	/** Backup power; carries its charge. New ones come fully charged. */
	public static final Item UPS_BATTERY = register("ups_battery", properties -> properties.component(ModComponents.CHARGE, 100));

	private ModItems() {
	}

	/** Forces the static registrations above. */
	public static void init() {
	}

	private static Item register(String name) {
		return register(name, UnaryOperator.identity());
	}

	private static Item register(String name, UnaryOperator<Item.Properties> extra) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(extra.apply(new Item.Properties().setId(key).stacksTo(1))));
	}
}
