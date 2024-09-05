package dev.emortal.minestom.gamesdk;

import dev.emortal.api.modules.LoadableModule;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.ModuleManager;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.GameProvider;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public interface MinestomGameServer {
    boolean TEST_MODE = MinestomGameServerImpl.TEST_MODE;

    static @NotNull Builder builder() {
        return new MinestomGameServerImpl.BuilderImpl();
    }

    static @NotNull MinestomGameServer create(@NotNull Supplier<GameSdkConfig> configSupplier) {
        return builder().commonModules().configSupplier(configSupplier).build();
    }

    @NotNull GameProvider getGameProvider();

    @NotNull ModuleManager getModuleManager();

    interface Builder {

        @NotNull Builder address(@NotNull String address);

        @NotNull Builder port(int port);

        @NotNull Builder mojangAuth(boolean mojangAuth);

        @NotNull Builder module(@NotNull Class<? extends Module> type, @NotNull LoadableModule.Creator creator);

        @NotNull Builder commonModules();

        @NotNull Builder.EndStep configSupplier(@NotNull Supplier<GameSdkConfig> configSupplier);

        interface EndStep {

            @NotNull MinestomGameServer build();
        }
    }
}
