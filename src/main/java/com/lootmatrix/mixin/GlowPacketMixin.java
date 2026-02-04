package com.lootmatrix.mixin;

import com.lootmatrix.glow.GlowManager;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截实体数据同步，确保发光效果持续生效
 * 这是纯服务端的 Mixin，不需要客户端安装
 */
@Mixin(ServerEntity.class)
public abstract class GlowPacketMixin {

    @Shadow
    @Final
    private Entity entity;

    /**
     * 在实体数据发送后，补发发光效果
     * 这确保服务器同步数据时不会覆盖我们的发光标志
     */
    @Inject(method = "sendDirtyEntityData", at = @At("TAIL"))
    private void afterSendDirtyEntityData(CallbackInfo ci) {
        // 获取追踪这个实体的所有玩家，对有发光效果的重新发送发光数据包
        // 这个方法在实体数据被标记为脏并发送后调用
        GlowManager.refreshGlowForEntity(this.entity);
    }
}
