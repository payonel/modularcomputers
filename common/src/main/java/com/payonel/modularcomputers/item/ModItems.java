package com.payonel.modularcomputers.item;

import static com.payonel.modularcomputers.ModularComputers.id;

import com.payonel.modularcomputers.device.CardKind;
import com.payonel.modularcomputers.hint.HintedItem;
import com.payonel.modularcomputers.machine.CpuTier;
import com.payonel.modularcomputers.machine.MemoryTier;
import com.payonel.modularcomputers.device.DiskTier;
import com.payonel.modularcomputers.power.BatteryTier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
	/** CPUs: the tier sets the bus capacity and the power draw. */
	public static final CpuItem TIER1_CPU = cpu("tier1_cpu", CpuTier.TIER_1);
	public static final CpuItem TIER2_CPU = cpu("tier2_cpu", CpuTier.TIER_2);
	public static final CpuItem TIER3_CPU = cpu("tier3_cpu", CpuTier.TIER_3);
	public static final List<CpuItem> CPUS = List.of(TIER1_CPU, TIER2_CPU, TIER3_CPU);
	/** Creative only (user, 2026-10-12): a tier 3 that runs Java programs, not Lua. */
	public static final CpuItem JAVA_CPU = (CpuItem) register("java_cpu",
		properties -> new CpuItem(properties, CpuTier.TIER_3, com.payonel.modularcomputers.machine.Architecture.JAVA), UnaryOperator.identity());
	/** Refined from nether quartz in a blast furnace: what chips are printed on. */
	public static final Item SILICON = register("silicon");
	/** A die, printed from silicon through a photomask: the chip in every part. */
	public static final Item CHIP = register("chip");
	/** Comes from the factory flashed with a bootloader (eeprom/boot.lua), as real boards ship with firmware (crafting it alone wipes it). */
	public static final Item EEPROM = register("eeprom", properties -> holdsData(properties)
		.component(ModComponents.EEPROM_IMAGE, com.payonel.modularcomputers.machine.LuaScripts.read("eeprom/boot")));
	/** Creative only, for the Java CPU: flashed with mcos's Java version (its class name). */
	public static final Item JAVA_EEPROM = register("java_eeprom", properties -> holdsData(properties)
		.component(ModComponents.EEPROM_IMAGE, com.payonel.modularcomputers.machine.Mcos.class.getName()));
	public static final List<Item> EEPROMS = List.of(EEPROM, JAVA_EEPROM);
	/** RAM sticks: the tier sets the size. */
	public static final MemoryItem TIER1_RAM = ram("tier1_ram", MemoryTier.TIER_1);
	public static final MemoryItem TIER2_RAM = ram("tier2_ram", MemoryTier.TIER_2);
	public static final MemoryItem TIER3_RAM = ram("tier3_ram", MemoryTier.TIER_3);
	public static final List<MemoryItem> RAMS = List.of(TIER1_RAM, TIER2_RAM, TIER3_RAM);
	/** Hard disks: the tier sets the size and the power draw. */
	public static final DiskItem TIER1_HARD_DISK = disk("tier1_hard_disk", DiskTier.TIER_1);
	public static final DiskItem TIER2_HARD_DISK = disk("tier2_hard_disk", DiskTier.TIER_2);
	public static final DiskItem TIER3_HARD_DISK = disk("tier3_hard_disk", DiskTier.TIER_3);
	public static final List<DiskItem> HARD_DISKS = List.of(TIER1_HARD_DISK, TIER2_HARD_DISK, TIER3_HARD_DISK);
	/** Expansion cards, for a computer's expansion slots or an expansion hub. */
	public static final Item NETWORK_CARD = register("network_card");
	/** WLAN cards: tier 1 reaches 16 blocks, tier 2 400 (OpenComputers' wireless cards). */
	public static final Item TIER1_WLAN_CARD = register("tier1_wlan_card");
	public static final Item TIER2_WLAN_CARD = register("tier2_wlan_card");
	public static final Item BLUETOOTH_CARD = register("bluetooth_card", BluetoothCardItem::new, UnaryOperator.identity());
	public static final Item REDSTONE_CARD = register("redstone_card");
	public static final Item SOUND_CARD = register("sound_card");
	/** HTTP and HTTPS to the real internet. */
	public static final Item INTERNET_CARD = register("internet_card");
	/** Runs server commands: creative only (OpenComputers' debug card). */
	public static final Item OPERATOR_CARD = register("operator_card");
	/** A bot golem, picked up (its parts in it): creative only until the Advanced Robotics Assembly makes them. */
	public static final Item BOT_GOLEM = register("bot_golem", BotGolemItem::new, ModItems::holdsData);
	/** Items with no recipe on purpose: the creative tab's (and giveall's). */
	public static final List<Item> CREATIVE_ONLY = List.of(JAVA_CPU, JAVA_EEPROM, OPERATOR_CARD, BOT_GOLEM);
	public static final Item MOTION_SENSOR_CARD = register("motion_sensor_card");
	public static final Item AUDIO_SENSOR_CARD = register("audio_sensor_card");
	public static final Item WEATHER_SENSOR_CARD = register("weather_sensor_card");
	/** Storage and a crafting grid: a player crafts with it in its computer's view; a robot's programs too. */
	public static final Item CRAFTING_CARD = register("crafting_card", ModItems::holdsData);
	/** Each card item and the kind of card it is. */
	public static final Map<Item, CardKind> CARDS = cards();
	/** Batteries for the UPS slot: a computer runs on one. */
	public static final BatteryItem TIER1_BATTERY = battery("tier1_battery", BatteryTier.TIER_1);
	public static final BatteryItem TIER2_BATTERY = battery("tier2_battery", BatteryTier.TIER_2);
	public static final BatteryItem TIER3_BATTERY = battery("tier3_battery", BatteryTier.TIER_3);
	public static final List<BatteryItem> BATTERIES = List.of(TIER1_BATTERY, TIER2_BATTERY, TIER3_BATTERY);

	private ModItems() {
	}

	private static Map<Item, CardKind> cards() {
		Map<Item, CardKind> cards = new LinkedHashMap<>();
		cards.put(NETWORK_CARD, CardKind.NETWORK);
		cards.put(TIER1_WLAN_CARD, CardKind.TIER1_WLAN);
		cards.put(TIER2_WLAN_CARD, CardKind.TIER2_WLAN);
		cards.put(BLUETOOTH_CARD, CardKind.BLUETOOTH);
		cards.put(REDSTONE_CARD, CardKind.REDSTONE);
		cards.put(SOUND_CARD, CardKind.SOUND);
		cards.put(INTERNET_CARD, CardKind.INTERNET);
		cards.put(OPERATOR_CARD, CardKind.OPERATOR);
		cards.put(MOTION_SENSOR_CARD, CardKind.MOTION_SENSOR);
		cards.put(AUDIO_SENSOR_CARD, CardKind.AUDIO_SENSOR);
		cards.put(WEATHER_SENSOR_CARD, CardKind.WEATHER_SENSOR);
		cards.put(CRAFTING_CARD, CardKind.CRAFTING);
		return Collections.unmodifiableMap(cards);
	}

	/** The kind of expansion card a stack is, if it is one. */
	public static Optional<CardKind> cardKind(ItemStack stack) {
		return Optional.ofNullable(CARDS.get(stack.getItem()));
	}

	/** Any EEPROM: what the EEPROM slot takes. */
	public static boolean isEeprom(ItemStack stack) {
		return EEPROMS.stream().anyMatch(stack::is);
	}

	/** Forces the static registrations above. */
	public static void init() {
	}

	private static MemoryItem ram(String name, MemoryTier tier) {
		return (MemoryItem) register(name, properties -> new MemoryItem(properties, tier), UnaryOperator.identity());
	}

	private static DiskItem disk(String name, DiskTier tier) {
		// Every disk comes with mcos: no trouble getting an OS once you can craft a computer. Crafted
		// alone, it's blank.
		return (DiskItem) register(name, properties -> new DiskItem(properties, tier),
			properties -> holdsData(properties).component(ModComponents.DISK_IMAGE, "mcos"));
	}

	private static CpuItem cpu(String name, CpuTier tier) {
		return (CpuItem) register(name, properties -> new CpuItem(properties, tier), UnaryOperator.identity());
	}

	private static BatteryItem battery(String name, BatteryTier tier) {
		return (BatteryItem) register(name, properties -> new BatteryItem(properties, tier), UnaryOperator.identity());
	}

	/** Things that will hold data of their own (an EEPROM's program, a disk's files) don't stack. */
	private static Item.Properties holdsData(Item.Properties properties) {
		return properties.stacksTo(1);
	}

	private static Item register(String name) {
		return register(name, UnaryOperator.identity());
	}

	private static Item register(String name, UnaryOperator<Item.Properties> extra) {
		return register(name, HintedItem::new, extra);
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, UnaryOperator<Item.Properties> extra) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
		// Everything stacks (64), like vanilla items; items differing in their data (a battery's
		// charge, a card's serial number) stack only with their own kind.
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(extra.apply(new Item.Properties().setId(key))));
	}
}
