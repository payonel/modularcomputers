package com.payonel.modularcomputers.gametest;

import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.check;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.teleport;

import com.mojang.blaze3d.platform.InputConstants;
import com.payonel.modularcomputers.block.RedstoneTerminalBlockEntity;
import com.payonel.modularcomputers.client.screen.CardHolderScreen;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.ModMenus;
import com.payonel.modularcomputers.runtime.Arguments;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RedstoneLampBlock;

/**
 * Client game test for the redstone terminal: half a block tall, a cable joined on one side, a lamp
 * it lights on another; its slots view has the radio and the battery.
 */
public class RedstoneTerminalClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().setUseConsistentSettings(true)
			.adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
			singleplayer.getConnection().waitForChunksRender();
			BlockPos feet = singleplayer.getServer().computeOnServer(server -> singleplayer.getConnection().getServerPlayer().blockPosition());
			BlockPos terminal = feet.north(2);
			BlockPos lamp = terminal.east();
			for (String command : List.of("setblock %d %d %d modularcomputers:redstone_terminal".formatted(terminal.getX(), terminal.getY(), terminal.getZ()),
				"setblock %d %d %d modularcomputers:cable".formatted(terminal.getX() - 1, terminal.getY(), terminal.getZ()),
				"setblock %d %d %d minecraft:redstone_lamp".formatted(lamp.getX(), lamp.getY(), lamp.getZ()))) {
				singleplayer.getServer().runCommand(command);
			}
			singleplayer.getServer().runOnServer(server -> {
				var be = (RedstoneTerminalBlockEntity) server.overworld().getBlockEntity(terminal);
				be.cards().setItem(RedstoneTerminalBlockEntity.BATTERY_SLOT, ModItems.TIER1_BATTERY.charged());
				be.device().set(new Arguments(List.of(Map.of("east", Map.of("powered", 15L)))));
			});
			teleport(singleplayer, feet, 180);
			context.getInput().lookAt(terminal);
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			context.waitFor(client -> client.level.getBlockState(lamp).getValueOrElse(RedstoneLampBlock.LIT, false));
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(5);
			context.takeScreenshot("redstone-terminal");

			singleplayer.getServer().runOnServer(server ->
				ModMenus.open(singleplayer.getConnection().getServerPlayer(), server.overworld().getBlockEntity(terminal)));
			context.waitForScreen(CardHolderScreen.class);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(2);
			context.takeScreenshot("redstone-terminal-slots");
			boolean battery = context.computeOnClient(client -> ((CardHolderScreen) client.gui.screen()).getMenu().slots
				.get(RedstoneTerminalBlockEntity.BATTERY_SLOT).hasItem());
			check(battery, "the battery shows in its slot");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(client -> client.gui.screen() == null);
		}
	}
}
