package com.manh.guessgame.user;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users",
        indexes = @Index(name = "idx_users_score", columnList = "score DESC, id ASC"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false)
    private int score = 0;

    @Column(nullable = false)
    private int turns = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
    }

    public User(String username, String email, String passwordHash, int turns) {
        this.username = username;
        this.email = email;
        this.password = passwordHash;
        this.turns = turns;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public int getScore() { return score; }
    public int getTurns() { return turns; }
    public Instant getCreatedAt() { return createdAt; }
}
