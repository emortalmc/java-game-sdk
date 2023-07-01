package dev.emortal.minestom.gamesdk.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/*
 * The map ID, game mode ID, and player IDs would usually come from the matchmaker, when running in the standard environment.
 * The allocation time comes from the implementation (the game SDK).
 */
public record GameCreationInfo(@Nullable String mapId, @NotNull String gameModeId, @NotNull Set<UUID> playerIds, @NotNull Instant allocationTime) {
}
