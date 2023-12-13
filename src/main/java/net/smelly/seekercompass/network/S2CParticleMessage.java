package net.smelly.seekercompass.network;

import com.teamabnormals.blueprint.client.ClientInfo;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * Message for telling the client to spawn particles
 * @author - SmellyModder(Luke Tonon)
 */
public final class S2CParticleMessage {
	private String particleName;
	private double posX, posY, posZ;
	private double motionX, motionY, motionZ;
	
	public S2CParticleMessage(String particleName, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		this.particleName = particleName;
		this.posX = posX;
		this.posY = posY;
		this.posZ = posZ;
		this.motionX = motionX;
		this.motionY = motionY;
		this.motionZ = motionZ;
	}
	
	public void serialize(FriendlyByteBuf buf) {
		buf.writeUtf(this.particleName);
		buf.writeDouble(this.posX);
		buf.writeDouble(this.posY);
		buf.writeDouble(this.posZ);
		buf.writeDouble(this.motionX);
		buf.writeDouble(this.motionY);
		buf.writeDouble(this.motionZ);
	}
	
	public static S2CParticleMessage deserialize(FriendlyByteBuf buf) {
		String particleName = buf.readUtf();
		double posX = buf.readDouble();
		double posY = buf.readDouble();
		double posZ = buf.readDouble();
		double motionX = buf.readDouble();
		double motionY = buf.readDouble();
		double motionZ = buf.readDouble();
		return new S2CParticleMessage(particleName, posX, posY, posZ, motionX, motionY, motionZ);
	}
	
	public static boolean handle(S2CParticleMessage message, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		if (context.getDirection().getReceptionSide() == LogicalSide.CLIENT) {
			context.enqueueWork(() -> {
				Level world = ClientInfo.getClientPlayer().level;
				ParticleType<?> particleType = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation(message.particleName));
				
				if (particleType instanceof SimpleParticleType simpleParticleType) {
					world.addParticle(simpleParticleType, message.posX, message.posY, message.posZ, message.motionX, message.motionY, message.motionZ);
				}
			});
		}
		context.setPacketHandled(true);
		return true;
	}
}