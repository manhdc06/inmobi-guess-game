package com.manh.guessgame.game;

import com.manh.guessgame.game.GameDtos.LeaderboardEntry;
import com.manh.guessgame.user.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class LeaderboardService {

    private final UserRepository userRepository;

    public LeaderboardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Top 10 được cache 5 giây (Caffeine, cấu hình trong application.yml).
     * Dù có hàng nghìn request/giây, DB chỉ bị query tối đa 1 lần mỗi 5 giây,
     * và câu query đó đi qua index (score DESC, id ASC) nên chỉ đọc 10 dòng.
     * sync = true: khi cache hết hạn, chỉ 1 thread query DB, các thread khác chờ kết quả.
     */
    @Cacheable(cacheNames = "leaderboard", key = "'top10'", sync = true)
    @Transactional(readOnly = true)
    public List<LeaderboardEntry> top10() {
        var rows = userRepository.findTop10();
        List<LeaderboardEntry> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            result.add(new LeaderboardEntry(i + 1, rows.get(i).getUsername(), rows.get(i).getScore()));
        }
        return List.copyOf(result);
    }
}
