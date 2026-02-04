package com.lootmatrix.glow;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;

/**
 * 发光效果事件监听器
 *
 * 处理以下场景以避免不同步问题：
 * 1. 玩家断开连接 - 清理发光数据
 * 2. 实体被移除 - 清理发光数据
 * 3. 定期刷新发光效果 - 确保效果持续
 * 4. 服务器启动 - 初始化队伍
 */
public class GlowEventHandler {

    // 刷新间隔（tick）- 每秒刷新一次
    private static final int REFRESH_INTERVAL = 20;
    private static int tickCounter = 0;

    public static void register() {
        // 玩家断开连接时清理
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            GlowManager.onPlayerDisconnect(handler.getPlayer()));

        // 实体被移除时清理
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) ->
            GlowManager.onEntityRemoved(entity));

        // 服务器启动时初始化队伍
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
            GlowManager.initializeTeams(server.getScoreboard()));

        // 定期刷新发光效果（每秒一次）
        ServerTickEvents.END_SERVER_TICK.register(GlowEventHandler::onServerTick);

        // 玩家加入时刷新（处理重连场景）
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // 玩家重新连接后，他们之前设置的发光效果已被清除
            // 如果需要持久化，可以在这里从存储恢复
        });
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter >= REFRESH_INTERVAL) {
            tickCounter = 0;
            // 刷新所有发光效果
            GlowManager.refreshAllGlow(server);
        }
    }
}
