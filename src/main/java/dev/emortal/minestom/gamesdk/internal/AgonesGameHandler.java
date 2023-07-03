package dev.emortal.minestom.gamesdk.internal;

import dev.emortal.api.kurushimi.Match;
import dev.emortal.api.kurushimi.Ticket;
import dev.emortal.api.kurushimi.messages.MatchCreatedMessage;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public final class AgonesGameHandler {

    private final GameManager gameManager;
    private final GameSdkConfig config;

    public AgonesGameHandler(@NotNull GameManager gameManager, @NotNull GameSdkConfig config, @NotNull MessagingModule messaging) {
        this.gameManager = gameManager;
        this.config = config;

        messaging.addListener(MatchCreatedMessage.class, this::onMatchCreated);
    }

    private void onMatchCreated(MatchCreatedMessage message) {
        Instant allocationTime = Instant.now();

        GameCreationInfo creationInfo = createInfo(message.getMatch(), allocationTime);
        Game game = this.gameManager.createGame(creationInfo);

        var initializer = new PreGameInitializer(this.config, game);
        initializer.scheduleGameStart();
    }

    private GameCreationInfo createInfo(Match match, Instant allocationTime) {
        Set<UUID> playerIds = new HashSet<>();

        for (Ticket ticket : match.getTicketsList()) {
            for (String playerId : ticket.getPlayerIdsList()) {
                playerIds.add(UUID.fromString(playerId));
            }
        }

        return new GameCreationInfo(match.hasMapId() ? match.getMapId() : null, match.getGameModeId(), playerIds, allocationTime);
    }
}
