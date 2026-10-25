package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.AllInOneBlock;
import com.payonel.modularcomputers.block.AllInOneBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * A part is one thing in one place, by its id (user, 2026-10-24): placing a picked-up computer moves
 * it into the world, in creative too (no copy stays in the hand); a copy of one in the world (creative
 * makes them) can't be placed; and a copy of a part (a disk) in a second computer waits off its bus
 * until the one in the world goes. No part is given a new id.
 */
public class PartHoldsGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);
	private static final BlockPos OTHER = new BlockPos(4, 1, 1);

	/** A tier 3 case at {@code pos}, facing south, with a CPU, RAM, an EEPROM, a battery and a disk. */
	private static AllInOneBlockEntity computer(GameTestHelper helper, BlockPos pos, ItemStack disk) {
		helper.setBlock(pos, ModBlocks.TIER3_CASE.defaultBlockState().setValue(AllInOneBlock.FACING, Direction.SOUTH));
		AllInOneBlockEntity computer = helper.getBlockEntity(pos, AllInOneBlockEntity.class);
		computer.parts().setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		computer.parts().setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
		computer.parts().setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		computer.parts().setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		computer.parts().setItem(CaseMenu.SATA_SLOTS, disk);
		return computer;
	}

	/** Breaks it as a player would, and gives what dropped. */
	private static ItemStack breakIt(GameTestHelper helper, BlockPos pos) {
		BlockPos at = helper.absolutePos(pos);
		helper.getLevel().destroyBlock(at, true);
		List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2));
		ItemStack stack = dropped.getFirst().getItem().copy();
		dropped.forEach(ItemEntity::discard);
		return stack;
	}

	private static Player placing(GameTestHelper helper, ItemStack stack, BlockPos pos, GameType mode) {
		Player player = helper.makeMockPlayer(mode);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		helper.placeAt(player, stack, pos.below(), Direction.UP);
		return player;
	}

	@GameTest
	public void placingAPickedUpComputerInCreativeMovesItNoCopyStays(GameTestHelper helper) {
		computer(helper, POS, new ItemStack(ModItems.TIER1_HARD_DISK));
		ItemStack item = breakIt(helper, POS);
		// As a creative player places it: the server's way, which puts a placed stack's count back.
		var player = helper.makeMockServerPlayer(GameType.CREATIVE);
		player.getAbilities().instabuild = true;
		player.setItemInHand(InteractionHand.MAIN_HAND, item);
		helper.setBlock(OTHER.below(), Blocks.STONE); // (something to place it on)
		BlockPos below = helper.absolutePos(OTHER.below());
		((net.minecraft.server.level.ServerPlayer) player).gameMode.useItemOn((net.minecraft.server.level.ServerPlayer) player, helper.getLevel(), item, InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
		helper.assertBlockPresent(ModBlocks.TIER3_CASE, OTHER);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "it moved into the world: nothing left in hand, " + player.getMainHandItem());
		helper.succeed();
	}

	@GameTest
	public void inSurvivalPlacingUsesOneItem(GameTestHelper helper) {
		ItemStack plain = new ItemStack(ModBlocks.CABLE.asItem(), 3);
		Player player = placing(helper, plain, POS, GameType.SURVIVAL);
		helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "one used, not two");
		helper.succeed();
	}

	@GameTest
	public void aCopyOfAComputerInTheWorldCantBePlaced(GameTestHelper helper) {
		AllInOneBlockEntity computer = computer(helper, POS, new ItemStack(ModItems.TIER1_HARD_DISK));
		// A creative copy: the case's item with all it holds (as Ctrl and pick block makes it), the case still there.
		ItemStack copy = new ItemStack(ModBlocks.TIER3_CASE.asItem());
		copy.applyComponents(computer.collectComponents());
		helper.assertTrue(!ModComponents.identities(copy).isEmpty(), "the copy carries its ids");
		Player player = placing(helper, copy, OTHER, GameType.CREATIVE);
		helper.assertBlockPresent(Blocks.AIR, OTHER);
		helper.assertTrue(!player.getMainHandItem().isEmpty(), "refused: still in hand");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void aCopiedDiskInASecondComputerWaitsOffItsBusUntilTheFirstGoes(GameTestHelper helper) {
		ItemStack disk = new ItemStack(ModItems.TIER1_HARD_DISK);
		ModComponents.serial(disk);
		AllInOneBlockEntity first = computer(helper, POS, disk.copy());
		AllInOneBlockEntity second = computer(helper, OTHER, disk.copy()); // (a creative copy: the same disk)
		String sata = com.payonel.modularcomputers.machine.Motherboard.sata(0);
		helper.startSequence()
			.thenExecute(() -> {
				helper.assertTrue(first.machine().board().bus().devices().stream().anyMatch(d -> d instanceof com.payonel.modularcomputers.device.Disk), "the first has it");
				helper.assertTrue(second.busEntries().stream().noneMatch(entry -> entry.key().equals(sata)), "the copy waits, as no disk");
			})
			.thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(POS), false))
			.thenWaitUntil(() -> helper.assertTrue(second.busEntries().stream().anyMatch(entry -> entry.key().equals(sata)), "the one left takes it"))
			.thenSucceed();
	}

	@GameTest
	public void aPickedUpGolemCanBePlacedAgain(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		BotGolem golem = com.payonel.modularcomputers.entity.ModEntities.BOT_GOLEM.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		var at = helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(POS));
		golem.snapTo(at.x, at.y, at.z, 0, 0);
		helper.getLevel().addFreshEntity(golem);
		golem.board().parts().setItem(CaseMenu.SATA_SLOTS, new ItemStack(ModItems.TIER1_HARD_DISK));
		ItemStack item = golem.asItem();
		helper.assertTrue(ModComponents.copyOfOneInTheWorld(item, helper.getLevel()), "while it stands, its item is a copy");
		golem.discard();
		helper.assertTrue(!ModComponents.copyOfOneInTheWorld(item, helper.getLevel()), "picked up, it's the one");
		helper.succeed();
	}
}
