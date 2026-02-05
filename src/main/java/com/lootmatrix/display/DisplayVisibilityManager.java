package com.lootmatrix.display;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 展示实体可见性管理器
 *
 * 实现原理：
 * 1. 使用 ClientboundRemoveEntitiesPacket 对不应看到实体的玩家隐藏实体
 * 2. 使用 ClientboundAddEntityPacket + ClientboundSetEntityDataPacket 对应看到的玩家显示实体
 * 3. 通过 Mixin 拦截实体追踪，阻止向未授权玩家发送实体数据
 *
 * 这是纯服务端实现，客户端不需要安装任何模组
 */
public class DisplayVisibilityManager {

    // 存储每个展示实体的可见玩家列表: 实体ID -> 可见玩家UUID集合
    // 如果实体不在此 Map 中，表示对所有人可见（默认行为）
    // 如果实体在 Map 中但集合为空，表示对所有人不可见
    private static final Map<Integer, Set<UUID>> visibilityMap = new ConcurrentHashMap<>();

    // 存储被限制可见性的实体原始状态
    private static final Set<Integer> restrictedEntities = ConcurrentHashMap.newKeySet();

    /**
     * 设置展示实体只对指定玩家可见
     *
     * @param display 展示实体
     * @param viewers 可以看到的玩家列表
     */
    public static void setVisibleTo(Display display, Collection<ServerPlayer> viewers) {
        int entityId = display.getId();

        Set<UUID> visiblePlayers = visibilityMap.computeIfAbsent(entityId, k -> ConcurrentHashMap.newKeySet());
        visiblePlayers.clear();

        for (ServerPlayer viewer : viewers) {
            visiblePlayers.add(viewer.getUUID());
        }

        restrictedEntities.add(entityId);

        // 更新所有玩家的可见性
        if (display.level() instanceof ServerLevel serverLevel) {
            updateVisibilityForAllPlayers(display, serverLevel);
        }
    }

    /**
     * 添加可以看到展示实体的玩家
     *
     * @param display 展示实体
     * @param viewer  要添加的玩家
     */
    public static void addViewer(Display display, ServerPlayer viewer) {
        int entityId = display.getId();

        Set<UUID> visiblePlayers = visibilityMap.computeIfAbsent(entityId, k -> ConcurrentHashMap.newKeySet());
        visiblePlayers.add(viewer.getUUID());
        restrictedEntities.add(entityId);

        // 向该玩家显示实体
        showEntityToPlayer(display, viewer);
    }

    /**
     * 移除可以看到展示实体的玩家
     *
     * @param display 展示实体
     * @param viewer  要移除的玩家
     */
    public static void removeViewer(Display display, ServerPlayer viewer) {
        int entityId = display.getId();

        Set<UUID> visiblePlayers = visibilityMap.get(entityId);
        if (visiblePlayers != null) {
            visiblePlayers.remove(viewer.getUUID());
        }

        // 向该玩家隐藏实体
        hideEntityFromPlayer(display, viewer);
    }

