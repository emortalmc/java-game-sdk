package dev.emortal.minestom.gamesdk.game;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import dev.agones.sdk.AgonesSDKProto;
import dev.emortal.minestom.core.module.ModuleEnvironment;
import dev.emortal.minestom.core.module.kubernetes.KubernetesModule;
import dev.emortal.minestom.gamesdk.config.GameCreationInfo;
import dev.emortal.minestom.gamesdk.config.GameSdkConfig;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AgonesListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgonesListener.class);

    public AgonesListener(@NotNull ModuleEnvironment environment, GameSdkConfig config, @NotNull GameManager gameManager) {
        KubernetesModule module = environment.moduleManager().getModule(KubernetesModule.class);
        if (module == null || module.getSdk() == null) {
            LOGGER.warn("AgonesListener is not running in a Kubernetes environment, disabling");
            return;
        }
        module.getSdk().watchGameServer(AgonesSDKProto.Empty.getDefaultInstance(), new AgonesGameServerWatcher(config, gameManager));
    }

    private static class AgonesGameServerWatcher implements StreamObserver<AgonesSDKProto.GameServer> {
        private final @NotNull GameSdkConfig config;
        private final @NotNull GameManager gameManager;

        private Instant lastAllocated = Instant.now();

        private AgonesGameServerWatcher(@NotNull GameSdkConfig config, @NotNull GameManager gameManager) {
            this.config = config;
            this.gameManager = gameManager;
        }

        @Override
        public void onNext(AgonesSDKProto.GameServer value) {
            if (!this.isNewAllocation(value)) return;

            Allocation allocation = Allocation.from(value);
            GameCreationInfo gameCreationInfo = new GameCreationInfo(allocation.playerIds(), this.lastAllocated);
            Game game = this.config.gameCreator().apply(gameCreationInfo);
            this.gameManager.registerGame(game);
            game.load();
        }

        @Override
        public void onError(Throwable t) {
            LOGGER.error("Agones game server watcher error: ", t);
        }

        @Override
        public void onCompleted() {
            LOGGER.info("Agones game server watcher completed");
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

    private record Allocation(
            @NotNull String matchId,
            @Nullable String backfillId,
            @NotNull Set<UUID> playerIds
    ) {
        private static final Gson GSON = new Gson();

        public static Allocation from(@NotNull AgonesSDKProto.GameServer gameServer) {
            Map<String, String> annotations = gameServer.getObjectMeta().getAnnotationsMap();
            return new Allocation(
                    annotations.get("openmatch.dev/match-id"),
                    annotations.get("openmatch.dev/backfill-id"), // nullable
                    parsePlayerIds(annotations.get("openmatch.dev/expected-players"))
            );
        }

        private static Set<UUID> parsePlayerIds(@NotNull String playerIds) {
            return GSON.fromJson(playerIds, new TypeToken<HashSet<UUID>>() {
            }.getType());
        }
    }
}
