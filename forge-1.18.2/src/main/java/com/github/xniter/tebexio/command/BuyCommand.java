package com.github.xniter.tebexio.command;

import com.github.xniter.tebexio.TebexForged;
import com.github.xniter.tebexio.TebexShop;
import com.github.xniter.tebexio.util.CmdUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.TextComponent;

public class BuyCommand implements Command<CommandSourceStack> {

    private final TebexForged plugin;

    public BuyCommand(TebexForged plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!plugin.hasShops()) {
            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent(ForgeMessageUtil.format("information_no_server"))
                            .setStyle(CmdUtil.ERROR_STYLE));
            return 1;
        }

        if (!plugin.hasVerifiedShop()) {
            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent("The webstore is temporarily unreachable. Please try again later.")
                            .setStyle(CmdUtil.ERROR_STYLE));
            return 1;
        }

        ForgeMessageUtil.sendMessage(context.getSource(),
                new TextComponent("                                            ").withStyle(ChatFormatting.STRIKETHROUGH));

        for (TebexShop shop : plugin.getShops()) {
            if (shop.getServerInformation() == null) {
                continue;
            }
            String domain = shop.getServerInformation().getAccount().getDomain();
            String label = shop.getName();

            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent("Click to visit ")
                            .withStyle(style -> style.withColor(ChatFormatting.GREEN))
                            .append(new TextComponent(label)
                                    .withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)))
                            .append(new TextComponent(":")
                                    .withStyle(style -> style.withColor(ChatFormatting.GREEN))));

            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent(domain).withStyle(style -> style.withColor(ChatFormatting.BLUE)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, domain))));
        }

        ForgeMessageUtil.sendMessage(context.getSource(),
                new TextComponent("                                            ").withStyle(ChatFormatting.STRIKETHROUGH));
        return 1;
    }
}
