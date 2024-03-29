package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.minestom.gamesdk.MinestomGameServer;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.util.GameEventPredicates;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.timer.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class PreGameInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreGameInitializer.class);

    private final @NotNull GameManager gameManager;

    private final @NotNull GameSdkConfig config;
    private final @NotNull Game game;

    // Pre-start items
    private final @NotNull EventNode<Event> preGameNode;
    private final @Nullable Task startTimeOutTask; // called if not all players have joined and determines whether to start the game or cancel it.

    private final AtomicInteger playerCount = new AtomicInteger();

    PreGameInitializer(@NotNull GameManager gameManager, @NotNull GameSdkConfig config, @NotNull Game game) {
        this.gameManager = gameManager;
        this.config = config;
        this.game = game;

        GameCreationInfo creationInfo = game.getCreationInfo();

        this.preGameNode = EventNode.event(creationInfo.id(), EventFilter.ALL, GameEventPredicates.inCreationInfo(creationInfo));
        GameEventNodes.PRE_GAME.addChild(this.preGameNode);

        this.preGameNode.addListener(PlayerSpawnEvent.class, event -> {
            int newCount = this.playerCount.incrementAndGet();
            if (newCount != creationInfo.playerIds().size()) return;

            LOGGER.info("Starting game {} early because all players have joined", creationInfo.id());
            this.cleanUpPreGame();
            gameManager.startGame(game);
        });

        // If in test mode, we don't want a countdown
        if (!MinestomGameServer.TEST_MODE) {
            this.startTimeOutTask = MinecraftServer.getSchedulerManager().buildTask(this::timeOut).delay(3, ChronoUnit.SECONDS).schedule();
        } else {
            this.startTimeOutTask = null;
        }
    }

    private void timeOut() {
        int actualPlayerCount = this.game.getPlayers().size();
        if (actualPlayerCount >= this.config.minPlayers()) {
            this.cleanUpPreGame();
            this.gameManager.startGame(this.game);
        } else {
            // TODO: This isn't a normal finish. We should inform players that the game couldn't start and send them back to the lobby.
            this.game.finish();
        }
    }

    private void cleanUpPreGame() {
        if (this.startTimeOutTask != null) this.startTimeOutTask.cancel();
        GameEventNodes.PRE_GAME.removeChild(this.preGameNode);
    }
}
