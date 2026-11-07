# Game Đoán Số – Java Spring Boot

Bài test Java: API game đoán số từ 1 đến 5. Người dùng đăng ký, đăng nhập (JWT), đoán số, mua thêm lượt, xem bảng xếp hạng và thông tin cá nhân. Có giao diện web đơn giản tại `http://localhost:8080`.

## Công nghệ

- Java 17, Spring Boot 3.3
- Spring Security + JWT (jjwt), mật khẩu băm bằng BCrypt
- Spring Data JPA; H2 (mặc định, không cần cài) hoặc MySQL
- Caffeine cache cho leaderboard
- JUnit 5, MockMvc, Mockito

## 1. Setup môi trường

- JDK 17 trở lên (`java -version`)
- Maven 3.9+ (`mvn -v`), hoặc mở project bằng IntelliJ IDEA, IDE có sẵn Maven
- Tuỳ chọn: MySQL 8 nếu muốn chạy với MySQL

## 2. Build và chạy

```bash
# chạy test
mvn test

# chạy app với H2 (dữ liệu lưu ở ./data)
mvn spring-boot:run

# hoặc build file jar rồi chạy
mvn clean package
java -jar target/guess-game-1.0.0.jar
```

Chạy với MySQL:

```bash
set DB_URL=jdbc:mysql://localhost:3306/guessgame?createDatabaseIfNotExist=true
set DB_USER=root
set DB_PASSWORD=your_password
set JWT_SECRET=mot-chuoi-bi-mat-dai-toi-thieu-32-ky-tu
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

Bảng được Hibernate tạo tự động (`ddl-auto: update`).

- Giao diện: http://localhost:8080
- H2 console (chỉ có ở profile mặc định): http://localhost:8080/h2-console, JDBC URL `jdbc:h2:file:./data/guessgame`, user `sa`, không có mật khẩu.

## 3. Danh sách API

| Method | Endpoint | Auth | Mô tả |
|---|---|---|---|
| POST | `/register` | Không | Đăng ký (username, email, password) |
| POST | `/login` | Không | Đăng nhập, trả về JWT |
| POST | `/guess` | Bearer | Đoán số 1–5, trừ 1 lượt, đúng thì +1 điểm |
| POST | `/buy-turns` | Bearer | Mua thêm 5 lượt (header `Idempotency-Key` tuỳ chọn) |
| GET | `/leaderboard` | Không | Top 10 người có điểm cao nhất |
| GET | `/me` | Bearer | Username, email, score, turns còn lại |

Lỗi trả về theo cùng một định dạng:

```json
{ "timestamp": "...", "status": 409, "error": "NO_TURNS_LEFT", "message": "Bạn đã hết lượt. Gọi POST /buy-turns để mua thêm" }
```

| HTTP status | Khi nào |
|---|---|
| 400 | Dữ liệu sai, ví dụ đoán số ngoài 1–5 |
| 401 | Thiếu token, token sai hoặc hết hạn, sai mật khẩu |
| 409 | Hết lượt, username hoặc email đã tồn tại |

## 4. Xác thực: cách lấy token

1. Gọi `POST /register` để tạo tài khoản. User mới được tặng 5 lượt, cấu hình ở `app.game.initial-turns`.
2. Gọi `POST /login` để nhận `accessToken`. Token hết hạn sau 60 phút.
3. Gửi kèm header `Authorization: Bearer <accessToken>` với các API cần đăng nhập.

## 5. Test nhanh bằng curl hoặc Postman

```bash
curl -X POST http://localhost:8080/register -H "Content-Type: application/json" \
  -d '{"username":"manh","email":"manh@example.com","password":"123456"}'

curl -X POST http://localhost:8080/login -H "Content-Type: application/json" \
  -d '{"username":"manh","password":"123456"}'
# -> {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":3600}

TOKEN=eyJ...

curl -X POST http://localhost:8080/guess -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"number":3}'
# -> {"guess":3,"serverNumber":5,"correct":false,"score":0,"turns":4}

curl -X POST http://localhost:8080/buy-turns -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: order-001"
# -> {"addedTurns":5,"turns":9,"duplicate":false}

