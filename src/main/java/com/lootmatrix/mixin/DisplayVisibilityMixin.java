package com.lootmatrix.mixin;

import com.lootmatrix.display.DisplayVisibilityManager;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截展示实体的追踪，阻止向未授权玩家发送实体数据
 *
 * 这是纯服务端的 Mixin，不需要客户端安装
 */
@Mixin(ServerEntity.class)
public abstract class DisplayVisibilityMixin {

    @Shadow
    @Final
    private Entity entity;

    /**
     * 拦截实体开始被玩家追踪
     * 如果是受限的展示实体且玩家无权查看，阻止追踪
     */
    @Inject(method = "addPairing", at = @At("HEAD"), cancellable = true)
    private void onAddPairing(ServerPlayer player, CallbackInfo ci) {
        if (this.entity instanceof Display display) {
            if (!DisplayVisibilityManager.canPlayerSee(display, player)) {
                // 阻止向该玩家发送实体数据
                ci.cancel();
            }
        }
    }

    /**
     * 在发送实体数据后检查可见性
     */
    @Inject(method = "sendDirtyEntityData", at = @At("TAIL"))
    private void afterSendDirtyEntityData(CallbackInfo ci) {
        // 对于展示实体，我们需要确保只有授权玩家能看到
        // 这里不需要额外处理，因为 addPairing 已经阻止了未授权玩家的追踪
    }
}
