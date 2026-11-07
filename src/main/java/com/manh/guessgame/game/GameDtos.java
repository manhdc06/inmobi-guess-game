package com.manh.guessgame.game;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class GameDtos {

    private GameDtos() {
    }

    public record GuessRequest(@NotNull @Min(1) @Max(5) Integer number) {
    }

    /** Kết quả 1 lần đoán: số user đoán, số server ra, đúng/sai, điểm và lượt còn lại. */
    public record GuessResponse(int guess, int serverNumber, boolean correct, int score, int turns) {
    }

    public record BuyTurnsResponse(int addedTurns, int turns, boolean duplicate) {
    }

    public record MeResponse(String username, String email, int score, int turns) {
    }

    public record LeaderboardEntry(int rank, String username, int score) {
    }
}
