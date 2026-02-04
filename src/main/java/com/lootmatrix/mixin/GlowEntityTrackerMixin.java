package com.lootmatrix.mixin;

import com.lootmatrix.glow.GlowManager;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 当实体进入玩家视野时，刷新发光效果
 * 这是纯服务端的 Mixin，不需要客户端安装
 */
@Mixin(ServerEntity.class)
public abstract class GlowEntityTrackerMixin {

    @Shadow
    @Final
    private Entity entity;

    /**
     * 当实体开始被玩家追踪时（进入视野），刷新发光效果
     */
    @Inject(method = "addPairing", at = @At("TAIL"))
    private void onStartTracking(ServerPlayer player, CallbackInfo ci) {
        // 实体进入玩家视野后，刷新发光效果
        GlowManager.refreshGlow(player, this.entity);
    }
}
