package com.lootmatrix.command;

import com.lootmatrix.display.DisplayVisibilityManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 展示实体可见性控制指令
 *
 * 用法：
 * /dpe_display show <展示实体> <玩家...>     - 设置展示实体只对指定玩家可见
 * /dpe_display add <展示实体> <玩家...>      - 添加可以看到展示实体的玩家
 * /dpe_display remove <展示实体> <玩家...>   - 移除可以看到展示实体的玩家
 * /dpe_display hide <展示实体>               - 隐藏展示实体（对所有人不可见）
 * /dpe_display clear <展示实体>              - 清除可见性限制（对所有人可见）
 * /dpe_display list <展示实体>               - 列出可以看到展示实体的玩家
 */
public class DisplayVisibilityCommand {

    private DisplayVisibilityCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("dpe_display")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))

                // /dpe_display show <display> <players>
                .then(Commands.literal("show")
                    .then(Commands.argument("display", EntityArgument.entity())
                        .then(Commands.argument("players", EntityArgument.players())
                            .executes(DisplayVisibilityCommand::showToPlayers))))

                // /dpe_display add <display> <players>
                .then(Commands.literal("add")
                    .then(Commands.argument("display", EntityArgument.entity())
                        .then(Commands.argument("players", EntityArgument.players())
                            .executes(DisplayVisibilityCommand::addPlayers))))

                // /dpe_display remove <display> <players>
                .then(Commands.literal("remove")
                    .then(Commands.argument("display", EntityArgument.entity())
                        .then(Commands.argument("players", EntityArgument.players())
                            .executes(DisplayVisibilityCommand::removePlayers))))

                // /dpe_display hide <display>
                .then(Commands.literal("hide")
                    .then(Commands.argument("display", EntityArgument.entity())
                        .executes(DisplayVisibilityCommand::hideFromAll)))

                // /dpe_display clear <display>
                .then(Commands.literal("clear")
                    .then(Commands.argument("display", EntityArgument.entity())
                        .executes(DisplayVisibilityCommand::clearVisibility)))

                // /dpe_display list <display>
                .then(Commands.literal("list")
                    .then(Commands.argument("display", EntityArgument.entity())
                        .executes(DisplayVisibilityCommand::listViewers)))
            ));
    }

    private static int showToPlayers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity entity = EntityArgument.getEntity(context, "display");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");

        if (!(entity instanceof Display display)) {
            source.sendFailure(Component.literal("目标实体不是展示实体（Display）"));
            return 0;
        }

        DisplayVisibilityManager.setVisibleTo(display, players);

        int count = players.size();
        source.sendSuccess(() -> Component.literal(
            String.format("已设置展示实体只对 %d 名玩家可见", count)
        ), true);

        return count;
    }

    private static int addPlayers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity entity = EntityArgument.getEntity(context, "display");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");

        if (!(entity instanceof Display display)) {
            source.sendFailure(Component.literal("目标实体不是展示实体（Display）"));
            return 0;
        }

        int count = 0;
        for (ServerPlayer player : players) {
            DisplayVisibilityManager.addViewer(display, player);
            count++;
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal(
            String.format("已添加 %d 名玩家可以看到展示实体", finalCount)
        ), true);

        return count;
    }

    private static int removePlayers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity entity = EntityArgument.getEntity(context, "display");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");

        if (!(entity instanceof Display display)) {
            source.sendFailure(Component.literal("目标实体不是展示实体（Display）"));
            return 0;
        }

        int count = 0;
        for (ServerPlayer player : players) {
            DisplayVisibilityManager.removeViewer(display, player);
            count++;
        }

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal(
            String.format("已移除 %d 名玩家对展示实体的可见性", finalCount)
        ), true);

        return count;
    }

    private static int hideFromAll(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity entity = EntityArgument.getEntity(context, "display");

        if (!(entity instanceof Display display)) {
            source.sendFailure(Component.literal("目标实体不是展示实体（Display）"));
            return 0;
        }

        DisplayVisibilityManager.hideFromAll(display);

        source.sendSuccess(() -> Component.literal("已隐藏展示实体（对所有人不可见）"), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int clearVisibility(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity entity = EntityArgument.getEntity(context, "display");

        if (!(entity instanceof Display display)) {
            source.sendFailure(Component.literal("目标实体不是展示实体（Display）"));
            return 0;
        }

        DisplayVisibilityManager.clearVisibility(display);

        source.sendSuccess(() -> Component.literal("已清除展示实体的可见性限制（对所有人可见）"), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int listViewers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity entity = EntityArgument.getEntity(context, "display");

        if (!(entity instanceof Display display)) {
            source.sendFailure(Component.literal("目标实体不是展示实体（Display）"));
            return 0;
        }

        Set<UUID> viewers = DisplayVisibilityManager.getViewers(display);

        if (viewers == null) {
            source.sendSuccess(() -> Component.literal("该展示实体对所有人可见（无限制）"), false);
            return 0;
        }

        if (viewers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("该展示实体对所有人隐藏"), false);
            return 0;
        }

        StringBuilder sb = new StringBuilder("可以看到该展示实体的玩家：\n");
        for (UUID uuid : viewers) {
            ServerPlayer player = source.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                sb.append("- ").append(player.getName().getString()).append("\n");
            } else {
                sb.append("- ").append(uuid.toString()).append(" (离线)\n");
            }
        }

        final String message = sb.toString();
        source.sendSuccess(() -> Component.literal(message), false);

        return viewers.size();
    }
}
