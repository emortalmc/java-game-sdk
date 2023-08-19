package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.api.model.matchmaker.Match;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.game.Game;
import java.time.Instant;
import java.util.HashSet;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;

/*
 * When we are in test mode, we only create and use a single game, and we recreate that game
 * when a player joins. The recreation makes it possible to get a fresh game without having
 * to restart the server test server.
 *
 * This is a separate class because we can't reassign the game with lambdas.
 */
final class TestGameHandler {

    private final GameManager gameManager;

    // We need some bogus creation info since we don't get it from Agones in test mode
    private final GameCreationInfo creationInfo = new GameCreationInfo(Match.getDefaultInstance(), new HashSet<>(), Instant.now());
    private Game game;

    TestGameHandler(@NotNull GameManager gameManager, @NotNull EventNode<Event> eventNode) {
        this.gameManager = gameManager;

        eventNode.addListener(PlayerLoginEvent.class, this::onJoin);
        eventNode.addListener(PlayerDisconnectEvent.class, $ -> this.onLeave());
    }

    private void onJoin(@NotNull PlayerLoginEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Component.text("The server is in test mode. Use /gamesdk start to start a game."));

        if (this.game == null) {
            this.game = this.gameManager.createGame(this.creationInfo);
        }

        this.game.getCreationInfo().playerIds().add(player.getUuid());
        this.game.onJoin(player);
        this.game.getPlayers().add(player);
        event.setSpawningInstance(this.game.getSpawningInstance());
    }

    private void onLeave() {
        this.gameManager.removeGame(this.game);
        this.gameManager.cleanUpGame(this.game);
        this.game = null;
    }
}
