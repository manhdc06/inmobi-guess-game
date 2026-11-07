package com.manh.guessgame.game;

import com.manh.guessgame.game.GameDtos.BuyTurnsResponse;
import com.manh.guessgame.game.GameDtos.GuessRequest;
import com.manh.guessgame.game.GameDtos.GuessResponse;
import com.manh.guessgame.game.GameDtos.LeaderboardEntry;
import com.manh.guessgame.game.GameDtos.MeResponse;
import com.manh.guessgame.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class GameController {

    private final GameService gameService;
    private final LeaderboardService leaderboardService;

    public GameController(GameService gameService, LeaderboardService leaderboardService) {
        this.gameService = gameService;
        this.leaderboardService = leaderboardService;
    }

    @PostMapping("/guess")
    public GuessResponse guess(@AuthenticationPrincipal AuthUser user,
                               @Valid @RequestBody GuessRequest request) {
        return gameService.guess(user.id(), request.number());
    }

    @PostMapping("/buy-turns")
    public BuyTurnsResponse buyTurns(@AuthenticationPrincipal AuthUser user,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return gameService.buyTurns(user.id(), idempotencyKey);
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> leaderboard() {
        return leaderboardService.top10();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return gameService.me(user.id());
    }
}
