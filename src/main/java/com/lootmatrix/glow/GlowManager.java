package com.lootmatrix.glow;

import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端发光效果管理器
 *
 * 实现原理：
 * 1. 使用 SetEntityDataPacket 设置实体的 DATA_SHARED_FLAGS_ID 来启用发光效果
 * 2. 使用 SetPlayerTeamPacket 将实体"虚拟地"加入带颜色的队伍（仅对特定玩家发包）
 * 3. 通过 Mixin 拦截服务器发送的实体数据包，确保发光标志不被覆盖
 * 4. 不同玩家可以看到不同颜色，因为数据包是独立发送的
 *
 * 这是纯服务端实现，客户端不需要安装任何模组
 */
public class GlowManager {

    // 存储每个玩家看到的发光信息: 观察者UUID -> (目标实体ID -> 发光颜色)
    private static final Map<UUID, Map<Integer, GlowColor>> playerGlowMap = new ConcurrentHashMap<>();

    // 存储每个实体原始的发光状态，用于恢复
    private static final Map<Integer, Byte> originalFlags = new ConcurrentHashMap<>();

    // Entity的共享flags数据访问器索引 (Glowing flag = 0x40, index 0)
    private static final int SHARED_FLAGS_INDEX = 0;
    private static final byte GLOWING_FLAG = 0x40;

    /**
     * 为指定玩家添加对目标实体的发光效果
     *
     * @param viewer   观察者玩家
     * @param target   目标实体
     * @param color    发光颜色
     */
    public static void addGlow(ServerPlayer viewer, Entity target, GlowColor color) {
        UUID viewerId = viewer.getUUID();
        int targetId = target.getId();

        // 记录原始flags
        if (!originalFlags.containsKey(targetId)) {
            originalFlags.put(targetId, target.getEntityData().get(
                    getSharedFlagsAccessor()
            ));
        }

        // 更新内存中的发光记录
        playerGlowMap.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>())
                .put(targetId, color);

        // 发送发光效果数据包
        sendGlowingPacket(viewer, target, true);

