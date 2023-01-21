package dev.emortal.minestom.gamesdk.game;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.emortal.api.agonessdk.EmptyStreamObserver;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.timer.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class GameManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameManager.class);

    private final @NotNull Set<Game> games = Collections.synchronizedSet(new HashSet<>());
    private final AtomicBoolean shouldAllocate = new AtomicBoolean(true);

    private final @NotNull GameSdkConfig config;
    private final @NotNull ModuleEnvironment environment;
    private final @Nullable SDKGrpc.SDKStub agonesSdk;

    public GameManager(@NotNull ModuleEnvironment environment, @NotNull GameSdkConfig config) {
        this.config = config;
        this.environment = environment;

        KubernetesModule kubernetesModule = environment.moduleManager().getModule(KubernetesModule.class);
        this.agonesSdk = kubernetesModule.getSdk();
    }

    public void addGame(@NotNull Game game) {
        boolean added = this.games.add(game);
        if (added) this.updateShouldAllocate();

        LOGGER.info("A");
        EventNode<Event> tempNode = EventNode.all(UUID.randomUUID().toString());
        LOGGER.info("B");
        this.environment.eventNode().addChild(tempNode);
        LOGGER.info("C");

        AtomicInteger playerCount = new AtomicInteger();
        LOGGER.info("D");
        int expectedPlayerCount = game.getGameCreationInfo().playerIds().size();
        LOGGER.info("E");

        Task startTask = MinecraftServer.getSchedulerManager().buildTask(() -> {
            LOGGER.info("F");
            Set<UUID> expectedPlayers = game.getGameCreationInfo().playerIds();
            Set<UUID> actualPlayers = game.getPlayers();

            Set<UUID> missingPlayers = new HashSet<>(expectedPlayers);
            missingPlayers.removeAll(actualPlayers);

            if (expectedPlayers.size() - missingPlayers.size() < this.config.minPlayers()) game.cancel();
            else this.environment.eventNode().removeChild(tempNode);
        }).delay(10, ChronoUnit.SECONDS).schedule();

        LOGGER.info("G");
        tempNode.addListener(PlayerLoginEvent.class, event -> {
            LOGGER.info("H: [{}] ({})", event.getPlayer().getUuid(), game.getGameCreationInfo().playerIds());
            if (!game.getGameCreationInfo().playerIds().contains(event.getPlayer().getUuid())) return;
            LOGGER.info("I");
            int newCount = playerCount.incrementAndGet();
            LOGGER.info("J {}", newCount);

            if (newCount == expectedPlayerCount) {
                LOGGER.info("Starting game early because all players have joined");
                this.environment.eventNode().removeChild(tempNode);
                game.fastStart();
                startTask.cancel();
            }
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
        boolean newValue = this.shouldAllocate.get();
        LOGGER.info("Updating should allocate from {} to {}", original, newValue);

        if (original != newValue) {
            this.agonesSdk.setLabel(AgonesSDKProto.KeyValue.newBuilder()
                    .setKey("should-allocate")
                    .setValue(String.valueOf(!original)).build(), new EmptyStreamObserver<>());
        }
    }
}
