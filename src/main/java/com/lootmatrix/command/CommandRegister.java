package com.lootmatrix.command;

public class CommandRegister {

    public static void register() {
        CalculateDistance.register();
        GlowCommand.register();
        DisplayVisibilityCommand.register();
        CanSeeCommand.register();
    }
}
