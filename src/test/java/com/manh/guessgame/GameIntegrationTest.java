package com.manh.guessgame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.manh.guessgame.game.GameService;
import com.manh.guessgame.user.User;
import com.manh.guessgame.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.game.initial-turns=5"
})
@AutoConfigureMockMvc
class GameIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired GameService gameService;
    @Autowired UserRepository userRepository;

    private String registerAndLogin(String username) throws Exception {
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username + "@test.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("accessToken").asText();
    }

    @Test
    void fullFlow_registerLoginGuessBuyMe() throws Exception {
        String token = registerAndLogin("alice");

        mvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@test.com"))
                .andExpect(jsonPath("$.turns").value(5));

        String guess = mvc.perform(post("/guess").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"number\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turns").value(4))
                .andReturn().getResponse().getContentAsString();
        JsonNode g = om.readTree(guess);
        assertThat(g.get("serverNumber").asInt()).isBetween(1, 5);
        assertThat(g.get("correct").asBoolean()).isEqualTo(g.get("serverNumber").asInt() == 3);

        mvc.perform(post("/buy-turns").header("Authorization", "Bearer " + token).header("Idempotency-Key", "k1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.turns").value(9));
        // gửi lại cùng key -> không cộng thêm
        mvc.perform(post("/buy-turns").header("Authorization", "Bearer " + token).header("Idempotency-Key", "k1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.turns").value(9));

        mvc.perform(get("/leaderboard")).andExpect(status().isOk());
    }

    @Test
    void validationAndSecurity() throws Exception {
        String token = registerAndLogin("bob");
        mvc.perform(post("/guess").contentType(MediaType.APPLICATION_JSON).content("{\"number\":3}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/guess").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"number\":6}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"email\":\"other@test.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isConflict());
    }

    /** 50 request /guess cùng lúc với 5 lượt: đúng 5 request thành công, turns không bao giờ âm. */
    @Test
    void concurrentGuesses_neverOverspendTurns() throws Exception {
        registerAndLogin("carol");
        User user = userRepository.findByUsername("carol").orElseThrow();
        Long userId = user.getId();

        int requests = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger noTurns = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    gameService.guess(userId, 1);
                    success.incrementAndGet();
                } catch (Exception e) {
                    noTurns.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        User after = userRepository.findById(userId).orElseThrow();
        assertThat(success.get()).isEqualTo(5);
        assertThat(noTurns.get()).isEqualTo(requests - 5);
        assertThat(after.getTurns()).isZero();
        assertThat(after.getScore()).isBetween(0, 5);
    }
}
