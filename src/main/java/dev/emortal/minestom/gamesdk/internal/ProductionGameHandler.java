package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.minestom.gamesdk.game.Game;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProductionGameHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductionGameHandler.class);

    private final @NotNull GameManager gameManager;

    ProductionGameHandler(@NotNull GameManager gameManager) {
        this.gameManager = gameManager;

        EventNode<Event> eventNode = GameEventNodes.GAME_MANAGER;
        eventNode.addListener(AsyncPlayerConfigurationEvent.class, this::onJoin);
        eventNode.addListener(PlayerSpawnEvent.class, this::onSpawn);
        eventNode.addListener(PlayerDisconnectEvent.class, this::onLeave);
    }

    private void onJoin(@NotNull AsyncPlayerConfigurationEvent event) {
        Player player = event.getPlayer();

        Game game = null;
        for (Game currentGame : this.gameManager.getGames()) {
            if (!currentGame.getCreationInfo().playerIds().contains(player.getUuid())) continue; // the game is not for this player

            game = currentGame;
            break;
        }

        if (game == null) {
            LOGGER.error("No game could be found for player {}", player.getUsername());
            return;
        }

        event.setSpawningInstance(game.getSpawningInstance(player));
    }

    void onSpawn(@NotNull PlayerSpawnEvent event) {
        Player player = event.getPlayer();

        Game game = null;
        for (Game currentGame : this.gameManager.getGames()) {
            if (!currentGame.getCreationInfo().playerIds().contains(player.getUuid())) continue; // the game is not for this player

            game = currentGame;
            break;
        }

        if (game == null) {
            LOGGER.error("No game could be found for player {}", player.getUsername());
            return;
        }
        GamePlayerTracker.addPlayer(game, player);
    }


    private void onLeave(@NotNull PlayerDisconnectEvent event) {
        Player player = event.getPlayer();

        Game game = this.gameManager.findGame(player);
        if (game == null) return;

        GamePlayerTracker.removePlayer(game, player);
    }
}
