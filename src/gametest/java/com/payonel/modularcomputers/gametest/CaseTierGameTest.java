package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.bus.DeviceType;
import com.payonel.modularcomputers.device.Terminal;
import com.payonel.modularcomputers.device.TerminalTier;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.CaseTier;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Case tiers: the same board, with more of it at higher tiers. A lower tier refuses the slots it
 * leaves out; tiers 2 and 3 have a gold first expansion slot that also takes a screen, which then
 * is the case's terminal, on its front.
 */
public class CaseTierGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);

	private static AllInOneBlockEntity place(GameTestHelper helper, Block block) {
		helper.setBlock(POS, block.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		return helper.getBlockEntity(POS, AllInOneBlockEntity.class);
	}

	private static CaseMenu menu(GameTestHelper helper, AllInOneBlockEntity computer) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		return (CaseMenu) computer.createMenu(0, player.getInventory(), player);
	}

	@GameTest
	public void eachTierOffersItsOwnSlotsOfTheSameBoard(GameTestHelper helper) {
		for (Block block : ModBlocks.CASES) {
			AllInOneBlockEntity computer = place(helper, block);
			CaseTier tier = computer.tier();
			CaseMenu menu = menu(helper, computer);
			int bays = 0;
			int sticks = 0;
			int drives = 0;
			int cards = 0;
			for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
				var place = menu.slots.get(slot);
				bays += place.mayPlace(ModItems.TIER1_BATTERY.charged()) ? 1 : 0;
				sticks += place.mayPlace(new ItemStack(ModItems.TIER1_RAM)) ? 1 : 0;
				drives += place.mayPlace(new ItemStack(ModItems.TIER1_HARD_DISK)) ? 1 : 0;
				cards += place.mayPlace(new ItemStack(ModItems.WLAN_CARD)) ? 1 : 0;
			}
			String name = tier.name();
			helper.assertValueEqual(List.of(bays, sticks, drives, cards), List.of(tier.bays(), tier.ram(), tier.sata(), tier.expansion()),
				name + ": bays, RAM, SATA, expansion");
			helper.assertTrue(menu.slots.get(CaseMenu.CPU_SLOT).mayPlace(new ItemStack(ModItems.TIER1_CPU)), name + " takes a CPU");
			helper.assertTrue(menu.slots.get(CaseMenu.EEPROM_SLOT).mayPlace(new ItemStack(ModItems.EEPROM)), name + " takes an EEPROM");
			helper.assertValueEqual(computer.power().capacity(), tier.bufferCapacity(), name + ": its power buffer");
			for (int slot = 0; slot < CaseMenu.PART_SLOTS; slot++) {
				helper.assertValueEqual(menu.slots.get(slot).isActive(), CaseMenu.available(tier, slot),
					name + ": slot " + slot + " is a slot only if this tier has it (no highlight otherwise)");
			}
		}
		helper.assertTrue(CaseTier.TIER_1.bufferCapacity() < CaseTier.TIER_3.bufferCapacity(), "bigger cases store more");
		helper.succeed();
	}

	@GameTest
	public void onlyTheGoldSlotOfTiers2And3TakesAScreen(GameTestHelper helper) {
		ItemStack screen = new ItemStack(ModBlocks.TIER2_SCREEN);
		for (Block block : ModBlocks.CASES) {
			AllInOneBlockEntity computer = place(helper, block);
			CaseMenu menu = menu(helper, computer);
			boolean gold = computer.tier().screenSlot();
			helper.assertValueEqual(menu.slots.get(CaseMenu.EXP_SLOTS).mayPlace(screen), gold, computer.tier() + ": its first expansion slot");
			helper.assertTrue(!menu.slots.get(CaseMenu.EXP_SLOTS + 1).mayPlace(screen), computer.tier() + ": no other slot takes one");
		}
		helper.succeed();
	}

	@GameTest
	public void aScreenInTheGoldSlotIsTheCasesTerminalOnItsFront(GameTestHelper helper) {
		AllInOneBlockEntity computer = place(helper, ModBlocks.TIER2_CASE);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER2_CPU));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		TestPrograms.flash(computer, TestPrograms.HELLO);
		helper.assertBlockProperty(POS, AllInOneBlock.SCREEN, false);
		helper.assertTrue(!computer.showsScreen(), "no screen: a blank front");

		computer.parts().setItem(CaseMenu.EXP_SLOTS, new ItemStack(ModBlocks.TIER2_SCREEN));
		helper.assertBlockProperty(POS, AllInOneBlock.SCREEN, true);
		var bus = computer.machine().board().bus();
		helper.assertValueEqual(bus.device("exp:0").map(device -> device.type()).orElse(null), DeviceType.TERMINAL, "on the bus in the gold slot");
		Terminal terminal = computer.terminal();
		helper.assertValueEqual(terminal.tier(), TerminalTier.TIER_2, "the screen's tier");
		String id = bus.id("exp:0");
		helper.assertTrue(id != null && id.startsWith("id:"), "it has its own id too");

		computer.pressPower();
		helper.assertValueEqual(terminal.buffer().row(0).strip(), TestPrograms.GREETING, "the console shows on it");

		computer.parts().setItem(CaseMenu.EXP_SLOTS, ItemStack.EMPTY);
		helper.assertBlockProperty(POS, AllInOneBlock.SCREEN, false);
		helper.assertTrue(bus.device("exp:0").isEmpty(), "gone from the bus");
		helper.assertTrue(terminal.buffer().isBlank(), "and dark");
		helper.succeed();
	}

	@GameTest
	public void aCaseWithoutAScreenOpensItsCaseViewOnRightClick(GameTestHelper helper) {
		place(helper, ModBlocks.TIER1_CASE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.useBlock(POS, player);
		helper.assertTrue(player.containerMenu instanceof CaseMenu, "the case view, got " + player.containerMenu);
		helper.succeed();
	}
}
