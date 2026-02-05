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

    // 追踪每个玩家客户端上实体所在的队伍: 观察者UUID -> (目标实体ID -> 队伍颜色)
    // 用于防止向客户端发送移除不存在的队伍成员的包
    private static final Map<UUID, Map<Integer, GlowColor>> playerTeamStateMap = new ConcurrentHashMap<>();

    // 缓存实体ID到队伍名称的映射，用于快速查找
    // 实体ID -> 实体在队伍系统中的名称（玩家名或UUID字符串）
    private static final Map<Integer, String> entityTeamCache = new ConcurrentHashMap<>();

    // 反向缓存：实体名称 -> 实体ID，用于快速反向查找
    private static final Map<String, Integer> entityNameToIdCache = new ConcurrentHashMap<>();

    // 线程局部变量：标记当前线程正在发送内部队伍恢复包
    // 这样 Mixin 可以跳过这些包，避免恢复包被错误拦截
    private static final ThreadLocal<Boolean> sendingInternalPacket = ThreadLocal.withInitial(() -> false);

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
     * 检查玩家是否对某个实体名称有发光效果
     * 用于拦截队伍包时检查（供 Mixin 调用）
     *
     * @param viewer 观察者玩家
     * @param entityName 实体在队伍系统中的名称（玩家名或UUID字符串）
     * @return 是否有发光效果
     */
    public static boolean hasGlowForEntityName(ServerPlayer viewer, String entityName) {
        UUID viewerId = viewer.getUUID();
        Map<Integer, GlowColor> glowMap = playerGlowMap.get(viewerId);
        if (glowMap == null || glowMap.isEmpty()) {
            return false;
        }

        // 检查entityName是否对应任何有发光效果的实体
        // entityName可能是玩家名或实体UUID
        net.minecraft.server.level.ServerLevel level = viewer.level();

        for (int entityId : glowMap.keySet()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                String name = getEntityTeamName(entity);
                if (name.equals(entityName)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查是否有任何观察者对某个实体名称有发光效果
     * 用于 Connection 级别的包拦截（供 Mixin 调用）
     *
     * @param entityName 实体在队伍系统中的名称（玩家名或UUID字符串）
     * @return 是否有任何观察者对该实体有发光效果
     */
    public static boolean hasGlowForAnyViewer(String entityName) {
        // 防止类初始化顺序问题导致的空指针
        if (entityNameToIdCache == null || playerGlowMap == null) {
            return false;
        }

        // 首先通过反向缓存快速查找实体ID
        Integer entityId = entityNameToIdCache.get(entityName);

        if (entityId == null) {
            // 如果没有在缓存中找到，说明这个实体从未被添加过发光效果
            return false;
        }

        // 检查是否有任何观察者对这个实体有发光效果
        for (Map.Entry<UUID, Map<Integer, GlowColor>> entry : playerGlowMap.entrySet()) {
            Map<Integer, GlowColor> glowMap = entry.getValue();
            if (glowMap != null && glowMap.containsKey(entityId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查当前线程是否正在发送内部队伍恢复包
     * 用于 Mixin 跳过这些包的拦截
     *
     * @return 是否正在发送内部包
     */
    public static boolean isSendingInternalPacket() {
        return sendingInternalPacket.get();
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
     * 使用虚拟队伍，不依赖服务器的真实记分板
     */
    private static void sendTeamColorPacket(ServerPlayer viewer, Entity target, GlowColor color) {
        UUID viewerId = viewer.getUUID();
        int targetId = target.getId();
        String teamName = color.getTeamName();
        String entityName = getEntityTeamName(target);

        // 检查实体是否已经在同一个队伍中，如果是，不需要重新发送
        Map<Integer, GlowColor> teamState = playerTeamStateMap.get(viewerId);
        if (teamState != null) {
            GlowColor oldColor = teamState.get(targetId);
            if (oldColor == color) {
                // 已经在同一个队伍中，不需要重新发送
                return;
            }
            // 不需要发送REMOVE包，当实体加入新队伍时，客户端会自动处理
            // 发送REMOVE包可能导致 "Player is either on another team or not on any team" 错误
        }

        // 不发送从真实队伍移除的包，因为：
        // 1. 客户端在收到ADD包时会自动将实体从旧队伍移到新队伍
        // 2. 发送REMOVE包可能导致客户端状态不同步错误

        // 创建一个临时的Scoreboard用于构建PlayerTeam对象
        // 这个Scoreboard不会被添加到服务器，只用于构建数据包
        Scoreboard tempScoreboard = new Scoreboard();
        PlayerTeam team = new PlayerTeam(tempScoreboard, teamName);
        team.setColor(color.getColor());

        // 发送队伍创建/更新包（包含完整的队伍信息）
        ClientboundSetPlayerTeamPacket createPacket = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true);
        viewer.connection.send(createPacket);

        // 发送将实体加入队伍的包
        ClientboundSetPlayerTeamPacket joinPacket = ClientboundSetPlayerTeamPacket.createPlayerPacket(
                team, entityName, ClientboundSetPlayerTeamPacket.Action.ADD
        );
        viewer.connection.send(joinPacket);

        // 记录队伍状态
        playerTeamStateMap.computeIfAbsent(viewerId, k -> new ConcurrentHashMap<>())
                .put(targetId, color);

        // 缓存实体名称供包拦截使用
        entityTeamCache.put(targetId, entityName);
        entityNameToIdCache.put(entityName, targetId);
    }

    /**
     * 发送将实体从虚拟发光队伍移除的数据包
     * 这会让客户端清除实体的队伍颜色，并恢复到真实队伍状态
     */
    private static void sendTeamRemovePacket(ServerPlayer viewer, Entity target, GlowColor color) {
        UUID viewerId = viewer.getUUID();
        int targetId = target.getId();
        String teamName = color.getTeamName();
        String entityName = getEntityTeamName(target);

        // 清理内部追踪状态
        Map<Integer, GlowColor> teamState = playerTeamStateMap.get(viewerId);
        if (teamState != null) {
            teamState.remove(targetId);
        }

        // 从缓存中移除（只有当没有其他观察者对这个实体有发光效果时）
        boolean hasOtherViewers = false;
        for (Map.Entry<UUID, Map<Integer, GlowColor>> entry : playerGlowMap.entrySet()) {
            if (!entry.getKey().equals(viewerId)) {
                Map<Integer, GlowColor> otherGlowMap = entry.getValue();
                if (otherGlowMap != null && otherGlowMap.containsKey(targetId)) {
                    hasOtherViewers = true;
                    break;
                }
            }
        }
        if (!hasOtherViewers) {
            entityTeamCache.remove(targetId);
            entityNameToIdCache.remove(entityName);
        }

        // 创建临时 Scoreboard 和 PlayerTeam 用于构建数据包
        Scoreboard tempScoreboard = new Scoreboard();
        PlayerTeam glowTeam = new PlayerTeam(tempScoreboard, teamName);

        // 发送将实体从虚拟发光队伍移除的包
        ClientboundSetPlayerTeamPacket removePacket = ClientboundSetPlayerTeamPacket.createPlayerPacket(
                glowTeam, entityName, ClientboundSetPlayerTeamPacket.Action.REMOVE
        );
        viewer.connection.send(removePacket);

        // 恢复实体到真实队伍
        // 获取服务器的真实记分板
        Scoreboard serverScoreboard = viewer.level().getServer().getScoreboard();

        // 获取实体所在的真实队伍
        // 对于玩家，直接使用 getPlayersTeam 方法
        // 对于其他实体，使用 UUID 字符串
        PlayerTeam realTeam = serverScoreboard.getPlayersTeam(entityName);

        // 如果直接查找失败，尝试手动遍历（作为后备方案）
        if (realTeam == null) {
            for (PlayerTeam team : serverScoreboard.getPlayerTeams()) {
                // 检查队伍成员，不区分大小写进行比较
                for (String member : team.getPlayers()) {
                    if (member.equalsIgnoreCase(entityName)) {
                        realTeam = team;
                        break;
                    }
                }
                if (realTeam != null) break;
            }
        }

        if (realTeam != null) {
            // 设置标志，表示正在发送内部队伍恢复包
            // 这样 Mixin 会跳过这些包的拦截
            sendingInternalPacket.set(true);
            try {
                // 实体在真实队伍中，发送将其加入真实队伍的包
                // 先发送队伍信息包（确保客户端有这个队伍的定义）
                ClientboundSetPlayerTeamPacket teamInfoPacket = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(realTeam, false);
                viewer.connection.send(teamInfoPacket);

                // 再发送将实体加入队伍的包
                ClientboundSetPlayerTeamPacket addPacket = ClientboundSetPlayerTeamPacket.createPlayerPacket(
                        realTeam, entityName, ClientboundSetPlayerTeamPacket.Action.ADD
                );
                viewer.connection.send(addPacket);
            } finally {
                sendingInternalPacket.set(false);
            }
        }
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
        UUID playerId = player.getUUID();
        playerGlowMap.remove(playerId);
        playerTeamStateMap.remove(playerId);
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

        for (Map<Integer, GlowColor> teamState : playerTeamStateMap.values()) {
            teamState.remove(entityId);
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
     * 清理旧版本可能创建的真实队伍，避免与虚拟队伍包冲突
     */
    public static void initializeTeams(Scoreboard scoreboard) {
        // 清理旧版本可能创建的真实队伍
        // 这些队伍可能导致客户端收到冲突的队伍同步包
        for (GlowColor color : GlowColor.values()) {
            String teamName = color.getTeamName();
            PlayerTeam existingTeam = scoreboard.getPlayerTeam(teamName);

            if (existingTeam != null) {
                // 移除队伍中的所有成员，然后删除队伍
                // 这样可以清理旧版本遗留的数据
                scoreboard.removePlayerTeam(existingTeam);
            }
        }

        // 不再创建真实队伍，我们使用虚拟队伍包
        // 虚拟队伍只通过数据包发送给特定玩家，不会被服务器同步
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
