package com.manh.guessgame.game;

import org.junit.jupiter.api.Test;

import com.manh.guessgame.user.User;
import com.manh.guessgame.user.UserRepository;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class GameServiceWinRateTest {

    @Test
    void randomOtherNumber_isNeverTheGuess_andCoversAllOthers() {
        GameService service = new GameService(null, null, 0.05, 5, new SplittableRandomAdapter(42));
        for (int guess = 1; guess <= 5; guess++) {
            boolean[] seen = new boolean[6];
            for (int i = 0; i < 2_000; i++) {
                int n = service.randomOtherNumber(guess);
                assertThat(n).isBetween(1, 5).isNotEqualTo(guess);
                seen[n] = true;
            }
            for (int n = 1; n <= 5; n++) {
                assertThat(seen[n]).isEqualTo(n != guess);
            }
        }
    }

    @Test
    void guess_winRateIsAboutFivePercent_andResultIsConsistent() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        Mockito.when(repo.consumeTurn(1L)).thenReturn(1);
        Mockito.when(repo.findById(1L)).thenReturn(Optional.of(new User("u", "u@test.com", "x", 0)));
        GameService service = new GameService(repo, null, 0.05, 5, new SplittableRandomAdapter(7));

        int wins = 0, total = 100_000;
        for (int i = 0; i < total; i++) {
            int guess = 1 + (i % 5);
            GameDtos.GuessResponse r = service.guess(1L, guess);
            assertThat(r.correct()).isEqualTo(r.serverNumber() == guess);
            if (r.correct()) wins++;
        }
        assertThat(wins / (double) total).isBetween(0.045, 0.055);
        Mockito.verify(repo, Mockito.times(wins)).incrementScore(1L);
    }

    /** SplittableRandom có seed cố định để test lặp lại được. */
    static class SplittableRandomAdapter implements java.util.random.RandomGenerator {
        private final SplittableRandom r;
        SplittableRandomAdapter(long seed) { this.r = new SplittableRandom(seed); }
        @Override public long nextLong() { return r.nextLong(); }
    }
}
