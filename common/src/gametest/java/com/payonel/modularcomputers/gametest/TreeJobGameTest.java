package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.CaseBlockEntity;
import com.payonel.modularcomputers.block.ModBlocks;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.GolemLegs;
import com.payonel.modularcomputers.entity.ModEntities;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The user's big test: a bot golem running mcos, its manual's program (docs/examples/golem-tree.lua)
 * as a service started at boot, takes an axe from the chest in front of it, flies up to a tree on a
 * ledge (holding itself in its energy field, legs and all: it keeps its tools, off the ground at a
 * fifth of their speed), cuts the trunk from the air, flies home, lands on its legs at the chest,
 * and puts the wood and the axe in it. Smaller than the user's (10 up, 5 ahead): a test's space is
 * 8 blocks.
 */
public class TreeJobGameTest {
	/** Where it starts, facing east (+X), and the chest in front of it. */
	private static final BlockPos START = new BlockPos(1, 1, 1);
	private static final BlockPos CHEST = START.east();
	/** The tree: its lowest log this far up and ahead of the golem; two logs, leaves over them. */
	private static final int UP = 3;
	private static final int FORWARD = 3;
	private static final int LOGS = 2;
	private static final BlockPos TRUNK = START.above(UP).east(FORWARD);

	private static void tree(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		// The ledge: under the trunk and the golem's spot beside it.
		for (int x = TRUNK.getX() - 1; x <= TRUNK.getX() + 1; x++) {
			for (int z = TRUNK.getZ() - 1; z <= TRUNK.getZ() + 1; z++) {
				helper.setBlock(new BlockPos(x, TRUNK.getY() - 1, z), Blocks.STONE);
			}
		}
		for (int log = 0; log < LOGS; log++) {
			helper.setBlock(TRUNK.above(log), Blocks.OAK_LOG);
		}
		BlockPos crown = TRUNK.above(LOGS);
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				helper.setBlock(crown.offset(x, 0, z), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
			}
		}
		helper.setBlock(crown.above(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
		helper.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.IRON_AXE));
	}

	/** The golem: a board booting mcos off its disk, a screen, its crafting card and its energy field card. */
	private static BotGolem golem(GameTestHelper helper) {
		BotGolem golem = ModEntities.BOT_GOLEM.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(START));
		golem.snapTo(at.x, at.y, at.z, BotGolemGameTest.EAST, 0);
		golem.setYBodyRot(BotGolemGameTest.EAST);
		golem.setYHeadRot(BotGolemGameTest.EAST);
		helper.getLevel().addFreshEntity(golem);
		var parts = golem.board().parts();
		parts.setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		parts.setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
		parts.setItem(CaseMenu.RAM_SLOTS + 1, new ItemStack(ModItems.TIER3_RAM));
		parts.setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		parts.setItem(CaseMenu.UPS_SLOT, ModItems.TIER3_BATTERY.charged());
		parts.setItem(CaseMenu.SATA_SLOTS, new ItemStack(ModItems.TIER3_HARD_DISK));
		parts.setItem(CaseMenu.EXP_SLOTS, new ItemStack(ModBlocks.TIER1_SCREEN));
		parts.setItem(CaseMenu.EXP_SLOTS + 1, new ItemStack(ModItems.CRAFTING_CARD));
		parts.setItem(CaseMenu.EXP_SLOTS + 2, new ItemStack(ModItems.ENERGY_FIELD_CARD));
		return golem;
	}

	/** The manual's program, by its name, found from wherever the test runs (the repository's docs). */
	private static String example(String name) throws IOException {
		Path dir = Path.of("").toAbsolutePath();
		while (!Files.isDirectory(dir.resolve("docs/examples"))) {
			dir = dir.getParent();
		}
		return Files.readString(dir.resolve("docs/examples/" + name + ".lua"));
	}

	private static int count(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
		int count = 0;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			count += chest.getItem(slot).is(item) ? chest.getItem(slot).getCount() : 0;
		}
		return count;
	}

	@GameTest(maxTicks = 3000) // (mcos boots off the disk first: a board call a read)
	public void aGolemFliesUpToATreeCutsItAndBringsTheWoodHome(GameTestHelper helper) throws IOException {
		tree(helper);
		BotGolem golem = golem(helper);
		// The program, and a service running it at boot (as the manual's tutorial makes one).
		Path disk = CaseBlockEntity.diskFolder(helper.getLevel().getServer(), golem.board().parts().getItem(CaseMenu.SATA_SLOTS).get(ModComponents.SERIAL));
		Files.writeString(disk.resolve("home/golem-tree.lua"), example("golem-tree"));
		Files.writeString(disk.resolve("etc/rc.d/treejob"), """
			description = "the tree job"
			command = "/bin/lua"
			command_args = {"/home/golem-tree.lua", "%d", "%d"}
			output_log = "/home/tree.out"
			""".formatted(UP, FORWARD));
		Files.writeString(disk.resolve("etc/rc.conf"), Files.readString(disk.resolve("etc/rc.conf")) + "treejob_enable=YES\n");
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
		helper.succeedWhen(() -> {
			String out;
			try {
				out = Files.exists(disk.resolve("home/tree.out")) ? Files.readString(disk.resolve("home/tree.out")) : "(nothing yet)";
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			helper.assertTrue(count(chest, Items.OAK_LOG) == LOGS, "the wood in the chest: " + count(chest, Items.OAK_LOG) + "; its program's output: " + out
				+ "; the golem: holding " + golem.getMainHandItem() + ", legs " + golem.legs() + ", held " + com.payonel.modularcomputers.entity.EnergyField.held(golem)
				+ ", at " + golem.position().subtract(helper.absoluteVec(Vec3.ZERO)) + ", robotics " + golem.robotics().state() + ", screen: "
				+ golem.terminal().buffer().row(0).strip() + " / " + golem.terminal().buffer().row(1).strip() + " / " + golem.terminal().buffer().row(2).strip());
			helper.assertTrue(count(chest, Items.IRON_AXE) == 1, "the axe back in the chest");
			for (int log = 0; log < LOGS; log++) {
				helper.assertBlockPresent(Blocks.AIR, TRUNK.above(log));
			}
			helper.assertTrue(golem.legs(), "on its legs");
			helper.assertTrue(golem.blockPosition().equals(helper.absolutePos(START)), "home: " + golem.blockPosition());
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(GolemLegs.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8)).isEmpty(),
				"no legs left lying about");
		});
	}
}
