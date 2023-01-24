package dev.emortal.minestom.gamesdk.config;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class GameCreationInfo {
    private final @NotNull Set<UUID> playerIds;
    private final @NotNull Instant allocationTime;

    public GameCreationInfo(@NotNull Set<UUID> playerIds, @NotNull Instant allocationTime) {
        this.playerIds = playerIds;
        this.allocationTime = allocationTime;
    }

    public @NotNull Set<UUID> playerIds() {
        return this.playerIds;
    }

    public @NotNull Instant allocationTime() {
        return this.allocationTime;
    }
}
