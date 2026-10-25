package com.payonel.modularcomputers.mixin;

import com.payonel.modularcomputers.entity.EnergyField;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A creeper an energy field holds can't blow: its fuse dies down, and a lit one waits until it's let go. */
@Mixin(Creeper.class)
public abstract class HeldCreeper {
	@Shadow
	public abstract void setSwellDir(int direction);

	@Inject(method = "tick", at = @At("HEAD"))
	private void modularcomputers$fuse(CallbackInfo info) {
		if (EnergyField.held((Creeper) (Object) this)) {
			setSwellDir(-1);
		}
	}

	@Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
	private void modularcomputers$explode(CallbackInfo info) {
		if (EnergyField.held((Creeper) (Object) this)) {
			info.cancel();
		}
	}
}
