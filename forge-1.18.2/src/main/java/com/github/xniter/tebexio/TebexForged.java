package com.github.xniter.tebexio;

import com.github.xniter.tebexio.command.*;
import com.github.xniter.tebexio.util.VersionCheck;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.buycraft.plugin.BuyCraftAPI;
import net.buycraft.plugin.BuyCraftAPIException;
import net.buycraft.plugin.data.QueuedPlayer;
import net.buycraft.plugin.data.responses.ServerInformation;
import net.buycraft.plugin.execution.DuePlayerFetcher;
import net.buycraft.plugin.execution.placeholder.NamePlaceholder;
import net.buycraft.plugin.execution.placeholder.PlaceholderManager;
import net.buycraft.plugin.execution.placeholder.UuidPlaceholder;
import net.buycraft.plugin.execution.strategy.PostCompletedCommandsTask;
import net.buycraft.plugin.execution.strategy.QueuedCommandExecutor;
import net.buycraft.plugin.shared.Setup;
import net.buycraft.plugin.shared.config.BuycraftConfiguration;
import net.buycraft.plugin.shared.tasks.PlayerJoinCheckTask;
import net.buycraft.plugin.shared.util.AnalyticsSend;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartingEvent;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod("tebexforged")
public class TebexForged {

    public static final String LEGACY_SERVER_KEY = "server-key";
    public static final String SHOP_KEY_PREFIX = "server-key.";
    public static final String DEFAULT_SHOP_NAME = "default";

    private static final Logger LOGGER = LogManager.getLogger("Tebex");

    private final String pluginVersion;

    private final PlaceholderManager placeholderManager = new PlaceholderManager();
    private static final BuycraftConfiguration configuration = new BuycraftConfiguration();
    private final Properties shopProperties = new Properties();
    private final Path baseDirectory = Paths.get("config", "buycraft");

    private final List<ForgeScheduledTask> scheduledTasks = new CopyOnWriteArrayList<>();
    private final Map<String, TebexShop> shops = Collections.synchronizedMap(new LinkedHashMap<>());

    private MinecraftServer server;
    private static ScheduledExecutorService executor;

    private OkHttpClient httpClient;

    private boolean stopped = false;

    public TebexForged() {
        pluginVersion = ModLoadingContext.get().getActiveContainer().getModInfo().getVersion().toString();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(configureCommand(Commands.literal("tebex")));
        event.getDispatcher().register(configureCommand(Commands.literal("buycraft")));

        if (!configuration.isDisableBuyCommand()) {
            configuration.getBuyCommandName().forEach(cmd ->
                    event.getDispatcher().register(Commands.literal(cmd).executes(new BuyCommand(this))));
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        MinecraftServer minecraftServer = event.getServer();
        if (!minecraftServer.isDedicatedServer()) {
            return;
        }

        server = event.getServer();
        executor = Executors.newScheduledThreadPool(2, r -> new Thread(r, "Buycraft Scheduler Thread"));

        try {
            try {
                Files.createDirectory(baseDirectory);
            } catch (FileAlreadyExistsException ignored) {
            }
            Path configPath = baseDirectory.resolve("config.properties");
            try {
                configuration.load(configPath);
                loadShopProperties(configPath);
            } catch (NoSuchFileException e) {
                configuration.fillDefaults();
                configuration.save(configPath);
                loadShopProperties(configPath);
            }
        } catch (IOException e) {
            getLogger().error("Unable to load configuration! The plugin will disable itself now.", e);
            return;
        }

        getLogger().warn("Forcing english translations while we wait on a forge bugfix!");
        httpClient = Setup.okhttp(baseDirectory.resolve("cache").toFile());

        boolean migratedLegacy = migrateLegacyServerKey();
        Map<String, String> discoveredKeys = discoverShopKeys();
        if (discoveredKeys.isEmpty()) {
            getLogger().info("Looks like this is a fresh setup. Get started by using '/tebex secret <shop> <key>' in the console.");
        } else {
            for (Map.Entry<String, String> entry : discoveredKeys.entrySet()) {
                bootstrapShop(entry.getKey(), entry.getValue());
            }
        }

        if (migratedLegacy) {
            try {
                saveShopProperties();
                getLogger().info("Migrated legacy 'server-key=' entry to 'server-key." + DEFAULT_SHOP_NAME + "='.");
            } catch (IOException e) {
                getLogger().warn("Could not persist legacy server-key migration", e);
            }
        }

        placeholderManager.addPlaceholder(new NamePlaceholder());
        placeholderManager.addPlaceholder(new UuidPlaceholder());

        if (configuration.isCheckForUpdates()) {
            String anyKey = firstConfiguredKey();
            if (anyKey != null) {
                VersionCheck check = new VersionCheck(this, pluginVersion, anyKey);
                try {
                    check.verify();
                } catch (IOException e) {
                    getLogger().error("Can't check for updates", e);
                }
                MinecraftForge.EVENT_BUS.register(check);
            }
        }
    }

    private String firstConfiguredKey() {
        synchronized (shops) {
            for (TebexShop shop : shops.values()) {
                if (shop.isConfigured()) return shop.getServerKey();
            }
        }
        return null;
    }

    private void loadShopProperties(Path configPath) throws IOException {
        shopProperties.clear();
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            shopProperties.load(reader);
        }
    }

