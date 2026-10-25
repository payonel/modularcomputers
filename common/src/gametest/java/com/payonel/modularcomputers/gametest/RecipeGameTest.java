package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.power.BatteryCharge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Every part, block and card crafts from its recipe (the user's design, 2026-10-12: real computer
 * materials from vanilla; each tier built around the tier below). The grids here are written out
 * on their own, so a recipe file that drifts from the design fails.
 */
public class RecipeGameTest {
	/** The grid letters: vanilla items by id, and {@code #} for the tier below. */
	private static final Map<Character, String> LEGEND = Map.ofEntries(
		Map.entry('I', "iron_ingot"), Map.entry('g', "gold_ingot"), Map.entry('D', "diamond"), Map.entry('C', "copper_ingot"),
		Map.entry('c', "copper_nugget"), Map.entry('G', "gold_nugget"), Map.entry('Q', "modularcomputers:chip"), Map.entry('R', "redstone"),
		Map.entry('A', "amethyst_shard"), Map.entry('L', "glowstone_dust"), Map.entry('p', "paper"), Map.entry('K', "dried_kelp"),
		Map.entry('P', "piston"), Map.entry('N', "note_block"), Map.entry('w', "glass_pane"), Map.entry('k', "chiseled_stone_bricks"),
		Map.entry('X', "comparator"), Map.entry('d', "dispenser"), Map.entry('r', "red_dye"), Map.entry('e', "green_dye"),
		Map.entry('b', "blue_dye"), Map.entry('Z', "breeze_rod"), Map.entry('E', "echo_shard"), Map.entry('H', "heart_of_the_sea"),
		Map.entry('B', "redstone_block"), Map.entry('x', "prismarine_crystals"), Map.entry('S', "sea_lantern"), Map.entry('z', "blaze_rod"),
		Map.entry('n', "netherite_ingot"), Map.entry('s', "nether_star"), Map.entry('y', "ender_eye"), Map.entry('O', "ender_pearl"),
		Map.entry('m', "emerald"), Map.entry('l', "lapis_lazuli"), Map.entry('j', "jukebox"), Map.entry('o', "observer"),
		Map.entry('u', "sculk_sensor"), Map.entry('t', "daylight_detector"), Map.entry('T', "redstone_torch"), Map.entry('V', "cobweb"), Map.entry('Y', "ender_eye"), Map.entry('v', "lightning_rod"),
		Map.entry('W', "modularcomputers:cable"), Map.entry('F', "iron_block"), Map.entry('h', "modularcomputers:tier1_hard_disk"),
		Map.entry('a', "crafting_table"), Map.entry('q', "chest"));

	private record Craft(String result, int count, String lower, String... rows) {
	}

	private static Craft make(String result, String... rows) {
		return new Craft(result, 1, null, rows);
	}

	private static Craft upgrade(String result, String lower, String... rows) {
		return new Craft(result, 1, lower, rows);
	}

