package dev.emortal.minestom.gamesdk.game;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.emortal.api.agonessdk.IgnoredStreamObserver;
import dev.emortal.api.modules.ModuleEnvironment;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.GameSdkModule;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GameManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameManager.class);

    private final @NotNull Set<Game> games = Collections.synchronizedSet(new HashSet<>());
    private final AtomicBoolean shouldAllocate = new AtomicBoolean(false); // If true, it won't be updated

    private final @NotNull GameSdkConfig config;
    private final @NotNull ModuleEnvironment environment;
    private final @Nullable SDKGrpc.SDKStub agonesSdk;

    private final @NotNull EventNode<Event> eventNode;

    public GameManager(@NotNull ModuleEnvironment environment, @NotNull GameSdkConfig config) {
        this.config = config;
        this.environment = environment;

        KubernetesModule kubernetesModule = environment.moduleManager().getModule(KubernetesModule.class);
        this.agonesSdk = kubernetesModule.getSdk();

        this.eventNode = EventNode.all("game-manager");
        MinecraftServer.getGlobalEventHandler().addChild(this.eventNode);

        if (GameSdkModule.TEST_MODE) {
            final GameCreationInfo creationInfo = new GameCreationInfo(
                    Instant.now(),
                    null,
                    "unknown",
                    new HashSet<>(),
                    null
            );
            this.createGame(creationInfo);

            this.eventNode.addListener(PlayerLoginEvent.class, event -> {
                Player player = event.getPlayer();
                player.sendMessage(Component.text("The server is in test mode. Use /gamesdk start to start a game."));

                Game game = this.games.iterator().next();
                game.getPlayers().add(player);
                game.getGameCreationInfo().playerIds().add(player.getUuid());

                game.onPlayerLogin(event);
            });
        } else {
            this.eventNode.addListener(PlayerLoginEvent.class, event -> {
                while (this.games.isEmpty()) {
                    try {
                        Thread.sleep(1000);
                    } catch (final InterruptedException exception) {
                        exception.printStackTrace();
                    }
                }
                for (final Game game : this.games) {
                    if (!game.getGameCreationInfo().playerIds().contains(event.getPlayer().getUuid())) continue;
                    game.onPlayerLogin(event);
                    break;
                }
            });
        }
    }

    public GameWrapper createGame(final @NotNull GameCreationInfo creationInfo) {
        final EventNode<Event> gameNode = EventNode.event(UUID.randomUUID().toString(), EventFilter.ALL, event -> {
            if (event instanceof PlayerEvent playerEvent) {
                return creationInfo.playerIds().contains(playerEvent.getPlayer().getUuid());
            }
            return true;
        });
        this.eventNode.addChild(gameNode);

        final Game game = this.config.gameCreator().apply(creationInfo, gameNode);
        return this.registerGame(game);
    }

    public GameWrapper registerGame(@NotNull Game game) {
        boolean added = games.add(game);
        if (added) updateShouldAllocate();

        return new GameWrapper(this, eventNode, config, game);
    }

    /**
     * Tells the GameManager that a game has ended and can be removed from the manager.
     * At this point, the Game and all its Instances should be completely cleaned up.
     *
     * @param game The game that has ended
     */
    public void removeGame(@NotNull Game game) {
        final boolean removed = games.remove(game);
        if (removed) updateShouldAllocate();

        eventNode.removeChild(game.getGameEventNode());
    }

    public @NotNull Optional<Game> findGame(@NotNull Player player) {
        return this.games.stream().filter(game -> game.getPlayers().contains(player)).findFirst();
    }

    private void updateShouldAllocate() {
        if (agonesSdk == null) return;

        final int gameCount = games.size();
        final boolean shouldAllocate = gameCount < config.maxGames();

        final boolean changed = this.shouldAllocate.getAndSet(shouldAllocate) != shouldAllocate;
        // If the current value is the same as the new value, don't bother updating
        if (!changed) return;

        LOGGER.info("Updating should allocate to {} (game count: {})", shouldAllocate, gameCount);
        agonesSdk.setLabel(AgonesSDKProto.KeyValue.newBuilder()
                .setKey("should-allocate")
                .setValue(String.valueOf(shouldAllocate)).build(), new IgnoredStreamObserver<>());

        updateReadyIfEmpty();
    }

    private void updateReadyIfEmpty() {
        if (agonesSdk == null) return;

        final int playerCount = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
        if (playerCount > 0) return;

        if (games.size() > 0) {
            // This is really weird. This would only happen if a game didn't unregister itself properly.
            LOGGER.warn("No players online, but there are still games running.");
        }

        LOGGER.info("Marking server as ready as no players are online.");
        agonesSdk.ready(AgonesSDKProto.Empty.getDefaultInstance(), new IgnoredStreamObserver<>());
    }
}
