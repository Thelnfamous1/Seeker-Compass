package net.smelly.seekercompass;

import com.mojang.datafixers.util.Pair;
import com.teamabnormals.blueprint.core.util.item.filling.TargetedItemCategoryFiller;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;
import net.smelly.seekercompass.advancements.SCCriteriaTriggers;
import net.smelly.seekercompass.enchants.SCEnchants;
import net.smelly.seekercompass.interfaces.Stalker;
import net.smelly.seekercompass.network.S2CParticleMessage;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * @author SmellyModder(Luke Tonon)
 */
public class SeekerCompassItem extends Item {
	private static final String VOODOO_TAG = "Voodoo";
	private static final String TRACKING_TAG = "TrackingEntity";
	private static final String ENTITY_TAG = "EntityStatus";
	private static final String ROTATIONS_TAG = "Rotations";
	private static final String TRACKING_ONLY = "TrackingOnly";
	private static final TargetedItemCategoryFiller FILLER = new TargetedItemCategoryFiller(() -> Items.COMPASS);
	
	public SeekerCompassItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level world, Entity entity, int itemSlot, boolean isSelected) {
		if (!world.isClientSide && isNotBroken(stack)) {
			CompoundTag tag = stack.getTag();
			if (tag != null) {
				if (tag.contains(VOODOO_TAG)) {
					VoodooData data = getVoodooData(tag);
					if (data.timer > 0) {
						tag.put(VOODOO_TAG, VoodooData.write(new VoodooData(data.timesUsed, data.timer - 1)));
					} else if (data.timesUsed >= 9) {
						tag.put(VOODOO_TAG, VoodooData.write(new VoodooData(0, 0)));
					}
				}

				if (tag.contains(TRACKING_TAG)) {
					Entity trackingEntity = this.getEntity((ServerLevel) world, stack);
					if (trackingEntity != null) {
						if (entity instanceof ServerPlayer) {
							int damage = 1;
							Stalker stalker = (Stalker) entity;
							if (EnchantmentHelper.getItemEnchantmentLevel(SCEnchants.STALKING.get(), stack) > 0) {
								if (stalker.getStalkingEntity() == trackingEntity) {
									stalker.setShouldBeStalking(true);
									damage = 10;
								}
							}
							if (world.getGameTime() % 20 == 0 && stack.isDamageableItem()) {
								ServerPlayer player = (ServerPlayer) entity;
								if (!player.getAbilities().instabuild) {
									int maxDamage = stack.getMaxDamage() - 1;
									stack.hurt(damage, player.getRandom(), player);
									stack.setDamageValue(Mth.clamp(stack.getDamageValue(), 0, maxDamage));
									if (stack.getDamageValue() == maxDamage) {
										player.playNotifySound(SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.5F, 1.5F);
									}
								}
							}
						}

						tag.put(ENTITY_TAG, EntityStatusData.write(trackingEntity));

						CompoundTag persistantData = trackingEntity.getPersistentData();
						persistantData.putBoolean(SCEvents.TAG_CHUNK_UPDATE, true);
						persistantData.putInt(SCEvents.TAG_CHUNK_TIMER, 20);

						if (EnchantmentHelper.getItemEnchantmentLevel(SCEnchants.PERSISTENCE.get(), stack) > 0 && trackingEntity instanceof Mob) {
							((Mob) trackingEntity).setPersistenceRequired();
						}
					} else if (tag.contains(ENTITY_TAG)) {
						EntityStatusData data = EntityStatusData.read(tag.getCompound(ENTITY_TAG));
						ChunkPos chunkpos = new ChunkPos(data.pos);
						if (!SCEvents.isChunkForced((ServerLevel) world, chunkpos)) {
							world.getChunkSource().updateChunkForced(chunkpos, false);
						}

						tag.put(ENTITY_TAG, EntityStatusData.writeMissingEntity(data));
					}

					if (tag.contains(ROTATIONS_TAG)) {
						RotationData rotations = RotationData.read(tag.getCompound(ROTATIONS_TAG));

						double turn;
						if (tag.contains(ENTITY_TAG)) {
							double yaw = entity.getYRot();
							yaw = positiveModulo(yaw / 360.0D, 1.0D);
							double angle = this.getAngleToTrackedEntity(stack, entity) / (double) ((float) Math.PI * 2F);
							turn = 0.5D - (yaw - 0.25D - angle);
						} else {
							turn = Math.random();
						}

						Pair<Long, double[]> rotationData = this.wobble(world, turn, rotations.lastUpdateTick, rotations.rotation, rotations.rota);
						rotations = new RotationData(rotationData.getSecond()[0], rotationData.getSecond()[1], rotationData.getFirst());

						tag.put(ROTATIONS_TAG, RotationData.write(rotations));
					} else {
						RotationData rotations = new RotationData(0.0F, 0.0F, 0L);

						double turn;
						if (tag.contains(ENTITY_TAG)) {
							double yaw = entity.getYRot();
							yaw = positiveModulo(yaw / 360.0D, 1.0D);
							double angle = this.getAngleToTrackedEntity(stack, entity) / (double) ((float) Math.PI * 2F);
							turn = 0.5D - (yaw - 0.25D - angle);
						} else {
							turn = Math.random();
						}

						Pair<Long, double[]> rotationData = this.wobble(world, turn, rotations.lastUpdateTick, rotations.rotation, rotations.rota);
						rotations = new RotationData(rotationData.getSecond()[0], rotationData.getSecond()[1], rotationData.getFirst());

						tag.put(ROTATIONS_TAG, RotationData.write(rotations));
					}
				}
			}
		}
	}
	
	@OnlyIn(Dist.CLIENT)
	@Override
	public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag flagIn) {
		CompoundTag tag = stack.getTag();
		if (SeekerCompassItem.isNotBroken(stack) && tag != null && !tag.getBoolean(TRACKING_ONLY) && tag.contains(TRACKING_TAG) && tag.contains(ENTITY_TAG)) {
			EntityStatusData status = EntityStatusData.read(tag.getCompound(ENTITY_TAG));
			
			tooltip.add(Component.translatable("tooltip.seeker_compass.tracking_entity"));
			
			tooltip.add((Component.translatable("tooltip.seeker_compass.entity_type").withStyle(ChatFormatting.GRAY)).append(Component.literal(I18n.get(status.entityType))));
			tooltip.add((Component.translatable("tooltip.seeker_compass.entity_name").withStyle(ChatFormatting.GRAY)).append(Component.literal(status.entityName)));
			
			boolean alive = status.isAlive;
			ChatFormatting color = alive ? ChatFormatting.GREEN : ChatFormatting.RED;
			String aliveString = String.valueOf(alive); 
			aliveString = aliveString.substring(0,1).toUpperCase() + aliveString.substring(1).toLowerCase();
			
			tooltip.add((Component.translatable("tooltip.seeker_compass.alive").withStyle(ChatFormatting.GRAY)).append(Component.literal(aliveString).withStyle(color)));
			
			tooltip.add((Component.translatable("tooltip.seeker_compass.health").withStyle(ChatFormatting.GRAY)).append(Component.literal(String.valueOf(status.health)).withStyle(ChatFormatting.GREEN)));
		
			if (EnchantmentHelper.getItemEnchantmentLevel(SCEnchants.TRACKING.get(), stack) > 0) {
				tooltip.add((Component.translatable("tooltip.seeker_compass.blockpos").withStyle(ChatFormatting.GRAY)).append(Component.literal(status.pos.toShortString())));
				tooltip.add((Component.translatable("tooltip.seeker_compass.standing_on").withStyle(ChatFormatting.GRAY)).append(Component.literal(I18n.get(world.getBlockState(status.pos.below()).getBlock().getDescriptionId()))));
				ResourceLocation biomeKey = world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getKey(world.getBiome(status.pos).get());
				tooltip.add(Component.translatable("tooltip.seeker_compass.biome").withStyle(ChatFormatting.GRAY).append(Component.translatable(biomeKey != null ? "biome." + biomeKey.getNamespace() + "." + biomeKey.getPath() : "Unknown")));
			}
		}
	}
	
	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		CompoundTag tag = stack.getTag();
		boolean hasTag = tag != null;
		if (hasTag && tag.getBoolean(TRACKING_ONLY)) return InteractionResultHolder.fail(stack);
		if (isNotBroken(stack) && hasTag && tag.contains(TRACKING_TAG) && !getTargetEntity(player, 8).isPresent()) {
			int level = EnchantmentHelper.getItemEnchantmentLevel(SCEnchants.VOODOO.get(), stack);
			if (level > 0) {
				if (tag.contains(VOODOO_TAG) && getVoodooData(tag).timer > 0 && !player.isCreative()) {
					if (!world.isClientSide) {
						player.sendSystemMessage(Component.translatable("message.seeker_compass.voodoo_cooldown").append((Component.literal(String.valueOf(getVoodooData(tag).timer)).withStyle(ChatFormatting.GOLD))));
					}
					return InteractionResultHolder.fail(stack);
				}
				
				if (world instanceof ServerLevel) {
					Entity entity = this.getEntity((ServerLevel) world, stack);
					if (entity != null && entity.hurt(DamageSource.playerAttack(player).bypassArmor().setMagic(), 1.5F + level)) {
						SCCriteriaTriggers.VOODOO_MAGIC.trigger((ServerPlayer) player);
							
						RandomSource rand = player.getRandom();
						Vec3 targetPosition = entity.position();
						for (int i = 0; i < 8; i++) {
							Vec3 position = targetPosition.add(rand.nextBoolean() ? -rand.nextFloat() : rand.nextFloat() * 1.25F, entity.getEyeHeight(), rand.nextBoolean() ? -rand.nextFloat() : rand.nextFloat() * 1.25F);
							Vec3 motion = position.subtract(targetPosition.add(0.0F, entity.getEyeHeight() * 0.35F, 0.0F)).scale(-0.5F);
							SeekerCompass.CHANNEL.send(PacketDistributor.ALL.with(() -> null), new S2CParticleMessage("seeker_compass:seeker_eyes", position.x(), position.y(), position.z(), motion.x(), motion.y(), motion.z()));
						}

						if (!player.isCreative()) {
							int damage = Mth.clamp(stack.getDamageValue() + 400, 0, stack.getMaxDamage() - 1);
							stack.setDamageValue(damage);

							if (damage == stack.getMaxDamage() - 1) {
								player.playNotifySound(SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.5F, 1.5F);
							}

							VoodooData data = getVoodooData(tag);
							int newTimesUsed = data.timesUsed + 1;
							if (newTimesUsed >= 9) {
								stack.getTag().put(VOODOO_TAG, VoodooData.write(new VoodooData(9, 12000)));
							} else {
								stack.getTag().put(VOODOO_TAG, VoodooData.write(new VoodooData(newTimesUsed, 0)));
							}
						}
					}
				}
				return InteractionResultHolder.consume(stack);
			} else if (EnchantmentHelper.getItemEnchantmentLevel(SCEnchants.WARPING.get(), stack) > 0) {
				if (world instanceof ServerLevel) {
					Entity entity = this.getEntity((ServerLevel) world, stack);

					if (entity != null) {
						Vec3 pos = entity.position();
						double x = pos.x();
						double y = pos.y();
						double z = pos.z();

						if (player.randomTeleport(x, y, z, false)) {
							player.fallDistance = 0.0F;
							world.playSound(null, x, y, z, SoundEvents.SHULKER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
							SeekerCompass.CHANNEL.send(PacketDistributor.ALL.with(() -> null), new S2CParticleMessage("seeker_compass:seeker_warp", player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F, 0.0F));

							if (!player.isCreative()) {
								if (player.getRandom().nextFloat() < SCConfig.COMMON.warpLoseCompassChance) {
									stack.shrink(1);
								}
								stack.setDamageValue(stack.getMaxDamage());
							}

							return InteractionResultHolder.success(stack);
						}
					}
				}
			} else if (EnchantmentHelper.getItemEnchantmentLevel(SCEnchants.STALKING.get(), stack) > 0) {
				if (world instanceof ServerLevel) {
					Entity entity = this.getEntity((ServerLevel) world, stack);
					if (entity instanceof LivingEntity) {
						Stalker stalker = (Stalker) player;
						if (stalker.isStalking()) {
							stalker.setStalkingEntity(null);
						} else {
							stalker.setStalkingEntity((LivingEntity) entity);
						}
					}
				}
			}
		}
		return super.use(world, player, hand);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		ItemStack stack = context.getItemInHand();
		CompoundTag tag = stack.getTag();
		boolean hasTag = tag != null;
		if (hasTag && tag.getBoolean(TRACKING_ONLY)) return InteractionResult.FAIL;
		Player player = context.getPlayer();
		Level world = context.getLevel();
		BlockPos placingPos = context.getClickedPos().above();
		if (isNotBroken(stack) && EnchantmentHelper.getItemEnchantmentLevel(SCEnchants.SUMMONING.get(), stack) > 0 && hasTag && tag.contains(TRACKING_TAG)) {
			if (world instanceof ServerLevel) {
				Entity trackedEntity = this.getEntity((ServerLevel) world, stack);
				if (SCConfig.COMMON.allSummoningCompass || trackedEntity instanceof TamableAnimal || trackedEntity.getType().is(SCTags.EntityTags.SUMMONABLES)) {
					if (((LivingEntity) trackedEntity).randomTeleport(placingPos.getX() + 0.5F, placingPos.getY(), placingPos.getZ() + 0.5F, false)) {
						world.playSound(null, placingPos.getX(), placingPos.getY(), placingPos.getZ(), SoundEvents.SHULKER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
						SeekerCompass.CHANNEL.send(PacketDistributor.ALL.with(() -> null), new S2CParticleMessage("seeker_compass:seeker_warp", trackedEntity.getX(), trackedEntity.getY(), trackedEntity.getZ(), 0.0F, 0.0F, 0.0F));

						if (!player.isCreative()) {
							int damage = Mth.clamp(stack.getDamageValue() + 300, 0, stack.getMaxDamage() - 1);
							stack.setDamageValue(damage);

							if (damage == stack.getMaxDamage() - 1) {
								player.playNotifySound(SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.5F, 1.5F);
							}
						}
						return InteractionResult.SUCCESS;
					}
				}
			}
		} else {
			if (tag == null || !tag.contains(TRACKING_TAG)) {
				boolean creative = player.isCreative();
				if (world.getBlockState(placingPos.below()).getBlock() == Blocks.OBSIDIAN && (player.experienceLevel >= 10 || creative)) {
					if (!creative) {
						player.experienceLevel -= 10;
					}

					world.playSound(null, placingPos.getX(), placingPos.getY(), placingPos.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.75F, 1.0F);

					if (world instanceof ServerLevel) {
						ServerLevel serverLevel = (ServerLevel) world;
						SCCriteriaTriggers.JOHN_CENA.trigger((ServerPlayer) player);

						for (ServerPlayer players : serverLevel.players()) {
							for (int i = 0; i < players.getInventory().getContainerSize(); i++) {
								ItemStack itemstack = players.getInventory().getItem(i);
								if (!itemstack.isEmpty() && itemstack.getItem() == this && itemstack.hasTag() && tag.contains(TRACKING_TAG) && player == this.getEntity(serverLevel, itemstack)) {
									tag.remove(TRACKING_TAG);
									tag.remove(ENTITY_TAG);
									tag.remove(ROTATIONS_TAG);

									RandomSource rand = player.getRandom();
									Vec3 targetPosition = players.position();
									for (int i2 = 0; i2 < 8; i2++) {
										Vec3 position = targetPosition.add(rand.nextBoolean() ? -rand.nextFloat() : rand.nextFloat() * 1.25F, players.getEyeHeight(), rand.nextBoolean() ? -rand.nextFloat() : rand.nextFloat() * 1.25F);
										Vec3 motion = targetPosition.subtract(position.add(0.0F, players.getEyeHeight() * 0.35F, 0.0F)).scale(-0.5F);
										SeekerCompass.CHANNEL.send(PacketDistributor.ALL.with(() -> null), new S2CParticleMessage("seeker_compass:seeker_eyes", targetPosition.x(), targetPosition.y(), targetPosition.z(), motion.x(), motion.y(), motion.z()));
									}

									player.level.playSound(null, players.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.75F, 1.5F);
								}
							}
						}
					}
					stack.shrink(1);
				}
			}
		}
		return super.useOn(context);
	}

	@Override
	public void fillItemCategory(CreativeModeTab group, NonNullList<ItemStack> stacks) {
		FILLER.fillItem(this, group, stacks);
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return !stack.hasTag() || !stack.getTag().getBoolean(TRACKING_ONLY);
	}

	@Override
	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		return false;
	}

	@Override
	public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
		return repair.getItem() == Items.MAGMA_CREAM;
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 16743936;
	}

	public static double positiveModulo(double numerator, double denominator) {
		return (numerator % denominator + denominator) % denominator;
	}

	public static boolean isNotBroken(ItemStack stack) {
		return stack.getDamageValue() < stack.getMaxDamage() - 1;
	}

	private Entity getEntity(ServerLevel world, ItemStack stack) {
		return world.getEntity(NbtUtils.loadUUID(stack.getTag().get(TRACKING_TAG)));
	}

	private static VoodooData getVoodooData(CompoundTag tag) {
		return VoodooData.read((tag).getCompound(VOODOO_TAG));
	}

	private double getAngleToTrackedEntity(ItemStack stack, Entity entity) {
		EntityStatusData data = EntityStatusData.read(stack.getTag().getCompound(ENTITY_TAG));
		BlockPos pos = data.pos;
		return Math.atan2((double) pos.getZ() - entity.getZ(), (double) pos.getX() - entity.getX());
	}
	
	private Pair<Long, double[]> wobble(Level world, double angle, long lastUpdateTickIn, double rotationIn, double rotaIn) {
		long lastUpdateTick = lastUpdateTickIn;
		double rotation = rotationIn;
		double rota = rotaIn;
		
		if(world.getGameTime() != lastUpdateTick) {
			lastUpdateTick = world.getGameTime();
			double d0 = angle - rotation;
			d0 = positiveModulo(d0 + 0.5D, 1.0D) - 0.5D;
			rota += d0 * 0.1D;
			rota *= 0.8D;
			rotation = positiveModulo(rotation + rota, 1.0D);
		}

		return Pair.of(lastUpdateTick, new double[] {rotation, rota});
	}
	
	private static Optional<Entity> getTargetEntity(Entity entityIn, int distance) {
		Vec3 Vec3 = entityIn.getEyePosition(1.0F);
		Vec3 Vec31 = entityIn.getViewVector(1.0F).scale(distance);
		Vec3 Vec32 = Vec3.add(Vec31);
		AABB axisalignedbb = entityIn.getBoundingBox().expandTowards(Vec31).inflate(1.0D);
		int i = distance * distance;
		Predicate<Entity> predicate = (p_217727_0_) -> !p_217727_0_.isSpectator() && p_217727_0_.canBeCollidedWith();
		EntityHitResult entityraytraceresult = rayTraceEntities(entityIn, Vec3, Vec32, axisalignedbb, predicate, i);
		if (entityraytraceresult == null) {
			return Optional.empty();
		} else {
			return Vec3.distanceToSqr(entityraytraceresult.getLocation()) > (double)i ? Optional.empty() : Optional.of(entityraytraceresult.getEntity());
		}
	}
	
	@Nullable
	private static EntityHitResult rayTraceEntities(Entity player, Vec3 p_221273_1_, Vec3 p_221273_2_, AABB p_221273_3_, Predicate<Entity> p_221273_4_, double p_221273_5_) {
		Level world = player.level;
		double d0 = p_221273_5_;
		Entity entity = null;
		Vec3 vector3d = null;

		for (Entity entity1 : world.getEntities(player, p_221273_3_)) {
			AABB axisalignedbb = entity1.getBoundingBox().inflate(entity1.getPickRadius());
			Optional<Vec3> optional = axisalignedbb.clip(p_221273_1_, p_221273_2_);
			if (axisalignedbb.contains(p_221273_1_)) {
				if (d0 >= 0.0D) {
					entity = entity1;
					vector3d = optional.orElse(p_221273_1_);
					d0 = 0.0D;
				}
			} else if (optional.isPresent()) {
				Vec3 vector3d1 = optional.get();
				double d1 = p_221273_1_.distanceToSqr(vector3d1);
				if (d1 < d0 || d0 == 0.0D) {
					if (entity1.getRootVehicle() == player.getRootVehicle() && !entity1.canRiderInteract()) {
						if (d0 == 0.0D) {
							entity = entity1;
							vector3d = vector3d1;
						}
					} else {
						entity = entity1;
						vector3d = vector3d1;
						d0 = d1;
					}
				}
			}
		}

		return entity == null ? null : new EntityHitResult(entity, vector3d);
	}

	static class EntityStatusData {
		public final boolean isAlive;
		public final float health;
		public final String entityType;
		public final String entityName;
		public final BlockPos pos;
		
		public EntityStatusData(boolean isAlive, float health, String entityType, String entityName, BlockPos pos) {
			this.isAlive = isAlive;
			this.health = health;
			this.entityType = entityType;
			this.entityName = entityName;
			this.pos = pos;
		}
		
		public static EntityStatusData read(CompoundTag compound) {
			return new EntityStatusData(compound.getBoolean("Alive"), compound.getFloat("Health"), compound.getString("EntityType"), compound.getString("EntityName"), NbtUtils.readBlockPos(compound.getCompound("Pos")));
		}
		
		public static CompoundTag write(Entity trackingEntity) {
			CompoundTag tag = new CompoundTag();
			tag.putBoolean("Alive", trackingEntity.isAlive());
			tag.putString("EntityType", trackingEntity.getType().getDescriptionId());
			tag.putString("EntityName", trackingEntity.getName().getString());
			
			if (trackingEntity instanceof LivingEntity) {
				tag.putFloat("Health", ((LivingEntity) trackingEntity).getHealth());
			}
			tag.put("Pos", NbtUtils.writeBlockPos(trackingEntity.blockPosition()));
			return tag;
		}
		
		public static CompoundTag writeMissingEntity(EntityStatusData status) {
			CompoundTag tag = new CompoundTag();
			tag.putBoolean("Alive", false);
			tag.putString("EntityType", status.entityType);
			tag.putString("EntityName", status.entityName);
			tag.putFloat("Health", 0.0F);
			tag.put("Pos", NbtUtils.writeBlockPos(status.pos));
			return tag;
		}
	}
	
	static class RotationData {
		public final double rotation;
		private final double rota;
		private final long lastUpdateTick;
		
		public RotationData(double rotation, double rota, long lastUpdateTick) {
			this.rotation = rotation;
			this.rota = rota;
			this.lastUpdateTick = lastUpdateTick;
		}
		
		public static RotationData read(CompoundTag compound) {
			return new RotationData(compound.getDouble("Rotation"), compound.getDouble("Rota"), compound.getLong("LastUpdateTick"));
		}
		
		public static CompoundTag write(RotationData data) {
			CompoundTag tag = new CompoundTag();
			tag.putDouble("Rotation", data.rotation);
			tag.putDouble("Rota", data.rota);
			tag.putLong("LastUpdateTick", data.lastUpdateTick);
			return tag;
		}
	}
	
	static class VoodooData {
		public final int timesUsed;
		public final int timer;
		
		public VoodooData(int timesUsed, int timer) {
			this.timer = timer;
			this.timesUsed = timesUsed;
		}
		
		public static VoodooData read(CompoundTag compound) {
			return new VoodooData(compound.getInt("TimesUsed"), compound.getInt("Timer"));
		}
		
		public static CompoundTag write(VoodooData data) {
			CompoundTag tag = new CompoundTag();
			tag.putInt("TimesUsed", data.timesUsed);
			tag.putInt("Timer", data.timer);
			return tag;
		}
	}
}