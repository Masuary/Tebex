package com.github.xniter.tebexio.command;

import com.github.xniter.tebexio.TebexForged;
import com.github.xniter.tebexio.TebexShop;
import com.github.xniter.tebexio.util.CmdUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;

public class ShopsCmd implements Command<CommandSourceStack> {
    private final TebexForged plugin;

    public ShopsCmd(final TebexForged plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        if (!plugin.hasShops()) {
            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent("No Tebex shops are configured. Use /tebex secret <shop> <key> to add one.")
                            .setStyle(CmdUtil.INFO_STYLE));
            return 1;
        }

        ForgeMessageUtil.sendMessage(context.getSource(),
                new TextComponent("Configured Tebex shops:").setStyle(CmdUtil.INFO_STYLE));

        for (TebexShop shop : plugin.getShops()) {
            if (shop.getServerInformation() != null) {
                String line = " - " + shop.getName()
                        + " -> " + shop.getServerInformation().getServer().getName()
                        + " (" + shop.getServerInformation().getAccount().getDomain() + ")";
                ForgeMessageUtil.sendMessage(context.getSource(),
                        new TextComponent(line).setStyle(CmdUtil.SUCCESS_STYLE));
            } else {
                String line = " - " + shop.getName() + " (unverified - key may be invalid or Tebex unreachable)";
                ForgeMessageUtil.sendMessage(context.getSource(),
                        new TextComponent(line).setStyle(CmdUtil.ERROR_STYLE));
            }
        }
        return 1;
    }
}
