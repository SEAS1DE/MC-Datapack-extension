package com.lootmatrix.mixin;

import com.lootmatrix.DatapackExtension;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.RideCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;




@Mixin(RideCommand.class)
public class ExampleMixin {
//    @Inject(at = @At("HEAD"), method = "mount", cancellable = true)
//    private static void mount(CommandSourceStack commandSourceStack, Entity entity, Entity entity2, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
//        Entity entity3 = entity.getVehicle();
//        commandSourceStack.sendSuccess(() -> Component.translatable("commands.ride.mount.success", entity.getDisplayName(), entity2.getDisplayName()), true);
//        cir.setReturnValue(1);
//    }
}
