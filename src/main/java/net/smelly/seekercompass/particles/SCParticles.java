package net.smelly.seekercompass.particles;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.smelly.seekercompass.SeekerCompass;

/**
 * @author SmellyModder(Luke Tonon)
 */
public class SCParticles {

	private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, SeekerCompass.MOD_ID);

	public static final RegistryObject<ParticleType<SimpleParticleType>> SEEKER_EYES = createBasicParticleType(true, "seeker_eyes");
	public static final RegistryObject<ParticleType<SimpleParticleType>> SEEKER_WARP = createBasicParticleType(true, "seeker_warp");
	
	private static RegistryObject<ParticleType<SimpleParticleType>> createBasicParticleType(boolean alwaysShow, String name) {
		ParticleType<SimpleParticleType> particleType = new SimpleParticleType(alwaysShow);
		return PARTICLE_TYPES.register(name, () -> particleType);
	}

	public static void register(IEventBus modEventBus){
		PARTICLE_TYPES.register(modEventBus);
	}
	
	@EventBusSubscriber(modid = SeekerCompass.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
	public static class RegisterParticleFactories {
		
		@SubscribeEvent(priority = EventPriority.LOWEST)
		public static void registerParticleTypes(RegisterParticleProvidersEvent event) {
			event.register(SEEKER_EYES.get(), SeekerEyesParticle.Factory::new);
			event.register(SEEKER_WARP.get(), SeekerWarpParticle.Factory::new);
		}
		
	}
}