package com.github.xniter.tebexio.command;

import com.github.xniter.tebexio.TebexForged;
import com.github.xniter.tebexio.TebexShop;
import com.github.xniter.tebexio.util.CmdUtil;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.buycraft.plugin.shared.util.ReportBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.TextComponent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ReportCmd implements Command<CommandSourceStack> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");

    private final TebexForged plugin;

    public ReportCmd(final TebexForged plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (!plugin.hasShops()) {
            ForgeMessageUtil.sendMessage(context.getSource(),
                    new TextComponent("No shops configured - nothing to report.").setStyle(CmdUtil.ERROR_STYLE));
            return 1;
        }

        ForgeMessageUtil.sendMessage(context.getSource(), new TextComponent(ForgeMessageUtil.format("report_wait"))
                .setStyle(CmdUtil.SUCCESS_STYLE));

        plugin.getExecutor().submit(() -> {
            String serverIP = plugin.getServer().getLocalIp().trim().isEmpty() ? "0.0.0.0" : plugin.getServer().getLocalIp().trim();
            String timestamp = DATE_FORMAT.format(new Date());

            for (TebexShop shop : plugin.getShops()) {
                ReportBuilder builder = ReportBuilder.builder()
                        .client(plugin.getHttpClient())
                        .configuration(plugin.getConfiguration())
                        .platform(shop.getPlatform())
                        .duePlayerFetcher(shop.getDuePlayerFetcher())
                        .ip(serverIP)
                        .port(plugin.getServer().getPort())
                        .serverOnlineMode(plugin.getServer().usesAuthentication())
                        .build();

                String filename = "report-" + shop.getName() + "-" + timestamp + ".txt";
                Path p = plugin.getBaseDirectory().resolve(filename);
                String generated = builder.generate();

                try (BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                    w.write(generated);
                    ForgeMessageUtil.sendMessage(context.getSource(),
                            new TextComponent(ForgeMessageUtil.format("report_saved", p.toAbsolutePath().toString()))
                                    .setStyle(CmdUtil.INFO_STYLE));
                } catch (IOException e) {
                    ForgeMessageUtil.sendMessage(context.getSource(),
                            new TextComponent(ForgeMessageUtil.format("report_cant_save"))
                                    .setStyle(CmdUtil.ERROR_STYLE));
                    plugin.getLogger().info(generated);
                }
            }
        });
        return 1;
    }
}
