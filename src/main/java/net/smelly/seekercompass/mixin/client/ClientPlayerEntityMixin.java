package net.smelly.seekercompass.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.smelly.seekercompass.SeekerCompass;
import net.smelly.seekercompass.interfaces.Stalker;
import net.smelly.seekercompass.network.C2SStopStalkingMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public final class ClientPlayerEntityMixin {

	@Inject(at = @At("HEAD"), method = "tick")
	private void updateStalking(CallbackInfo info) {
		LivingEntity stalkingEntity = ((Stalker) (Object) this).getStalkingEntity();
		if (stalkingEntity != null && stalkingEntity.isRemoved()) {
			SeekerCompass.CHANNEL.sendToServer(new C2SStopStalkingMessage());
		}
	}

	@Inject(at = @At("HEAD"), method = "isHandsBusy", cancellable = true)
	private void isHandsBusy(CallbackInfoReturnable<Boolean> info) {
		if (((Stalker) (Object) this).isStalking()) {
			info.setReturnValue(true);
		}
	}

}
