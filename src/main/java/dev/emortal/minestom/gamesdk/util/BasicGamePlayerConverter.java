package dev.emortal.minestom.gamesdk.util;

import dev.emortal.api.model.gametracker.BasicGamePlayer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BasicGamePlayerConverter {

    public static @NotNull BasicGamePlayer fromMinestomPlayer(@NotNull Player player) {
        return BasicGamePlayer.newBuilder()
                .setId(player.getUuid().toString())
                .setUsername(player.getUsername())
                .build();
    }
}
