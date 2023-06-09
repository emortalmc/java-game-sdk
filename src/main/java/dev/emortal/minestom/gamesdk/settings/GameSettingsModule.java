package dev.emortal.minestom.gamesdk.settings;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import dev.emortal.api.grpc.gameplayerdata.GamePlayerDataProto;
import dev.emortal.api.grpc.gameplayerdata.GamePlayerDataProto.GetGamePlayerDataResponse;
import dev.emortal.api.grpc.gameplayerdata.GamePlayerDataServiceGrpc;
import dev.emortal.api.model.gamedata.GameDataGameMode;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.ModuleEnvironment;
import dev.emortal.api.utils.GrpcStubCollection;
import java.util.UUID;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;

public final class GameSettingsModule extends Module {

    private final GamePlayerDataServiceGrpc.GamePlayerDataServiceFutureStub playerDataService = GrpcStubCollection.getGamePlayerDataService().orElse(null);

    public GameSettingsModule(@NotNull ModuleEnvironment environment) {
        super(environment);
    }

    @Override
    public boolean onLoad() {
        return true;
    }

    @Override
    public void onUnload() {
    }

    public <T extends Message> @NotNull Future<T> getSettings(@NotNull GameSettingsType<T> type, @NotNull UUID playerId) {
        return Futures.lazyTransform(getPlayerData(playerId, type.mode()), response -> unpack(response.getData(), type.type()));
    }

    public <T extends Message> @NotNull T getSettingsNow(@NotNull GameSettingsType<T> type, @NotNull UUID playerId) {
        final GetGamePlayerDataResponse response = Futures.getUnchecked(getPlayerData(playerId, type.mode()));
        return unpack(response.getData(), type.type());
    }

    private <T extends Message> @NotNull T unpack(@NotNull Any data, @NotNull Class<T> type) {
        try {
            return data.unpack(type);
        } catch (final InvalidProtocolBufferException exception) {
            throw new RuntimeException(exception);
        }
    }

    private @NotNull ListenableFuture<GetGamePlayerDataResponse> getPlayerData(@NotNull UUID playerId, @NotNull GameDataGameMode gameMode) {
        final GamePlayerDataProto.GamePlayerDataRequest request = GamePlayerDataProto.GamePlayerDataRequest.newBuilder()
                .setPlayerId(playerId.toString())
                .setGameMode(gameMode)
                .build();
        return playerDataService.getGamePlayerData(request);
    }
}
