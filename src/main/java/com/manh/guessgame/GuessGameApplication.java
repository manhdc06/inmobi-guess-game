package com.manh.guessgame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;

// Không dùng user/password mặc định của Spring Security (đăng nhập bằng JWT)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableCaching
public class GuessGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuessGameApplication.class, args);
    }
}
