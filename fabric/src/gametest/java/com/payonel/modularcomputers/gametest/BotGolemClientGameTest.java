package com.payonel.modularcomputers.gametest;

import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.check;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.count;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.read;
import static com.payonel.modularcomputers.gametest.AllInOneClientGameTest.teleport;

import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.ModEntities;
import com.payonel.modularcomputers.item.ModItems;
import com.payonel.modularcomputers.menu.CaseMenu;
import java.awt.image.BufferedImage;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;

/**
 * Bot golems as players see them: one with a tier 1 screen in its gold slot, running a program,
 * its face showing the screen's text (green, tier 1's phosphor); one without, its face an empty
 * bezel; the first holding a pickaxe, in its right hand; and its screen view and its parts view,
 * in the golem's own art, its hand's slot holding the pickaxe.
 */
public class BotGolemClientGameTest implements FabricClientGameTest {
	/** Facing south (toward the player, who faces north). */
	private static final float SOUTH = 0;
	/** Lit phosphor: a tier 1 screen's text, at least this green, and red well under it. */
	private static final int LIT = 120;
	/** How many lit pixels its face shows at least, at this distance. */
	private static final int LIT_PIXELS = 15;
	/** What's written: every row in reverse video, so its whole glass lights (a tier 1's text is tiny on a golem's face). */
	private static final String LIGHT_IT = "\u001B[2J\u001B[H\u001B[7m" + (" ".repeat(50) + "\r\n").repeat(15) + "\u001B[0m";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().setUseConsistentSettings(true)
			.adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
			singleplayer.getConnection().waitForChunksRender();
			BlockPos feet = singleplayer.getServer().computeOnServer(server -> singleplayer.getConnection().getServerPlayer().blockPosition());
			BlockPos withScreen = feet.north(2);
			BlockPos without = withScreen.east(2);
			int id = singleplayer.getServer().computeOnServer(server -> {
				BotGolem golem = golem(server.overworld(), withScreen);
				golem.board().parts().setItem(CaseMenu.EXP_SLOTS, new ItemStack(com.payonel.modularcomputers.block.ModBlocks.TIER1_SCREEN));
				golem.board().machine().board().eeprom().orElseThrow().flash(TestPrograms.HELLO);
				golem.board().pressPower();
				golem.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
				// The other on a charging pad, charging (its particles), its battery run down first.
				server.overworld().setBlock(without, com.payonel.modularcomputers.block.ModBlocks.CHARGING_PAD.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
				((com.payonel.modularcomputers.block.ChargingPadBlockEntity) server.overworld().getBlockEntity(without)).cards()
					.setItem(com.payonel.modularcomputers.block.ChargingPadBlockEntity.BATTERY, ModItems.TIER1_BATTERY.charged());
				BotGolem charging = golem(server.overworld(), without);
				charging.snapTo(charging.getX(), charging.getY() + com.payonel.modularcomputers.block.ChargingPadBlock.HEIGHT / 16.0, charging.getZ(), SOUTH, 0);
				ItemStack flat = new ItemStack(ModItems.TIER1_BATTERY);
				flat.set(com.payonel.modularcomputers.item.ModComponents.CHARGE, com.payonel.modularcomputers.power.BatteryCharge.EMPTY);
				charging.board().parts().setItem(CaseMenu.UPS_SLOT, flat);
				return golem.getId();
			});
			teleport(singleplayer, feet, 180);
			context.waitFor(client -> client.level.getEntity(id) instanceof BotGolem shown && shown.showsScreen()
				&& !shown.terminal().buffer().isBlank());
			singleplayer.getServer().runOnServer(server -> ((BotGolem) server.overworld().getEntity(id)).machine().board()
				.virtualBus().console().write(LIGHT_IT));
			context.waitFor(client -> client.level.getEntity(id) instanceof BotGolem shown && shown.terminal().buffer().row(0).isBlank()
				&& shown.terminal().buffer().backgrounds()[0] != com.payonel.modularcomputers.display.TextBuffer.DEFAULT_BACKGROUND);
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			context.getInput().lookAt(withScreen); // (its middle: its body, its face above)
			singleplayer.getConnection().waitForChunksRender();
			context.waitTicks(5);
			BufferedImage shot = read(context.takeScreenshot("bot-golems"));
			int lit = count(shot, 0, 0, shot.getWidth(), shot.getHeight(),
				rgb -> AllInOneClientGameTest.green(rgb) > LIT && AllInOneClientGameTest.red(rgb) < AllInOneClientGameTest.green(rgb) / 2);
			check(lit > LIT_PIXELS, "its face shows its screen's text, found " + lit + " lit pixels");
			// The other, charging on its pad: its green particles round it.
			String padState = singleplayer.getServer().computeOnServer(server -> {
				var pad = (com.payonel.modularcomputers.block.ChargingPadBlockEntity) server.overworld().getBlockEntity(without);
				var golems = server.overworld().getEntitiesOfClass(BotGolem.class, new net.minecraft.world.phys.AABB(without).inflate(2));
				return pad.charging() + " buffer " + pad.buffer().level() + " battery " + pad.cards().getItem(0) + " golems " + golems.stream().map(g -> g.position() + " " + g.blockPosition()).toList()
					+ " pad at " + without;
			});
			check(padState.startsWith("true"), "the pad charging the golem on it: " + padState);
			context.runOnClient(client -> client.options.particles().set(net.minecraft.server.level.ParticleStatus.ALL)); // (the test's settings may cut them)
			context.getInput().lookAt(without);
			context.waitTicks(10);
			context.takeScreenshot("bot-golem-charging");
			context.getInput().lookAt(withScreen);
			context.waitTicks(2);
			// Its screen view: right-click it.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(com.payonel.modularcomputers.client.screen.DisplayScreen.class);
			context.waitTicks(2);
			context.takeScreenshot("bot-golem-view");
			// Its parts view: its view's toggle (the server opens it).
			singleplayer.getServer().runOnServer(server -> {
				if (server.overworld().getEntity(id) instanceof BotGolem golem) {
					golem.openParts(singleplayer.getConnection().getServerPlayer());
				}
			});
			context.waitForScreen(com.payonel.modularcomputers.client.screen.CaseScreen.class);
			context.waitTicks(2);
			// What its bus panel lists: the client's copy of its board, as the server's.
			var server = singleplayer.getServer().computeOnServer(s -> keys((BotGolem) s.overworld().getEntity(id)));
			var client = context.computeOnClient(c -> keys((BotGolem) c.level.getEntity(id)));
			check(client.equals(server) && client.contains("exp:0") && client.contains("exp:3"),
				"its bus panel lists its screen and its robotics device, as the server's board: client " + client + ", server " + server);
			String hand = context.computeOnClient(c -> c.player.containerMenu instanceof CaseMenu menu && menu.hasHand()
				? menu.slots.get(CaseMenu.HAND_SLOT).getItem().toString() : "no hand slot");
			check(hand.contains("iron_pickaxe"), "its hand's slot holds its pickaxe: " + hand);
			context.getInput().setCursorPos(0, 0); // (off the board: no part's tooltip in the picture)
			context.waitTicks(2);
			context.takeScreenshot("bot-golem-parts");
		}
	}

	private static java.util.List<String> keys(BotGolem golem) {
		return golem.board().busEntries().stream().map(com.payonel.modularcomputers.board.BoardAssembly.BusEntry::key).toList();
	}

	/** A golem at {@code pos}, facing south, with a CPU, RAM, an EEPROM and a battery. */
	private static BotGolem golem(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
		BotGolem golem = ModEntities.BOT_GOLEM.create(level, EntitySpawnReason.COMMAND);
		golem.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SOUTH, 0);
		golem.setYBodyRot(SOUTH);
		golem.setYHeadRot(SOUTH);
		level.addFreshEntity(golem);
		var parts = golem.board().parts();
		parts.setItem(CaseMenu.CPU_SLOT, new ItemStack(ModItems.TIER3_CPU));
		parts.setItem(CaseMenu.RAM_SLOTS, new ItemStack(ModItems.TIER3_RAM));
		parts.setItem(CaseMenu.EEPROM_SLOT, new ItemStack(ModItems.EEPROM));
		parts.setItem(CaseMenu.UPS_SLOT, ModItems.TIER1_BATTERY.charged());
		return golem;
	}
}
