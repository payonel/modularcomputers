package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.CaseBlock;
import com.payonel.modularcomputers.block.CaseBlockEntity;
import com.payonel.modularcomputers.block.CableBlock;
import com.payonel.modularcomputers.block.ChargingPadBlock;
import com.payonel.modularcomputers.block.ChargingPadBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.ModEntities;
import com.payonel.modularcomputers.item.BatteryItem;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.machine.Motherboard;
import com.payonel.modularcomputers.menu.CaseMenu;
import com.payonel.modularcomputers.power.BatteryCharge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The charging pad in a world (user design, 2026-10-24): a bot golem standing on it charges, from
 * the pad's battery, or from a running computer's power by cable; with neither, nothing; and the
 * computer's program hears a robot dock and leave.
 */
public class ChargingPadGameTest {
	private static final BlockPos PAD = new BlockPos(3, 1, 3);
	/** How long its program waits at a time, in seconds. */
	private static final double WAIT = 1000;

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** A pad facing south, with a charged battery when {@code battery}. */
	private static ChargingPadBlockEntity pad(GameTestHelper helper, boolean battery) {
		floor(helper);
		helper.setBlock(PAD, ModBlocks.CHARGING_PAD.defaultBlockState().setValue(ChargingPadBlock.FACING, Direction.SOUTH));
		ChargingPadBlockEntity pad = helper.getBlockEntity(PAD, ChargingPadBlockEntity.class);
		if (battery) {
			pad.cards().setItem(ChargingPadBlockEntity.BATTERY, ModItems.TIER1_BATTERY.charged());
		}
		return pad;
	}

	/** A golem standing at {@code at}, with a flat battery (and nothing running: a pad charges it all the same). */
	private static BotGolem golem(GameTestHelper helper, BlockPos at) {
		BotGolem golem = ModEntities.BOT_GOLEM.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at)).add(0, ChargingPadBlock.HEIGHT / 16.0, 0);
		golem.snapTo(pos.x, pos.y, pos.z, 0, 0);
		helper.getLevel().addFreshEntity(golem);
		ItemStack flat = new ItemStack(ModItems.TIER1_BATTERY);
		flat.set(ModComponents.CHARGE, BatteryCharge.EMPTY);
		golem.board().parts().setItem(CaseMenu.UPS_SLOT, flat);
		return golem;
	}

	private static int charge(BotGolem golem) {
		return BatteryItem.charge(golem.board().parts().getItem(CaseMenu.UPS_SLOT)).units();
	}

	@GameTest(maxTicks = 100)
	public void aGolemOnItChargesFromItsBattery(GameTestHelper helper) {
		ChargingPadBlockEntity pad = pad(helper, true);
		BotGolem golem = golem(helper, PAD);
		helper.succeedWhen(() -> {
			helper.assertTrue(pad.charging(), "charging");
			helper.assertTrue(charge(golem) >= ChargingPadBlockEntity.CHARGE_RATE * 10, "its battery filling: " + charge(golem));
		});
	}

	@GameTest(maxTicks = 10)
	public void aBatteryPutInChargesAtOnce(GameTestHelper helper) {
		ChargingPadBlockEntity pad = pad(helper, true);
		BotGolem golem = golem(helper, PAD);
		helper.succeedWhen(() -> helper.assertTrue(pad.charging() && charge(golem) > 0, "charging within its first ticks"));
	}

	@GameTest(maxTicks = 60)
	public void withNoPowerItChargesNothing(GameTestHelper helper) {
		ChargingPadBlockEntity pad = pad(helper, false);
		BotGolem golem = golem(helper, PAD);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(!pad.charging(), "not charging");
			helper.assertValueEqual(charge(golem), 0, "its battery as flat as it was");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void aGolemBesideItDoesntCharge(GameTestHelper helper) {
		ChargingPadBlockEntity pad = pad(helper, true);
		BotGolem golem = golem(helper, PAD.east(2));
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(!pad.charging(), "not charging");
			helper.assertValueEqual(charge(golem), 0, "nothing given");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200)
	public void aRunningComputerPowersItByCableAndHearsARobotDockAndLeave(GameTestHelper helper) {
		ChargingPadBlockEntity pad = pad(helper, false); // (no battery: the bus's power)
		BlockPos casePos = PAD.west(2);
		helper.setBlock(casePos, ModBlocks.TIER3_CASE.defaultBlockState().setValue(CaseBlock.FACING, Direction.SOUTH));
		CaseBlockEntity computer = helper.getBlockEntity(casePos, CaseBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER2_BATTERY.charged());
		String padAddress = Motherboard.id(pad.id());
		List<Map<?, ?>> heard = new ArrayList<>();
		TestPrograms.flash(computer, board -> {
			while (true) {
				board.waitForInterrupt(WAIT, padAddress);
				Object event;
				while ((event = board.invoke(padAddress, "pull").first()) != null) {
					synchronized (heard) {
						heard.add((Map<?, ?>) event);
					}
				}
			}
		});
		BlockPos cable = helper.absolutePos(PAD.west());
		helper.getLevel().setBlock(cable, CableBlock.withConnections(helper.getLevel(), cable, ModBlocks.CABLE.defaultBlockState()), Block.UPDATE_ALL);
		computer.pressPower();
		BotGolem[] golem = {null};
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(computer.machine().board().bus().devices().contains(pad.device()), "the pad on its bus"))
			.thenExecute(() -> golem[0] = golem(helper, PAD))
			.thenWaitUntil(() -> {
				helper.assertTrue(pad.charging(), "charging from the bus");
				helper.assertTrue(charge(golem[0]) > 0, "its battery filling");
			})
			.thenExecute(() -> golem[0].discard()) // (picked up: off the pad)
			.thenWaitUntil(() -> {
				synchronized (heard) {
					helper.assertValueEqual(heard.stream().map(event -> event.get("event")).toList(), List.of("docked", "undocked"), "its program heard");
				}
			})
			.thenSucceed();
	}
}
