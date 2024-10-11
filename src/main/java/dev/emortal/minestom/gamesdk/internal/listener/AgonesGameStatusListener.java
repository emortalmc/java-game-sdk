package dev.emortal.minestom.gamesdk.internal.listener;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.agones.sdk.beta.BetaAgonesSDKProto;
import dev.emortal.api.agonessdk.IgnoredStreamObserver;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.Game;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.emortal.minestom.gamesdk.game.GameProvider;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AgonesGameStatusListener implements GameStatusListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgonesGameStatusListener.class);
    private static final boolean DISABLE_AGONES_GAME_COUNTER = Boolean.parseBoolean(System.getenv("DISABLE_AGONES_GAME_COUNTER"));

    private final @NotNull GameProvider gameProvider;
    private final @NotNull KubernetesModule kubeModule;
    private final @NotNull SDKGrpc.SDKStub sdk;

    public AgonesGameStatusListener(@NotNull GameProvider gameProvider, @NotNull KubernetesModule kubeModule) {
        this.kubeModule = kubeModule;
        this.gameProvider = gameProvider;
        this.sdk = kubeModule.getAgonesSdk();
    }

    @Override
    public void onGameRemoved(@NotNull Game game) {
        if (DISABLE_AGONES_GAME_COUNTER) return;

        Thread.startVirtualThread(() -> {
            this.kubeModule.updateAgonesCounter("games", -1);
            this.kubeModule.removeFromAgonesList("games", game.getCreationInfo().id());

            if (this.gameProvider.getGameCount() == 0) {
                LOGGER.info("Marking server as ready as no players are online.");
                Thread.startVirtualThread(() -> this.sdk.ready(AgonesSDKProto.Empty.getDefaultInstance(), new IgnoredStreamObserver<>()));
            }
        });
    }
}
