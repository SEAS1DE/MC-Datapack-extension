# 展示实体可见性控制系统

## 概述

这个系统实现了**纯服务端**控制展示实体（Display Entity）对特定玩家的可见性，**客户端不需要安装任何模组**。

## 实现原理

### 核心机制

1. **隐藏实体**：使用 `ClientboundRemoveEntitiesPacket` 向不应看到实体的玩家发送移除包
2. **显示实体**：使用实体的 `getAddEntityPacket()` + `ClientboundSetEntityDataPacket` 向应看到的玩家发送显示包
3. **拦截追踪**：通过 Mixin 拦截 `ServerEntity.addPairing()`，阻止向未授权玩家发送实体数据

### 持续机制

1. **DisplayVisibilityMixin** - 在实体开始被玩家追踪时检查可见性
2. **定时刷新** - 每2秒刷新所有受限展示实体的可见性

## 使用方法

### 命令

```mcfunction
# 设置展示实体只对指定玩家可见
/dpe_display show @e[type=text_display,limit=1] PlayerA PlayerB

# 添加可以看到展示实体的玩家
/dpe_display add @e[type=text_display,limit=1] PlayerC

# 移除可以看到展示实体的玩家
/dpe_display remove @e[type=text_display,limit=1] PlayerA

# 隐藏展示实体（对所有人不可见）
/dpe_display hide @e[type=text_display,limit=1]

# 清除可见性限制（恢复对所有人可见）
/dpe_display clear @e[type=text_display,limit=1]

# 列出可以看到展示实体的玩家
/dpe_display list @e[type=text_display,limit=1]
```

### 支持的展示实体类型

- `text_display` - 文本展示实体
- `block_display` - 方块展示实体
- `item_display` - 物品展示实体

### 代码调用

```java
// 获取展示实体和玩家
Display display = ...;
ServerPlayer playerA = ...;
ServerPlayer playerB = ...;

// 设置只对 playerA 和 playerB 可见
DisplayVisibilityManager.setVisibleTo(display, List.of(playerA, playerB));

// 添加 playerC 可以看到
DisplayVisibilityManager.addViewer(display, playerC);

// 移除 playerA 的可见性
DisplayVisibilityManager.removeViewer(display, playerA);

// 对所有人隐藏
DisplayVisibilityManager.hideFromAll(display);

// 清除限制，恢复对所有人可见
DisplayVisibilityManager.clearVisibility(display);

// 检查玩家是否可以看到
boolean canSee = DisplayVisibilityManager.canPlayerSee(display, player);
```

## 示例场景

### 只给特定玩家显示提示文字

```mcfunction
# 召唤一个文本展示实体
summon text_display ~ ~2 ~ {text:'{"text":"只有你能看到这段话！","color":"gold"}'}

# 设置只对执行命令的玩家可见
execute as @p run dpe_display show @e[type=text_display,distance=..3,limit=1] @s
```

### 玩家专属 UI

```mcfunction
# 为每个玩家创建专属的 UI 元素
execute as @a at @s run function custom:create_player_ui

# 在 create_player_ui.mcfunction 中：
summon text_display ~ ~2 ~ {text:'{"text":"你的分数: 100"}',Tags:["player_ui"]}
execute as @e[type=text_display,tag=player_ui,distance=..1,limit=1] run dpe_display show @s @p
```

## 与发光效果系统的对比

| 特性 | 发光效果系统 | 展示实体可见性系统 |
|------|-------------|-------------------|
| 目标 | 任何实体 | 仅展示实体 |
| 效果 | 添加发光边框 | 控制是否可见 |
| 颜色 | 通过队伍控制 | 不适用 |
| 纯服务端 | ✅ | ✅ |

## 文件结构

```
src/main/java/com/lootmatrix/
├── display/
│   ├── DisplayVisibilityManager.java     # 核心管理器
│   └── DisplayVisibilityEventHandler.java # 事件处理
├── mixin/
│   └── DisplayVisibilityMixin.java       # 拦截实体追踪
└── command/
    └── DisplayVisibilityCommand.java     # 命令接口
```

## 注意事项

1. **服务器重启**：可见性设置存储在内存中，服务器重启后会丢失
2. **实体移除**：当展示实体被移除时，相关的可见性数据会自动清理
3. **玩家断线**：当玩家断开连接时，会从所有可见性列表中移除
4. **性能**：定时刷新每2秒执行一次，对大量展示实体可能有轻微影响
