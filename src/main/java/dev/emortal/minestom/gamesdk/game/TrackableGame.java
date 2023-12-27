package dev.emortal.minestom.gamesdk.game;

import com.google.protobuf.Message;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface TrackableGame {

    default @NotNull List<? extends Message> createGameStartExtraData() {
        return List.of();
    }

    default @NotNull List<? extends Message> createGameUpdateExtraData() {
        return List.of();
    }

    default @NotNull List<? extends Message> createGameFinishExtraData() {
        return List.of();
    }
}
