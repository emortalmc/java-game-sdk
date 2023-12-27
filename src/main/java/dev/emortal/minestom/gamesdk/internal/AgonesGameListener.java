package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.api.message.gamesdk.GameReadyMessage;
import dev.emortal.api.message.matchmaker.MatchCreatedMessage;
import dev.emortal.api.model.matchmaker.Match;
import dev.emortal.api.model.matchmaker.Ticket;
import dev.emortal.api.utils.kafka.FriendlyKafkaProducer;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.Game;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.network.ConnectionManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AgonesGameListener {

    private final @NotNull GameManager gameManager;
    private final @NotNull GameSdkConfig config;
    private final @NotNull FriendlyKafkaProducer kafkaProducer;

    public AgonesGameListener(@NotNull GameManager gameManager, @NotNull GameSdkConfig config, @NotNull MessagingModule messaging) {
        this.gameManager = gameManager;
        this.config = config;
        this.kafkaProducer = messaging.getKafkaProducer();

        messaging.addListener(MatchCreatedMessage.class, message -> this.onMatchCreated(message.getMatch()));
    }

    private void onMatchCreated(@NotNull Match match) {
        if (!this.isGameForThisServer(match)) return;

        GameCreationInfo creationInfo = this.createInfo(match);
        Game game = this.gameManager.createGame(creationInfo);

        new PreGameInitializer(this.gameManager, this.config, game);

        this.movePlayersOnThisServer(game, creationInfo.playerIds());
        this.notifyGameReady(match);
    }

    private boolean isGameForThisServer(@NotNull Match match) {
        return match.getAssignment().getServerId().equals(Environment.getHostname());
    }

    private @NotNull GameCreationInfo createInfo(@NotNull Match match) {
        Set<UUID> playerIds = new HashSet<>();

        for (Ticket ticket : match.getTicketsList()) {
            for (String playerId : ticket.getPlayerIdsList()) {
                playerIds.add(UUID.fromString(playerId));
            }
        }

        return new GameCreationInfo(match, playerIds);
    }

    private void notifyGameReady(@NotNull Match match) {
        this.kafkaProducer.produceAndForget(GameReadyMessage.newBuilder().setMatch(match).build());
    }

    private void movePlayersOnThisServer(@NotNull Game newGame, @NotNull Set<UUID> playerIds) {
        ConnectionManager connectionManager = MinecraftServer.getConnectionManager();

        Set<CompletableFuture<Void>> futures = new HashSet<>();
        int i = 0;

        for (UUID playerId : playerIds) {
            Player player = connectionManager.getPlayer(playerId);
            if (player == null) continue;

            Game oldGame = this.gameManager.findGame(player);
            if (oldGame == null) continue;

            GamePlayerTracker.removePlayer(oldGame, player);
            GamePlayerTracker.addPlayer(newGame, player);

            // Increment after using the index
            futures.add(player.setInstance(newGame.getSpawningInstance(player)));
            player.respawn();
        }

        if (futures.isEmpty()) return;
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }
}
