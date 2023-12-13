package net.smelly.seekercompass;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;
import net.smelly.seekercompass.network.S2CParticleMessage;

import java.util.stream.Stream;

/**
 * @author SmellyModder(Luke Tonon)
 */
@EventBusSubscriber(modid = SeekerCompass.MOD_ID)
public final class SCEvents {
	private static final String TAG_SPAWNED = "seeker_compass:piglin_spawned";
	public static final String TAG_CHUNK_UPDATE = "seeker_compass:chunk_update";
	public static final String TAG_CHUNK_TIMER = "seeker_compass:chunk_timer";
	private static final String TAG_PREV_CHUNK = "seeker_compass:prev_chunk";
	
	@SubscribeEvent
	public static void trackEntity(PlayerInteractEvent.EntityInteract event) {
		Level level = event.getLevel();
		Entity target = event.getTarget();
		
		if (level.isClientSide || target == null) return;
		
		Player player = event.getEntity();
		if (target instanceof LivingEntity livingEntity) {
			if (livingEntity.isAlive()) {
				InteractionHand hand = event.getHand();
				ItemStack itemstack = player.getItemInHand(hand);
				
				if (itemstack.getItem() == SeekerCompass.SEEKER_COMPASS.get() && SeekerCompassItem.isNotBroken(itemstack)) {
					CompoundTag tag = itemstack.getTag();
					boolean hasTag = tag != null;
					if (hasTag && tag.getBoolean("TrackingOnly")) return;
					if (hasTag && tag.contains("TrackingEntity")) {
						Entity entity = ((ServerLevel) level).getEntity(NbtUtils.loadUUID(tag.get("TrackingEntity")));
						
						if (entity == target) {
							tag.remove("TrackingEntity");
							tag.remove("EntityStatus");
							tag.remove("Rotations");
							player.level.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.75F, 1.5F);
							
							RandomSource rand = player.getRandom();
							for (int i = 0; i < 8; i++) {
								Vec3 targetPosition = target.position();
								Vec3 position = targetPosition.add(rand.nextBoolean() ? -rand.nextFloat() : rand.nextFloat() * 1.25F, target.getEyeHeight(), rand.nextBoolean() ? -rand.nextFloat() : rand.nextFloat() * 1.25F);
								Vec3 motion = targetPosition.subtract(position.add(0.0F, target.getEyeHeight() * 0.35F, 0.0F)).scale(-0.5F);
								
								SeekerCompass.CHANNEL.send(PacketDistributor.ALL.with(() -> null), new S2CParticleMessage("seeker_compass:seeker_eyes", targetPosition.x(), targetPosition.y(), targetPosition.z(), motion.x(), motion.y(), motion.z()));
							}
							return;
						}
					}
					
					itemstack.getOrCreateTag().put("TrackingEntity", NbtUtils.createUUID(target.getUUID()));
					player.awardStat(Stats.ITEM_USED.get(itemstack.getItem()));
					player.swing(hand);
					player.level.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.75F, 0.25F);
					
					RandomSource rand = player.getRandom();
					Vec3 targetPosition = target.position();
					for (int i = 0; i < 8; i++) {
						Vec3 position = targetPosition.add(rand.nextBoolean() ? -rand.nextFloat() : rand.nextFloat() * 1.25F, target.getEyeHeight(), rand.nextBoolean() ? -rand.nextFloat() : rand.nextFloat() * 1.25F);
						Vec3 motion = position.subtract(targetPosition.add(0.0F, target.getEyeHeight() * 0.35F, 0.0F)).scale(-0.5F);
						SeekerCompass.CHANNEL.send(PacketDistributor.ALL.with(() -> null), new S2CParticleMessage("seeker_compass:seeker_eyes", position.x(), position.y(), position.z(), motion.x(), motion.y(), motion.z()));
					}
				}
			}
		}
	}
	
	@SubscribeEvent
	public static void onEntitySpawned(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide) return;
		double compassChance = SCConfig.COMMON.zombifiedPiglinCompassChance;
		if (compassChance > 0.0F) {
			Entity entity = event.getEntity();
			if (entity instanceof ZombifiedPiglin) {
				CompoundTag nbt = entity.getPersistentData();
				if (!nbt.getBoolean(TAG_SPAWNED)) {
					ZombifiedPiglin piglin = (ZombifiedPiglin) entity;
					if (piglin.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() && piglin.getRandom().nextFloat() <= compassChance) {
						piglin.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(SeekerCompass.SEEKER_COMPASS.get()));
						piglin.setDropChance(EquipmentSlot.OFFHAND, 2.0F);
					}
					nbt.putBoolean(TAG_SPAWNED, true);
				}
			}
		}
	}
	
	@SubscribeEvent
	public static void onEntityTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		ChunkPos chunkpos = new ChunkPos(entity.blockPosition());
		CompoundTag tag = entity.getPersistentData();
		
		if (!(entity.level instanceof ServerLevel)) return;
		ServerLevel level = (ServerLevel) entity.level;
		
		if (tag.contains(TAG_CHUNK_UPDATE) && tag.getBoolean(TAG_CHUNK_UPDATE)) {
			if (tag.contains(TAG_PREV_CHUNK)) {
				long prevChunkLong = tag.getLong(TAG_PREV_CHUNK);
				ChunkPos prevChunkPos = new ChunkPos(ChunkPos.getX(prevChunkLong), ChunkPos.getZ(prevChunkLong));
				if (!chunkpos.equals(prevChunkPos)) {
					if (!isChunkForced(level, prevChunkPos)) {
						level.getChunkSource().updateChunkForced(prevChunkPos, false);
					}
				}
			}
			
			if (tag.contains(TAG_CHUNK_TIMER)) {
				int timer = tag.getInt(TAG_CHUNK_TIMER);
				if (timer > 0) {
					level.getChunkSource().updateChunkForced(chunkpos, true);
					tag.putInt(TAG_CHUNK_TIMER, timer - 1);
				} else {
					if (!isChunkForced(level, chunkpos)) {
						level.getChunkSource().updateChunkForced(chunkpos, false);
					}
					tag.putBoolean(TAG_CHUNK_UPDATE, false);
				}
				tag.putLong(TAG_PREV_CHUNK, chunkpos.toLong());
			}
		}
	}
	
	/*
	 * Checks if the chunk(chunk to be unloaded) is a spawn chunk or forced already by the force chunk command
	 */
	public static boolean isChunkForced(ServerLevel level, ChunkPos pos) {
		LevelData levelData = level.getLevelData();
		ChunkPos spawnChunk = new ChunkPos(new BlockPos(levelData.getXSpawn(), 0, levelData.getZSpawn()));
		Stream<ChunkPos> spawnChunks = ChunkPos.rangeClosed(spawnChunk, 11);
		
		for (long values : level.getForcedChunks()) {
			if (pos.equals(new ChunkPos(ChunkPos.getX(values), ChunkPos.getZ(values)))) {
				return true;
			}
		}

		return spawnChunks.anyMatch(chunk -> chunk.equals(pos));
	}
}