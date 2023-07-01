package dev.emortal.minestom.gamesdk.internal;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.emortal.api.agonessdk.IgnoredStreamObserver;
import dev.emortal.api.kurushimi.AllocationData;
import dev.emortal.api.kurushimi.Match;
import dev.emortal.api.kurushimi.Ticket;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import io.grpc.stub.StreamObserver;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Base64;

public final class AgonesGameHandler implements StreamObserver<AgonesSDKProto.GameServer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgonesGameHandler.class);

    public static void initialize(@NotNull GameManager gameManager, @NotNull GameSdkConfig config, @Nullable KubernetesModule kubernetesModule) {
        if (kubernetesModule == null || kubernetesModule.getSdk() == null) {
            LOGGER.warn("AgonesGameHandler is not running in a Kubernetes environment, disabling");
            return;
        }

        new AgonesGameHandler(gameManager, config, kubernetesModule.getSdk());
    }

    private final GameManager gameManager;
    private final GameSdkConfig config;

    private final SDKGrpc.SDKStub sdk;

    private final AtomicBoolean shouldAllocate = new AtomicBoolean(false);
    private Instant lastAllocated = Instant.now();

    private AgonesGameHandler(@NotNull GameManager gameManager, @NotNull GameSdkConfig config, @NotNull SDKGrpc.SDKStub sdk) {
        this.gameManager = gameManager;
        this.config = config;

        this.sdk = sdk;
        sdk.watchGameServer(AgonesSDKProto.Empty.getDefaultInstance(), this);

        final EventNode<GameCountUpdatedEvent> eventNode = EventNode.type("agones-game-handler", EventFilter.from(GameCountUpdatedEvent.class, null, null));
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
        eventNode.addListener(GameCountUpdatedEvent.class, event -> updateShouldAllocate(event.newCount()));
    }

    private void updateShouldAllocate(int gameCount) {
        final boolean shouldAllocate = gameCount < config.maxGames();

        final boolean changed = this.shouldAllocate.getAndSet(shouldAllocate) != shouldAllocate;
        // If the current value is the same as the new value, don't bother updating
        if (!changed) return;

        LOGGER.info("Updating should allocate to {} (game count: {})", shouldAllocate, gameCount);
        sdk.setLabel(AgonesSDKProto.KeyValue.newBuilder()
                .setKey("should-allocate")
                .setValue(String.valueOf(shouldAllocate)).build(), new IgnoredStreamObserver<>());

        updateReadyIfEmpty(gameCount);
    }

    private void updateReadyIfEmpty(int gameCount) {
        final int playerCount = MinecraftServer.getConnectionManager().getOnlinePlayers().size();
        if (playerCount > 0) return;

        if (gameCount > 0) {
            // This is really weird. This would only happen if a game didn't unregister itself properly.
            LOGGER.warn("No players online, but there are still games running.");
        }

        LOGGER.info("Marking server as ready as no players are online.");
        sdk.ready(AgonesSDKProto.Empty.getDefaultInstance(), new IgnoredStreamObserver<>());
    }

    @Override
    public void onNext(@NotNull AgonesSDKProto.GameServer value) {
        if (!isNewAllocation(value)) return;

        final String encodedData = value.getObjectMeta().getAnnotationsMap().get("emortal.dev/allocation-data");
        final byte[] rawData = Base64.getDecoder().decode(encodedData);

        final AllocationData allocationData;
        try {
            allocationData = AllocationData.parseFrom(rawData);
        } catch (final InvalidProtocolBufferException exception) {
            LOGGER.error("Failed to parse allocation data: ", exception);
            return;
        }

        // Cannot be null as isNewAllocation returns false if it is null
        final Instant allocationTime = parseAllocationTime(value);

        final GameCreationInfo creationInfo = createInfo(allocationTime, allocationData);
        final Game game = gameManager.createGame(creationInfo);

        final PreGameInitializer initializer = new PreGameInitializer(config, game);
        initializer.scheduleGameStart();
    }

    private GameCreationInfo createInfo(Instant allocationTime, AllocationData data) {
        final Match match = data.getMatch();

        final Set<UUID> playerIds = new HashSet<>();
        for (final Ticket ticket : match.getTicketsList()) {
            for (final String playerId : ticket.getPlayerIdsList()) {
                playerIds.add(UUID.fromString(playerId));
            }
        }

        return new GameCreationInfo(match.hasMapId() ? match.getMapId() : null, match.getGameModeId(), playerIds, allocationTime);
    }

    @Override
    public void onError(@NotNull Throwable throwable) {
        LOGGER.error("Agones game server watcher error: ", throwable);
    }

    @Override
    public void onCompleted() {
        LOGGER.info("Agones game server watcher completed");
    }

    private @Nullable Instant parseAllocationTime(AgonesSDKProto.GameServer gameServer) {
        final String lastAllocated = gameServer.getObjectMeta().getAnnotationsMap().get("agones.dev/last-allocated");
        if (lastAllocated == null) return null;

        return Instant.parse(lastAllocated);
    }

    /**
     * Checks if the allocation is new and if so, updates the last allocated time.
     *
     * @param gameServer {@link dev.agones.sdk.AgonesSDKProto.GameServer} to check
     * @return true if the allocation is new, false otherwise
     */
    private boolean isNewAllocation(AgonesSDKProto.GameServer gameServer) {
        final String lastAllocated = gameServer.getObjectMeta().getAnnotationsMap().get("agones.dev/last-allocated");
        if (lastAllocated == null) return false;

        final Instant instant = Instant.parse(lastAllocated);
        if (instant.isAfter(this.lastAllocated)) {
            this.lastAllocated = instant;
            return true;
        }

        return false;
    }
}
