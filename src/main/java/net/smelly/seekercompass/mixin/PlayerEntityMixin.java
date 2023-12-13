package net.smelly.seekercompass.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.smelly.seekercompass.SeekerCompass;
import net.smelly.seekercompass.interfaces.Stalkable;
import net.smelly.seekercompass.interfaces.Stalker;
import net.smelly.seekercompass.network.S2CUpdateStalkerMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Player.class)
public final class PlayerEntityMixin implements Stalker {
	@Nullable
	private LivingEntity stalkingEntity;
	private boolean shouldBeStalking;

	@Inject(at = @At("HEAD"), method = "tick")
	private void tickStalking(CallbackInfo info) {
		if ((Object) this instanceof ServerPlayer) {
			if (!this.shouldBeStalking() && this.isStalking()) {
				this.setStalkingEntity(null);
			}
			this.setShouldBeStalking(false);
		}
	}

	@Override
	public void setStalkingEntity(@Nullable LivingEntity stalkingEntity) {
		LivingEntity prevEntity = this.stalkingEntity;
		this.stalkingEntity = stalkingEntity;
		boolean nonNull = stalkingEntity != null;
		if ((Object) this instanceof ServerPlayer) {
			if (prevEntity instanceof Stalkable stalkable) {
				if (stalkable.isBeingStalkedBy((Player) (Object) this)) {
					stalkable.removeStalker((Player) (Object) this);
				}
			}
			if (nonNull) {
				((Stalkable) stalkingEntity).addStalker((ServerPlayer) (Object) this);
			}
			this.setShouldBeStalking(nonNull);
			SeekerCompass.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) (Object) this), new S2CUpdateStalkerMessage(stalkingEntity != null ? stalkingEntity.getId() : -1));
		}
	}

	@Nullable
	@Override
	public LivingEntity getStalkingEntity() {
		return this.stalkingEntity;
	}

	@Override
	public boolean isStalking() {
		return this.stalkingEntity != null;
	}

	@Override
	public void setShouldBeStalking(boolean canStalk) {
		this.shouldBeStalking = canStalk;
	}

	@Override
	public boolean shouldBeStalking() {
		return this.shouldBeStalking;
	}
}
