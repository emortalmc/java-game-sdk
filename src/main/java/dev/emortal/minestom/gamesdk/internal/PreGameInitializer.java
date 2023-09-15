package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.minestom.gamesdk.MinestomGameServer;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.util.GameEventPredicates;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.timer.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

final class PreGameInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(PreGameInitializer.class);
    // I really didn't like the dependency this had on the game manager, so I thought this was probably the best way to separate them.
    private static final EventNode<Event> PRE_GAME_PARENT = EventNode.all("pre_game");

    private final @NotNull GameSdkConfig config;
    private final @NotNull Game game;

    // Pre-start items
    private final @NotNull EventNode<Event> preGameNode;
    private final @Nullable Task startTimeOutTask; // called if not all players have joined and determines whether to start the game or cancel it.

    private final AtomicInteger playerCount = new AtomicInteger();

    public PreGameInitializer(@NotNull GameSdkConfig config, @NotNull Game game) {
        this.config = config;
        this.game = game;

        GameCreationInfo creationInfo = game.getCreationInfo();

        this.preGameNode = EventNode.event(creationInfo.id(), EventFilter.ALL, GameEventPredicates.inCreationInfo(creationInfo));
        PRE_GAME_PARENT.addChild(this.preGameNode);

        this.preGameNode.addListener(PlayerLoginEvent.class, event -> {
            int newCount = this.playerCount.incrementAndGet();
            if (newCount != creationInfo.playerIds().size()) return;

            LOGGER.info("Starting game {} early because all players have joined", creationInfo.id());
            this.cleanUpPreGame();
            game.start();
        });

        // If in test mode, we don't want a countdown
        if (!MinestomGameServer.TEST_MODE) {
            this.startTimeOutTask = MinecraftServer.getSchedulerManager().buildTask(this::timeOut).delay(5, ChronoUnit.SECONDS).schedule();
        } else {
            this.startTimeOutTask = null;
        }
    }

    private void timeOut() {
        Set<UUID> expectedPlayers = this.game.getCreationInfo().playerIds();

        Set<UUID> actualPlayers = new HashSet<>();
        for (Player player : this.game.getPlayers()) {
            actualPlayers.add(player.getUuid());
        }

        Set<UUID> missingPlayers = new HashSet<>(expectedPlayers);
        missingPlayers.removeAll(actualPlayers);

        if (expectedPlayers.size() - missingPlayers.size() < this.config.minPlayers()) {
            this.game.finish();
        } else {
            this.cleanUpPreGame();
            this.game.start();
        }
    }

    private void cleanUpPreGame() {
        if (this.startTimeOutTask != null) this.startTimeOutTask.cancel();
        PRE_GAME_PARENT.removeChild(this.preGameNode);
    }
}
