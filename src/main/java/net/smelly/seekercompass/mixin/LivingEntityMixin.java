package net.smelly.seekercompass.mixin;

import com.teamabnormals.blueprint.common.world.storage.tracking.TrackedDataManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.smelly.seekercompass.SeekerCompass;
import net.smelly.seekercompass.interfaces.ClientStalkable;
import net.smelly.seekercompass.interfaces.Stalkable;
import net.smelly.seekercompass.interfaces.Stalker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements Stalkable, ClientStalkable {
	private final Set<Player> stalkers = new HashSet<>();

	public LivingEntityMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Inject(at = @At("HEAD"), method = "tick")
	private void tickStalking(CallbackInfo info) {
		if (!this.level.isClientSide) {
			Set<Player> stalkers = this.stalkers;
			int prevSize = stalkers.size();
			stalkers.removeIf(player -> !player.isAlive() || ((Stalker) player).getStalkingEntity() != (Object) this);
			if (prevSize != stalkers.size()) {
				this.setBeingStalked(this.hasStalkers());
			}
		}
	}

	@Override
	public void addStalker(Player player) {
		if (this.stalkers.add(player)) {
			this.setBeingStalked(this.hasStalkers());
		}
	}

	@Override
	public void removeStalker(Player player) {
		if (this.stalkers.remove(player)) {
			this.setBeingStalked(this.hasStalkers());
		}
	}

	@Override
	public boolean hasStalkers() {
		return !this.stalkers.isEmpty();
	}

	@Override
	public boolean isBeingStalkedBy(Player player) {
		return this.stalkers.contains(player);
	}

	@Override
	public void setBeingStalked(boolean beingStalked) {
		TrackedDataManager.INSTANCE.setValue(this, SeekerCompass.BEING_STALKED, beingStalked);
	}

	@Override
	public boolean isBeingStalked() {
		return TrackedDataManager.INSTANCE.getValue(this, SeekerCompass.BEING_STALKED);
	}
}
