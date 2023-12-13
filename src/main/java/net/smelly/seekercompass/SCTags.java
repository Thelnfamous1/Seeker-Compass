package net.smelly.seekercompass;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

public class SCTags {
	
	public static class EntityTags {
		public static final TagKey<EntityType<?>> SUMMONABLES = createTag("summonables");
		
		public static TagKey<EntityType<?>> createTag(String name) {
			return TagKey.create(Registry.ENTITY_TYPE_REGISTRY, new ResourceLocation(SeekerCompass.MOD_ID, name));
		}
	}

}