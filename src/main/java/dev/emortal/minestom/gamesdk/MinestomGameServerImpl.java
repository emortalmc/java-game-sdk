package dev.emortal.minestom.gamesdk;

import dev.emortal.api.modules.LoadableModule;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.ModuleProvider;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.MinestomServer;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import dev.emortal.minestom.gamesdk.command.GameSdkCommand;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.GameProvider;
import dev.emortal.minestom.gamesdk.internal.AgonesGameListener;
import dev.emortal.minestom.gamesdk.internal.GameManager;
import dev.emortal.minestom.gamesdk.internal.listener.AgonesGameStatusListener;
import dev.emortal.minestom.gamesdk.internal.GameTracker;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

final class MinestomGameServerImpl implements MinestomGameServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomGameServerImpl.class);
    static final boolean TEST_MODE = !Environment.isProduction() && Boolean.parseBoolean(System.getenv("GAME_SDK_TEST_MODE"));

    private final GameProvider gameProvider;

    private MinestomGameServerImpl(@NotNull GameProvider gameProvider) {
        this.gameProvider = gameProvider;
    }

    @Override
    public @NotNull GameProvider getGameProvider() {
        return this.gameProvider;
    }

    static final class BuilderImpl implements Builder, Builder.EndStep {

        private final MinestomServer.Builder serverBuilder = MinestomServer.builder();
        private Supplier<GameSdkConfig> configSupplier;

        @Override
        public @NotNull Builder address(@NotNull String address) {
            this.serverBuilder.address(address);
            return this;
        }

        @Override
        public @NotNull Builder port(int port) {
            this.serverBuilder.port(port);
            return this;
        }

        @Override
        public @NotNull Builder mojangAuth(boolean mojangAuth) {
            this.serverBuilder.mojangAuth(mojangAuth);
            return this;
        }

        @Override
        public @NotNull Builder module(@NotNull Class<? extends Module> type, @NotNull LoadableModule.Creator creator) {
            this.serverBuilder.module(type, creator);
            return this;
        }

        @Override
        public @NotNull Builder commonModules() {
            this.serverBuilder.commonModules();
            return this;
        }

        @Override
        public @NotNull EndStep configSupplier(@NotNull Supplier<GameSdkConfig> configSupplier) {
            this.configSupplier = configSupplier;
            return this;
        }

        @Override
        public @NotNull MinestomGameServerImpl build() {
            MinestomServer server = this.serverBuilder.build();
            MinestomGameServerImpl gameServer = initialize(server.getModuleManager(), this.configSupplier.get());
            server.start();
            return gameServer;
        }

        private static @NotNull MinestomGameServerImpl initialize(@NotNull ModuleProvider moduleProvider, @NotNull GameSdkConfig config) {
            LOGGER.info("Initializing Game SDK (test mode: {}, config: {})", TEST_MODE, config);

            MessagingModule messaging = moduleProvider.getModule(MessagingModule.class);
            KubernetesModule kubernetesModule = moduleProvider.getModule(KubernetesModule.class);
            boolean hasAgones = kubernetesModule != null && kubernetesModule.getAgonesSdk() != null;

            GameManager gameManager = new GameManager(config);
            if (!TEST_MODE && hasAgones) {
                gameManager.addGameStatusListener(new AgonesGameStatusListener(gameManager, kubernetesModule));
            }

            if (messaging != null) {
                GameTracker gameTracker = new GameTracker(messaging, config);
                gameManager.addGameStatusListener(gameTracker);

                new AgonesGameListener(gameManager, config, messaging);
            }

            MinecraftServer.getCommandManager().register(new GameSdkCommand(gameManager));

            return new MinestomGameServerImpl(gameManager);
        }
    }
}
