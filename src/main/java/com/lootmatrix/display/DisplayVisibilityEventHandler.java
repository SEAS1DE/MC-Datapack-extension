package com.lootmatrix.display;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;

/**
 * 展示实体可见性事件处理器
 *
 * 处理以下场景：
 * 1. 玩家断开连接 - 清理可见性数据
 * 2. 实体被移除 - 清理可见性数据
 * 3. 定期刷新可见性 - 确保效果持续
 */
public class DisplayVisibilityEventHandler {

    // 刷新间隔（tick）- 每2秒刷新一次
    private static final int REFRESH_INTERVAL = 40;
    private static int tickCounter = 0;

    public static void register() {
        // 玩家断开连接时清理
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            DisplayVisibilityManager.onPlayerDisconnect(handler.getPlayer()));

        // 实体被移除时清理
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) ->
            DisplayVisibilityManager.onEntityRemoved(entity));

        // 定期刷新可见性
        ServerTickEvents.END_SERVER_TICK.register(DisplayVisibilityEventHandler::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= REFRESH_INTERVAL) {
            tickCounter = 0;
            DisplayVisibilityManager.refreshAllVisibility(server);
        }
    }
}
