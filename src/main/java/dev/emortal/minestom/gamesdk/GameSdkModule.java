package dev.emortal.minestom.gamesdk;

import dev.emortal.api.modules.LoadableModule;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.ModuleData;
import dev.emortal.api.modules.ModuleEnvironment;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.command.GameSdkCommand;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.internal.AgonesGameHandler;
import dev.emortal.minestom.gamesdk.internal.GameManager;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ModuleData(name = "game-sdk", softDependencies = {KubernetesModule.class}, required = false)
public final class GameSdkModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameSdkModule.class);
    public static final boolean TEST_MODE = !Environment.isProduction() && Boolean.parseBoolean(System.getenv("GAME_SDK_TEST_MODE"));

    public static LoadableModule.@NotNull Creator create(@NotNull GameSdkConfig config) {
        return environment -> new GameSdkModule(environment, config);
    }

    private final GameSdkConfig config;
    private final GameManager gameManager;

    private GameSdkModule(ModuleEnvironment environment, GameSdkConfig config) {
        super(environment);
        this.config = config;
        this.gameManager = new GameManager(config);
    }

    @Override
    public boolean onLoad() {
        LOGGER.info("Initializing Game SDK (test mode: {}, config: {})", TEST_MODE, config);

        AgonesGameHandler.initialize(gameManager, config, getModule(KubernetesModule.class));
        MinecraftServer.getCommandManager().register(new GameSdkCommand(gameManager));

        return true;
    }

    @Override
    public void onUnload() {
    }
}
