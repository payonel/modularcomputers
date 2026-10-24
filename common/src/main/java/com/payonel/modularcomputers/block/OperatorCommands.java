package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.ModularComputers;
import com.payonel.modularcomputers.device.OperatorCard;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * How an operator card's block runs its commands: as a command block runs its own, from the
 * block's center in its world, with a command block's permission (gamemaster), named "Operator
 * Card"; what the command says is kept for the program (not sent to anyone), and each command is
 * logged, so a server's admins see what ran.
 */
final class OperatorCommands {
	private static final Component NAME = Component.literal("Operator Card");

	private OperatorCommands() {
	}

	static OperatorCard.Outcome run(ServerLevel level, BlockPos pos, String command) {
		StringBuilder output = new StringBuilder();
		CommandSource listener = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				output.append(message.getString()).append('\n');
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
				return false; // (the log says what ran)
			}
		};
		boolean[] success = {false};
		long[] result = {0};
		CommandSourceStack source = new CommandSourceStack(listener, Vec3.atCenterOf(pos), Vec2.ZERO, level, LevelBasedPermissionSet.GAMEMASTER, NAME,
			level.getServer()).withCallback((ok, value) -> {
				success[0] |= ok;
				result[0] = value;
			});
		ModularComputers.LOGGER.info("Operator Card at {} in {}: {}", pos.toShortString(), level.dimension().identifier(), command);
		level.getServer().getCommands().performPrefixedCommand(source, command);
		return new OperatorCard.Outcome(success[0], result[0], output.toString());
	}
}
