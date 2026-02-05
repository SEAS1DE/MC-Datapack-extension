package com.lootmatrix.mixin;

import com.lootmatrix.glow.GlowColor;
import com.lootmatrix.glow.GlowManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 拦截服务端发送的队伍包，防止与虚拟发光队伍冲突
 * 这是纯服务端的 Mixin，不需要客户端安装
 *
 * 问题场景：
 * 1. 玩家A在服务器真实队伍"Orange"中
 * 2. 我们给玩家A添加了虚拟发光效果，将其加入虚拟队伍"glow_gold"
 * 3. 服务器发送REMOVE包将玩家A从"Orange"移除
 * 4. 客户端收到包后尝试从"Orange"移除玩家A，但玩家A在客户端是在"glow_gold"中
 * 5. 客户端抛出异常："Player is either on another team or not on any team"
 *
 * 解决方案：
 * 拦截发往有发光效果的玩家的队伍REMOVE包，过滤掉那些可能导致冲突的实体
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class GlowTeamPacketMixin {

    // 缓存所有发光队伍名称 - 懒加载
    @Unique
    private static Set<String> glowTeamNames;

    /**
     * 获取发光队伍名称集合（懒加载）
     */
    @Unique
    private static Set<String> getGlowTeamNames() {
        if (glowTeamNames == null) {
            glowTeamNames = new HashSet<>();
            for (GlowColor color : GlowColor.values()) {
                glowTeamNames.add(color.getTeamName());
            }
        }
        return glowTeamNames;
    }

    /**
     * 拦截发送给玩家的所有数据包
     * 过滤掉可能导致队伍状态冲突的包
     */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSend(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ClientboundSetPlayerTeamPacket teamPacket)) {
            return;
        }

        // 如果是内部发送的恢复包，不要拦截
        if (GlowManager.isSendingInternalPacket()) {
            return;
        }

        String teamName = teamPacket.getName();
        Collection<String> players = teamPacket.getPlayers();

        // 如果是发光队伍的包，不要拦截（这是我们自己发送的）
        if (getGlowTeamNames().contains(teamName)) {
            return;
        }

        // 只处理有成员变更的包（players 非空意味着是 ADD 或 REMOVE 操作）
        if (players.isEmpty()) {
            return;
        }

        // 当包含玩家列表时，检查是否有发光效果的实体
        // 这些包是 ADD_PLAYERS 或 REMOVE_PLAYERS 操作
        for (String playerName : players) {
            if (GlowManager.hasGlowForAnyViewer(playerName)) {
                // 取消这个包，因为客户端上这些实体可能在虚拟发光队伍中
                ci.cancel();
                return;
            }
        }
    }
}
