package dev.emortal.minestom.gamesdk.settings;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import dev.emortal.api.grpc.gameplayerdata.GamePlayerDataProto;
import dev.emortal.api.grpc.gameplayerdata.GamePlayerDataServiceGrpc;
import dev.emortal.api.modules.Module;
import dev.emortal.api.modules.annotation.ModuleData;
import dev.emortal.api.modules.env.ModuleEnvironment;
import dev.emortal.api.utils.GrpcStubCollection;
import java.util.UUID;

import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;

@ModuleData(name = "game-settings")
public final class GameSettingsModule extends Module {

    private final GamePlayerDataServiceGrpc.GamePlayerDataServiceBlockingStub playerDataService = GrpcStubCollection.getGamePlayerDataService().orElse(null);

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

    @Blocking
    public <T extends Message> @NotNull T getSettings(@NotNull GameSettingsType<T> type, @NotNull UUID playerId) {
        var request = GamePlayerDataProto.GamePlayerDataRequest.newBuilder()
                .setPlayerId(playerId.toString())
                .setGameMode(type.mode())
                .build();

        var response = this.playerDataService.getGamePlayerData(request);
        return this.unpack(response.getData(), type.type());
    }

    private <T extends Message> @NotNull T unpack(@NotNull Any data, @NotNull Class<T> type) {
        try {
            return data.unpack(type);
        } catch (InvalidProtocolBufferException exception) {
            throw new RuntimeException(exception);
        }
    }
}
