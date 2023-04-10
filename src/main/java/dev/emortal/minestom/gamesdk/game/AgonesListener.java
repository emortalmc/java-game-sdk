package dev.emortal.minestom.gamesdk.game;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.agones.sdk.AgonesSDKProto;
import dev.emortal.api.kurushimi.AllocationData;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Base64;

public final class AgonesListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgonesListener.class);

    public AgonesListener(@NotNull ModuleEnvironment environment, @NotNull GameManager gameManager) {
        KubernetesModule module = environment.moduleManager().getModule(KubernetesModule.class);
        if (module == null || module.getSdk() == null) {
            LOGGER.warn("AgonesListener is not running in a Kubernetes environment, disabling");
            return;
        }
        module.getSdk().watchGameServer(AgonesSDKProto.Empty.getDefaultInstance(), new AgonesGameServerWatcher(gameManager));
    }

    private static class AgonesGameServerWatcher implements StreamObserver<AgonesSDKProto.GameServer> {
        private final @NotNull GameManager gameManager;

        private Instant lastAllocated = Instant.now();

        private AgonesGameServerWatcher(@NotNull GameManager gameManager) {
            this.gameManager = gameManager;
        }

        @Override
        public void onNext(AgonesSDKProto.GameServer value) {
            if (!this.isNewAllocation(value)) return;

            String encodedData = value.getObjectMeta().getAnnotationsMap().get("emortal.dev/allocation-data");
            byte[] rawData = Base64.getDecoder().decode(encodedData);

            AllocationData allocationData;
            try {
                allocationData = AllocationData.parseFrom(rawData);
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error("Failed to parse allocation data: ", e);
                return;
            }

            // Cannot be null as isNewAllocation returns false if it is null
            Instant allocationTime = this.parseAllocationTime(value);

            GameCreationInfo gameCreationInfo = GameCreationInfo.fromAllocationData(allocationTime, allocationData);
            final GameWrapper gameWrapper = this.gameManager.createGame(gameCreationInfo);
            gameWrapper.scheduleGameStart();
        }

        @Override
        public void onError(Throwable t) {
            LOGGER.error("Agones game server watcher error: ", t);
        }

        @Override
        public void onCompleted() {
            LOGGER.info("Agones game server watcher completed");
        }

        private @Nullable Instant parseAllocationTime(AgonesSDKProto.GameServer gameServer) {
            String string = gameServer.getObjectMeta().getAnnotationsMap().get("agones.dev/last-allocated");
            if (string == null) return null;

            return Instant.parse(string);
        }

        /**
         * Checks if the allocation is new and if so, updates the last allocated time.
         *
         * @param gameServer {@link dev.agones.sdk.AgonesSDKProto.GameServer} to check
         * @return true if the allocation is new, false otherwise
         */
        private boolean isNewAllocation(AgonesSDKProto.GameServer gameServer) {
            String string = gameServer.getObjectMeta().getAnnotationsMap().get("agones.dev/last-allocated");
            if (string == null) return false;

            Instant instant = Instant.parse(string);
            if (instant.isAfter(this.lastAllocated)) {
                this.lastAllocated = instant;
                return true;
            }

            return false;
        }
    }
}
