# 服务端发包实现实体发光效果系统

## 概述

这个系统实现了**纯服务端**控制的实体发光效果，**客户端不需要安装任何模组**。不同玩家可以看到同一实体的不同发光颜色。

## 实现原理

### 核心机制

1. **发光效果**：通过 `ClientboundSetEntityDataPacket` 修改实体的 `DATA_SHARED_FLAGS_ID` 中的 Glowing flag (0x40)
2. **颜色控制**：通过 `ClientboundSetPlayerTeamPacket` 将实体"虚拟地"加入带颜色的队伍
3. **玩家隔离**：数据包仅发送给指定观察者，因此不同玩家看到的效果完全独立

### 发光效果持续机制（关键）

为了让发光效果持续生效，系统使用了两个 Mixin：

1. **GlowPacketMixin**：拦截所有发送给玩家的 `ClientboundSetEntityDataPacket`
   - 当服务器同步实体数据时，检查是否需要添加发光标志
   - 在数据包发送前修改，确保发光标志不被服务器覆盖

2. **GlowEntityTrackerMixin**：处理实体进入玩家视野的场景
   - 当实体开始被玩家追踪时（进入视野），自动刷新发光效果
   - 确保切换维度、传送等场景后发光效果恢复

### 纯服务端验证

本模组是**纯服务端模组**，原因如下：
- 所有 Mixin 都只修改服务端类（`ServerGamePacketListenerImpl`, `ServerEntity`）
- 客户端只接收标准的 Minecraft 协议数据包
- 发光效果通过原版游戏的发光机制实现，客户端无需任何修改

### 关于预创建队伍

**可以简化实现**：系统在服务器启动时自动创建16个颜色队伍（`glow_red`, `glow_green` 等），你不需要手动创建。队伍仅用于控制发光颜色，不影响实际游戏逻辑。

## 使用方法

### 命令

```mcfunction
# 初始化所有发光颜色队伍（服务器启动时自动执行）
/dpe_glow init

# 为自己添加对目标实体的红色发光效果
/dpe_glow add @e[type=zombie,limit=1] red

# 为指定玩家添加对目标实体的绿色发光效果
/dpe_glow add @e[type=zombie,limit=1] green PlayerA

# 移除自己对目标实体的发光效果
/dpe_glow remove @e[type=zombie,limit=1]

# 清除自己看到的所有发光效果（一键清除）
/dpe_glow clear

# 清除指定玩家看到的所有发光效果
/dpe_glow clear PlayerA PlayerB

# 清除所有玩家对某实体的发光效果
/dpe_glow clearall @e[type=zombie,limit=1]
```

### 可用颜色

| 颜色名称 | 对应队伍颜色 |
|---------|-------------|
| black | 黑色 |
| dark_blue | 深蓝色 |
| dark_green | 深绿色 |
| dark_aqua | 深青色 |
| dark_red | 深红色 |
| dark_purple | 深紫色 |
| gold | 金色 |
| gray | 灰色 |
| dark_gray | 深灰色 |
| blue | 蓝色 |
| green | 绿色 |
| aqua | 青色 |
| red | 红色 |
| light_purple | 淡紫色 |
| yellow | 黄色 |
| white | 白色 |

### 代码调用

```java
// 获取玩家和实体
ServerPlayer viewer = ...;
Entity target = ...;

// 添加红色发光效果（仅 viewer 可见）
GlowManager.addGlow(viewer, target, GlowColor.RED);

// 添加绿色发光效果给另一个玩家
GlowManager.addGlow(anotherPlayer, target, GlowColor.GREEN);

// 移除发光效果
GlowManager.removeGlow(viewer, target);

// 一键清除玩家看到的所有发光效果
GlowManager.clearAllGlow(viewer);

// 清除所有玩家对某实体的发光效果
GlowManager.clearGlowForEntity(target);
```

## 示例场景

### A玩家看我为红色，B玩家看我为绿色

```java
// 假设 targetEntity 是"我"
Entity targetEntity = ...;
ServerPlayer playerA = ...;
ServerPlayer playerB = ...;

// A玩家看到红色发光
GlowManager.addGlow(playerA, targetEntity, GlowColor.RED);

// B玩家看到绿色发光
GlowManager.addGlow(playerB, targetEntity, GlowColor.GREEN);

// 一键清除所有发光效果
GlowManager.clearGlowForEntity(targetEntity);
```

或者使用命令：
```mcfunction
# A看我为红色（在A的视角执行，或指定A）
execute as @a[name=PlayerA] run dpe_glow add @s red @a[name=PlayerA]

# B看我为绿色
execute as @a[name=PlayerB] run dpe_glow add @s green @a[name=PlayerB]

# 清除所有人对我的发光效果
dpe_glow clearall @s
```

## 同步问题处理

### 自动处理的场景

1. **玩家断开连接** - 自动清理该玩家的发光记录
2. **实体被移除** - 自动清理所有玩家对该实体的发光记录
3. **服务器重启** - 发光效果会丢失（仅存在于内存中）

### 可能的不同步场景及解决方案

1. **实体重新进入视野**
   - 当实体离开后重新进入玩家视野时，客户端会收到新的实体数据包，发光效果可能丢失
   - 解决方案：调用 `GlowManager.refreshGlow(viewer, target)` 刷新

2. **实体数据同步**
   - 如果游戏逻辑修改了实体的发光状态，可能会覆盖我们发送的效果
   - 解决方案：重新发送发光数据包

### 持久化（可选）

如果需要在服务器重启后恢复发光效果，可以：
1. 将发光数据保存到 NBT 或数据库
2. 在 `ServerPlayConnectionEvents.JOIN` 事件中恢复

## 文件结构

```
src/main/java/com/lootmatrix/glow/
├── GlowColor.java      # 发光颜色枚举
├── GlowManager.java    # 发光效果管理器核心
└── GlowEventHandler.java # 事件处理器

src/main/java/com/lootmatrix/command/
└── GlowCommand.java    # 发光效果命令
```

## 技术细节

### 数据包类型

1. `ClientboundSetEntityDataPacket` - 设置实体的发光标志
2. `ClientboundSetPlayerTeamPacket` - 管理队伍成员和颜色

### 发光标志位

- Entity flags 位于 SynchedEntityData 索引 0
- Glowing flag 是 bit 6 (0x40)

### 队伍命名规则

- 队伍名称格式：`glow_<颜色名>`
- 例如：`glow_red`, `glow_green`, `glow_blue`
