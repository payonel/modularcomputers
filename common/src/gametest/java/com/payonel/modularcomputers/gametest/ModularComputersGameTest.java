package com.payonel.modularcomputers.gametest;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.config.ModConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;

/** Server game tests: run headless on a dedicated server, on each loader (GameTests; part of {@code testAll}). */
public class ModularComputersGameTest {
	@GameTest
	public void modIsLoadedAndInitialized(GameTestHelper helper) {
		helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ModularComputers.id("tier3_case")), "mod not loaded");
		helper.assertTrue(ModularComputers.isInitialized(), "common initializer did not run");
		helper.succeed();
	}

	/**
	 * {@code /modularcomputers reload} shows what it warned of to whoever ran it (the user's,
	 * 2026-11-18): its success, then each bad value and unknown key as the log says it, in the game's
	 * failure style; with none, its success alone. Its file holds the defaults and bad lines only, so
	 * the tests beside it run on the defaults throughout (a bad value is its default).
	 */
	@GameTest
	public void reloadShowsItsWarningsToWhoeverRanIt(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<Component> said = new ArrayList<>();
		CommandSource runner = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message);
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		CommandSourceStack source = server.createCommandSourceStack().withSource(runner);
		Path file = ModConfig.path();
		try {
			String saved = Files.readString(file);
			try {
				Files.writeString(file, ModConfig.defaults() + "power.draw.terminal: lots\nrobot.cost.walk: 2\n");
				server.getCommands().performPrefixedCommand(source, ModularComputers.MOD_ID + " reload");
				helper.assertTrue(said.size() == 3, "its success and two warnings: " + said);
				helper.assertTrue(said.get(0).getContents() instanceof TranslatableContents success
					&& success.getKey().equals("commands.modularcomputers.reload"), "its success first: " + said.get(0));
				helper.assertTrue(said.get(1).getString().equals("power.draw.terminal should be a whole number of power units a tick, 0 or more, not \"lots\"; using 10"),
					"a bad value, as the log says it: " + said.get(1).getString());
				helper.assertTrue(said.get(2).getString().equals("robot.cost.walk is no setting; ignored"), "an unknown key: " + said.get(2).getString());
				helper.assertTrue(java.util.Objects.equals(said.get(1).getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.RED)),
					"in the game's failure style");
				said.clear();
				Files.writeString(file, ModConfig.defaults());
				server.getCommands().performPrefixedCommand(source, ModularComputers.MOD_ID + " reload");
				helper.assertTrue(said.size() == 1, "none: its success alone: " + said);
			} finally {
				Files.writeString(file, saved);
				ModConfig.load();
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		helper.succeed();
	}

	@GameTest
	public void everyItemOfTheModIsInItsCreativeTab(GameTestHelper helper) {
		var listed = com.payonel.modularcomputers.ModCreativeTab.items();
		for (var item : com.payonel.modularcomputers.command.ModCommands.modItems()) {
			helper.assertTrue(listed.stream().filter(stack -> stack.is(item)).count() == 1, "once in the tab: " + item);
		}
		helper.succeed();
	}
}
