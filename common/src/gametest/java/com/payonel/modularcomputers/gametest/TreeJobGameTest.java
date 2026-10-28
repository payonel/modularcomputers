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
 * as a service started at boot: it takes an axe from the chest behind it (from whatever slot), walks
 * forward looking for a tree, is carried up the cliff in its own field (frozen there: the field's
 * moves feel the way), set down on the ledge, cuts the trunk from the ground (no tools in the air,
 * nor in a field), comes home the way it went, and leaves things ready to run again: the axe in the
 * chest's first slot, the wood in the chest, its hands and storage empty. When something's wrong
 * (no axe, a cliff too high) it comes home all the same, tidy, and says why.
 */
public class TreeJobGameTest {
	/** Where it starts, facing east (+X), and the chest behind it. */
	private static final BlockPos START = new BlockPos(1, 1, 1);
	private static final BlockPos CHEST = START.west();
	/** The axe's slot in the chest: not the first (it finds it wherever it is). */
	private static final int AXE_SLOT = 4;
	/** The tree: on a ledge this many blocks ahead, its lowest log this far up; two logs, leaves over them. */
	private static final int FORWARD = 3;
	private static final int UP = 2;
	private static final int LOGS = 2;
	private static final BlockPos TRUNK = START.east(FORWARD).above(UP);
	/** The cliff's face, a block before the trunk: the ledge runs from there on, the tree on it. */
	private static final int CLIFF = FORWARD - 1;

	private static void tree(GameTestHelper helper, boolean axe) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		// The cliff: a ledge as high as the trunk's foot, from a block before it on (room to stand).
		for (int x = START.getX() + CLIFF; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = START.getY(); y < TRUNK.getY(); y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
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
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
		if (axe) {
			helper.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(AXE_SLOT, new ItemStack(Items.IRON_AXE));
		}
	}

	/** The golem: a board booting mcos off its disk, a screen, its crafting card and its energy field card (its lift). */
	private static BotGolem golem(GameTestHelper helper) {
		BotGolem golem = ModEntities.TIER2_BOT_GOLEM.create(helper.getLevel(), EntitySpawnReason.COMMAND);
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

	/** The job, as a service started at boot with these arguments; its output file. */
	private static Path job(GameTestHelper helper, BotGolem golem, String args) throws IOException {
		Path disk = CaseBlockEntity.diskFolder(helper.getLevel().getServer(), golem.board().parts().getItem(CaseMenu.SATA_SLOTS).get(ModComponents.SERIAL));
		Files.writeString(disk.resolve("home/golem-tree.lua"), example("golem-tree"));
		Files.writeString(disk.resolve("etc/rc.d/treejob"), """
			description = "the tree job"
			command = "/bin/lua"
			command_args = "/home/golem-tree.lua %s"
			output_log = "/home/tree.out"
			""".formatted(args));
		Files.writeString(disk.resolve("etc/rc.conf"), Files.readString(disk.resolve("etc/rc.conf")) + "treejob_enable=YES\n");
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		return disk.resolve("home/tree.out");
	}

	private static String read(Path out) {
		try {
			return Files.exists(out) ? Files.readString(out) : "(nothing yet)";
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Home and tidy: on its legs where it started, its hands and storage empty, the axe in the chest's first slot (when it had one). */
	private static void homeAndTidy(GameTestHelper helper, BotGolem golem, ChestBlockEntity chest, Path out, boolean axe) {
		helper.assertTrue(golem.legs(), "on its legs; its output: " + read(out));
		helper.assertTrue(golem.blockPosition().equals(helper.absolutePos(START)), "home: " + golem.blockPosition() + "; " + read(out));
		helper.assertTrue(golem.getMainHandItem().isEmpty() && golem.getOffhandItem().isEmpty(), "its hands empty: " + golem.getMainHandItem() + ", " + golem.getOffhandItem());
		var storage = golem.board().parts().getItem(CaseMenu.EXP_SLOTS + 1).getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
			net.minecraft.world.item.component.ItemContainerContents.EMPTY);
		helper.assertTrue(storage.nonEmptyItemCopyStream().findAny().isEmpty(), "its storage empty: " + storage);
		if (axe) {
			helper.assertTrue(chest.getItem(0).is(Items.IRON_AXE), "the axe in the chest's first slot: " + chest.getItem(0));
		}
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(GolemLegs.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8)).isEmpty(), "no legs left lying about");
	}

	@GameTest(maxTicks = 3000) // (mcos boots off the disk first: a board call a read)
	public void aGolemFliesToATreeOnACliffCutsItAndBringsTheWoodHomeReadyToRunAgain(GameTestHelper helper) throws IOException {
		tree(helper, true);
		BotGolem golem = golem(helper);
		Path out = job(helper, golem, "");
		ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
		helper.succeedWhen(() -> {
			helper.assertTrue(read(out).contains("brought them home"), "its output: " + read(out) + "; the golem at "
				+ golem.position().subtract(helper.absoluteVec(Vec3.ZERO)) + ", robotics " + golem.robotics().state());
			helper.assertTrue(count(chest, Items.OAK_LOG) == LOGS, "the wood in the chest: " + count(chest, Items.OAK_LOG));
			for (int log = 0; log < LOGS; log++) {
				helper.assertBlockPresent(Blocks.AIR, TRUNK.above(log));
			}
			homeAndTidy(helper, golem, chest, out, true);
		});
	}

	@GameTest(maxTicks = 2000)
	public void withNoAxeInTheChestItSaysSoAndStaysHomeTidy(GameTestHelper helper) throws IOException {
		tree(helper, false);
		BotGolem golem = golem(helper);
		golem.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_PLANKS, 3)); // (something in its way)
		Path out = job(helper, golem, "");
		ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
		helper.succeedWhen(() -> {
			helper.assertTrue(read(out).contains("there's no axe in the chest behind me, so I came home"), "its output: " + read(out));
			helper.assertTrue(count(chest, Items.OAK_PLANKS) == 3, "what it held, put in the chest");
			homeAndTidy(helper, golem, chest, out, false);
		});
	}

	@GameTest(maxTicks = 3000)
	public void aCliffTooHighItComesHomeTidyAndSaysWhy(GameTestHelper helper) throws IOException {
		tree(helper, true);
		BotGolem golem = golem(helper);
		Path out = job(helper, golem, "8 1"); // (it climbs one block at most: the cliff is two)
		ChestBlockEntity chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
		helper.succeedWhen(() -> {
			helper.assertTrue(read(out).contains("while looking for a tree, I found a cliff") && read(out).contains("so I came home"), "its output: " + read(out));
			helper.assertBlockPresent(Blocks.OAK_LOG, TRUNK);
			homeAndTidy(helper, golem, chest, out, true);
		});
	}
}
