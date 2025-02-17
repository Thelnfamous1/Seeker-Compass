package net.smelly.seekercompass.network;

import com.teamabnormals.blueprint.client.ClientInfo;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import net.smelly.seekercompass.interfaces.Stalker;
import net.smelly.seekercompass.sound.StalkingSound;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public final class S2CUpdateStalkerMessage {
	private final int entityId;

	public S2CUpdateStalkerMessage(int entityId) {
		this.entityId = entityId;
	}

	public void serialize(FriendlyByteBuf buf) {
		buf.writeInt(this.entityId);
	}

	public static S2CUpdateStalkerMessage deserialize(FriendlyByteBuf buf) {
		return new S2CUpdateStalkerMessage(buf.readInt());
	}

	public static boolean handle(S2CUpdateStalkerMessage message, Supplier<NetworkEvent.Context> ctx) {
		NetworkEvent.Context context = ctx.get();
		if (context.getDirection().getReceptionSide() == LogicalSide.CLIENT) {
			context.enqueueWork(() -> {
				LocalPlayer player = ClientInfo.getClientPlayer();
				int id = message.entityId;
				Stalker stalker = (Stalker) player;
				LivingEntity prev = stalker.getStalkingEntity();
				if (id == -1) {
					stalker.setStalkingEntity(null);
					updateClientStalking(prev, null);
				} else {
					Entity entity = player.level.getEntity(id);
					if (entity instanceof LivingEntity) {
						stalker.setStalkingEntity((LivingEntity) entity);
						updateClientStalking(prev, (LivingEntity) entity);
					}
				}
			});
		}
		context.setPacketHandled(true);
		return true;
	}

	@OnlyIn(Dist.CLIENT)
	private static void updateClientStalking(@Nullable LivingEntity prevEntity, @Nullable LivingEntity stalkingEntity) {
		boolean nonNull = stalkingEntity != null;
		Options options = ClientInfo.MINECRAFT.options;
		if (nonNull) {
			options.hideGui = true;
		} else if (options.hideGui) {
			options.hideGui = false;
		}
		if (prevEntity != stalkingEntity) {
			ClientInfo.MINECRAFT.getSoundManager().play(new StalkingSound(nonNull, ClientInfo.getClientPlayer()));
		}
		if (nonNull || ClientInfo.MINECRAFT.getCameraEntity() == prevEntity) {
			ClientInfo.MINECRAFT.setCameraEntity(stalkingEntity);
		}
	}
}
