package net.smelly.seekercompass;

import com.teamabnormals.blueprint.common.loot.modification.LootModifierProvider;
import com.teamabnormals.blueprint.common.loot.modification.modifiers.LootPoolEntriesModifier;
import com.teamabnormals.blueprint.common.world.storage.tracking.DataProcessors;
import com.teamabnormals.blueprint.common.world.storage.tracking.SyncType;
import com.teamabnormals.blueprint.common.world.storage.tracking.TrackedData;
import com.teamabnormals.blueprint.common.world.storage.tracking.TrackedDataManager;
import com.teamabnormals.blueprint.core.util.DataUtil;
import com.teamabnormals.blueprint.core.util.modification.selection.ConditionedResourceSelector;
import com.teamabnormals.blueprint.core.util.modification.selection.selectors.EmptyResourceSelector;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.data.DataGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemDamageFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.smelly.seekercompass.enchants.SCEnchants;
import net.smelly.seekercompass.modification.modifiers.BiasedItemWeightModifier;
import net.smelly.seekercompass.modification.modifiers.SCLootModifiers;
import net.smelly.seekercompass.network.C2SStopStalkingMessage;
import net.smelly.seekercompass.network.S2CParticleMessage;
import net.smelly.seekercompass.network.S2CUpdateStalkedMessage;
import net.smelly.seekercompass.network.S2CUpdateStalkerMessage;
import net.smelly.seekercompass.particles.SCParticles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Collections;

/**
 * @author SmellyModder(Luke Tonon)
 */
