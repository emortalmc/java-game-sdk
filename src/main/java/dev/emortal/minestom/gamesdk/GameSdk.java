package dev.emortal.minestom.gamesdk;

import dev.emortal.minestom.core.module.Module;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.AgonesListener;
import dev.emortal.minestom.gamesdk.game.GameManager;
import org.jetbrains.annotations.NotNull;

public final class GameSdk extends Module {
    private static GameSdkConfig config;
    private static GameManager gameManager;
    private static ModuleEnvironment environment;

    private GameSdk(ModuleEnvironment environment) {
        super(environment);
        GameSdk.environment = environment;
    }

    public static void init(@NotNull GameSdkConfig config) {
        GameSdk.config = config;
        GameSdk.gameManager = new GameManager(environment, config);
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
