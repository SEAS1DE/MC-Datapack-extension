package com.lootmatrix.mixin;

import net.minecraft.server.commands.RideCommand;
import org.spongepowered.asm.mixin.Mixin;




@Mixin(RideCommand.class)
public class ExampleMixin {
//    @Inject(at = @At("HEAD"), method = "mount", cancellable = true)
//    private static void mount(CommandSourceStack commandSourceStack, Entity entity, Entity entity2, CallbackInfoReturnable<Integer> cir) throws CommandSyntaxException {
//        Entity entity3 = entity.getVehicle();
//        commandSourceStack.sendSuccess(() -> Component.translatable("commands.ride.mount.success", entity.getDisplayName(), entity2.getDisplayName()), true);
//        cir.setReturnValue(1);
//    }
}