    /**
     * 清除展示实体的可见性限制，恢复对所有人可见
     *
     * @param display 展示实体
     */
    public static void clearVisibility(Display display) {
        int entityId = display.getId();

        visibilityMap.remove(entityId);
        restrictedEntities.remove(entityId);

        // 向所有玩家显示实体
        if (display.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                showEntityToPlayer(display, player);
            }
        }
    }

    /**
     * 设置展示实体对所有人隐藏
     *
     * @param display 展示实体
     */
    public static void hideFromAll(Display display) {
        int entityId = display.getId();

        visibilityMap.put(entityId, ConcurrentHashMap.newKeySet());
        restrictedEntities.add(entityId);

        // 向所有玩家隐藏实体
        if (display.level() instanceof ServerLevel serverLevel) {
            for (ServerPlayer player : serverLevel.players()) {
                hideEntityFromPlayer(display, player);
            }
        }
    }

    /**
     * 检查玩家是否可以看到展示实体
     *
     * @param display 展示实体
     * @param player  玩家
     * @return 是否可见
     */
    public static boolean canPlayerSee(Display display, ServerPlayer player) {
        int entityId = display.getId();

        // 如果不在限制列表中，对所有人可见
        if (!restrictedEntities.contains(entityId)) {
            return true;
        }

        Set<UUID> visiblePlayers = visibilityMap.get(entityId);
        if (visiblePlayers == null) {
            return true; // 默认可见
        }

        return visiblePlayers.contains(player.getUUID());
    }

    /**
     * 检查实体是否有可见性限制
     */
    public static boolean hasVisibilityRestriction(Entity entity) {
        return restrictedEntities.contains(entity.getId());
    }

    /**
     * 检查实体是否是 Display 类型且有可见性限制
     */
    public static boolean isRestrictedDisplay(Entity entity) {
        return entity instanceof Display && restrictedEntities.contains(entity.getId());
    }

    /**
     * 检查玩家是否可以看到指定实体（供 Mixin 调用）
     */
    public static boolean canPlayerSeeEntity(int entityId, UUID playerId) {
        if (!restrictedEntities.contains(entityId)) {
            return true;
        }

        Set<UUID> visiblePlayers = visibilityMap.get(entityId);
        if (visiblePlayers == null) {
            return true;
        }

        return visiblePlayers.contains(playerId);
    }

    /**
     * 获取展示实体的可见玩家列表
     */
    public static Set<UUID> getViewers(Display display) {
        Set<UUID> viewers = visibilityMap.get(display.getId());
        return viewers != null ? new HashSet<>(viewers) : null;
    }

    /**
     * 向玩家显示实体
     */
    private static void showEntityToPlayer(Display display, ServerPlayer player) {
        // 检查玩家连接是否有效
        if (player.connection == null || player.hasDisconnected()) {
            return;
        }

        try {
            // 创建并发送添加实体包
            // 使用 ClientboundAddEntityPacket 的静态工厂方法
            ClientboundAddEntityPacket addPacket = new ClientboundAddEntityPacket(
                    display.getId(),
                    display.getUUID(),
                    display.getX(),
                    display.getY(),
                    display.getZ(),
                    display.getXRot(),
                    display.getYRot(),
                    display.getType(),
                    0, // 额外数据
                    display.getDeltaMovement(),
                    display.getYHeadRot()
            );
            player.connection.send(addPacket);

            // 发送实体数据包
            List<SynchedEntityData.DataValue<?>> entityData = display.getEntityData().getNonDefaultValues();
            if (entityData != null && !entityData.isEmpty()) {
                ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(
                        display.getId(), entityData
                );
                player.connection.send(dataPacket);
            }
        } catch (Exception e) {
            // 忽略发送失败的情况（玩家可能已断开连接）
        }
    }

    /**
     * 向玩家隐藏实体
     */
    private static void hideEntityFromPlayer(Display display, ServerPlayer player) {
        // 检查玩家连接是否有效
        if (player.connection == null || player.hasDisconnected()) {
            return;
        }

        try {
            ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(display.getId());
            player.connection.send(removePacket);
        } catch (Exception e) {
            // 忽略发送失败的情况（玩家可能已断开连接）
        }
    }

    /**
     * 更新所有玩家对某实体的可见性
     */
    private static void updateVisibilityForAllPlayers(Display display, ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (canPlayerSee(display, player)) {
                showEntityToPlayer(display, player);
            } else {
                hideEntityFromPlayer(display, player);
            }
        }
    }

    /**
     * 当玩家断开连接时清理数据
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID playerId = player.getUUID();
        // 从所有可见性列表中移除该玩家
        for (Set<UUID> viewers : visibilityMap.values()) {
            viewers.remove(playerId);
        }
    }

    /**
     * 当实体被移除时清理数据
     */
    public static void onEntityRemoved(Entity entity) {
        int entityId = entity.getId();
        visibilityMap.remove(entityId);
        restrictedEntities.remove(entityId);
    }

    /**
     * 当新玩家进入视野范围时检查可见性（供 Mixin 调用）
     * 返回 true 表示应该阻止默认的追踪行为
     */
    public static boolean shouldBlockTracking(Entity entity, ServerPlayer player) {
        if (!(entity instanceof Display display)) {
            return false;
        }

        return !canPlayerSee(display, player);
    }

    /**
     * 刷新所有受限展示实体的可见性
     */
    public static void refreshAllVisibility(net.minecraft.server.MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Integer entityId : restrictedEntities) {
                Entity entity = level.getEntity(entityId);
                if (entity instanceof Display display) {
                    updateVisibilityForAllPlayers(display, level);
                }
            }
        }
    }
}
