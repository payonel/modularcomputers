package com.payonel.modularcomputers.mixin;

import com.payonel.modularcomputers.ModularComputers;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A world save's start and end (autosave, /save-all, shutdown): every save goes through here, on
 * every loader (Fabric's save events hook the same place; NeoForge has none before a save).
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerSaves {
	@Inject(method = "saveAllChunks", at = @At("HEAD"))
	private void modularcomputers$saving(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> info) {
		ModularComputers.saving();
	}

	@Inject(method = "saveAllChunks", at = @At("RETURN"))
	private void modularcomputers$saved(boolean silent, boolean flush, boolean force, CallbackInfoReturnable<Boolean> info) {
		ModularComputers.saved();
	}
}