curl http://localhost:8080/me -H "Authorization: Bearer $TOKEN"
curl http://localhost:8080/leaderboard
```

Với Postman: tạo request `POST /login`, copy `accessToken`, rồi ở tab Authorization chọn **Bearer Token** và dán token vào.

## 6. Logic game

- Server sinh số trong khoảng 1–5.
- **Tỷ lệ thắng là 5%** (cấu hình ở `app.game.win-rate`):
  - Mỗi lần đoán, server quyết định thắng với xác suất 5%.
  - Thắng: số của server bằng đúng số user đoán.
  - Thua: server chọn ngẫu nhiên một trong 4 số còn lại.
  - Vì vậy `serverNumber` trả về luôn khớp với `correct`.
- Dùng `SecureRandom` để không đoán trước được kết quả.
- Chỉ được đoán khi `turns > 0`. Mỗi lần đoán trừ 1 lượt, đoán đúng được +1 điểm.

## 7. Phần nâng cao

### 7.1. Một user gọi `/guess` nhiều lần cùng lúc

Cách làm thông thường là đọc `turns`, kiểm tra lớn hơn 0, rồi lưu `turns - 1`. Cách này bị race condition: 2 request cùng đọc `turns = 1`, cả hai đều qua bước kiểm tra, và user được chơi 2 lần dù chỉ có 1 lượt.

Project này trừ lượt bằng một câu UPDATE có điều kiện, chạy nguyên tử trong database:

```sql
UPDATE users SET turns = turns - 1 WHERE id = ? AND turns > 0;
```

- Database khoá dòng khi thực hiện UPDATE, nên các request của cùng một user chạy lần lượt trên dòng đó.
- UPDATE trả về số dòng bị ảnh hưởng:
  - Bằng 1: đã trừ lượt, tiếp tục đoán.
  - Bằng 0: hết lượt, trả về 409.
- Điểm cũng được cộng bằng `score = score + 1` trong database, nên không bị mất điểm khi nhiều request ghi cùng lúc.
- Bước trừ lượt và cộng điểm nằm trong cùng một transaction. Nếu có lỗi giữa chừng thì rollback toàn bộ.
- Không dùng `synchronized` trong Java, vì cách đó không còn đúng khi chạy nhiều instance sau load balancer. Khoá ở mức database thì đúng cả khi scale ngang.

Test `GameIntegrationTest.concurrentGuesses_neverOverspendTurns` kiểm chứng điều này: bắn 50 request cùng lúc vào user có 5 lượt, kết quả đúng 5 request thành công và `turns` bằng 0, không bao giờ âm.

`/buy-turns` cũng xử lý trường hợp request bị gửi lặp:
- Client gửi header `Idempotency-Key`. Bảng `turn_purchases` có unique constraint trên cặp `(user_id, idempotency_key)`.
- Nếu bấm 2 lần hoặc client retry với cùng key, lượt chỉ được cộng một lần.
- Đây là cách các cổng thanh toán như MoMo hay VNPAY tránh trừ tiền hoặc cộng hàng hai lần.

### 7.2. `/leaderboard` và `/me` vẫn nhanh khi có nhiều user

**`/leaderboard`**
- Có index `(score DESC, id ASC)`, nên lấy top 10 chỉ cần đọc 10 dòng đầu của index, không phải quét và sắp xếp cả bảng.
- Kết quả được cache trong bộ nhớ bằng Caffeine trong 5 giây, với `sync = true`. Dù có hàng nghìn request mỗi giây, database chỉ bị query tối đa một lần mỗi 5 giây.
- Khi cache hết hạn, chỉ một thread query database, các thread khác chờ kết quả đó. Nhờ vậy tránh được hiện tượng cache stampede.
- Đánh đổi: bảng xếp hạng có thể trễ tối đa 5 giây.

**`/me`**
- Truy vấn theo khoá chính (PK), độ phức tạp O(log n), luôn nhanh kể cả khi bảng có hàng triệu dòng.
- User id lấy từ JWT, không cần query thêm để xác thực.
- Không cache, vì `turns` và `score` thay đổi sau mỗi lần đoán và phải trả về số chính xác.

**Hướng mở rộng khi tải rất lớn** (chưa làm trong bài test):
- Thay Caffeine bằng Redis để nhiều instance dùng chung cache.
- Leaderboard dùng Redis Sorted Set:
  - Mỗi lần đoán đúng chạy `ZINCRBY leaderboard 1 <user>`.
  - Lấy top 10 bằng `ZREVRANGE leaderboard 0 9 WITHSCORES`, độ phức tạp O(log n).
- Tách read replica cho các API chỉ đọc.

## 8. Bảo mật

- **JWT (HS256), stateless:**
  - Secret đọc từ biến môi trường `JWT_SECRET`, bắt buộc dài tối thiểu 32 byte.
  - Token có hạn 60 phút.
  - Trong token chỉ có user id và username, không chứa thông tin nhạy cảm.
- **Mật khẩu:**
  - Băm bằng BCrypt.
  - Kiểm tra độ dài 6–72 ký tự; 72 là giới hạn của BCrypt.
- **Đăng nhập sai:**
  - Sai username hay sai mật khẩu đều nhận cùng một thông báo.
  - Thời gian phản hồi như nhau trong cả hai trường hợp (so sánh với một hash giả), nên không dò được username nào tồn tại.
- **Kiểm tra dữ liệu đầu vào:**
  - Mọi input được kiểm tra bằng Bean Validation, ví dụ số đoán phải từ 1 đến 5 và email phải đúng định dạng.
  - Lỗi trả về theo định dạng thống nhất, không lộ stack trace.
- **Phân quyền:**
  - Không cần đăng nhập: `/register`, `/login`, `/leaderboard`.
  - Các API còn lại bắt buộc có token hợp lệ.
- **User id lấy từ token**, không lấy từ request body. Vì vậy user không thể đoán số hay mua lượt thay cho người khác.
- **Gợi ý khi đưa lên production:**
  - Thêm rate limit cho `/login` và `/guess`, ví dụ dùng Bucket4j.
  - Bắt buộc HTTPS.
  - Dùng refresh token.

## 9. Cấu trúc project

```
src/main/java/com/manh/guessgame
├── auth/        # /register, /login
├── game/        # /guess, /buy-turns, /me, /leaderboard, logic game, lịch sử mua lượt
├── user/        # entity User, repository (các câu UPDATE nguyên tử)
├── security/    # JWT service, filter
├── config/      # Spring Security
└── common/      # xử lý lỗi chung
src/main/resources/static/index.html   # giao diện web
src/test/java/...                      # test tích hợp, test concurrency, test tỷ lệ thắng
```

## 10. Hướng phát triển

- **Thanh toán thật:**
  - Tích hợp VNPAY hoặc MoMo cho `/buy-turns`.
  - Tạo đơn hàng ở trạng thái `PENDING`, chỉ cộng lượt khi nhận được IPN/webhook có chữ ký hợp lệ.
  - Dùng lại cơ chế idempotency theo mã đơn hàng.
- **Lưu lịch sử:** thêm bảng lịch sử các lần đoán để thống kê và chống gian lận.
- **Đăng nhập OAuth2:** hỗ trợ đăng nhập bằng Google.