	private static final List<Craft> CRAFTS = List.of(
		make("tier1_cpu", "III", "QAQ", "GRG"),
		upgrade("tier2_cpu", "tier1_cpu", "ggg", "Q#Q", "GRG"),
		upgrade("tier3_cpu", "tier2_cpu", "DDD", "Q#Q", "GRG"),
		make("tier1_ram", "QQQ", "cIc", "GGG"),
		upgrade("tier2_ram", "tier1_ram", "QQQ", "g#g", "GGG"),
		upgrade("tier3_ram", "tier2_ram", "QQQ", "D#D", "GGG"),
		make("eeprom", " Q ", "pRp", "GGG"),
		make("photomask", "IwI", "QAQ", "CRC"),
		make("disk_station", "QQQ", "mhm", "FPF"),
		make("tier1_hard_disk", "IRI", "IPI", "IQI"),
		upgrade("tier2_hard_disk", "tier1_hard_disk", "IRI", "g#g", "IQI"),
		upgrade("tier3_hard_disk", "tier2_hard_disk", "IRI", "D#D", "IQI"),
		make("tier1_case", "III", "CNC", "IRI"),
		upgrade("tier2_case", "tier1_case", "gZg", "Z#Z", "gZg"),
		upgrade("tier3_case", "tier2_case", "EHE", "D#D", "EBE"),
		make("tier1_screen", "IwI", "RQR", "IkI"),
		upgrade("tier2_screen", "tier1_screen", "gLg", "L#L", "gLg"),
		upgrade("tier3_screen", "tier2_screen", "xDx", "L#L", "xDx"),
		new Craft("cable", 8, null, new String[] {"KKK", "KCK", "KKK"}),
		make("expansion_hub", "CXC", "XQX", "gXg"),
		make("robotic_piston", "IPI", "PQP", "ICI"),
		make("network_card", "WQ ", "ccc", "GGG"),
		make("tier1_wlan_card", "TQT", "ccc", "GGG"),
		make("internet_card", "VQY", "ccc", "GGG"),
		make("tier2_wlan_card", "OQm", "ccc", "GGG"),
		make("bluetooth_card", "OQm", "clc", "GGG"),
		make("redstone_card", "XQ ", "ccc", "GGG"),
		make("sound_card", "jQ ", "ccc", "GGG"),
		make("motion_sensor_card", "oQ ", "ccc", "GGG"),
		make("audio_sensor_card", "uQ ", "ccc", "GGG"),
		make("weather_sensor_card", "tQv", "ccc", "GGG"),
		make("crafting_card", "aQq", "ccc", "GGG"),
		upgrade("tier2_battery", "tier1_battery", "ggg", "I#I", "SzS"),
		upgrade("tier3_battery", "tier2_battery", "DsD", "n#n", "nyn"));

	/** A grid item by id: vanilla's, unless it names its namespace (our chip). */
	private static Item vanilla(String id) {
		return BuiltInRegistries.ITEM.getValue(id.contains(":") ? Identifier.parse(id) : Identifier.withDefaultNamespace(id));
	}

	private static Item ours(String id) {
		return BuiltInRegistries.ITEM.getValue(ModularComputers.id(id));
	}

	private static List<ItemStack> grid(Craft craft, ItemStack lower) {
		List<ItemStack> cells = new ArrayList<>();
		for (String row : craft.rows()) {
			for (char cell : row.toCharArray()) {
				cells.add(cell == ' ' ? ItemStack.EMPTY : cell == '#' ? lower.copy() : new ItemStack(vanilla(LEGEND.get(cell))));
			}
		}
		return cells;
	}

	private static ItemStack craft(GameTestHelper helper, List<ItemStack> cells) {
		CraftingInput input = CraftingInput.of(3, 3, cells);
		return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
			.map(recipe -> recipe.value().assemble(input))
			.orElse(ItemStack.EMPTY);
	}

	@GameTest
	public void everyPartBlockAndCardCraftsFromItsGrid(GameTestHelper helper) {
		for (Craft craft : CRAFTS) {
			for (String row : craft.rows()) {
				for (char cell : row.toCharArray()) {
					if (cell != ' ' && cell != '#') {
						helper.assertTrue(vanilla(LEGEND.get(cell)) != Items.AIR, "a vanilla item: " + LEGEND.get(cell));
					}
				}
			}
			ItemStack lower = craft.lower() == null ? ItemStack.EMPTY : new ItemStack(ours(craft.lower()));
			ItemStack made = craft(helper, grid(craft, lower));
			helper.assertValueEqual(made.getItem(), ours(craft.result()), craft.result() + "'s recipe");
			helper.assertValueEqual(made.getCount(), craft.count(), craft.result() + "'s count");
		}
		helper.succeed();
	}

