package dev.emortal.minestom.gamesdk.internal;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.agones.sdk.AgonesSDKProto;
import dev.agones.sdk.SDKGrpc;
import dev.emortal.api.kurushimi.AllocationData;
import dev.emortal.api.kurushimi.Match;
import dev.emortal.api.kurushimi.Ticket;
import dev.emortal.minestom.gamesdk.game.Game;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import io.grpc.stub.StreamObserver;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Base64;

public final class AgonesGameHandler implements StreamObserver<AgonesSDKProto.GameServer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgonesGameHandler.class);

    private final GameManager gameManager;
    private final GameSdkConfig config;

    private Instant lastAllocated = Instant.now();

    public AgonesGameHandler(@NotNull GameManager gameManager, @NotNull GameSdkConfig config, @NotNull SDKGrpc.SDKStub sdk) {
        this.gameManager = gameManager;
        this.config = config;

        sdk.watchGameServer(AgonesSDKProto.Empty.getDefaultInstance(), this);
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
