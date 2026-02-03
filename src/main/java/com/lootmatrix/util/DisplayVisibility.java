package com.lootmatrix.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;

import java.util.ArrayList;
import java.util.Set;

public final class DisplayVisibility {
	public static final String VISIBLE_TAG_PREFIX = "dpe_visible:";

	private DisplayVisibility() {
	}

	public static boolean hasVisibilityTags(Display display) {
		for (String tag : display.getTags()) {
			if (tag.startsWith(VISIBLE_TAG_PREFIX)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isVisibleTo(Display display, ServerPlayer player) {
		String tag = VISIBLE_TAG_PREFIX + player.getUUID();
		return display.getTags().contains(tag);
	}

	public static void clearVisibilityTags(Display display) {
		Set<String> tags = display.getTags();
		for (String tag : new ArrayList<>(tags)) {
			if (tag.startsWith(VISIBLE_TAG_PREFIX)) {
				display.removeTag(tag);
			}
		}
	}

	public static void addVisiblePlayer(Display display, ServerPlayer player) {
		display.addTag(VISIBLE_TAG_PREFIX + player.getUUID());
	}
}
