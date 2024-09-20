package dev.emortal.minestom.gamesdk.util;

import com.google.protobuf.Message;
import dev.emortal.api.model.gamedata.GameDataGameMode;
import dev.emortal.api.service.gameplayerdata.GamePlayerDataService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class GamePlayerDataRepository<T extends Message> {
    private final @Nullable GamePlayerDataService gamePlayerDataService;

    private final @NotNull T defaultData;
    private final @NotNull Class<T> playerDataClass;
    private final @NotNull GameDataGameMode gameMode;

    public GamePlayerDataRepository(@Nullable GamePlayerDataService gamePlayerDataService, @NotNull T defaultData, @NotNull Class<T> playerDataClass, @NotNull GameDataGameMode gameMode) {
        this.gamePlayerDataService = gamePlayerDataService;
        this.defaultData = defaultData;
        this.playerDataClass = playerDataClass;
        this.gameMode = gameMode;
    }

    public @NotNull T getPlayerData(@NotNull UUID playerId) {
        if (this.gamePlayerDataService == null) return this.defaultData;

        T playerData = this.gamePlayerDataService.getGameData(this.gameMode, this.playerDataClass, playerId);
        return playerData != null ? playerData : this.defaultData;
    }

    public @NotNull Map<UUID, T> getPlayerData(@NotNull Set<UUID> playerIds) {
        if (this.gamePlayerDataService == null)
            return playerIds.stream().collect(Collectors.toMap(id -> id, id -> this.defaultData));

        Map<UUID, T> responseData = this.gamePlayerDataService.getGameData(this.gameMode, this.playerDataClass, playerIds);

        for (Map.Entry<UUID, T> entry : responseData.entrySet()) {
            if (entry.getValue() == null) {
                responseData.put(entry.getKey(), this.defaultData);
            }
        }

        return responseData;
    }
}
