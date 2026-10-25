package com.payonel.modularcomputers.mixin;

import com.payonel.modularcomputers.entity.EnergyField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player an energy field holds can't use anything, or break blocks. */
@Mixin(ServerPlayerGameMode.class)
public abstract class HeldGameMode {
	@Shadow
	@Final
	protected ServerPlayer player;

	@Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
	private void modularcomputers$use(ServerPlayer user, Level level, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> info) {
		if (EnergyField.held(user)) {
			info.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void modularcomputers$useOn(ServerPlayer user, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hit,
		CallbackInfoReturnable<InteractionResult> info) {
		if (EnergyField.held(user)) {
			info.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
	private void modularcomputers$break(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxY, int sequence,
		CallbackInfo info) {
		if (EnergyField.held(player)) {
			info.cancel();
		}
	}
}
