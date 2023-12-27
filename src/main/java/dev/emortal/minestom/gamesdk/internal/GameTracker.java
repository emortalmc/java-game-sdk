package dev.emortal.minestom.gamesdk.internal;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import dev.emortal.api.message.gametracker.CommonGameData;
import dev.emortal.api.message.gametracker.GameFinishMessage;
import dev.emortal.api.message.gametracker.GameStartMessage;
import dev.emortal.api.message.gametracker.GameUpdateMessage;
import dev.emortal.api.model.gametracker.BasicGamePlayer;
import dev.emortal.api.utils.ProtoTimestampConverter;
import dev.emortal.api.utils.kafka.FriendlyKafkaProducer;
import dev.emortal.minestom.core.Environment;
import dev.emortal.minestom.core.module.messaging.MessagingModule;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.internal.GameManager;
import dev.emortal.minestom.gamesdk.internal.listener.GameStatusListener;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class GameTracker implements GameStatusListener {
    public static final int DEFAULT_UPDATE_INTERVAL = 10;

    private static final Logger LOGGER = LoggerFactory.getLogger(GameTracker.class);
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform()
                    .name("game-update-scheduler")
                    .daemon()
                    .uncaughtExceptionHandler((thread, exception) ->
                            LOGGER.error("An error occurred while updating games", exception))
                    .factory()
    );

    private final @NotNull GameManager gameManager;
    private final @NotNull FriendlyKafkaProducer kafkaProducer;

    public GameTracker(@NotNull GameManager gameManager, @NotNull MessagingModule messagingModule, @NotNull GameSdkConfig gameSdkConfig) {
        this.gameManager = gameManager;
        this.kafkaProducer = messagingModule.getKafkaProducer();

        SCHEDULER.scheduleAtFixedRate(this::updateGames, gameSdkConfig.trackingUpdateInterval(), gameSdkConfig.trackingUpdateInterval(), TimeUnit.SECONDS);
    }

    @Override
    public void onGameStart(@NotNull Game game) {
        GameStartMessage message = GameStartMessage.newBuilder()
                .setCommonData(this.createCommonGameData(game))
                .setMapId(game.getCreationInfo().mapId())
                .setStartTime(ProtoTimestampConverter.now())
                .addAllContent(this.packMessages(game.createGameStartExtraData()))
                .build();

        this.kafkaProducer.produceAndForget(message);
    }

    @Override
    public void onGameRemoved(@NotNull Game game) {
        GameFinishMessage message = GameFinishMessage.newBuilder()
                .setCommonData(this.createCommonGameData(game))
                .addAllContent(this.packMessages(game.createGameFinishExtraData()))
                .setEndTime(ProtoTimestampConverter.now())
                .build();

        this.kafkaProducer.produceAndForget(message);
    }

    private void updateGames() {
        System.out.println("updating games");
        for (Game game : this.gameManager.getGames()) {
            System.out.println("updating game " + game.getCreationInfo().id());
            try {
                this.updateGame(game);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            System.out.println("updated game " + game.getCreationInfo().id());
        }
    }

    private void updateGame(@NotNull Game game) {
        GameUpdateMessage message = GameUpdateMessage.newBuilder()
                .setCommonData(this.createCommonGameData(game))
                .addAllContent(this.packMessages(game.createGameUpdateExtraData()))
                .build();

        this.kafkaProducer.produceAndForget(message);
    }

    private @NotNull List<Any> packMessages(@NotNull List<? extends Message> messages) {
        List<Any> gameContent = new ArrayList<>();

        for (Message message : messages) {
            gameContent.add(Any.pack(message));
        }

        return gameContent;
    }

    private @NotNull CommonGameData createCommonGameData(@NotNull Game game) {
        GameCreationInfo creationInfo = game.getCreationInfo();
        return CommonGameData.newBuilder()
                .setGameId(creationInfo.id())
                .setGameModeId(creationInfo.gameModeId())
                .setServerId(Environment.getHostname())
                .addAllPlayers(this.createGamePlayers(game))
                .build();
    }

    private @NotNull List<BasicGamePlayer> createGamePlayers(@NotNull Game game) {
        List<BasicGamePlayer> gamePlayers = new ArrayList<>();

        for (Player player : game.getPlayers()) {
            gamePlayers.add(BasicGamePlayer.newBuilder()
                    .setId(player.getUuid().toString())
                    .setUsername(player.getUsername())
                    .build());
        }

        return gamePlayers;
    }
}
