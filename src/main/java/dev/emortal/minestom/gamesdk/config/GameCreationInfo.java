package dev.emortal.minestom.gamesdk.config;

import dev.emortal.api.model.matchmaker.Match;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/*
 * The map ID, game mode ID, and player IDs would usually come from the matchmaker, when running in the standard environment.
 * The allocation time comes from the implementation (the game SDK).
 */
public record GameCreationInfo(@NotNull Match match, @NotNull Set<UUID> playerIds, @NotNull Instant allocationTime) {

    public @NotNull String id() {
        return this.match.getId();
    }

    public @Nullable String mapId() {
        if (!this.match.hasMapId()) return null;
        return this.match.getMapId();
    }

    public @NotNull String gameModeId() {
        return this.match.getGameModeId();
    }
}
