package net.smelly.seekercompass.modification.modifiers;

import com.teamabnormals.blueprint.common.loot.modification.LootModifierSerializers;

public final class SCLootModifiers {
	public static final BiasedItemWeightModifier.Serializer BIASED_ITEM_WEIGHT_MODIFIER = LootModifierSerializers.REGISTRY.register("seeker_compass:biased_item_weight", new BiasedItemWeightModifier.Serializer());

	public static void load() {}
}
