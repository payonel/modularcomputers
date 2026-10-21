package com.payonel.modularcomputers.gametest.mixin;

import com.payonel.modularcomputers.gametest.MouseGrabs;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts mouse grabs for {@link MouseGrabs}. */
@Mixin(MouseHandler.class)
public abstract class MouseGrabCounter {
	@Inject(method = "grabMouse", at = @At("HEAD"))
	private void modularcomputers$countGrab(CallbackInfo info) {
		MouseGrabs.count++;
	}
}
