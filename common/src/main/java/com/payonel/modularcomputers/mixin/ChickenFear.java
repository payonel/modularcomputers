package com.payonel.modularcomputers.mixin;

import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.GolemFear;
import net.minecraft.world.entity.animal.chicken.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A chicken runs from a flying golem ({@link GolemFear}). */
@Mixin(Chicken.class)
public abstract class ChickenFear {
	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void modularcomputers$fearFlyingGolems(CallbackInfo info) {
		GolemFear.fear((Chicken) (Object) this, GolemFear.ANIMAL_PRIORITY, BotGolem::flying);
	}
}
