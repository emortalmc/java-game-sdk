package dev.emortal.minestom.gamesdk;

import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.AgonesListener;
import dev.emortal.minestom.gamesdk.game.GameManager;
import org.jetbrains.annotations.NotNull;

public final class GameSdkModule extends Module {
    private static GameSdkConfig config;
    private static GameManager gameManager;
    private static ModuleEnvironment environment;

    public GameSdkModule(ModuleEnvironment environment) {
        super(environment);
        GameSdkModule.environment = environment;
    }

    public static void init(@NotNull GameSdkConfig config) {
        GameSdkModule.config = config;
        GameSdkModule.gameManager = new GameManager(environment, config);
        new AgonesListener(environment, config, gameManager);
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
