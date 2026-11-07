package com.manh.guessgame.auth;

import com.manh.guessgame.auth.AuthDtos.LoginRequest;
import com.manh.guessgame.auth.AuthDtos.RegisterRequest;
import com.manh.guessgame.auth.AuthDtos.RegisterResponse;
import com.manh.guessgame.auth.AuthDtos.TokenResponse;
import com.manh.guessgame.common.ApiException;
import com.manh.guessgame.security.AuthUser;
import com.manh.guessgame.security.JwtService;
import com.manh.guessgame.user.User;
import com.manh.guessgame.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    // Hash giả để so sánh khi username không tồn tại -> thời gian phản hồi như nhau,
    // tránh bị dò username qua thời gian (timing attack).
    private final String dummyHash;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final int initialTurns;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       @Value("${app.game.initial-turns}") int initialTurns) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.initialTurns = initialTurns;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public RegisterResponse register(RegisterRequest req) {
        String username = req.username().trim();
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username đã tồn tại");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email đã được sử dụng");
        }
        try {
            User user = userRepository.save(
                    new User(username, email, passwordEncoder.encode(req.password()), initialTurns));
            return new RegisterResponse(user.getId(), user.getUsername(), user.getEmail(), user.getTurns());
        } catch (DataIntegrityViolationException e) {
            // 2 request đăng ký trùng gửi cùng lúc: unique constraint trong DB chặn lại
            throw new ApiException(HttpStatus.CONFLICT, "USER_EXISTS", "Username hoặc email đã tồn tại");
        }
    }

    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username().trim()).orElse(null);
        String hash = user != null ? user.getPassword() : dummyHash;
        boolean matches = passwordEncoder.matches(req.password(), hash);
        if (user == null || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Sai username hoặc mật khẩu");
        }
        String token = jwtService.generateToken(new AuthUser(user.getId(), user.getUsername()));
        return new TokenResponse(token, "Bearer", jwtService.getExpiresInSeconds());
    }
}
