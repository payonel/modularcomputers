package com.payonel.modularcomputers.mixin;

import com.payonel.modularcomputers.entity.EnergyField;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob an energy field holds can't act: its AI (its goals, its pathfinding, its looking) doesn't run. */
@Mixin(Mob.class)
public abstract class HeldMob {
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
	private void modularcomputers$held(CallbackInfo info) {
		if (EnergyField.held((Mob) (Object) this)) {
			info.cancel();
		}
	}
}
