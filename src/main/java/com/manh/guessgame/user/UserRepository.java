package com.manh.guessgame.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Trừ 1 lượt một cách nguyên tử (atomic) ngay trong database.
     * Điều kiện "turns > 0" nằm trong câu UPDATE nên dù user gọi /guess
     * nhiều lần cùng lúc, database chỉ cho trừ đúng số lượt đang có
     * (row lock của UPDATE). Trả về 1 nếu trừ được, 0 nếu hết lượt.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.turns = u.turns - 1 where u.id = :id and u.turns > 0")
    int consumeTurn(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.score = u.score + 1 where u.id = :id")
    int incrementScore(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.turns = u.turns + :amount where u.id = :id")
    int addTurns(@Param("id") Long id, @Param("amount") int amount);

    /** Top 10 theo score, dùng index (score DESC, id ASC). Bằng điểm thì ai đăng ký trước xếp trên. */
    @Query("select u.username as username, u.score as score from User u order by u.score desc, u.id asc limit 10")
    List<LeaderboardRow> findTop10();

    interface LeaderboardRow {
        String getUsername();
        int getScore();
    }
}