	@GameTest
	public void siliconIsQuartzFromABlastFurnace(GameTestHelper helper) {
		var input = new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(Items.QUARTZ));
		ItemStack made = helper.getLevel().recipeAccess().getRecipeFor(RecipeType.BLASTING, input, helper.getLevel())
			.map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
		helper.assertValueEqual(made.getItem(), ModItems.SILICON, "silicon from the blast furnace");
		helper.assertTrue(helper.getLevel().recipeAccess().getRecipeFor(RecipeType.SMELTING, input, helper.getLevel()).isEmpty(),
			"not from a plain furnace");
		helper.succeed();
	}

	@GameTest
	public void anUpgradeTakesTheTierBelowOnly(GameTestHelper helper) {
		Craft tier2 = CRAFTS.stream().filter(craft -> craft.result().equals("tier2_cpu")).findFirst().orElseThrow();
		helper.assertTrue(craft(helper, grid(tier2, new ItemStack(ModItems.TIER2_CPU))).isEmpty(), "not around the same tier");
		helper.assertTrue(craft(helper, grid(tier2, ItemStack.EMPTY)).isEmpty(), "not without the tier below");
		// A part that has been in a computer (it has an id) still upgrades.
		ItemStack used = new ItemStack(ModItems.TIER1_CPU);
		ModComponents.serial(used);
		helper.assertValueEqual(craft(helper, grid(tier2, used)).getItem(), (Item) ModItems.TIER2_CPU, "a used CPU upgrades");
		helper.succeed();
	}

	@GameTest
	public void anUpgradedBatteryKeepsItsCharge(GameTestHelper helper) {
		Craft tier2 = CRAFTS.stream().filter(craft -> craft.result().equals("tier2_battery")).findFirst().orElseThrow();
		ItemStack tier1 = new ItemStack(ModItems.TIER1_BATTERY);
		tier1.set(ModComponents.CHARGE, new BatteryCharge(12_345, 99));
		ItemStack made = craft(helper, grid(tier2, tier1));
		helper.assertValueEqual(made.getItem(), (Item) ModItems.TIER2_BATTERY, "a tier 2 battery");
		helper.assertValueEqual(BatteryItem.charge(made).units(), 12_345, "the tier 1's charge goes with it");
		helper.assertValueEqual(BatteryItem.charge(made).settledAt(), BatteryCharge.UNSETTLED, "it counts its own time from the first look");

		ItemStack empty = craft(helper, grid(tier2, new ItemStack(ModItems.TIER1_BATTERY)));
		helper.assertValueEqual(BatteryItem.charge(empty).units(), 0, "an empty one stays empty");
		helper.succeed();
	}

	@GameTest
	public void anEepromComesFlashedAndCraftingItAloneWipesIt(GameTestHelper helper) {
		String os = com.payonel.modularcomputers.machine.LuaScripts.read("eeprom/boot");
		helper.assertValueEqual(new ItemStack(ModItems.EEPROM).get(ModComponents.EEPROM_IMAGE), os, "a new EEPROM holds its Lua firmware");
		Craft eeprom = CRAFTS.stream().filter(craft -> craft.result().equals("eeprom")).findFirst().orElseThrow();
		helper.assertValueEqual(craft(helper, grid(eeprom, ItemStack.EMPTY)).get(ModComponents.EEPROM_IMAGE), os, "so does a crafted one");

		ItemStack used = new ItemStack(ModItems.EEPROM);
		ModComponents.serial(used);
		used.set(ModComponents.EEPROM_IMAGE, "something a program wrote");
		List<ItemStack> alone = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
		alone.set(4, used);
		ItemStack wiped = craft(helper, alone);
		helper.assertTrue(wiped.is(ModItems.EEPROM), "alone, an EEPROM is wiped, got " + wiped);
		helper.assertValueEqual(wiped.get(ModComponents.EEPROM_IMAGE), "", "nothing on it");
		helper.assertTrue(!wiped.has(ModComponents.SERIAL), "and no id");
		alone.set(4, wiped);
		helper.assertTrue(craft(helper, alone).isEmpty(), "a blank one has nothing to wipe");

		ItemStack disk = new ItemStack(ModItems.TIER1_HARD_DISK);
		ModComponents.serial(disk);
		alone.set(4, disk);
		ItemStack wipedDisk = craft(helper, alone);
		helper.assertTrue(wipedDisk.is(ModItems.TIER1_HARD_DISK) && !wipedDisk.has(ModComponents.SERIAL), "a disk alone is wiped (its files go when it's taken: DiskGameTest)");
		helper.succeed();
	}
}
