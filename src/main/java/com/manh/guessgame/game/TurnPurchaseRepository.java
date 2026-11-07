package com.manh.guessgame.game;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurnPurchaseRepository extends JpaRepository<TurnPurchase, Long> {

    boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
