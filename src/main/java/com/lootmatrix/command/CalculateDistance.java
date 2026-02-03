package com.lootmatrix.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import static java.lang.Math.sqrt;

public class CalculateDistance {

    private CalculateDistance(){}



    public static int relativeDistance(CommandContext<CommandSourceStack> context) {

        int x1 = IntegerArgumentType.getInteger(context, "x1");
        int y1 = IntegerArgumentType.getInteger(context, "y1");
        int z1 = IntegerArgumentType.getInteger(context, "z1");
        int x2 = IntegerArgumentType.getInteger(context, "x2");
        int y2 = IntegerArgumentType.getInteger(context, "y2");
        int z2 = IntegerArgumentType.getInteger(context, "z2");

        int temp = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1);

        int value = (int) sqrt(temp);

        context.getSource().sendSuccess(() -> Component.literal("RelativeDistance = %s (meters)".formatted(value)), false);
        return value;

    }

    public static int relativeDistanceCentimeter(CommandContext<CommandSourceStack> context){

        int x1 = IntegerArgumentType.getInteger(context, "x1");
        int y1 = IntegerArgumentType.getInteger(context, "y1");
        int z1 = IntegerArgumentType.getInteger(context, "z1");
        int x2 = IntegerArgumentType.getInteger(context, "x2");
        int y2 = IntegerArgumentType.getInteger(context, "y2");
        int z2 = IntegerArgumentType.getInteger(context, "z2");

        double temp = ((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));

        double value = sqrt(temp) * 100;

        int finalValue = (int) value;
        context.getSource().sendSuccess(() -> Component.literal("RelativeDistance = %s (centimeters)".formatted(finalValue)), false);
        return finalValue;
    }


    private static int calculateDistanceOneEntity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity e1 = context.getSource().getEntity();
        if (e1 == null){
            context.getSource().sendFailure(Component.literal("Entity not found"));
            return 0;
        }

        Entity e2 = EntityArgument.getEntity(context, "e2");

        Vec3 pos1 = e1.position();
        Vec3 pos2 = e2.position();

        // 高性能计算：只计算距离平方
        double dx = pos1.x - pos2.x;
        double dy = pos1.y - pos2.y;
        double dz = pos1.z - pos2.z;
        double distanceSq = dx*dx + dy*dy + dz*dz;

        // 如果需要实际距离，只在显示时计算一次
        double distance = Math.sqrt(distanceSq);

        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(
                String.format("距离: %.2f", distance)
        ), false);

        return (int) distance;
    }

    private static int calculateDistanceOneEntityCentimeter(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Entity e1 = context.getSource().getEntity();
        if (e1 == null){
            context.getSource().sendFailure(Component.literal("Entity not found"));
            return 0;
        }


        Entity e2 = EntityArgument.getEntity(context, "e2");

        Vec3 pos1 = e1.position();
        Vec3 pos2 = e2.position();

        // 高性能计算：只计算距离平方
        double dx = pos1.x - pos2.x;
        double dy = pos1.y - pos2.y;
        double dz = pos1.z - pos2.z;
        double distanceSq = dx*dx + dy*dy + dz*dz;

        // 如果需要实际距离，只在显示时计算一次
        double distance = Math.sqrt(distanceSq) * 100;

        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(
                String.format("距离: %.2f (cm)", distance)
        ), false);

        return (int) distance;
    }

    public static void register(){
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("dpe_distance")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    //.executes(calculateDistance::calculateDistanceOne)
                                    .then(
                                            Commands.argument("e2", EntityArgument.entity())
                                                    .executes(CalculateDistance::calculateDistanceOneEntity)
                                                    .then(
                                                            Commands.literal("cm")
                                                                    .executes(CalculateDistance::calculateDistanceOneEntityCentimeter)
                                                    )
                                    )
                                    .then(
                                            Commands.argument("x1", IntegerArgumentType.integer())
                                                    .then(
                                                            Commands.argument("y1", IntegerArgumentType.integer())
                                                                    .then(
                                                                            Commands.argument("z1", IntegerArgumentType.integer())
                                                                                    .then(
                                                                                            Commands.argument("x2", IntegerArgumentType.integer())
                                                                                                    .then(
                                                                                                            Commands.argument("y2", IntegerArgumentType.integer())
                                                                                                                    .then(
                                                                                                                            Commands.argument("z2", IntegerArgumentType.integer())
                                                                                                                                    .executes(CalculateDistance::relativeDistance)
                                                                                                                                    .then(
                                                                                                                                            Commands.literal("cm")
                                                                                                                                                    .executes(CalculateDistance::relativeDistanceCentimeter)
                                                                                                                                    )
                                                                                                                    )
                                                                                                    )
                                                                                    )
                                                                    )
                                                    )
                                    )
            );
        });
    }
}
