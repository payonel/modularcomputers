package com.payonel.modularcomputers.block;

import com.payonel.modularcomputers.machine.Machine;
import com.payonel.modularcomputers.network.OpenDisplayPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Something with a screen view and a parts view, which a player switches between (a case, a screen
 * block, a golem); with a power button when it's a computer.
 */
public interface ViewHost extends DisplayHost {
	/** What the server sends to open its screen view. */
	OpenDisplayPayload openDisplayPayload();

	/** Opens its parts view (a menu) for {@code player}. */
	void openParts(ServerPlayer player);

	/** Its power button pressed; empty when it has none (a screen block). */
	default java.util.Optional<Machine.PowerPress> pressPowerButton() {
		return java.util.Optional.empty();
	}
}
