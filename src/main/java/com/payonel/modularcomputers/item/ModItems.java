package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Component items that go into computers. */
public final class ModItems {
	public static final Item CPU = register("cpu");
	/** Every EEPROM holds the stand-in program for now; programmable contents come with the Lua runtime. */
	public static final Item EEPROM = register("eeprom");

	private ModItems() {
	}

	/** Forces the static registrations above. */
	public static void init() {
	}

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
	}
}
