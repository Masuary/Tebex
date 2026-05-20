package com.github.xniter.tebexio.command;

import com.github.xniter.tebexio.TebexForged;
import com.github.xniter.tebexio.TebexShop;
import com.github.xniter.tebexio.util.CmdUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.buycraft.plugin.BuyCraftAPI;
import net.buycraft.plugin.data.responses.ServerInformation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;

import java.io.IOException;

public class SecretCmd implements Command<CommandSourceStack> {
    private final TebexForged plugin;

    public SecretCmd(final TebexForged plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (context.getSource().getEntity() != null) {
            ForgeMessageUtil.sendMessage(context.getSource(), new TextComponent(ForgeMessageUtil.format("secret_console_only"))
                    .setStyle(CmdUtil.ERROR_STYLE));
            return 0;
        }

        String shopName = StringArgumentType.getString(context, "shop");
        String secret = StringArgumentType.getString(context, "secret");

        plugin.getExecutor().submit(() -> {
            BuyCraftAPI client = BuyCraftAPI.create(secret, plugin.getHttpClient());
            ServerInformation information;
            try {
                information = client.getServerInformation().execute().body();
            } catch (IOException e) {
                plugin.getLogger().error("Unable to verify secret for shop '" + shopName + "'", e);
                ForgeMessageUtil.sendMessage(context.getSource(),
                        new TextComponent(ForgeMessageUtil.format("secret_does_not_work")).setStyle(CmdUtil.ERROR_STYLE));
                return;
            }

            ServerInformation validatedInfo = information;
            plugin.getServer().execute(() -> {
                TebexShop existing = plugin.getShop(shopName);
                boolean wasFresh = existing == null;

                plugin.addOrUpdateShop(shopName, secret, client, validatedInfo);

                try {
                    plugin.saveShopProperties();
                } catch (IOException e) {
                    ForgeMessageUtil.sendMessage(context.getSource(),
                            new TextComponent(ForgeMessageUtil.format("secret_cant_be_saved")).setStyle(CmdUtil.ERROR_STYLE));
                }

                if (validatedInfo != null) {
                    String verb = wasFresh ? "added" : "updated";
                    ForgeMessageUtil.sendMessage(context.getSource(),
                            new TextComponent("Shop '" + shopName + "' " + verb + " - linked to "
                                    + validatedInfo.getServer().getName()
                                    + " for " + validatedInfo.getAccount().getName() + ".").setStyle(CmdUtil.SUCCESS_STYLE));
                }
            });
        });
        return 1;
    }
}
