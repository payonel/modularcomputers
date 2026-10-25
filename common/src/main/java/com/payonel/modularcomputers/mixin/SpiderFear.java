package com.payonel.modularcomputers.mixin;

import com.payonel.modularcomputers.entity.BotGolem;
import com.payonel.modularcomputers.entity.GolemFear;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Spider runs from any golem ({@link GolemFear}). */
@Mixin(Spider.class)
public abstract class SpiderFear {
	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void modularcomputers$fearGolems(CallbackInfo info) {
		GolemFear.fear((Spider) (Object) this, GolemFear.SPIDER_PRIORITY, golem -> true);
	}
}
