package com.lootmatrix;

import com.lootmatrix.command.CalculateDistance;
import com.lootmatrix.command.CommandRegister;
import com.lootmatrix.display.DisplayVisibilityEventHandler;
import com.lootmatrix.glow.GlowEventHandler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatapackExtension implements ModInitializer {

    public static final String MOD_ID = "datapack-extension";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);



	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

        // 注册指令
        CommandRegister.register();

        // 注册发光效果事件处理器
        GlowEventHandler.register();

        // 注册展示实体可见性事件处理器
        DisplayVisibilityEventHandler.register();

        // LOGGER.info("Hello Fabric world!");
	}
}