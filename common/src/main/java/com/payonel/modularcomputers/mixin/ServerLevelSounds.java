package com.payonel.modularcomputers.mixin;

import com.payonel.modularcomputers.block.CardWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The sounds a world plays, for audio sensors ({@link CardWorld#heard}): every sound the server
 * sends players goes through these, at a place or on an entity, on every loader; and a block
 * broken, whose sound players make from its level event.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSounds {
	@Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
		at = @At("HEAD"))
	private void modularcomputers$soundAt(Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume,
		float pitch, long seed, CallbackInfo info) {
		CardWorld.heard((ServerLevel) (Object) this, x, y, z, sound.value(), volume);
	}

	@Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
		at = @At("HEAD"))
	private void modularcomputers$soundOn(Entity except, Entity on, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed,
		CallbackInfo info) {
		CardWorld.heard((ServerLevel) (Object) this, on.getX(), on.getY(), on.getZ(), sound.value(), volume);
	}

	@Inject(method = "levelEvent", at = @At("HEAD"))
	private void modularcomputers$levelEvent(Entity source, int type, BlockPos pos, int data, CallbackInfo info) {
		CardWorld.levelEvent((ServerLevel) (Object) this, type, pos, data);
	}
}
