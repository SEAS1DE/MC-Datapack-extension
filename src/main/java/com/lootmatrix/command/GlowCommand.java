package com.lootmatrix.command;

import com.lootmatrix.glow.GlowColor;
import com.lootmatrix.glow.GlowManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * 发光效果指令
 *
 * 用法：
 * /dpe_glow add <目标实体> <颜色> [观察者玩家]  - 为观察者添加对目标的发光效果
 * /dpe_glow remove <目标实体> [观察者玩家]     - 移除观察者对目标的发光效果
 * /dpe_glow clear [观察者玩家]                  - 清除观察者看到的所有发光效果
 * /dpe_glow clearall <目标实体>                 - 清除所有玩家对目标的发光效果
 * /dpe_glow init                                - 初始化发光队伍
 */
public class GlowCommand {

    private GlowCommand() {}

    // 颜色建议提供器
    private static final SuggestionProvider<CommandSourceStack> COLOR_SUGGESTIONS = (context, builder) -> {
        return SharedSuggestionProvider.suggest(
                Arrays.stream(GlowColor.values())
                        .map(c -> c.name().toLowerCase())
                        .collect(Collectors.toList()),
                builder
        );
    };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("dpe_glow")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))

                    // /dpe_glow init - 初始化队伍
                    .then(Commands.literal("init")
                            .executes(GlowCommand::initTeams))

                    // /dpe_glow add <target> <color> [viewer]
                    .then(Commands.literal("add")
                            .then(Commands.argument("target", EntityArgument.entity())
                                    .then(Commands.argument("color", StringArgumentType.word())
                                            .suggests(COLOR_SUGGESTIONS)
                                            // 不指定观察者，使用执行者
                                            .executes(GlowCommand::addGlowSelf)
                                            // 指定观察者
                                            .then(Commands.argument("viewer", EntityArgument.players())
                                                    .executes(GlowCommand::addGlow)))))

                    // /dpe_glow remove <target> [viewer]
                    .then(Commands.literal("remove")
                            .then(Commands.argument("target", EntityArgument.entity())
                                    .executes(GlowCommand::removeGlowSelf)
                                    .then(Commands.argument("viewer", EntityArgument.players())
                                            .executes(GlowCommand::removeGlow))))

                    // /dpe_glow clear [viewer]
                    .then(Commands.literal("clear")
                            .executes(GlowCommand::clearGlowSelf)
                            .then(Commands.argument("viewer", EntityArgument.players())
                                    .executes(GlowCommand::clearGlow)))

                    // /dpe_glow clearall <target>
                    .then(Commands.literal("clearall")
                            .then(Commands.argument("target", EntityArgument.entity())
                                    .executes(GlowCommand::clearAllGlowForEntity)))
            );
        });
    }

    private static int initTeams(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        GlowManager.initializeTeams(source.getServer().getScoreboard());

        source.sendSuccess(() -> Component.literal("已初始化所有发光颜色队伍"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int addGlowSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer viewer = source.getPlayerOrException();
        Entity target = EntityArgument.getEntity(context, "target");
        String colorName = StringArgumentType.getString(context, "color");

        GlowColor color = parseColor(colorName);
        if (color == null) {
            source.sendFailure(Component.literal("无效的颜色: " + colorName));
            return 0;
        }

        GlowManager.addGlow(viewer, target, color);

        source.sendSuccess(() -> Component.literal(
                String.format("已为你添加对 %s 的 %s 发光效果",
                        target.getName().getString(), color.name().toLowerCase())
        ), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int addGlow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity target = EntityArgument.getEntity(context, "target");
        String colorName = StringArgumentType.getString(context, "color");
        Collection<ServerPlayer> viewers = EntityArgument.getPlayers(context, "viewer");

        GlowColor color = parseColor(colorName);
        if (color == null) {
            source.sendFailure(Component.literal("无效的颜色: " + colorName));
            return 0;
        }

        int count = 0;
        for (ServerPlayer viewer : viewers) {
            GlowManager.addGlow(viewer, target, color);
            count++;
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal(
                String.format("已为 %d 名玩家添加对 %s 的 %s 发光效果",
                        finalCount, target.getName().getString(), color.name().toLowerCase())
        ), true);

        return count;
    }

    private static int removeGlowSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer viewer = source.getPlayerOrException();
        Entity target = EntityArgument.getEntity(context, "target");

        GlowManager.removeGlow(viewer, target);

        source.sendSuccess(() -> Component.literal(
                String.format("已移除你对 %s 的发光效果", target.getName().getString())
        ), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int removeGlow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity target = EntityArgument.getEntity(context, "target");
        Collection<ServerPlayer> viewers = EntityArgument.getPlayers(context, "viewer");

        int count = 0;
        for (ServerPlayer viewer : viewers) {
            GlowManager.removeGlow(viewer, target);
            count++;
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal(
                String.format("已为 %d 名玩家移除对 %s 的发光效果",
                        finalCount, target.getName().getString())
        ), true);

        return count;
    }

    private static int clearGlowSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer viewer = source.getPlayerOrException();

        GlowManager.clearAllGlow(viewer);

        source.sendSuccess(() -> Component.literal("已清除你看到的所有发光效果"), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int clearGlow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> viewers = EntityArgument.getPlayers(context, "viewer");

        int count = 0;
        for (ServerPlayer viewer : viewers) {
            GlowManager.clearAllGlow(viewer);
            count++;
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal(
                String.format("已清除 %d 名玩家看到的所有发光效果", finalCount)
        ), true);

        return count;
    }

    private static int clearAllGlowForEntity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity target = EntityArgument.getEntity(context, "target");

        GlowManager.clearGlowForEntity(target);

        source.sendSuccess(() -> Component.literal(
                String.format("已清除所有玩家对 %s 的发光效果", target.getName().getString())
        ), true);

        return Command.SINGLE_SUCCESS;
    }

    private static GlowColor parseColor(String name) {
        try {
            return GlowColor.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
