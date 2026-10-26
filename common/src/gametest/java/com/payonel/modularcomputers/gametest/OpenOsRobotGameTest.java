package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.block.CaseBlockEntity;
import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.ModEntities;
import com.payonel.modularcomputers.item.ModComponents;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * openos's robot: an OpenComputers robot program (OpenComputers' robot library, ported) running on a
 * bot golem's mcos, its moves the golem's walks and turns, each waited for as OpenComputers' robot
 * calls waited. It runs as a service at boot (no terminal: its output is its log).
 */
public class OpenOsRobotGameTest {
	/** Where it starts, facing east (+X); a stone two ahead and one to its right, which it ends facing. */
	private static final BlockPos START = new BlockPos(1, 1, 1);
	private static final BlockPos STONE = START.east().south(2);

	@GameTest(maxTicks = 3000) // (mcos boots off the disk first: a board call a read)
	public void anOpenComputersRobotProgramMovesTheGolem(GameTestHelper helper) throws IOException {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		helper.setBlock(STONE, Blocks.STONE);
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
		Path disk = CaseBlockEntity.diskFolder(helper.getLevel().getServer(), parts.getItem(CaseMenu.SATA_SLOTS).get(ModComponents.SERIAL));
		Files.writeString(disk.resolve("home/oc.lua"), """
			local robot = require("robot")
			local component = require("component")
			local before = robot.detect()
			local moved = robot.forward()
			local turned = robot.turnRight()
			local again = robot.forward()
			local solid, kind = robot.detect()
			local out = io.open("/home/oc.out", "w")
			out:write(tostring(before), " ", tostring(moved), " ", tostring(turned), " ", tostring(again), " ",
			  tostring(solid), " ", kind, " ", tostring(computer.isRobot()), " ", tostring(component.isAvailable("robot")), "\\n")
			out:close()
			""");
		Files.writeString(disk.resolve("etc/rc.d/oc"), """
			description = "an OpenComputers robot program"
			command = "/bin/openos"
			command_args = {"/home/oc.lua"}
			output_log = "/home/oc.log"
			""");
		Files.writeString(disk.resolve("etc/rc.conf"), Files.readString(disk.resolve("etc/rc.conf")) + "oc_enable=YES\n");
		helper.runAfterDelay(1, () -> golem.board().pressPower());
		helper.succeedWhen(() -> {
			String out;
			String log;
			try {
				out = Files.exists(disk.resolve("home/oc.out")) ? Files.readString(disk.resolve("home/oc.out")) : "(nothing yet)";
				log = Files.exists(disk.resolve("home/oc.log")) ? Files.readString(disk.resolve("home/oc.log")) : "";
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			helper.assertTrue(out.equals("false true true true true solid true true\n"), "what it saw: " + out + "; its log: " + log);
			helper.assertTrue(golem.blockPosition().equals(helper.absolutePos(START.east().south())), "one ahead, then one to its right: "
				+ golem.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)));
		});
	}
}
