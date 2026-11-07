package com.manh.guessgame.game;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Lịch sử mua lượt. Cặp (user_id, idempotency_key) là unique: client gửi lại
 * cùng một request (mạng chập chờn, bấm 2 lần) thì chỉ được cộng lượt 1 lần.
 */
@Entity
@Table(name = "turn_purchases",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_user_key",
                columnNames = {"user_id", "idempotency_key"}))
public class TurnPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false)
    private int turns;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TurnPurchase() {
    }

    public TurnPurchase(Long userId, String idempotencyKey, int turns) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.turns = turns;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getTurns() { return turns; }
    public Instant getCreatedAt() { return createdAt; }
}
