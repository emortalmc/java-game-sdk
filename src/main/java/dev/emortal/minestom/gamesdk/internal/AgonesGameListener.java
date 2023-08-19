package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.api.message.matchmaker.MatchCreatedMessage;
import dev.emortal.api.model.matchmaker.Match;
import dev.emortal.api.model.matchmaker.Ticket;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public final class AgonesGameListener {

    private final GameManager gameManager;
    private final GameSdkConfig config;

    public AgonesGameListener(@NotNull GameManager gameManager, @NotNull GameSdkConfig config, @NotNull MessagingModule messaging) {
        this.gameManager = gameManager;
        this.config = config;

        messaging.addListener(MatchCreatedMessage.class, message -> this.onMatchCreated(message.getMatch()));
    }

    private void onMatchCreated(@NotNull Match match) {
        Instant allocationTime = Instant.now();

        GameCreationInfo creationInfo = this.createInfo(match, allocationTime);
        Game game = this.gameManager.createGame(creationInfo);

        PreGameInitializer initializer = new PreGameInitializer(this.config, game);
        initializer.scheduleGameStart();
    }

    private @NotNull GameCreationInfo createInfo(@NotNull Match match, @NotNull Instant allocationTime) {
        Set<UUID> playerIds = new HashSet<>();

        for (Ticket ticket : match.getTicketsList()) {
            for (String playerId : ticket.getPlayerIdsList()) {
                playerIds.add(UUID.fromString(playerId));
            }
        }

        return new GameCreationInfo(match, playerIds, allocationTime);
    }
}
