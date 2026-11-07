package com.manh.guessgame.game;

import com.manh.guessgame.common.ApiException;
import com.manh.guessgame.game.GameDtos.BuyTurnsResponse;
import com.manh.guessgame.game.GameDtos.GuessResponse;
import com.manh.guessgame.game.GameDtos.MeResponse;
import com.manh.guessgame.user.User;
import com.manh.guessgame.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;

@Service
public class GameService {

    static final int MIN_NUMBER = 1;
    static final int MAX_NUMBER = 5;

    private final UserRepository userRepository;
    private final TurnPurchaseRepository purchaseRepository;
    private final double winRate;
    private final int turnsPerPurchase;
    private final RandomGenerator random;

    @Autowired
    public GameService(UserRepository userRepository,
                       TurnPurchaseRepository purchaseRepository,
                       @Value("${app.game.win-rate}") double winRate,
                       @Value("${app.game.turns-per-purchase}") int turnsPerPurchase) {
        this(userRepository, purchaseRepository, winRate, turnsPerPurchase, new SecureRandom());
    }

    GameService(UserRepository userRepository, TurnPurchaseRepository purchaseRepository,
                double winRate, int turnsPerPurchase, RandomGenerator random) {
        if (winRate < 0 || winRate > 1) {
            throw new IllegalArgumentException("win-rate phải nằm trong [0, 1]");
        }
        this.userRepository = userRepository;
        this.purchaseRepository = purchaseRepository;
        this.winRate = winRate;
        this.turnsPerPurchase = turnsPerPurchase;
        this.random = random;
    }

    /**
     * Đoán số.
     *
     * Tính đúng đắn khi 1 user gọi /guess nhiều lần cùng lúc:
     * - Không đọc turns lên rồi mới trừ (read-modify-write sẽ bị race condition).
     * - Dùng 1 câu UPDATE có điều kiện: "turns = turns - 1 WHERE id = ? AND turns > 0".
     *   Database khoá dòng khi UPDATE nên các request chạy tuần tự trên dòng đó;
     *   user có N lượt thì đúng N request thành công, các request còn lại nhận 409.
     * - Cộng điểm cũng bằng UPDATE "score = score + 1", không bị mất điểm khi chạy song song.
     * - Cả hai nằm trong 1 transaction: lỗi giữa chừng thì rollback, không mất lượt oan.
     */
    @Transactional
    public GuessResponse guess(Long userId, int number) {
        if (number < MIN_NUMBER || number > MAX_NUMBER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NUMBER",
                    "Chỉ được đoán số từ " + MIN_NUMBER + " đến " + MAX_NUMBER);
        }
        if (userRepository.consumeTurn(userId) == 0) {
            if (!userRepository.existsById(userId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User không tồn tại");
            }
            throw new ApiException(HttpStatus.CONFLICT, "NO_TURNS_LEFT",
                    "Bạn đã hết lượt. Gọi POST /buy-turns để mua thêm");
        }

        boolean correct = random.nextDouble() < winRate;
        int serverNumber = correct ? number : randomOtherNumber(number);
        if (correct) {
            userRepository.incrementScore(userId);
        }

        User user = findUser(userId);
        return new GuessResponse(number, serverNumber, correct, user.getScore(), user.getTurns());
    }

    /**
     * Tỷ lệ thắng bị điều chỉnh còn winRate (mặc định 5%) thay vì 20% như random thuần 1-5.
     * Thua thì server trả về 1 số khác số user đoán, chọn ngẫu nhiên đều trong 4 số còn lại,
     * để kết quả trả về luôn nhất quán với "đúng/sai".
     */
    int randomOtherNumber(int guess) {
        int candidate = MIN_NUMBER + random.nextInt(MAX_NUMBER - MIN_NUMBER); // 1..4
        return candidate >= guess ? candidate + 1 : candidate;
    }

    /**
     * Mua thêm lượt (cộng trực tiếp, chưa tích hợp cổng thanh toán).
     * Nếu client gửi header Idempotency-Key thì request lặp lại với cùng key chỉ cộng 1 lần.
     */
    @Transactional
    public BuyTurnsResponse buyTurns(Long userId, String idempotencyKey) {
        if (idempotencyKey != null) {
            String key = idempotencyKey.trim();
            if (key.isEmpty() || key.length() > 64) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                        "Idempotency-Key phải dài 1-64 ký tự");
            }
            if (purchaseRepository.existsByUserIdAndIdempotencyKey(userId, key)) {
                return new BuyTurnsResponse(0, findUser(userId).getTurns(), true);
            }
            try {
                purchaseRepository.saveAndFlush(new TurnPurchase(userId, key, turnsPerPurchase));
            } catch (DataIntegrityViolationException e) {
                // 2 request cùng key chạy song song: unique constraint chặn request thứ 2
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_PURCHASE",
                        "Request mua lượt với key này đang được xử lý");
            }
        } else {
            purchaseRepository.save(new TurnPurchase(userId, UUID.randomUUID().toString(), turnsPerPurchase));
        }

        if (userRepository.addTurns(userId, turnsPerPurchase) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User không tồn tại");
        }
        return new BuyTurnsResponse(turnsPerPurchase, findUser(userId).getTurns(), false);
    }

    /** Đọc theo khoá chính (PK) nên luôn nhanh, kể cả khi bảng có hàng triệu user. */
    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User user = findUser(userId);
        return new MeResponse(user.getUsername(), user.getEmail(), user.getScore(), user.getTurns());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User không tồn tại"));
    }
}
