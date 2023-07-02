package dev.emortal.minestom.gamesdk;

import dev.emortal.api.modules.ModuleProvider;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.MinestomServer;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.command.GameSdkCommand;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.internal.AgonesGameHandler;
import dev.emortal.minestom.gamesdk.internal.GameManager;
import dev.emortal.minestom.gamesdk.internal.listener.AgonesGameUpdateListener;
import dev.emortal.minestom.gamesdk.internal.listener.GameUpdateListener;
import java.util.function.Supplier;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MinestomGameServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomGameServer.class);
    public static final boolean TEST_MODE = !Environment.isProduction() && Boolean.parseBoolean(System.getenv("GAME_SDK_TEST_MODE"));

    public static void create(@NotNull Supplier<GameSdkConfig> configSupplier) {
        var server = MinestomServer.builder().commonModules().build();
        initGameSdk(server.getModuleManager(), configSupplier.get());
        server.start();
    }

    private static void initGameSdk(ModuleProvider moduleProvider, GameSdkConfig config) {
        var kubernetesModule = moduleProvider.getModule(KubernetesModule.class);
        boolean hasAgones = kubernetesModule != null && kubernetesModule.getSdk() != null;

        LOGGER.info("Initializing Game SDK (test mode: {}, config: {})", TEST_MODE, config);

        var updateListener = TEST_MODE || !hasAgones ? GameUpdateListener.NO_OP : new AgonesGameUpdateListener(config, kubernetesModule.getSdk());
        var gameManager = new GameManager(config, updateListener);

        if (hasAgones) {
            new AgonesGameHandler(gameManager, config, kubernetesModule.getSdk());
        }
        MinecraftServer.getCommandManager().register(new GameSdkCommand(gameManager));
    }

    private MinestomGameServer() {
        throw new AssertionError("This class cannot be instantiated.");
    }
}
