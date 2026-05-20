package com.github.xniter.tebexio.command;

import com.github.xniter.tebexio.TebexForged;
import com.github.xniter.tebexio.TebexShop;
import com.github.xniter.tebexio.util.CmdUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextComponent;

import java.util.stream.Stream;

public class InfoCmd implements Command<CommandSourceStack> {
    private final TebexForged plugin;

    public InfoCmd(final TebexForged plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) {
        String shopName = StringArgumentType.getString(context, "shop");
        TebexShop shop = plugin.getShop(shopName);

        if (shop == null || shop.getApiClient() == null) {
            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent("No shop named '" + shopName + "' is configured.")
                            .setStyle(CmdUtil.ERROR_STYLE));
            return 1;
        }

        if (shop.getServerInformation() == null) {
            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent(ForgeMessageUtil.format("information_no_server"))
                            .setStyle(CmdUtil.ERROR_STYLE));
            return 1;
        }

        String webstoreURL = shop.getServerInformation().getAccount().getDomain();

        Component webstore = new TextComponent(webstoreURL)
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, webstoreURL))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent(webstoreURL))));

        Component server = new TextComponent(shop.getServerInformation().getServer().getName()).withStyle(ChatFormatting.GREEN);

        Stream.of(
                new TextComponent("Shop '" + shopName + "' information:").withStyle(ChatFormatting.GRAY),
                new TextComponent(ForgeMessageUtil.format("information_sponge_server") + " ").withStyle(ChatFormatting.GRAY).append(server),
                new TextComponent(ForgeMessageUtil.format("information_currency", shop.getServerInformation().getAccount().getCurrency().getIso4217()))
                        .withStyle(ChatFormatting.GRAY),
                new TextComponent(ForgeMessageUtil.format("information_domain", "")).withStyle(ChatFormatting.GRAY).append(webstore)
        ).forEach(message -> ForgeMessageUtil.sendMessage(context.getSource(), message));

        return 1;
    }
}
