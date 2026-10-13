package com.payonel.modularcomputers.mixin;

import com.payonel.modularcomputers.block.CardWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every block change on the server reaches motion sensors (Fabric has no general block-change event). */
@Mixin(ServerLevel.class)
public abstract class ServerLevelBlockUpdates {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void modularcomputers$blockChanged(BlockPos pos, BlockState old, BlockState current, int flags, CallbackInfo info) {
		CardWorld.blockChanged((ServerLevel) (Object) this, pos, old, current);
	}
}
