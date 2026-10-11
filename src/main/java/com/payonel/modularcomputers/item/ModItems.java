package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.payonel.modularcomputers.device.CardKind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Parts that go into computers. Only the CPU and EEPROM do anything yet; the others fill their
 * slots (and the bus) so the case view can be built out.
 */
public final class ModItems {
	public static final Item CPU = register("cpu");
	/** Every EEPROM holds the stand-in program for now; programmable contents come with the Lua runtime. */
	public static final Item EEPROM = register("eeprom");
	public static final Item RAM = register("ram");
	public static final Item HARD_DISK = register("hard_disk");
	/** Expansion cards, for a computer's expansion slots or an expansion hub. */
	public static final Item WLAN_CARD = register("wlan_card");
	public static final Item BLUETOOTH_CARD = register("bluetooth_card");
	public static final Item REDSTONE_CARD = register("redstone_card");
	public static final Item AUDIO_CARD = register("audio_card");
	public static final Item MOTION_SENSOR_CARD = register("motion_sensor_card");
	public static final Item AUDIO_SENSOR_CARD = register("audio_sensor_card");
	public static final Item WEATHER_SENSOR_CARD = register("weather_sensor_card");
	/** Each card item and the kind of card it is. */
	public static final Map<Item, CardKind> CARDS = cards();
	/** Backup power; carries its charge. New ones come fully charged. */
	public static final Item UPS_BATTERY = register("ups_battery", properties -> properties.component(ModComponents.CHARGE, 100));

	private ModItems() {
	}

	private static Map<Item, CardKind> cards() {
		Map<Item, CardKind> cards = new LinkedHashMap<>();
		cards.put(WLAN_CARD, CardKind.WLAN);
		cards.put(BLUETOOTH_CARD, CardKind.BLUETOOTH);
		cards.put(REDSTONE_CARD, CardKind.REDSTONE);
		cards.put(AUDIO_CARD, CardKind.AUDIO);
		cards.put(MOTION_SENSOR_CARD, CardKind.MOTION_SENSOR);
		cards.put(AUDIO_SENSOR_CARD, CardKind.AUDIO_SENSOR);
		cards.put(WEATHER_SENSOR_CARD, CardKind.WEATHER_SENSOR);
		return Collections.unmodifiableMap(cards);
	}

	/** The kind of expansion card a stack is, if it is one. */
	public static Optional<CardKind> cardKind(ItemStack stack) {
		return Optional.ofNullable(CARDS.get(stack.getItem()));
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
