package com.payonel.modularcomputers.gametest;

import static com.payonel.modularcomputers.gametest.CaseClientGameTest.check;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.count;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.read;
import static com.payonel.modularcomputers.gametest.CaseClientGameTest.teleport;

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
 * in the golem's own art, its hand's slot holding the pickaxe, and its crafting card's tab open:
 * its grid, its result and its storage. The other, its back to the player, has a crafting card in:
 * a tiny chest on its back. And a pig held up in an energy field: a translucent blue sphere round it.
 * And a golem's torso beside its legs, detached.
 */
public class BotGolemClientGameTest implements FabricClientGameTest {
	/** Facing south (toward the player, who faces north), and north (its back to the player). */
	private static final float SOUTH = 0;
	private static final float NORTH = 180;
	/** How much bluer (than red) a pixel the field covers turns, and how many turn at least. */
	private static final int FIELD_BLUE = 20;
	private static final int FIELD_PIXELS = 500;
	/** The lane its crafting card is in. */
	private static final int CRAFTING_LANE = 1;

	/** A crafting card with two planks in its grid, one over the other (sticks), and some logs in its storage. */
	private static ItemStack craftingCard() {
		ItemStack card = new ItemStack(ModItems.CRAFTING_CARD);
		var items = net.minecraft.core.NonNullList.withSize(com.payonel.modularcomputers.device.CraftingCard.SLOTS, ItemStack.EMPTY);
		items.set(0, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
		items.set(3, new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS));
		items.set(com.payonel.modularcomputers.device.CraftingCard.GRID, new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 12));
		card.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
		return card;
	}
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
				golem.board().parts().setItem(CaseMenu.EXP_SLOTS + CRAFTING_LANE, craftingCard());
				// The other on a charging pad, charging (its particles), its battery run down first.
				server.overworld().setBlock(without, com.payonel.modularcomputers.block.ModBlocks.CHARGING_PAD.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
				((com.payonel.modularcomputers.block.ChargingPadBlockEntity) server.overworld().getBlockEntity(without)).cards()
					.setItem(com.payonel.modularcomputers.block.ChargingPadBlockEntity.BATTERY, ModItems.TIER1_BATTERY.charged());
				BotGolem charging = golem(server.overworld(), without);
				charging.snapTo(charging.getX(), charging.getY() + com.payonel.modularcomputers.block.ChargingPadBlock.HEIGHT / 16.0, charging.getZ(), NORTH, 0);
				charging.setYBodyRot(NORTH);
				charging.setYHeadRot(NORTH);
				charging.board().parts().setItem(CaseMenu.EXP_SLOTS + CRAFTING_LANE, new ItemStack(ModItems.CRAFTING_CARD)); // (its back to us: its chest)
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
				rgb -> CaseClientGameTest.green(rgb) > LIT && CaseClientGameTest.red(rgb) < CaseClientGameTest.green(rgb) / 2);
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
			// A golem's torso, beside its legs, detached.
			BlockPos torsoAt = feet.north(2).west(2);
			int torsoId = singleplayer.getServer().computeOnServer(server -> {
				BotGolem torso = golem(server.overworld(), torsoAt);
				torso.legs(false);
				var legs = com.payonel.modularcomputers.entity.ModEntities.GOLEM_LEGS.create(server.overworld(), EntitySpawnReason.COMMAND);
				legs.snapTo(torsoAt.getX() - 0.5, torsoAt.getY(), torsoAt.getZ() + 0.5, SOUTH, 0);
				server.overworld().addFreshEntity(legs);
				return torso.getId();
			});
			context.waitFor(client -> client.level.getEntity(torsoId) instanceof BotGolem shown && !shown.legs());
			context.getInput().lookAt(torsoAt);
			context.waitTicks(10);
			context.takeScreenshot("bot-golem-legs");
			// A pig held up in an energy field, in front of the player.
			BlockPos pigAt = feet.north(3).west(2).above(2);
			int pigId = singleplayer.getServer().computeOnServer(server -> {
				var pig = net.minecraft.world.entity.EntityTypes.PIG.create(server.overworld(), EntitySpawnReason.COMMAND);
				pig.snapTo(pigAt.getX() + 0.5, pigAt.getY(), pigAt.getZ() + 0.5, 0, 0);
				pig.setNoGravity(true); // (up there, before the field: to see what the field adds)
				pig.setNoAi(true);
				server.overworld().addFreshEntity(pig);
				return pig.getId();
			});
			context.getInput().lookAt(pigAt);
			context.waitTicks(10);
			BufferedImage bare = read(context.takeScreenshot("energy-field-before"));
			singleplayer.getServer().runOnServer(server -> {
				var pig = server.overworld().getEntity(pigId);
				var field = com.payonel.modularcomputers.entity.ModEntities.ENERGY_FIELD.create(server.overworld(), EntitySpawnReason.TRIGGERED);
				field.hold(pig, new com.payonel.modularcomputers.entity.EnergyField.Listener() {
					@Override
					public void arrived() {
					}

					@Override
					public void blocked(String why) {
					}

					@Override
					public void lost(String why) {
					}
				});
				server.overworld().addFreshEntity(field);
			});
			context.waitTicks(10);
			BufferedImage held = read(context.takeScreenshot("energy-field"));
			// The pixels the field turned bluer (over the sky, the grass and the pig alike).
			int blue = 0;
			for (int y = 0; y < held.getHeight(); y++) {
				for (int x = 0; x < held.getWidth(); x++) {
					int was = bare.getRGB(x, y);
					int now = held.getRGB(x, y);
					if (CaseClientGameTest.blue(now) - CaseClientGameTest.red(now) > CaseClientGameTest.blue(was) - CaseClientGameTest.red(was) + FIELD_BLUE) {
						blue++;
					}
				}
			}
			check(blue > FIELD_PIXELS, "the field's blue sphere round the pig: " + blue + " pixels turned bluer");
			context.getInput().lookAt(withScreen);
			context.waitTicks(2);
			// Its screen view: right-click it.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(com.payonel.modularcomputers.client.screen.DisplayScreen.class);
			context.waitTicks(2);
			context.takeScreenshot("bot-golem-view");
			// Its hands show under the screen view's buttons too, as in the parts view, its tabs under them (the user's report).
			String column = context.computeOnClient(c -> {
				var view = (com.payonel.modularcomputers.client.screen.DisplayScreen) c.gui.screen();
				var hands = view.handPlate();
				var tab = view.tab(CRAFTING_LANE);
				return hands.width() > 0 && hands.y() >= view.partsButton().bottom() && tab.y() >= hands.bottom() ? "ok" : hands + " " + tab;
			});
			check(column.equals("ok"), "its hands under the screen view's buttons, its crafting tab under them: " + column);
			// Its crafting card's tab, under the screen view's buttons: it opens the parts view, that card open,
			// its panel left of the board (in the bus panel's place: the buttons stay right).
			CaseClientGameTest.clickOnScreen(context, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,
				screen -> ((com.payonel.modularcomputers.client.screen.DisplayScreen) screen).tab(CRAFTING_LANE));
			context.waitForScreen(com.payonel.modularcomputers.client.screen.CaseScreen.class);
			context.waitTicks(2);
			String grid = context.computeOnClient(c -> {
				CaseMenu menu = (CaseMenu) c.player.containerMenu;
				int lane = CaseMenu.CRAFT_SLOTS + CRAFTING_LANE * CaseMenu.CRAFT_LANE_SLOTS;
				var planks = menu.slots.get(lane);
				var result = menu.slots.get(lane + CaseMenu.CRAFT_RESULT);
				var screen = (com.payonel.modularcomputers.client.screen.CaseScreen) c.gui.screen();
				return planks.isActive() + " " + planks.getItem() + " " + result.isActive() + " " + result.getItem() + " panel "
					+ (screen.panel().right() < screen.board().x() && screen.tab(CRAFTING_LANE).x() > screen.board().right() && !screen.showsBus());
			});
			check(grid.startsWith("true") && grid.contains("oak_planks") && grid.contains("true 4 minecraft:stick") && grid.endsWith("panel true"),
				"its crafting tab shows its grid and what it makes, left of the board in the bus panel's place, the tabs right: " + grid);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(2);
			var crafting = CaseClientGameTest.read(context.takeScreenshot("bot-golem-crafting"));
			check(CaseClientGameTest.ringed(context, crafting, screen -> ((com.payonel.modularcomputers.client.screen.CaseScreen) screen).tab(CRAFTING_LANE))
				&& !CaseClientGameTest.ringed(context, crafting, screen -> ((com.payonel.modularcomputers.client.screen.CaseScreen) screen).partsButton()),
				"its crafting tab ringed (open), the parts view's button not");
			// The parts view's button: the bus panel back (the user's report: no way back to it from a card's).
			CaseClientGameTest.clickOnScreen(context, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,
				screen -> ((com.payonel.modularcomputers.client.screen.CaseScreen) screen).partsButton());
			check(context.computeOnClient(c -> ((com.payonel.modularcomputers.client.screen.CaseScreen) c.gui.screen()).showsBus()),
				"the parts view's button shows the bus panel again");
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
			// A golem in hand: sneaking and using it opens its parts view, on the item (the user's), its hands too.
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			context.waitFor(c -> c.gui.screen() == null);
			singleplayer.getServer().runOnServer(s -> {
				var player = s.getPlayerList().getPlayers().getFirst();
				ItemStack inHand = ((BotGolem) s.overworld().getEntity(id)).asItem();
				inHand.remove(com.payonel.modularcomputers.item.ModComponents.SERIAL); // (another golem's: not this one, which is in the world)
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, inHand);
			});
			context.runOnClient(c -> c.player.setXRot(-90)); // (at the sky: nothing to use it on)
			context.waitTicks(2);
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(com.payonel.modularcomputers.client.screen.CaseScreen.class);
			context.getInput().releaseKey(options -> options.keyShift);
			context.waitTicks(2);
			String heldView = context.computeOnClient(c -> c.player.containerMenu instanceof CaseMenu menu && menu.hasHand()
				? menu.slots.get(CaseMenu.HAND_SLOT).getItem() + " " + menu.slots.get(CaseMenu.CPU_SLOT).getItem() : "no golem view: " + c.player.containerMenu);
			check(heldView.contains("iron_pickaxe") && heldView.contains("cpu"), "the held golem's view, its hand and its CPU: " + heldView);
			context.getInput().setCursorPos(0, 0);
			context.waitTicks(2);
			context.takeScreenshot("bot-golem-held");
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
