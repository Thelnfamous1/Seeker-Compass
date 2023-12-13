package net.smelly.seekercompass.modification.modifiers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import com.teamabnormals.blueprint.common.loot.modification.modifiers.LootModifier;
import com.teamabnormals.blueprint.core.util.modification.ObjectModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.PredicateManager;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Supplier;

import static com.teamabnormals.blueprint.common.loot.modification.modifiers.LootPoolEntriesModifier.ENTRIES;
import static com.teamabnormals.blueprint.common.loot.modification.modifiers.LootPoolsModifier.POOLS;

public final class BiasedItemWeightModifier implements LootModifier<BiasedItemWeightModifier> {
	private static final Field LOOT_ENTRY_ITEM = ObfuscationReflectionHelper.findField(LootItem.class, "f_79564_");
	private static final Field WEIGHT = ObfuscationReflectionHelper.findField(LootPoolSingletonContainer.class, "f_79675_");


	private final int index;
	private final int bias;
	private final Supplier<Item> biasedItem;
	
	public BiasedItemWeightModifier(int index, int bias, Supplier<Item> biasedItem) {
		this.index = index;
		this.bias = bias;
		this.biasedItem = biasedItem;
	}
	
	@Override
	public void modify(LootTableLoadEvent event) {
		try {
			LootPoolEntryContainer[] lootEntries = (LootPoolEntryContainer[]) ENTRIES.get(((List<LootPool>) POOLS.get(event.getTable())).get(this.index));
			Item biasedItem = this.biasedItem.get();
			int bias = this.bias;
			for(LootPoolEntryContainer lootEntry : lootEntries){
				if (lootEntry instanceof LootItem itemLootPoolEntryContainer) {
					try {
						Item item = (Item) LOOT_ENTRY_ITEM.get(itemLootPoolEntryContainer);
						if (item != biasedItem) {
							WEIGHT.set(itemLootPoolEntryContainer, (int) WEIGHT.get(itemLootPoolEntryContainer) + bias);
						}
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					}
				}
			}
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	@Override
	public ObjectModifier.Serializer<BiasedItemWeightModifier, Gson, Pair<Gson, PredicateManager>> getSerializer() {
		return SCLootModifiers.BIASED_ITEM_WEIGHT_MODIFIER;
	}

	public static final class Serializer implements LootModifier.Serializer<BiasedItemWeightModifier> {

		@Override
		public JsonElement serialize(BiasedItemWeightModifier config, Gson gson) throws JsonParseException {
			JsonObject jsonObject = new JsonObject();
			jsonObject.addProperty("index", config.index);
			jsonObject.addProperty("bias", config.bias);
			jsonObject.addProperty("biased_item", String.valueOf(ForgeRegistries.ITEMS.getKey(config.biasedItem.get())));
			return jsonObject;
		}

		@Override
		public BiasedItemWeightModifier deserialize(JsonElement jsonElement, Pair<Gson, PredicateManager> gsonPredicateManagerPair) throws JsonParseException {
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			String itemName = jsonObject.get("biased_item").getAsString();
			Item biasedItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));
			if (biasedItem == null) {
				throw new JsonParseException("Unknown item: " + itemName);
			}
			return new BiasedItemWeightModifier(jsonObject.get("index").getAsInt(), jsonObject.get("bias").getAsInt(), () -> biasedItem);
		}
	}
}