    private boolean migrateLegacyServerKey() {
        String legacy = shopProperties.getProperty(LEGACY_SERVER_KEY, "").trim();
        if (legacy.isEmpty() || legacy.equalsIgnoreCase("INVALID")) {
            return false;
        }
        boolean hasNamed = false;
        for (String name : shopProperties.stringPropertyNames()) {
            if (name.startsWith(SHOP_KEY_PREFIX)) {
                hasNamed = true;
                break;
            }
        }
        if (hasNamed) {
            shopProperties.remove(LEGACY_SERVER_KEY);
            return true;
        }
        shopProperties.setProperty(SHOP_KEY_PREFIX + DEFAULT_SHOP_NAME, legacy);
        shopProperties.remove(LEGACY_SERVER_KEY);
        return true;
    }

    private Map<String, String> discoverShopKeys() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String propertyName : shopProperties.stringPropertyNames()) {
            if (propertyName.startsWith(SHOP_KEY_PREFIX)) {
                String name = propertyName.substring(SHOP_KEY_PREFIX.length()).trim();
                if (name.isEmpty()) continue;
                String key = shopProperties.getProperty(propertyName, "").trim();
                if (key.isEmpty() || key.equalsIgnoreCase("INVALID")) continue;
                result.put(name, key);
            }
        }
        return result;
    }

    private void bootstrapShop(String name, String serverKey) {
        getLogger().info("Validating server key for shop '" + name + "'...");
        BuyCraftAPI client = BuyCraftAPI.create(serverKey, httpClient);
        ServerInformation info = null;
        try {
            info = client.getServerInformation().execute().body();
            if (info != null) {
                getLogger().info("Shop '" + name + "' linked to server " + info.getServer().getName() + " (" + info.getAccount().getName() + ").");
            }
        } catch (IOException e) {
            getLogger().error(String.format("Cannot reach Tebex for shop '%s': %s", name, e.getMessage()));
        } catch (BuyCraftAPIException e) {
            getLogger().error(String.format("Tebex server key for shop '%s' appears invalid: %s", name, e.getMessage()));
        } catch (Exception e) {
            getLogger().error(String.format("Unexpected exception while validating shop '%s': %s", name, e.getMessage()));
        }
        registerShop(name, serverKey, client, info);
    }

    private TebexShop registerShop(String name, String serverKey, BuyCraftAPI client, ServerInformation info) {
        TebexShop shop = new TebexShop(name, serverKey);
        ForgeBuycraftPlatform shopPlatform = new ForgeBuycraftPlatform(this, shop);
        shop.setPlatform(shopPlatform);
        shop.setApiClient(client);
        shop.setServerInformation(info);

        DuePlayerFetcher duePlayerFetcher = new DuePlayerFetcher(shopPlatform, configuration.isVerbose());
        PostCompletedCommandsTask completedCommandsTask = new PostCompletedCommandsTask(shopPlatform);
        QueuedCommandExecutor commandExecutor = new QueuedCommandExecutor(shopPlatform, completedCommandsTask);
        PlayerJoinCheckTask playerJoinCheckTask = new PlayerJoinCheckTask(shopPlatform);

        shop.setDuePlayerFetcher(duePlayerFetcher);
        shop.setCompletedCommandsTask(completedCommandsTask);
        shop.setCommandExecutor(commandExecutor);
        shop.setPlayerJoinCheckTask(playerJoinCheckTask);

        shopPlatform.executeAsyncLater(duePlayerFetcher, 1, TimeUnit.SECONDS);
        scheduledTasks.add(ForgeScheduledTask.Builder.create().withInterval(1).withDelay(1).withTask(commandExecutor).build());
        scheduledTasks.add(ForgeScheduledTask.Builder.create().withAsync(true).withInterval(20).withDelay(20).withTask(completedCommandsTask).build());
        scheduledTasks.add(ForgeScheduledTask.Builder.create().withInterval(20).withDelay(20).withTask(playerJoinCheckTask).build());

        if (info != null) {
            ForgeScheduledTask analyticsTask = ForgeScheduledTask.Builder.create()
                    .withAsync(true)
                    .withInterval(20 * 60 * 60 * 24)
                    .withTask(() -> {
                        try {
                            AnalyticsSend.postServerInformation(httpClient, shop.getServerKey(), shopPlatform, server.usesAuthentication());
                        } catch (IOException e) {
                            getLogger().warn("Can't send analytics for shop '" + shop.getName() + "'", e);
                        }
                    })
                    .build();
            shop.setAnalyticsTask(analyticsTask);
            scheduledTasks.add(analyticsTask);
        }

        shops.put(name, shop);
        return shop;
    }

    public TebexShop addOrUpdateShop(String name, String serverKey, BuyCraftAPI client, ServerInformation info) {
        TebexShop existing = shops.get(name);
        if (existing != null) {
            existing.setServerKey(serverKey);
            existing.setApiClient(client);
            if (info != null) {
                existing.setServerInformation(info);
            }
            shopProperties.setProperty(SHOP_KEY_PREFIX + name, serverKey);
            return existing;
        }
        TebexShop shop = registerShop(name, serverKey, client, info);
        shopProperties.setProperty(SHOP_KEY_PREFIX + name, serverKey);
        return shop;
    }

    public boolean removeShop(String name) {
        TebexShop shop = shops.remove(name);
        if (shop == null) {
            return false;
        }
        List<ForgeScheduledTask> toRemove = new ArrayList<>();
        for (ForgeScheduledTask task : scheduledTasks) {
            Runnable r = task.getTask();
            if (r == shop.getCommandExecutor()
                    || r == shop.getCompletedCommandsTask()
                    || r == shop.getPlayerJoinCheckTask()
                    || (shop.getAnalyticsTask() != null && task == shop.getAnalyticsTask())) {
                toRemove.add(task);
            }
        }
        scheduledTasks.removeAll(toRemove);
        shop.setApiClient(null);
        shopProperties.remove(SHOP_KEY_PREFIX + name);
        return true;
    }

    public void saveShopProperties() throws IOException {
        Path configPath = baseDirectory.resolve("config.properties");
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            shopProperties.store(writer, "BuycraftX configuration file");
        }
    }

    private SuggestionProvider<CommandSourceStack> shopNameSuggestions() {
        return (ctx, builder) -> {
            String remaining = builder.getRemaining().toLowerCase();
            for (String name : shopNames()) {
                if (name.toLowerCase().startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    private List<String> shopNames() {
        synchronized (shops) {
            return new ArrayList<>(shops.keySet());
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> configureCommand(LiteralArgumentBuilder<CommandSourceStack> command) {
        CouponCmd couponCmd = new CouponCmd(this);
        SuggestionProvider<CommandSourceStack> shopSuggestions = shopNameSuggestions();
        return command
                .then(Commands.literal("shops").executes(new ShopsCmd(this)))
                .then(Commands.literal("report").executes(new ReportCmd(this)))
                .then(Commands.literal("info")
                        .then(Commands.argument("shop", StringArgumentType.word())
                                .suggests(shopSuggestions)
                                .executes(new InfoCmd(this))))
                .then(Commands.literal("forcecheck")
                        .then(Commands.argument("shop", StringArgumentType.word())
                                .suggests(shopSuggestions)
                                .executes(new ForceCheckCmd(this))))
                .then(Commands.literal("secret")
                        .then(Commands.argument("shop", StringArgumentType.word())
                                .suggests(shopSuggestions)
                                .then(Commands.argument("secret", StringArgumentType.word()).executes(new SecretCmd(this)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("shop", StringArgumentType.word())
                                .suggests(shopSuggestions)
                                .executes(new RemoveShopCmd(this))))
                .then(Commands.literal("coupon")
                        .then(Commands.argument("shop", StringArgumentType.word())
                                .suggests(shopSuggestions)
                                .then(Commands.literal("create")
                                        .then(Commands.argument("data", StringArgumentType.greedyString()).executes(couponCmd::create)))
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("code", StringArgumentType.word()).executes(couponCmd::delete)))));
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer().getServer() == null || !event.getPlayer().getServer().isDedicatedServer()) {
            return;
        }
        String playerName = event.getPlayer().getName().getString();
        for (TebexShop shop : getShops()) {
            if (shop.getApiClient() == null || shop.getDuePlayerFetcher() == null) {
                continue;
            }
            QueuedPlayer qp = shop.getDuePlayerFetcher().fetchAndRemoveDuePlayer(playerName);
            if (qp != null) {
                shop.getPlayerJoinCheckTask().queue(qp);
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (stopped || server == null || !server.isDedicatedServer() || event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ForgeScheduledTask task : scheduledTasks) {
            if (task.getCurrentDelay() > 0) {
                task.setCurrentDelay(task.getCurrentDelay() - 1);
                continue;
            }

            if (task.getInterval() > -1 && task.getCurrentIntervalTicks() > 0) {
                task.setCurrentIntervalTicks(task.getCurrentIntervalTicks() - 1);
                continue;
            }

            if (!task.isAsync()) {
                try {
                    task.getTask().run();
                } catch (Exception e) {
                    LOGGER.error("Error executing scheduled task!", e);
                }
            } else {
                executor.submit(task.getTask());
            }

            if (task.getInterval() > -1) {
                task.setCurrentIntervalTicks(task.getInterval());
            }
        }
        scheduledTasks.removeIf(task -> task.getCurrentDelay() <= 0 && task.getInterval() <= -1);
    }

    @SubscribeEvent
    public void serverStopping(ServerStoppedEvent event) {
        int shopCount = shops.size();
        event.getServer().executeBlocking(() -> {
            scheduledTasks.clear();
            if (!executor.isTerminated() || !executor.isShutdown()) {
                executor.shutdownNow();
            }
            try {
                if (httpClient != null && !httpClient.cache().isClosed()) {
                    httpClient.cache().flush();
                    httpClient.cache().close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (httpClient != null) {
                httpClient.dispatcher().cancelAll();
                httpClient.connectionPool().evictAll();
            }
            httpClient = null;
        });

        shops.clear();
        this.stopped = true;
        LOGGER.info("Unloaded Tebex successfully (" + shopCount + " shop" + (shopCount == 1 ? "" : "s") + " released).");
    }

    public Logger getLogger() {
        return LOGGER;
    }

    public final String getPluginVersion() {
        return pluginVersion;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }

    public BuycraftConfiguration getConfiguration() {
        return configuration;
    }

    public Path getBaseDirectory() {
        return baseDirectory;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public TebexShop getShop(String name) {
        return shops.get(name);
    }

    public List<TebexShop> getShops() {
        synchronized (shops) {
            return new ArrayList<>(shops.values());
        }
    }

    public boolean hasShops() {
        return !shops.isEmpty();
    }

    public boolean hasVerifiedShop() {
        synchronized (shops) {
            for (TebexShop shop : shops.values()) {
                if (shop.getServerInformation() != null) return true;
            }
        }
        return false;
    }
}
