package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.client.ModularComputersClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;

/**
 * Client game test: boots the real client (on Xvfb when headless), checks startup state,
 * creates a singleplayer world, exercises it, takes screenshots, and shuts the game down.
 * Screenshots land in {@code build/run/clientGameTest/screenshots}.
 */
public class ModularComputersClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// Startup: both entrypoints ran before the title screen was shown.
		check(ModularComputers.isInitialized(), "common initializer did not run");
		check(ModularComputersClient.isInitialized(), "client initializer did not run");
		context.takeScreenshot("modularcomputers-title");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("modularcomputers-world");

			// Drive the integrated server and verify the result from both sides.
			BlockPos pos = singleplayer.getServer().computeOnServer(server ->
				singleplayer.getConnection().getServerPlayer().blockPosition().above(3));
			singleplayer.getServer().runCommand("setblock %d %d %d minecraft:redstone_block"
				.formatted(pos.getX(), pos.getY(), pos.getZ()));
			singleplayer.getServer().runOnServer(server ->
				check(server.overworld().getBlockState(pos).is(Blocks.REDSTONE_BLOCK), "server did not place block"));
			context.waitFor(client -> client.level != null
				&& client.level.getBlockState(pos).is(Blocks.REDSTONE_BLOCK));
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
