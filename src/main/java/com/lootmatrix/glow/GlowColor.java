package com.lootmatrix.glow;

import net.minecraft.ChatFormatting;

/**
 * 发光颜色枚举，对应预创建的队伍颜色
 */
public enum GlowColor {
    BLACK("glow_black", ChatFormatting.BLACK),
    DARK_BLUE("glow_dark_blue", ChatFormatting.DARK_BLUE),
    DARK_GREEN("glow_dark_green", ChatFormatting.DARK_GREEN),
    DARK_AQUA("glow_dark_aqua", ChatFormatting.DARK_AQUA),
    DARK_RED("glow_dark_red", ChatFormatting.DARK_RED),
    DARK_PURPLE("glow_dark_purple", ChatFormatting.DARK_PURPLE),
    GOLD("glow_gold", ChatFormatting.GOLD),
    GRAY("glow_gray", ChatFormatting.GRAY),
    DARK_GRAY("glow_dark_gray", ChatFormatting.DARK_GRAY),
    BLUE("glow_blue", ChatFormatting.BLUE),
    GREEN("glow_green", ChatFormatting.GREEN),
    AQUA("glow_aqua", ChatFormatting.AQUA),
    RED("glow_red", ChatFormatting.RED),
    LIGHT_PURPLE("glow_light_purple", ChatFormatting.LIGHT_PURPLE),
    YELLOW("glow_yellow", ChatFormatting.YELLOW),
    WHITE("glow_white", ChatFormatting.WHITE);

    private final String teamName;
    private final ChatFormatting color;

    GlowColor(String teamName, ChatFormatting color) {
        this.teamName = teamName;
        this.color = color;
    }

    public String getTeamName() {
        return teamName;
    }

    public ChatFormatting getColor() {
        return color;
    }
}
