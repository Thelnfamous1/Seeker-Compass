package net.smelly.seekercompass;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.smelly.seekercompass.interfaces.ClientStalkable;
import net.smelly.seekercompass.interfaces.Stalkable;
import net.smelly.seekercompass.mixin.client.EntityRendererInvokerMixin;
import org.lwjgl.opengl.GL11;

@Mod.EventBusSubscriber(modid = SeekerCompass.MOD_ID)
public final class StalkerEyeHandler {
	private static final ResourceLocation STALKER_EYE = new ResourceLocation(SeekerCompass.MOD_ID, "textures/entity/stalker_eye.png");

	/*
	@SubscribeEvent
	public static void onEntityTracking(EntityTrackingEvent event) {
		if (!event.isUpdating()) {
			Entity entity = event.getEntity();
			if (entity instanceof Stalkable) {
				Stalkable stalkable = (Stalkable) entity;
				if (stalkable.isDirty()) {
					PacketDistributor.PacketTarget packetTarget = entity instanceof ServerPlayer ? PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity) : PacketDistributor.TRACKING_ENTITY.with(() -> entity);
					SeekerCompass.CHANNEL.send(packetTarget, new S2CUpdateStalkedMessage(entity.getId(), stalkable.hasStalkers()));
				}
			}
		}
	}
	 */

	@SubscribeEvent
	public static void onStartTrackingEntity(PlayerEvent.StartTracking event) {
		Entity target = event.getTarget();
		if (target instanceof LivingEntity) {
			Player player = event.getEntity();
			if (player instanceof ServerPlayer) {
				//SeekerCompass.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player), new S2CUpdateStalkedMessage(target.getId(), ((Stalkable) target).hasStalkers()));
			}
		}
	}

	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent
	public static void onLivingRender(RenderLivingEvent.Post<?, ?> event) {
		if (SCConfig.CLIENT.stalkingEyeProcedure.rendersAboveEntity) {
			LivingEntity entity = event.getEntity();
			if (((ClientStalkable) entity).isBeingStalked()) {
				LivingEntityRenderer<? extends LivingEntity, ?> livingRenderer = event.getRenderer();
				PoseStack matrixStack = event.getPoseStack();
				matrixStack.pushPose();
				EntityRenderDispatcher dispatcher = ((EntityRendererInvokerMixin<LivingEntity>) livingRenderer).getEntityRenderDispatcher();
				float offset = ((EntityRendererInvokerMixin<LivingEntity>) livingRenderer).callShouldShowName(entity) && ForgeHooksClient.isNameplateInRenderDistance(entity, dispatcher.distanceToSqr(entity)) ? 0.5F : 0.0F;
				matrixStack.translate(0.0D, entity.getBbHeight() + 0.5F + offset, 0.0D);
				matrixStack.mulPose(dispatcher.cameraOrientation());
				matrixStack.scale(-0.025F * 2.0F, -0.025F * 2.0F, 0.025F * 2.0F);
				RenderSystem.enableBlend();
				RenderSystem.enableDepthTest();
				RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
				Matrix4f matrix4f = matrixStack.last().pose();
				Tesselator tessellator = Tesselator.getInstance();
				BufferBuilder builder = tessellator.getBuilder();
				//ClientInfo.MINECRAFT.getTextureManager().bind(STALKER_EYE);
				RenderSystem.setShader(GameRenderer::getPositionTexShader);
				RenderSystem.setShaderTexture(0, STALKER_EYE);
				builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
				builder.vertex(matrix4f, -3.5F, 5.0F, 0.0F).uv(0, 1).endVertex();
				builder.vertex(matrix4f, 3.5F, 5.0F, 0.0F).uv(1, 1).endVertex();
				builder.vertex(matrix4f, 3.5F, 0.0F, 0.0F).uv(1, 0).endVertex();
				builder.vertex(matrix4f, -3.5F, 0.0F, 0.0F).uv(0, 0).endVertex();
				tessellator.end();

				RenderSystem.disableBlend();
				matrixStack.popPose();
			}
		}
	}

	public static void registerStalkingEye(RegisterGuiOverlaysEvent event){
		event.registerAboveAll("stalking_eye", StalkerEyeHandler::renderStalkingEye);
	}

	public static void renderStalkingEye(ForgeGui gui, PoseStack poseStack, float partialTick, int screenWidth, int screenHeight) {
		if (SCConfig.CLIENT.stalkingEyeProcedure.rendersInGUI) {
			Minecraft minecraft = Minecraft.getInstance();
			if (!minecraft.isPaused()) {
				Entity cameraEntity = minecraft.getCameraEntity();
				if (cameraEntity instanceof Stalkable && ((ClientStalkable) cameraEntity).isBeingStalked() || isHoveringOverStalkedEntity(minecraft.hitResult)) {
					poseStack.pushPose();
					RenderSystem.enableBlend();

					//minecraft.getTextureManager().bind(STALKER_EYE);
					RenderSystem.setShader(GameRenderer::getPositionTexShader);
					RenderSystem.setShaderTexture(0, STALKER_EYE);
					float middle = screenWidth / 2.0F;
					int bottom = screenHeight - gui.rightHeight;
					int top = bottom + 10;

					Tesselator tessellator = Tesselator.getInstance();
					BufferBuilder bufferbuilder = tessellator.getBuilder();
					bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
					float left = middle - 7;
					float right = middle + 7;
					bufferbuilder.vertex(left, top, 0).uv(0, 1).endVertex();
					bufferbuilder.vertex(right, top, 0).uv(1, 1).endVertex();
					bufferbuilder.vertex(right, bottom, 0).uv(1, 0).endVertex();
					bufferbuilder.vertex(left, bottom, 0).uv(0, 0).endVertex();
					tessellator.end();

					RenderSystem.disableBlend();
					poseStack.popPose();

					gui.rightHeight += 11;
				}
			}
		}
	}

	private static boolean isHoveringOverStalkedEntity(HitResult rayTraceResult) {
		if (rayTraceResult instanceof EntityHitResult) {
			Entity entity = ((EntityHitResult) rayTraceResult).getEntity();
			return entity instanceof ClientStalkable && ((ClientStalkable) entity).isBeingStalked();
		}
		return false;
	}

}
