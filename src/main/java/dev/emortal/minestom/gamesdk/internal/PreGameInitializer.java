package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.minestom.gamesdk.GameSdkModule;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.util.GameEventPredicates;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.timer.Task;
import net.minestom.server.utils.time.TimeUnit;
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

    private final GameSdkConfig config;
    private final Game game;

    // Pre-start items
    private final @Nullable EventNode<Event> preGameNode;
    private final AtomicInteger playerCount = new AtomicInteger();
    private final @Nullable Task startTimeOutTask; // called if not all players have joined and determines whether to start the game or cancel it.

    public PreGameInitializer(@NotNull GameSdkConfig config, @NotNull Game game) {
        this.config = config;
        this.game = game;

        preGameNode = EventNode.event(UUID.randomUUID().toString(), EventFilter.ALL, GameEventPredicates.inGame(game.getCreationInfo()));
        PRE_GAME_PARENT.addChild(preGameNode);

        preGameNode.addListener(PlayerLoginEvent.class, event -> {
            final int newCount = playerCount.incrementAndGet();
            if (newCount != game.getCreationInfo().playerIds().size()) return;

            LOGGER.info("Starting game early because all players have joined");
            cleanUpPreGame();
            game.start();
        });

        // If in test mode, we don't want a countdown
        if (!GameSdkModule.TEST_MODE) {
            startTimeOutTask = MinecraftServer.getSchedulerManager().buildTask(this::timeOut).delay(10, ChronoUnit.SECONDS).schedule();
        } else {
            startTimeOutTask = null;
        }
    }

    private void timeOut() {
        final Set<UUID> expectedPlayers = game.getCreationInfo().playerIds();

        final Set<UUID> actualPlayers = new HashSet<>();
        for (final var player : game.getPlayers()) {
            actualPlayers.add(player.getUuid());
        }

        final Set<UUID> missingPlayers = new HashSet<>(expectedPlayers);
        missingPlayers.removeAll(actualPlayers);

        if (expectedPlayers.size() - missingPlayers.size() < config.minPlayers()) {
            game.finish();
        } else {
            cleanUpPreGame();
        }
    }

    public void scheduleGameStart() {
        MinecraftServer.getSchedulerManager()
                .buildTask(game::start)
                .delay(5, TimeUnit.SECOND)
                .schedule();
    }

    private void cleanUpPreGame() {
        if (startTimeOutTask != null) startTimeOutTask.cancel();
        if (preGameNode != null) PRE_GAME_PARENT.removeChild(preGameNode);
    }
}
