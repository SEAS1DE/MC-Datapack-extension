package com.lootmatrix.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

/**
 * 视线检测指令
 *
 * 用法：
 * /dpe_cansee <观察者> <目标实体...> [fov]           - 检测观察者视野内是否能看到所有目标实体
 * /dpe_cansee <观察者> <目标实体...> [fov] strict    - 严格模式，检测准心附近
 *
 * 参数：
 * - 观察者：执行视线检测的实体
 * - 目标实体：要检测是否可见的实体（可以是多个）
 * - fov：视野角度（度数），默认70度，strict模式默认10度
 * - strict：严格模式，只检测准心附近的实体
 *
 * 返回值：
 * - 如果所有目标实体都在视野内，返回目标实体数量
 * - 如果有任何一个目标实体不在视野内，返回0
 */
public class CanSeeCommand {

    private CanSeeCommand() {}

    // 默认视野角度（度）
    private static final double DEFAULT_FOV = 70.0;
    // 严格模式默认视野角度（度）
    private static final double DEFAULT_STRICT_FOV = 10.0;
    // 最大检测距离
    private static final double MAX_DISTANCE = 256.0;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("dpe_cansee")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))

                // /dpe_cansee <viewer> <targets>
                .then(Commands.argument("viewer", EntityArgument.entity())
                    .then(Commands.argument("targets", EntityArgument.entities())
                        // 默认模式，默认FOV
                        .executes(ctx -> canSee(ctx, DEFAULT_FOV, false))

                        // 指定FOV
                        .then(Commands.argument("fov", DoubleArgumentType.doubleArg(1.0, 180.0))
                            .executes(ctx -> canSee(ctx, DoubleArgumentType.getDouble(ctx, "fov"), false))

                            // 严格模式
                            .then(Commands.literal("strict")
                                .executes(ctx -> canSee(ctx, DoubleArgumentType.getDouble(ctx, "fov"), true))))

                        // 严格模式，默认严格FOV
                        .then(Commands.literal("strict")
                            .executes(ctx -> canSee(ctx, DEFAULT_STRICT_FOV, true))

                            // 严格模式，指定FOV
                            .then(Commands.argument("fov", DoubleArgumentType.doubleArg(1.0, 180.0))
                                .executes(ctx -> canSee(ctx, DoubleArgumentType.getDouble(ctx, "fov"), true))))))
            ));
    }

    private static int canSee(CommandContext<CommandSourceStack> ctx, double fovDegrees, boolean strict)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Entity viewer = EntityArgument.getEntity(ctx, "viewer");
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");

        if (targets.isEmpty()) {
            source.sendFailure(Component.literal("未找到目标实体"));
            return 0;
        }

        // 转换FOV为弧度的一半（用于计算夹角）
        double halfFovRad = Math.toRadians(fovDegrees / 2.0);
        double cosHalfFov = Math.cos(halfFovRad);

        // 获取观察者的眼睛位置和视线方向
        Vec3 eyePos = viewer.getEyePosition();
        Vec3 lookDir = viewer.getViewVector(1.0F).normalize();

        int visibleCount = 0;

        for (Entity target : targets) {
            if (target == viewer) {
                // 跳过自己
                continue;
            }

            boolean canSeeTarget;
            if (strict) {
                canSeeTarget = canSeeStrict(eyePos, lookDir, target, cosHalfFov);
            } else {
                canSeeTarget = canSeeNormal(viewer, eyePos, lookDir, target, cosHalfFov);
            }

            if (canSeeTarget) {
                visibleCount++;
            } else {
                // 如果有任何一个目标不可见，直接返回0
                final String targetName = target.getName().getString();
                source.sendSuccess(() -> Component.literal(
                    String.format("无法看到: %s", targetName)
                ), false);
                return 0;
            }
        }

        // 所有目标都可见
        final int count = visibleCount;
        final String mode = strict ? "严格模式" : "普通模式";
        source.sendSuccess(() -> Component.literal(
            String.format("可以看到所有 %d 个目标实体 (%s, FOV: %.1f°)", count, mode, fovDegrees)
        ), false);

        return visibleCount;
    }

    /**
     * 普通视线检测 - 检测目标实体是否在视野范围内
     * 考虑实体的整个碰撞箱
     */
    private static boolean canSeeNormal(Entity viewer, Vec3 eyePos, Vec3 lookDir, Entity target, double cosHalfFov) {
        // 检查距离
        double distance = eyePos.distanceTo(target.position());
        if (distance > MAX_DISTANCE) {
            return false;
        }

        // 获取目标实体的碰撞箱
        AABB targetBox = target.getBoundingBox();

        // 检查碰撞箱的多个点是否在视野内
        // 检查中心点和8个角点
        Vec3[] checkPoints = {
            targetBox.getCenter(),
            new Vec3(targetBox.minX, targetBox.minY, targetBox.minZ),
            new Vec3(targetBox.maxX, targetBox.minY, targetBox.minZ),
            new Vec3(targetBox.minX, targetBox.maxY, targetBox.minZ),
            new Vec3(targetBox.maxX, targetBox.maxY, targetBox.minZ),
            new Vec3(targetBox.minX, targetBox.minY, targetBox.maxZ),
            new Vec3(targetBox.maxX, targetBox.minY, targetBox.maxZ),
            new Vec3(targetBox.minX, targetBox.maxY, targetBox.maxZ),
            new Vec3(targetBox.maxX, targetBox.maxY, targetBox.maxZ)
        };

        for (Vec3 point : checkPoints) {
            if (isPointInFov(eyePos, lookDir, point, cosHalfFov)) {
                // 检查是否有方块遮挡（可选的射线检测）
                if (hasLineOfSight(viewer, eyePos, point)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 严格视线检测 - 只检测目标实体是否在准心附近
     * 不考虑碰撞箱，只检测实体中心点
     */
    private static boolean canSeeStrict(Vec3 eyePos, Vec3 lookDir, Entity target, double cosHalfFov) {
        // 检查距离
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        double distance = eyePos.distanceTo(targetCenter);
        if (distance > MAX_DISTANCE) {
            return false;
        }

        // 严格模式只检查实体中心点
        return isPointInFov(eyePos, lookDir, targetCenter, cosHalfFov);
    }

    /**
     * 检查一个点是否在视野角度内
     */
    private static boolean isPointInFov(Vec3 eyePos, Vec3 lookDir, Vec3 targetPoint, double cosHalfFov) {
        // 计算从眼睛到目标点的方向向量
        Vec3 toTarget = targetPoint.subtract(eyePos).normalize();

        // 计算视线方向和目标方向的夹角余弦值
        double dot = lookDir.dot(toTarget);

        // 如果余弦值大于等于半视野角的余弦值，说明在视野内
        return dot >= cosHalfFov;
    }

    /**
     * 检查从眼睛位置到目标点是否有视线（无方块遮挡）
     */
    private static boolean hasLineOfSight(Entity viewer, Vec3 eyePos, Vec3 targetPoint) {
        // 使用射线检测判断是否有方块遮挡
        // ClipContext 用于射线追踪
        var level = viewer.level();
        var clipResult = level.clip(new net.minecraft.world.level.ClipContext(
            eyePos,
            targetPoint,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            viewer
        ));

        // 如果射线击中的位置就是目标位置（或非常接近），说明没有遮挡
        return clipResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS ||
               clipResult.getLocation().distanceToSqr(targetPoint) < 1.0;
    }
}
