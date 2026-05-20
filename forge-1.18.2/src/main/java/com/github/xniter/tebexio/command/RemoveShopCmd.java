package com.github.xniter.tebexio.command;

import com.github.xniter.tebexio.TebexForged;
import com.github.xniter.tebexio.util.CmdUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;

import java.io.IOException;

public class RemoveShopCmd implements Command<CommandSourceStack> {
    private final TebexForged plugin;

    public RemoveShopCmd(final TebexForged plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() != null) {
            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent(ForgeMessageUtil.format("secret_console_only"))
                            .setStyle(CmdUtil.ERROR_STYLE));
            return 0;
        }

        String shopName = StringArgumentType.getString(context, "shop");
        if (!plugin.removeShop(shopName)) {
            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent("No shop named '" + shopName + "' was found.")
                            .setStyle(CmdUtil.ERROR_STYLE));
            return 0;
        }

        try {
            plugin.saveShopProperties();
        } catch (IOException e) {
            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent("Shop removed from memory, but the configuration file could not be updated: " + e.getMessage())
                            .setStyle(CmdUtil.ERROR_STYLE));
            return 1;
        }

        ForgeMessageUtil.sendMessage(context.getSource(),
                new TextComponent("Removed shop '" + shopName + "'.")
                        .setStyle(CmdUtil.SUCCESS_STYLE));
        return 1;
    }
}
