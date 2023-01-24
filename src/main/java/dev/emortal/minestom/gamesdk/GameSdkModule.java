package dev.emortal.minestom.gamesdk;

import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleData;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.command.GameSdkCommand;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.AgonesListener;
import dev.emortal.minestom.gamesdk.game.GameManager;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ModuleData(name = "game-sdk", softDependencies = {KubernetesModule.class}, required = false)
public final class GameSdkModule extends Module {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameSdkModule.class);
    public static final boolean TEST_MODE;

    private static GameSdkConfig config;
    private static GameManager gameManager;
    private static ModuleEnvironment environment;

    static {
        TEST_MODE = !Environment.isProduction() && Boolean.parseBoolean(System.getenv("GAME_SDK_TEST_MODE"));
    }

    public GameSdkModule(ModuleEnvironment environment) {
        super(environment);
        GameSdkModule.environment = environment;
    }

    public static void init(@NotNull GameSdkConfig config) {
        LOGGER.info("Initializing Game SDK (test mode: {}, config: {})", TEST_MODE, config);

        GameSdkModule.config = config;
        GameSdkModule.gameManager = new GameManager(environment, config);
        new AgonesListener(environment, config, gameManager);

        MinecraftServer.getCommandManager().register(new GameSdkCommand(GameSdkModule.gameManager, config));
    }

    public static GameManager getGameManager() {
        return gameManager;
    }

    @Override
    public boolean onLoad() {
        return false;
    }

    @Override
    public void onUnload() {

    }
}
