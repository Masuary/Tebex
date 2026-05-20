package com.github.xniter.tebexio;

import net.buycraft.plugin.BuyCraftAPI;
import net.buycraft.plugin.IBuycraftPlatform;
import net.buycraft.plugin.data.responses.ServerInformation;
import net.buycraft.plugin.execution.DuePlayerFetcher;
import net.buycraft.plugin.execution.strategy.CommandExecutor;
import net.buycraft.plugin.execution.strategy.PostCompletedCommandsTask;
import net.buycraft.plugin.shared.tasks.PlayerJoinCheckTask;

public class TebexShop {

    private final String name;
    private String serverKey;

    private IBuycraftPlatform platform;
    private BuyCraftAPI apiClient;
    private ServerInformation serverInformation;

    private DuePlayerFetcher duePlayerFetcher;
    private CommandExecutor commandExecutor;
    private PostCompletedCommandsTask completedCommandsTask;
    private PlayerJoinCheckTask playerJoinCheckTask;
    private ForgeScheduledTask analyticsTask;

    public TebexShop(String name, String serverKey) {
        this.name = name;
        this.serverKey = serverKey;
    }

    public String getName() {
        return name;
    }

    public String getServerKey() {
        return serverKey;
    }

    public void setServerKey(String serverKey) {
        this.serverKey = serverKey;
    }

    public IBuycraftPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(IBuycraftPlatform platform) {
        this.platform = platform;
    }

    public BuyCraftAPI getApiClient() {
        return apiClient;
    }

    public void setApiClient(BuyCraftAPI apiClient) {
        this.apiClient = apiClient;
    }

    public ServerInformation getServerInformation() {
        return serverInformation;
    }

    public void setServerInformation(ServerInformation serverInformation) {
        this.serverInformation = serverInformation;
    }

    public DuePlayerFetcher getDuePlayerFetcher() {
        return duePlayerFetcher;
    }

    public void setDuePlayerFetcher(DuePlayerFetcher duePlayerFetcher) {
        this.duePlayerFetcher = duePlayerFetcher;
    }

    public CommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    public void setCommandExecutor(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    public PostCompletedCommandsTask getCompletedCommandsTask() {
        return completedCommandsTask;
    }

    public void setCompletedCommandsTask(PostCompletedCommandsTask completedCommandsTask) {
        this.completedCommandsTask = completedCommandsTask;
    }

    public PlayerJoinCheckTask getPlayerJoinCheckTask() {
        return playerJoinCheckTask;
    }

    public void setPlayerJoinCheckTask(PlayerJoinCheckTask playerJoinCheckTask) {
        this.playerJoinCheckTask = playerJoinCheckTask;
    }

    public ForgeScheduledTask getAnalyticsTask() {
        return analyticsTask;
    }

    public void setAnalyticsTask(ForgeScheduledTask analyticsTask) {
        this.analyticsTask = analyticsTask;
    }

    public boolean isConfigured() {
        return serverKey != null && !serverKey.equalsIgnoreCase("INVALID") && !serverKey.isEmpty();
    }
}
