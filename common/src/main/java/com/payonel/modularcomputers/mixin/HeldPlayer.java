package com.payonel.modularcomputers.mixin;

import com.payonel.modularcomputers.entity.EnergyField;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player an energy field holds can't attack. */
@Mixin(Player.class)
public abstract class HeldPlayer {
	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void modularcomputers$held(Entity target, CallbackInfo info) {
		if (EnergyField.held((Player) (Object) this)) {
			info.cancel();
		}
	}
}
