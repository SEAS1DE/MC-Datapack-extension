package com.lootmatrix;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import org.joml.Matrix3x2fStack;

import java.util.logging.Logger;


public class DatapackExtensionClient implements ClientModInitializer {

    public static final String MOD_ID = "datapack_extension";
    private float totalTickProgress = 0;

    @Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "last_element"), hudLayer());
        
	}

    private HudElement hudLayer() {
        return (graphics, deltaTracker) -> {
            totalTickProgress += deltaTracker.getGameTimeDeltaPartialTick(true);

            // Push matrix
            Matrix3x2fStack matrices = graphics.pose();
            matrices.pushMatrix();

            // 简单旋转 + 缩放动画
            float scale = Mth.sin(totalTickProgress / 100f) / 2f + 1.5f;
            matrices.scale(scale, scale);
            float rotation = totalTickProgress / 50f % 360;
            matrices.rotate(0);
            matrices.translate(60f, 60f);

            // 绘制一个红色方块
            graphics.fill(0, 0, 20, 20, 0x80FF0000);

            // Pop matrix
            matrices.popMatrix();
        };
    }

}