package dev.emortal.minestom.gamesdk.game;

import dev.emortal.minestom.gamesdk.GameSdkModule;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.trait.PlayerEvent;
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
import java.util.stream.Collectors;

class GameWrapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameWrapper.class);

    private final @NotNull GameManager gameManager;
    private final @NotNull EventNode<Event> parentEventNode;
    private final @NotNull GameSdkConfig config;

    private final @NotNull Game game;
    private final @NotNull GameCreationInfo gameCreationInfo;

    // Pre-start items
    private final @Nullable EventNode<Event> preGameNode;
    private final @NotNull AtomicInteger playerCount = new AtomicInteger();
    private final @Nullable Task startTimeOutTask; // called if not all players have joined and determines whether to start the game or cancel it.

    // Global items
    private final @Nullable EventNode<Event> gameNode;

    public GameWrapper(@NotNull GameManager gameManager, @NotNull EventNode<Event> parentNode,
                       @NotNull GameSdkConfig config, @NotNull Game game) {
        this.gameManager = gameManager;
        this.parentEventNode = parentNode;
        this.config = config;
        this.game = game;
        this.gameCreationInfo = game.getGameCreationInfo();

        this.preGameNode = EventNode.event(UUID.randomUUID().toString(), EventFilter.ALL, event -> {
            System.out.println("B");
            if (event instanceof PlayerEvent playerEvent) {
                System.out.println("C");
                if (!this.gameCreationInfo.playerIds().contains(playerEvent.getPlayer().getUuid())) return false;
            }
            return true;
        });
        this.gameNode = EventNode.all(UUID.randomUUID().toString());

        this.preGameNode.addListener(PlayerLoginEvent.class, event -> {
            System.out.println("D");
            int newCount = this.playerCount.incrementAndGet();

            if (newCount == this.gameCreationInfo.playerIds().size()) {
                LOGGER.info("Starting game early because all players have joined");
                this.cleanUpPreGame();
                game.start();
            }
        });
        this.gameNode.addListener(PlayerDisconnectEvent.class, event -> game.onPlayerQuit(event.getPlayer()));

        // If in test mode, we don't want a countdown
        if (!GameSdkModule.TEST_MODE) {
            this.startTimeOutTask = MinecraftServer.getSchedulerManager().buildTask(() -> {
                Set<UUID> expectedPlayers = game.getGameCreationInfo().playerIds();
                Set<UUID> actualPlayers = game.getPlayers().stream().map(Entity::getUuid).collect(Collectors.toSet());

                Set<UUID> missingPlayers = new HashSet<>(expectedPlayers);
                missingPlayers.removeAll(actualPlayers);

                if (expectedPlayers.size() - missingPlayers.size() < this.config.minPlayers()) {
                    game.cancel();
                } else {
                    this.cleanUpPreGame();
                }
            }).delay(10, ChronoUnit.SECONDS).schedule();
        } else {
            this.startTimeOutTask = null;
        }
    }

    private void cleanUpPreGame() {
        if (this.startTimeOutTask != null) this.startTimeOutTask.cancel();
        if (this.preGameNode != null) this.parentEventNode.removeChild(this.preGameNode);
    }
}
