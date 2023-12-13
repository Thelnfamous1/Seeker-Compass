package net.smelly.seekercompass.interfaces;

import net.minecraft.world.entity.player.Player;

public interface Stalkable{
	void addStalker(Player player);

	void removeStalker(Player player);

	boolean hasStalkers();

	boolean isBeingStalkedBy(Player player);
}
