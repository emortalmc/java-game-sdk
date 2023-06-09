package dev.emortal.minestom.gamesdk.settings;

import dev.emortal.api.model.gamedata.GameDataGameMode;
import org.jetbrains.annotations.NotNull;

public record GameSettingsType<T>(@NotNull Class<T> type, @NotNull GameDataGameMode mode) {
}
