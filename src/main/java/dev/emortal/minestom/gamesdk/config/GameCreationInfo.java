package dev.emortal.minestom.gamesdk.config;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record GameCreationInfo(@NotNull Set<UUID> playerIds, @NotNull Instant allocationTime) {
}
