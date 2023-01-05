package dev.emortal.minestom.gamesdk.game;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.emortal.api.agonessdk.EmptyStreamObserver;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class GameManager {
    private final @NotNull Set<Game> games = new HashSet<>();
    private final AtomicBoolean shouldAllocate = new AtomicBoolean(true);

    private final @NotNull ModuleEnvironment environment;
    private final @Nullable SDKGrpc.SDKStub agonesSdk;
    private final @NotNull GameSdkConfig config;

    public GameManager(@NotNull ModuleEnvironment environment, @NotNull GameSdkConfig config) {
        this.config = config;
        this.environment = environment;
        KubernetesModule kubernetesModule = environment.moduleManager().getModule(KubernetesModule.class);
        this.agonesSdk = kubernetesModule.getSdk();
    }

    public void addGame(@NotNull Game game) {
        boolean added = this.games.add(game);
        if (added) this.updateShouldAllocate();

        AtomicInteger playerCount = new AtomicInteger();
        int expectedPlayerCount = game.getGameCreationInfo().playerIds().size();

        MinecraftServer.getSchedulerManager().buildTask(() -> {
            Set<UUID> expectedPlayers = game.getGameCreationInfo().playerIds();
            Set<UUID> actualPlayers = game.getPlayers();

            Set<UUID> missingPlayers = new HashSet<>(expectedPlayers);
            missingPlayers.removeAll(actualPlayers);

            if (expectedPlayers.size() - missingPlayers.size() < this.config.minPlayers()) game.cancel();
        }).delay(10, ChronoUnit.SECONDS).schedule();

        this.environment.eventNode().addListener(PlayerLoginEvent.class, event -> {
            if (!game.getGameCreationInfo().playerIds().contains(event.getPlayer().getUuid())) return;
            int newCount = playerCount.incrementAndGet();

            if (newCount == expectedPlayerCount) game.fastStart();
        });
    }

    /**
     * Tells the GameManager that a game has ended and can be removed from the manager.
     * At this point, the Game and all its Instances should be completely cleaned up.
     *
     * @param game The game that has ended
     */
    public void removeGame(@NotNull Game game) {
        boolean removed = this.games.remove(game);
        if (removed) this.updateShouldAllocate();
    }

    private void updateShouldAllocate() {
        if (this.agonesSdk == null) return;

        boolean original = this.shouldAllocate.getAndSet(this.games.size() < this.config.maxGames());
        if (original != this.shouldAllocate.get()) {
            this.agonesSdk.setLabel(AgonesSDKProto.KeyValue.newBuilder()
                    .setKey("should-allocate")
                    .setValue(String.valueOf(!original)).build(), new EmptyStreamObserver<>());
        }
    }
}
