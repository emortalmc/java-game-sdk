package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.minestom.core.utils.KurushimiMinestomUtils;
import dev.emortal.minestom.gamesdk.MinestomGameServer;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.GameFinishedEvent;
import dev.emortal.minestom.gamesdk.game.GameProvider;
import dev.emortal.minestom.gamesdk.internal.listener.GameStatusListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class GameManager implements GameProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameManager.class);

    private final @NotNull GameSdkConfig config;

    private final List<GameStatusListener> statusListeners = new CopyOnWriteArrayList<>();
    private final Set<Game> games = Collections.synchronizedSet(new HashSet<>());

    public GameManager(@NotNull GameSdkConfig config) {
        this.config = config;

        if (MinestomGameServer.TEST_MODE) {
            this.initTestMode();
        } else {
            this.initProductionMode();
        }

        GameEventNodes.GAME_MANAGER.addListener(GameFinishedEvent.class, this::onGameFinish);

        Gauge.builder("gamesdk.game_count", this.games, Set::size)
                .description("The amount of games currently running")
                .register(Metrics.globalRegistry);
    }

    private void initTestMode() {
        new TestGameHandler(this);
    }

    private void initProductionMode() {
        new ProductionGameHandler(this);
    }

    @NotNull Game createGame(@NotNull GameCreationInfo creationInfo) {
        Game game = this.config.gameCreator().createGame(creationInfo);
        this.registerGame(game);
        for (GameStatusListener listener : this.statusListeners) {
            listener.onGameAdded(game);
        }
        return game;
    }

    private void registerGame(@NotNull Game game) {
        boolean added = this.games.add(game);
        if (!added) {
            LOGGER.warn("Attempted to add game {} that is already registered", game);
            return;
        }
        GameEventNodes.GAMES.addChild(game.getEventNode());
    }

    public void startGame(@NotNull Game game) {
        LOGGER.info("Starting game {}", game.getCreationInfo().id());
        game.start();
        for (GameStatusListener listener : this.statusListeners) {
            listener.onGameStart(game);
        }

        game.getMeters().add(Gauge.builder("gamesdk.game_start_time", System.currentTimeMillis(), Long::doubleValue)
                .tag("gameId", game.getCreationInfo().id())
                .description("The time the game started")
                .register(Metrics.globalRegistry));
    }

    private void removeGame(@NotNull Game game) {
        boolean removed = this.games.remove(game);
        if (!removed) {
            LOGGER.warn("Attempted to remove game {} that is not registered", game);
            return;
        }
        GameEventNodes.GAMES.removeChild(game.getEventNode());
    }

    private void onGameFinish(@NotNull GameFinishedEvent event) {
        Game game = event.game();
        if (!this.games.contains(game)) {
            // Definitely don't want a double remove and clean up
            LOGGER.info("Game {} already finished and removed. Ignoring finish request.", game.getCreationInfo().id());
            return;
        }

        LOGGER.info("Game {} finished", game.getCreationInfo().id());
        for (GameStatusListener listener : this.statusListeners) {
            listener.onGameFinish(game);
        }

        this.removeGame(game);
        switch (this.config.finishBehaviour()) {
            case LOBBY ->
                    KurushimiMinestomUtils.sendToLobby(game.getPlayers(), () -> this.cleanUpGame(game), () -> this.cleanUpGame(game));
            case REQUEUE ->
                    KurushimiMinestomUtils.sendToGamemode(game.getPlayers(), game.getCreationInfo().gameModeId(),
                            () -> this.cleanUpGame(game), () -> this.cleanUpGame(game), 1);
        }
    }

    private void cleanUpGame(@NotNull Game game) {
        LOGGER.info("Cleaning up game {}", game.getCreationInfo().id());
        this.kickAllRemainingPlayers(game);
        game.cleanUp();
        game.getMeters().forEach(Meter::close);

        // We call this here to ensure all the game's players are disconnected and the game is unregistered, so the check for the
        // player count will actually see the new player count after the players are disconnected.
        for (GameStatusListener listener : this.statusListeners) {
            listener.onGameRemoved(game);
        }
    }

    private void kickAllRemainingPlayers(@NotNull Game game) {
        for (Player player : game.getPlayers()) {
            // Don't kick players that aren't online
            if (!player.isOnline()) continue;

            // The player may have been moved to a different game on the same server
            if (player.getInstance() != game.getSpawningInstance(player)) continue;

            player.kick(Component.text("The game ended but we weren't able to connect you to a lobby. Please reconnect.", NamedTextColor.RED));
        }
    }

    @Override
    public @Nullable Game findGame(@NotNull Player player) {
        for (Game game : this.games) {
            if (game.getPlayers().contains(player)) return game;
        }
        return null;
    }

    @Override
    public int getGameCount() {
        return this.games.size();
    }

    public @NotNull Set<Game> getGames() {
        return this.games;
    }

    public void addGameStatusListener(@NotNull GameStatusListener statusListener) {
        this.statusListeners.add(statusListener);
    }
}
