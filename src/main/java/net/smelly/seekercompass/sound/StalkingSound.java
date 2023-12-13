package net.smelly.seekercompass.sound;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class StalkingSound extends AbstractSoundInstance {

	public StalkingSound(boolean activate, LocalPlayer clientPlayer) {
		super(SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, clientPlayer.getRandom());
		this.looping = false;
		this.volume = 0.5F;
		this.pitch = (activate ? 0.75F : 0.6F) - new Random().nextFloat() * 0.1F;
		//this.priority = true;
		this.delay = 0;
		this.relative = true;
	}

}
