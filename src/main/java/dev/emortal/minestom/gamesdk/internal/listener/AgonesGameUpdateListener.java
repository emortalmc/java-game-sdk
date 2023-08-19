package dev.emortal.minestom.gamesdk.internal.listener;

import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.emortal.api.agonessdk.IgnoredStreamObserver;
import dev.emortal.api.message.gamesdk.GameReadyMessage;
import dev.emortal.api.utils.kafka.FriendlyKafkaProducer;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import dev.emortal.minestom.gamesdk.game.Game;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AgonesGameUpdateListener implements GameUpdateListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgonesGameUpdateListener.class);
    private static final String GAME_SDK_TOPIC = "game-sdk";

    private final GameSdkConfig config;
    private final SDKGrpc.SDKStub sdk;
    private final FriendlyKafkaProducer kafkaProducer;

    private final AtomicInteger gameCount = new AtomicInteger(0);
    private final AtomicBoolean shouldAllocate = new AtomicBoolean(false);

    public AgonesGameUpdateListener(@NotNull GameSdkConfig config, @NotNull SDKGrpc.SDKStub sdk, @NotNull FriendlyKafkaProducer kafkaProducer) {
        this.config = config;
        this.sdk = sdk;
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void onGameAdded(@NotNull Game game) {
        this.updateShouldAllocate(this.gameCount.incrementAndGet());
        this.kafkaProducer.produceAndForget(GAME_SDK_TOPIC, GameReadyMessage.newBuilder().setMatch(game.getCreationInfo().match()).build());
    }

    @Override
    public void onGameRemoved(@NotNull Game game) {
        this.updateShouldAllocate(this.gameCount.decrementAndGet());
    }

    private void updateShouldAllocate(int gameCount) {
        boolean shouldAllocate = gameCount < this.config.maxGames();

        boolean changed = this.shouldAllocate.getAndSet(shouldAllocate) != shouldAllocate;
        // If the current value is the same as the new value, don't bother updating
        if (!changed) return;

        LOGGER.info("Updating should allocate to {} (game count: {})", shouldAllocate, gameCount);

        var keyValue = AgonesSDKProto.KeyValue.newBuilder().setKey("should-allocate").setValue(String.valueOf(shouldAllocate)).build();
        this.sdk.setLabel(keyValue, new IgnoredStreamObserver<>());

        this.updateReadyIfEmpty(gameCount);
    }

    private void updateReadyIfEmpty(int gameCount) {
        int playerCount = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
        if (playerCount > 0) return;

        if (gameCount > 0) {
            // This is really weird. This would only happen if a game didn't unregister itself properly.
            LOGGER.warn("No players online, but there are still games running.");
        }

        LOGGER.info("Marking server as ready as no players are online.");
        this.sdk.ready(AgonesSDKProto.Empty.getDefaultInstance(), new IgnoredStreamObserver<>());
    }
}
