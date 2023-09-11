package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.minestom.core.utils.KurushimiMinestomUtils;
import dev.emortal.minestom.gamesdk.MinestomGameServer;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.GameFinishedEvent;
import dev.emortal.minestom.gamesdk.game.GameProvider;
import dev.emortal.minestom.gamesdk.internal.listener.GameUpdateListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class GameManager implements GameProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameManager.class);

    private final GameSdkConfig config;
    private final GameUpdateListener updateListener;

    // The event node used by the game manager to listen for events.
    private final EventNode<Event> eventNode = EventNode.all("game-manager");
    // The event node that is the parent of all the game event nodes, for an easy tree view when looking for game nodes.
    private final EventNode<Event> gamesEventNode = EventNode.all("games");

    private final Set<Game> games = Collections.synchronizedSet(new HashSet<>());

    public GameManager(@NotNull GameSdkConfig config, @NotNull GameUpdateListener updateListener) {
        this.config = config;
        this.updateListener = updateListener;

        MinecraftServer.getGlobalEventHandler().addChild(this.eventNode);
        MinecraftServer.getGlobalEventHandler().addChild(this.gamesEventNode);

        if (MinestomGameServer.TEST_MODE) {
            this.initTestMode();
        } else {
            this.initProductionMode();
        }

        this.eventNode.addListener(GameFinishedEvent.class, this::onGameFinish);
    }

    private void initTestMode() {
        new TestGameHandler(this, this.eventNode);
    }

    private void initProductionMode() {
        new ProductionGameHandler(this, this.eventNode);
    }

    @NotNull Game createGame(@NotNull GameCreationInfo creationInfo) {
        Game game = this.config.gameCreator().createGame(creationInfo);
        this.registerGame(game);
        this.updateListener.onGameAdded(game);
        return game;
    }

    private void registerGame(@NotNull Game game) {
        boolean added = this.games.add(game);
        if (!added) {
            LOGGER.warn("Attempted to add game {} that is already registered", game);
            return;
        }
        this.gamesEventNode.addChild(game.getEventNode());
    }

    void removeGame(@NotNull Game game) {
        boolean removed = this.games.remove(game);
        if (!removed) {
            LOGGER.warn("Attempted to remove game {} that is not registered", game);
            return;
        }
        this.gamesEventNode.removeChild(game.getEventNode());
    }

    private void onGameFinish(@NotNull GameFinishedEvent event) {
        Game game = event.game();
        this.removeGame(game);
        KurushimiMinestomUtils.sendToLobby(game.getPlayers(), () -> this.cleanUpGame(game), () -> this.cleanUpGame(game));
    }

    void cleanUpGame(@NotNull Game game) {
        this.kickAllRemainingPlayers(game);
        game.cleanUp();

        // We call this here to ensure all the game's players are disconnected and the game is unregistered, so the check for the
        // player count will actually see the new player count after the players are disconnected.
        this.updateListener.onGameRemoved(game);
    }

    private void kickAllRemainingPlayers(@NotNull Game game) {
        for (Player player : game.getPlayers()) {
            // Don't kick players that aren't online
            if (!player.isOnline()) continue;

            // The player may have been moved to a different game on the same server
            if (player.getInstance() != game.getSpawningInstance()) continue;

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

    @NotNull Set<Game> getGames() {
        return this.games;
    }
}