        // 发送队伍颜色数据包
        sendTeamColorPacket(viewer, target, color);
    }

    /**
     * 移除指定玩家对目标实体的发光效果
     *
     * @param viewer 观察者玩家
     * @param target 目标实体
     */
    public static void removeGlow(ServerPlayer viewer, Entity target) {
        UUID viewerId = viewer.getUUID();
        int targetId = target.getId();

        Map<Integer, GlowColor> glowMap = playerGlowMap.get(viewerId);
        if (glowMap != null) {
            GlowColor color = glowMap.remove(targetId);
            if (color != null) {
                // 发送取消发光效果
                sendGlowingPacket(viewer, target, false);
                // 从队伍中移除
                sendTeamRemovePacket(viewer, target, color);
            }
        }
    }

    /**
     * 清除指定玩家看到的所有发光效果
     *
     * @param viewer 观察者玩家
     */
    public static void clearAllGlow(ServerPlayer viewer) {
        UUID viewerId = viewer.getUUID();
        Map<Integer, GlowColor> glowMap = playerGlowMap.remove(viewerId);

        if (glowMap != null) {
            ServerLevel level = viewer.level();
            for (Map.Entry<Integer, GlowColor> entry : glowMap.entrySet()) {
                int entityId = entry.getKey();
                GlowColor color = entry.getValue();

                Entity target = level.getEntity(entityId);
                if (target != null) {
                    sendGlowingPacket(viewer, target, false);
                    sendTeamRemovePacket(viewer, target, color);
                }
            }
        }
    }

    /**
     * 清除所有玩家对指定实体的发光效果
     *
     * @param target 目标实体
     */
    public static void clearGlowForEntity(Entity target) {
        int targetId = target.getId();

        for (Map.Entry<UUID, Map<Integer, GlowColor>> entry : playerGlowMap.entrySet()) {
            Map<Integer, GlowColor> glowMap = entry.getValue();
            GlowColor color = glowMap.remove(targetId);

            if (color != null && target.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                ServerPlayer viewer = serverLevel.getServer().getPlayerList()
                        .getPlayer(entry.getKey());
                if (viewer != null) {
                    sendGlowingPacket(viewer, target, false);
                    sendTeamRemovePacket(viewer, target, color);
                }
            }
        }

        originalFlags.remove(targetId);
    }

    /**
     * 获取玩家当前看到的某实体的发光颜色
     *
     * @param viewer 观察者
     * @param target 目标实体
     * @return 发光颜色，如果没有则返回null
     */
    public static GlowColor getGlowColor(ServerPlayer viewer, Entity target) {
        Map<Integer, GlowColor> glowMap = playerGlowMap.get(viewer.getUUID());
        if (glowMap != null) {
            return glowMap.get(target.getId());
        }
        return null;
    }

    /**
     * 检查玩家是否能看到某实体的发光效果
     */
    public static boolean hasGlow(ServerPlayer viewer, Entity target) {
        return getGlowColor(viewer, target) != null;
    }

    /**
     * 发送设置发光标志的数据包
     */
    private static void sendGlowingPacket(ServerPlayer viewer, Entity target, boolean glowing) {
        // 获取当前的flags
        byte currentFlags = target.getEntityData().get(getSharedFlagsAccessor());
        byte newFlags;

        if (glowing) {
            newFlags = (byte) (currentFlags | GLOWING_FLAG);
        } else {
            // 恢复原始状态
            Byte original = originalFlags.get(target.getId());
            if (original != null) {
                // 检查原始状态是否有发光
                if ((original & GLOWING_FLAG) != 0) {
                    newFlags = currentFlags; // 保持发光
                } else {
                    newFlags = (byte) (currentFlags & ~GLOWING_FLAG);
                }
            } else {
                newFlags = (byte) (currentFlags & ~GLOWING_FLAG);
            }
        }

        // 创建SynchedEntityData.DataValue
        List<SynchedEntityData.DataValue<?>> dataValues = List.of(
                SynchedEntityData.DataValue.create(getSharedFlagsAccessor(), newFlags)
        );

        // 发送数据包
        ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(
                target.getId(),
                dataValues
        );

        viewer.connection.send(packet);
    }

    /**
     * 发送队伍颜色数据包，让玩家看到正确的发光颜色
     */
    private static void sendTeamColorPacket(ServerPlayer viewer, Entity target, GlowColor color) {
        String teamName = color.getTeamName();
        String entityName = getEntityTeamName(target);

        // 确保队伍存在（发送创建或更新队伍的包）
        Scoreboard scoreboard = viewer.level().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);

        if (team == null) {
            // 创建临时队伍对象用于发送数据包
            team = new PlayerTeam(scoreboard, teamName);
            team.setColor(color.getColor());
        }

        // 发送队伍创建/更新包
        ClientboundSetPlayerTeamPacket createPacket = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true);
        viewer.connection.send(createPacket);

        // 发送将实体加入队伍的包
        ClientboundSetPlayerTeamPacket joinPacket = ClientboundSetPlayerTeamPacket.createPlayerPacket(
                team, entityName, ClientboundSetPlayerTeamPacket.Action.ADD
        );
        viewer.connection.send(joinPacket);
    }

    /**
     * 发送将实体从队伍移除的数据包
     */
    private static void sendTeamRemovePacket(ServerPlayer viewer, Entity target, GlowColor color) {
        String teamName = color.getTeamName();
        String entityName = getEntityTeamName(target);

        Scoreboard scoreboard = viewer.level().getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);

        if (team == null) {
            team = new PlayerTeam(scoreboard, teamName);
        }

        // 发送将实体从队伍移除的包
        ClientboundSetPlayerTeamPacket leavePacket = ClientboundSetPlayerTeamPacket.createPlayerPacket(
                team, entityName, ClientboundSetPlayerTeamPacket.Action.REMOVE
        );
        viewer.connection.send(leavePacket);
    }

    /**
     * 获取实体在队伍系统中的名称
     * 玩家使用玩家名，其他实体使用UUID字符串
     */
    private static String getEntityTeamName(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return player.getGameProfile().name();
        } else {
            return entity.getUUID().toString();
        }
    }

    /**
     * 获取Entity的共享flags数据访问器
     */
    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Byte> getSharedFlagsAccessor() {
        // Entity.DATA_SHARED_FLAGS_ID 是 protected 的，这里通过反射或直接创建
        // 在Minecraft中，这个访问器的ID是0，类型是Byte
        return (EntityDataAccessor<Byte>) EntityDataSerializers.BYTE.createAccessor(0);
    }

    /**
     * 当玩家断开连接时清理数据
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        playerGlowMap.remove(player.getUUID());
    }

    /**
     * 当实体被移除时清理数据
     */
    public static void onEntityRemoved(Entity entity) {
        int entityId = entity.getId();
        originalFlags.remove(entityId);

        for (Map<Integer, GlowColor> glowMap : playerGlowMap.values()) {
            glowMap.remove(entityId);
        }
    }

    /**
     * 刷新玩家对某实体的发光效果（用于实体重新进入视野时）
     */
    public static void refreshGlow(ServerPlayer viewer, Entity target) {
        GlowColor color = getGlowColor(viewer, target);
        if (color != null) {
            sendGlowingPacket(viewer, target, true);
            sendTeamColorPacket(viewer, target, color);
        }
    }

    /**
     * 刷新所有玩家的所有发光效果（定期调用以确保持续）
     */
    public static void refreshAllGlow(net.minecraft.server.MinecraftServer server) {
        for (Map.Entry<UUID, Map<Integer, GlowColor>> viewerEntry : playerGlowMap.entrySet()) {
            UUID viewerId = viewerEntry.getKey();
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);

            if (viewer == null) continue;

            ServerLevel level = viewer.level();
            Map<Integer, GlowColor> glowMap = viewerEntry.getValue();

            for (Map.Entry<Integer, GlowColor> glowEntry : glowMap.entrySet()) {
                int entityId = glowEntry.getKey();
                GlowColor color = glowEntry.getValue();

                Entity target = level.getEntity(entityId);
                if (target != null) {
                    sendGlowingPacket(viewer, target, true);
                    sendTeamColorPacket(viewer, target, color);
                }
            }
        }
    }

    /**
     * 刷新所有观察者对某实体的发光效果（在实体数据同步后调用）
     */
    public static void refreshGlowForEntity(Entity target) {
        int targetId = target.getId();

        if (!(target.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        for (Map.Entry<UUID, Map<Integer, GlowColor>> viewerEntry : playerGlowMap.entrySet()) {
            UUID viewerId = viewerEntry.getKey();
            Map<Integer, GlowColor> glowMap = viewerEntry.getValue();

            GlowColor color = glowMap.get(targetId);
            if (color != null) {
                ServerPlayer viewer = serverLevel.getServer().getPlayerList().getPlayer(viewerId);
                if (viewer != null) {
                    sendGlowingPacket(viewer, target, true);
                    // 队伍只需要在第一次设置，之后不需要重复发送
                }
            }
        }
    }

    /**
     * 初始化发光队伍（在服务器启动时调用）
     */
    public static void initializeTeams(Scoreboard scoreboard) {
        for (GlowColor color : GlowColor.values()) {
            String teamName = color.getTeamName();
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);

            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
            }

            team.setColor(color.getColor());
            // 可选：设置队伍不可见，避免显示名称颜色等
            team.setSeeFriendlyInvisibles(false);
        }
    }

    /**
     * 修改实体数据包，确保发光标志被设置（供 Mixin 调用）
     *
     * @param originalPacket 原始数据包
     * @param viewer 观察者玩家
     * @param target 目标实体
     * @return 修改后的数据包
     */
    public static ClientboundSetEntityDataPacket modifyEntityDataPacket(
            ClientboundSetEntityDataPacket originalPacket,
            ServerPlayer viewer,
            Entity target) {

        List<SynchedEntityData.DataValue<?>> originalValues = originalPacket.packedItems();
        List<SynchedEntityData.DataValue<?>> newValues = new ArrayList<>();
        boolean foundFlags = false;

        EntityDataAccessor<Byte> flagsAccessor = getSharedFlagsAccessor();

        for (SynchedEntityData.DataValue<?> value : originalValues) {
            if (value.id() == flagsAccessor.id()) {
                // 找到了 flags 字段，添加发光标志
                if (value.value() instanceof Byte flags) {
                    byte newFlags = (byte) (flags | GLOWING_FLAG);
                    newValues.add(SynchedEntityData.DataValue.create(flagsAccessor, newFlags));
                    foundFlags = true;
                } else {
                    newValues.add(value);
                }
            } else {
                newValues.add(value);
            }
        }

        // 如果原始包中没有 flags 字段，添加一个
        if (!foundFlags) {
            byte currentFlags = target.getEntityData().get(flagsAccessor);
            byte newFlags = (byte) (currentFlags | GLOWING_FLAG);
            newValues.add(SynchedEntityData.DataValue.create(flagsAccessor, newFlags));
        }

        return new ClientboundSetEntityDataPacket(originalPacket.id(), newValues);
    }

    /**
     * 检查某个玩家对某实体是否有发光效果（供 Mixin 调用）
     */
    public static boolean hasGlowForViewer(UUID viewerId, int entityId) {
        Map<Integer, GlowColor> glowMap = playerGlowMap.get(viewerId);
        return glowMap != null && glowMap.containsKey(entityId);
    }
}