@SuppressWarnings("deprecation")
@Mod(value = SeekerCompass.MOD_ID)
public class SeekerCompass {
	public static final String MOD_ID = "seeker_compass";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID.toUpperCase());
	public static final String NETWORK_PROTOCOL = "SC1";
	public static final TrackedData<Boolean> BEING_STALKED = TrackedData.Builder.create(DataProcessors.BOOLEAN, () -> false).setSyncType(SyncType.TO_CLIENTS).build();
	public static SeekerCompass instance;

	public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(new ResourceLocation(MOD_ID, "net"))
			.networkProtocolVersion(() -> NETWORK_PROTOCOL)
			.clientAcceptedVersions(NETWORK_PROTOCOL::equals)
			.serverAcceptedVersions(NETWORK_PROTOCOL::equals)
			.simpleChannel();

	public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
	public static final RegistryObject<Item> SEEKER_COMPASS = ITEMS.register("seeker_compass", () -> new SeekerCompassItem((new Item.Properties()).stacksTo(1).durability(1200).rarity(Rarity.UNCOMMON).tab(CreativeModeTab.TAB_TOOLS)));

	public SeekerCompass() {
		instance = this;

		CHANNEL.registerMessage(0, S2CParticleMessage.class, S2CParticleMessage::serialize, S2CParticleMessage::deserialize, S2CParticleMessage::handle);
		CHANNEL.registerMessage(1, S2CUpdateStalkerMessage.class, S2CUpdateStalkerMessage::serialize, S2CUpdateStalkerMessage::deserialize, S2CUpdateStalkerMessage::handle);
		CHANNEL.registerMessage(2, C2SStopStalkingMessage.class, C2SStopStalkingMessage::serialize, C2SStopStalkingMessage::deserialize, C2SStopStalkingMessage::handle);
		CHANNEL.registerMessage(3, S2CUpdateStalkedMessage.class, S2CUpdateStalkedMessage::serialize, S2CUpdateStalkedMessage::deserialize, S2CUpdateStalkedMessage::handle);

		final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		ITEMS.register(modEventBus);
		SCEnchants.ENCHANTMENTS.register(modEventBus);
		SCParticles.register(modEventBus);
		SCLootModifiers.load();

		modEventBus.addListener(this::setupCommon);
		modEventBus.addListener(this::onGatherData);

		modEventBus.addListener((ModConfigEvent event) -> {
			IConfigSpec<?> spec = event.getConfig().getSpec();
			if (spec == SCConfig.COMMON_SPEC) {
				SCConfig.COMMON.load();
			} else if (spec == SCConfig.CLIENT_SPEC) {
				SCConfig.CLIENT.load();
			}
		});

		ModLoadingContext context = ModLoadingContext.get();
		context.registerConfig(ModConfig.Type.CLIENT, SCConfig.CLIENT_SPEC);
		context.registerConfig(ModConfig.Type.COMMON, SCConfig.COMMON_SPEC);

		DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
			modEventBus.addListener(EventPriority.LOWEST, this::setupClient);
		});

		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> StalkerEyeHandler::registerOverlay);
	}

	private void setupCommon(final FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			//DataUtil.add(CreativeModeTab.TAB_TOOLS.getEnchantmentCategories(), SCEnchants.SEEKER_COMPASS);
			CreativeModeTab.TAB_TOOLS.setEnchantmentCategories(
					DataUtil.concatArrays(CreativeModeTab.TAB_TOOLS.getEnchantmentCategories(), SCEnchants.SEEKER_COMPASS));
			TrackedDataManager.INSTANCE.registerData(new ResourceLocation(MOD_ID, "being_stalked"),
					BEING_STALKED);
		});
	}

	private void onGatherData(GatherDataEvent event) {
		DataGenerator dataGenerator = event.getGenerator();
		if (event.includeServer()) {
			dataGenerator.addProvider(true, createLootModifierDataProvider(dataGenerator));
		}
	}

	@OnlyIn(Dist.CLIENT)
	private void setupClient(final FMLClientSetupEvent event) {
		event.enqueueWork(() -> {
			ItemProperties.register(SEEKER_COMPASS.get(), new ResourceLocation("angle"), new ItemPropertyFunction() {
				private double rotation;
				private double rota;
				private long lastUpdateTick;

				@OnlyIn(Dist.CLIENT)
				public float call(ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity livingEntity, int seed) {
					if (!SeekerCompassItem.isNotBroken(stack)) {
						return 0.0F;
					} else {
						if (livingEntity == null && !stack.isFramed()) {
							return 0.484375F;
						} else {
							boolean flag = livingEntity != null;
							Entity entity = flag ? livingEntity : stack.getFrame();
							if (world == null) {
								world = (ClientLevel) entity.level;
							}

							CompoundTag tag = stack.getTag();
							if (tag != null && tag.contains("Rotations") && tag.contains("EntityStatus") && !stack.isFramed()) {
								return (float) SeekerCompassItem.positiveModulo(SeekerCompassItem.RotationData.read(tag.getCompound("Rotations")).rotation, 1.0F);
							} else {
								double randRotation = Math.random();

								if (flag) {
									randRotation = this.wobble(world, randRotation);
								}

								return (float) SeekerCompassItem.positiveModulo((float) randRotation, 1.0F);
							}
						}
					}
				}

				@OnlyIn(Dist.CLIENT)
				private double wobble(ClientLevel world, double rotation) {
					if (world.getGameTime() != this.lastUpdateTick) {
						this.lastUpdateTick = world.getGameTime();
						double d0 = rotation - this.rotation;
						d0 = SeekerCompassItem.positiveModulo(d0 + 0.5D, 1.0D) - 0.5D;
						this.rota += d0 * 0.1D;
						this.rota *= 0.8D;
						this.rotation = SeekerCompassItem.positiveModulo(this.rotation + this.rota, 1.0D);
					}

					return this.rotation;
				}
			});
			ItemProperties.register(SEEKER_COMPASS.get(), new ResourceLocation("broken"), (stack, world, entity, seed) -> SeekerCompassItem.isNotBroken(stack) ? 0.0F : 1.0F);
		});
	}

	private static LootModifierProvider createLootModifierDataProvider(DataGenerator dataGenerator) {
		return new LootModifierProvider(dataGenerator, MOD_ID) {
			/*
			new ModifierDataProvider.ProviderEntry<>(
						new TargetedModifier<>(
								new ResourceLocation("gameplay/fishing/treasure"),
								Arrays.asList(
										new ConfiguredModifier<>(LootModifiers.ENTRIES_MODIFIER, new LootPoolEntriesModifier(false, 0, Collections.singletonList(LootItem.lootTableItem(SEEKER_COMPASS.get()).apply(SetItemDamageFunction.setDamage(ConstantValue.exactly(0.0F))).build()))),
										new ConfiguredModifier<>(SCLootModifiers.BIASED_ITEM_WEIGHT_MODIFIER, new BiasedItemWeightModifier(0, 1, SEEKER_COMPASS))
								)
						)
				),
				new ModifierDataProvider.ProviderEntry<>(
						new TargetedModifier<>(
								new ResourceLocation("chests/nether_bridge"),
								Collections.singletonList(new ConfiguredModifier<>(LootModifiers.ENTRIES_MODIFIER, new LootPoolEntriesModifier(false, 0, Collections.singletonList(LootItem.lootTableItem(SEEKER_COMPASS.get()).apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 1.0F))).build()))))
						)
				)
			 */
			@Override
			protected void registerEntries() {
				ConditionedResourceSelector empty = new ConditionedResourceSelector(new EmptyResourceSelector(), new ICondition[0]);
				this.entry("gameplay/fishing/treasure")
						.selector(empty)
						.addModifier(new LootPoolEntriesModifier(false, 0, Collections.singletonList(LootItem.lootTableItem(SEEKER_COMPASS.get()).apply(SetItemDamageFunction.setDamage(ConstantValue.exactly(0.0F))).build())))
						.addModifier(new BiasedItemWeightModifier(0, 1, SEEKER_COMPASS));
				this.entry("chests/nether_bridge")
						.selector(empty)
						.addModifier(new LootPoolEntriesModifier(false, 0, Collections.singletonList(LootItem.lootTableItem(SEEKER_COMPASS.get()).apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 1.0F))).build())));
			}
		};
	}
}