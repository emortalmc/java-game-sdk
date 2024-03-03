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
import dev.emortal.minestom.gamesdk.game.GameUpdateRequestEvent;
import dev.emortal.minestom.gamesdk.internal.listener.GameStatusListener;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public final class GameTracker implements GameStatusListener {
    public static final int DEFAULT_MIN_UPDATE_INTERVAL = 5;
    public static final int DEFAULT_MAX_UPDATE_INTERVAL = 180; // 3 minutes

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
    private static final EventNode<Event> EVENT_NODE = GameEventNodes.GAME_MANAGER;

    private final @NotNull FriendlyKafkaProducer kafkaProducer;
    private final @NotNull GameSdkConfig config;

    private final @NotNull Map<Game, ScheduledFuture<?>> gameMaxTimeUpdateTasks = new ConcurrentHashMap<>();

    public GameTracker(@NotNull MessagingModule messagingModule, @NotNull GameSdkConfig config) {
        this.kafkaProducer = messagingModule.getKafkaProducer();
        this.config = config;

        EVENT_NODE.addListener(GameUpdateRequestEvent.class, this::onGameUpdateRequest);
    }

    private void maxTimeUpdate(@NotNull Game game) {
        long nextExpectedUpdate = game.getLastGameTrackerUpdate() + (this.config.maxTrackingInterval() * 1000L);
        if (nextExpectedUpdate < System.currentTimeMillis()) {
            // Game has been updated in the meantime, let's reschedule this task
            this.gameMaxTimeUpdateTasks.put(game, SCHEDULER.schedule(() -> {
                this.maxTimeUpdate(game);
            }, nextExpectedUpdate - System.currentTimeMillis(), TimeUnit.MILLISECONDS));
            return;
        }

        // Game hasn't been updated in a while, let's update it now
        this.updateGame(game);
    }

    @Override
    public void onGameStart(@NotNull Game game) {
        GameStartMessage.Builder messageBuilder = GameStartMessage.newBuilder()
                .setCommonData(this.createCommonGameData(game))
                .setStartTime(ProtoTimestampConverter.now())
                .addAllContent(this.packMessages(game.createGameStartExtraData()));

        if (game.getCreationInfo().mapId() != null) {
            messageBuilder.setMapId(game.getCreationInfo().mapId());
        }

        this.gameMaxTimeUpdateTasks.put(game, SCHEDULER.schedule(() -> {
            this.maxTimeUpdate(game);
        }, this.config.maxTrackingInterval(), TimeUnit.MILLISECONDS));

        this.kafkaProducer.produceAndForget(messageBuilder.build());
    }

    @Override
    public void onGameRemoved(@NotNull Game game) {
        // Cancel the max time update task
        this.gameMaxTimeUpdateTasks.remove(game).cancel(false);

        GameFinishMessage message = GameFinishMessage.newBuilder()
                .setCommonData(this.createCommonGameData(game))
                .addAllContent(this.packMessages(game.createGameFinishExtraData()))
                .setEndTime(ProtoTimestampConverter.now())
                .build();

        this.kafkaProducer.produceAndForget(message);
    }

    private void onGameUpdateRequest(@NotNull GameUpdateRequestEvent event) {
        Game game = event.game();
        long minNextUpdate = game.getLastGameTrackerUpdate() + (this.config.minTrackingInterval() * 1000L);

        boolean wasMarkedForQueue = game.markTrackerUpdateQueued();
        if (wasMarkedForQueue) {
            // Game was already queued for an update, ignore
            return;
        }

        if (minNextUpdate > System.currentTimeMillis()) {
            // Game was updated too recently, queue an update if it isn't already queued

            // Schedule an update for the next interval
            SCHEDULER.schedule(() -> {
                game.markTrackerUpdated();
                this.updateGame(game);
            }, minNextUpdate - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            return;
        }

        // Last game update is longer than the min, update immediately
        this.updateGame(game);
    }

    private void updateGame(@NotNull Game game) {
        game.markTrackerUpdated();
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
