# 视线检测指令

## 概述

这个指令用于检测一个实体的视野内是否能看到指定的目标实体。支持普通模式和严格模式。

## 命令格式

```mcfunction
/dpe_cansee <观察者> <目标实体...> [fov] [strict]
```

## 参数说明

| 参数 | 类型 | 说明 |
|------|------|------|
| 观察者 | 实体选择器 | 执行视线检测的实体 |
| 目标实体 | 实体选择器 | 要检测是否可见的实体（可以是多个） |
| fov | 数字（1-180） | 视野角度（度），普通模式默认70°，严格模式默认10° |
| strict | 字面量 | 严格模式标志 |

## 返回值

- **成功**：返回可见的目标实体数量
- **失败**：如果有任何一个目标实体不可见，返回 0

## 模式说明

### 普通模式
- 检测目标实体的**整个碰撞箱**是否在视野范围内
- 碰撞箱的任意部分在视野内即视为可见
- 会进行方块遮挡检测（射线追踪）
- 默认 FOV：70°

### 严格模式 (strict)
- 只检测目标实体的**中心点**是否在准心附近
- 更精确，适用于瞄准检测
- 不进行方块遮挡检测（只检查角度）
- 默认 FOV：10°

## 使用示例

```mcfunction
# 检测玩家是否能看到最近的僵尸
/dpe_cansee @p @e[type=zombie,limit=1,sort=nearest]

# 检测玩家是否能看到所有标记为"target"的实体
/dpe_cansee @p @e[tag=target]

# 使用自定义视野角度（90度）
/dpe_cansee @p @e[type=zombie,limit=1] 90

# 严格模式 - 检测准心是否对准实体
/dpe_cansee @p @e[type=zombie,limit=1] strict

# 严格模式 + 自定义角度（5度，非常精确）
/dpe_cansee @p @e[type=zombie,limit=1] 5 strict

# 或者先写 strict 再写角度
/dpe_cansee @p @e[type=zombie,limit=1] strict 5
```

## 实际应用场景

### 1. 技能释放检测
```mcfunction
# 检测玩家是否瞄准了敌人，用于释放技能
execute as @a[tag=casting] if entity @s run execute 
    store result score @s aim_success 
    run dpe_cansee @s @e[type=!player,tag=enemy,distance=..20] strict

execute as @a[tag=casting,scores={aim_success=1..}] run function custom:cast_skill
```

### 2. 隐身检测
```mcfunction
# 检测守卫是否看到了玩家
execute as @e[tag=guard] store result score @s can_see_player 
    run dpe_cansee @s @a[gamemode=survival,distance=..30] 120

execute as @e[tag=guard,scores={can_see_player=1..}] run function custom:alert
```

### 3. 教学/引导系统
```mcfunction
# 检测玩家是否看向了目标点
execute as @a[tag=in_tutorial] store result score @s looking_at_target 
    run dpe_cansee @s @e[tag=tutorial_target,limit=1] 30

execute as @a[tag=in_tutorial,scores={looking_at_target=1..}] run tellraw @s "很好！你找到了目标！"
```

## 技术细节

### 普通模式算法
1. 获取观察者的眼睛位置和视线方向
2. 获取目标实体的碰撞箱
3. 检查碰撞箱的中心点和8个角点
4. 如果任意点在 FOV 角度内，进行射线追踪
5. 如果射线未被方块阻挡，返回可见

### 严格模式算法
1. 获取观察者的眼睛位置和视线方向
2. 获取目标实体碰撞箱的中心点
3. 计算视线方向与目标方向的夹角
4. 如果夹角小于 FOV/2，返回可见

### 性能考虑
- 最大检测距离：128格
- 普通模式每个实体检测9个点
- 射线追踪可能有一定性能开销
- 严格模式更轻量，只检测1个点
