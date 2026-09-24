# Hướng dẫn: chạy, đọc code, luồng xử lý và công nghệ

Tài liệu này dành cho người mới mở repo: làm sao chạy được app trong vài phút, nên đọc
code theo thứ tự nào, một request đi qua những đâu, và vì sao chọn từng công nghệ.
Chi tiết về Oracle và Kubernetes xem thêm ở [README](../README.md).

---

## 1. Dự án là gì

Một **sổ cái (ledger) ngân hàng lõi** theo nguyên tắc **bút toán kép (double-entry)**:

- Không có cột "số dư". Mọi thay đổi tiền là một **giao dịch** gồm các **bút toán** Nợ/Có,
  và trong mỗi giao dịch **tổng Nợ = tổng Có**.
- **Số dư = tổng Có − tổng Nợ** của tài khoản, luôn tính ra từ lịch sử bút toán.
- Bút toán **không bao giờ bị sửa hay xóa**. Ghi sai thì tạo một giao dịch **đảo ngược**.
- Sau khi ghi sổ, sự kiện giao dịch được phát ra **Kafka** một cách tin cậy để các hệ
  thống khác (compliance, fraud, notification, reporting) xử lý.

Ví dụ: An có 400k và rút 100k. Hệ thống ghi một giao dịch gồm 2 bút toán:

| Tài khoản | Loại | Số tiền |
|---|---|---|
| An | Nợ (tiền ra) | 100k |
| Quỹ tiền mặt | Có (tiền vào) | 100k |

Số dư của An lúc này tính ra là 300k.

---

## 2. Chạy dự án

### Yêu cầu
- **Java 17**
- **Docker Desktop** (đang chạy, trạng thái "Engine running")
- Không cần cài Maven, vì repo có sẵn Maven Wrapper

### Bước 1: Khởi động hạ tầng
```bash
docker compose up -d postgres kafka redis
```
Oracle **không bắt buộc** (profile mặc định dùng PostgreSQL). Kiểm tra bằng `docker ps`:
phải có 3 container `core-banking-db`, `core-banking-kafka`, `core-banking-redis`.

| Service | Cổng trên máy host |
|---|---|
| PostgreSQL | 5434 |
| Kafka | 9092 |
| Redis | 6380 |

Các cổng này cố ý lệch khỏi cổng mặc định để tránh trùng với service đã cài sẵn trên máy.

### Bước 2: Chạy app
| Terminal | Lệnh |
|---|---|
| Git Bash / macOS / Linux | `./mvnw spring-boot:run` |
| PowerShell / CMD | `.\mvnw.cmd spring-boot:run` |

Đợi tới dòng log `Started CoreBankingSystemApplication`. Flyway tự tạo bảng ở lần chạy đầu.

### Bước 3: Mở Swagger UI
**http://localhost:8080/swagger-ui.html**

Thử theo thứ tự (mỗi API có sẵn body mẫu, bấm "Try it out"):
1. **Tạo tài khoản** 2 lần: một tài khoản khách hàng và một "quỹ tiền mặt". Ghi lại 2 `id`.
2. **Nạp tiền** vào tài khoản khách hàng, với `counterpartyAccountId` là id của quỹ.
3. **Rút tiền**. Thử rút nhiều hơn số dư để thấy lỗi **409**.
4. **Xem số dư**.

### Dừng
- App: `Ctrl + C`
- Hạ tầng: `docker compose stop` (giữ dữ liệu). Lệnh `docker compose down -v` sẽ **xóa sạch** dữ liệu.

### Chạy test
```bash
docker compose up -d postgres kafka redis
./mvnw test
```
Test kết nối vào PostgreSQL thật của docker-compose. Riêng các test cần Kafka hoặc Redis
cô lập thì dùng Testcontainers.

### Lỗi thường gặp
| Lỗi | Cách xử lý |
|---|---|
| `error during connect ... docker_engine` | Docker Desktop chưa chạy |
| `bash: .mvnw.cmd: command not found` | Đang ở Git Bash, dùng `./mvnw` |
| `Connection refused` tới `localhost:5434` | Container Postgres chưa chạy xong |
| `Port 8080 already in use` | Tắt app khác đang dùng cổng 8080 |

---

## 3. API

| Method | URL | Chức năng | Lỗi có thể trả về |
|---|---|---|---|
| `POST` | `/accounts` | Tạo tài khoản | |
| `GET` | `/accounts/{id}/balance` | Xem số dư | 404 |
| `POST` | `/accounts/{id}/deposit` | Nạp tiền | 400 (sai loại tiền), 404 |
| `POST` | `/accounts/{id}/withdraw` | Rút tiền | 400, 404, **409 (không đủ số dư)** |
| `GET` | `/actuator/health` | Health check cho Kubernetes | |

Body của nạp/rút:
```json
{ "counterpartyAccountId": "<id quỹ tiền mặt>", "amount": 100000, "currency": "VND", "createdBy": "an" }
```
`counterpartyAccountId` bắt buộc phải có, vì theo bút toán kép tiền vào tài khoản này thì
phải đi ra từ một tài khoản khác.

---

## 4. Cấu trúc thư mục

```
src/main/java/com/banking/core_banking_system/
├── account/          REST API, tạo tài khoản, nạp/rút, tính số dư, cache Redis
├── ledger/           TRÁI TIM: Transaction (aggregate), LedgerEntry, LedgerService
│   └── event/        Sự kiện domain: TransactionPostedEvent, TransactionReversedEvent
├── outbox/           Bảng outbox + job đẩy sự kiện lên Kafka
├── compliance/       Consumer Kafka ghi compliance_records (idempotent)
├── frauddetection/   Consumer Kafka (PoC: chỉ ghi log)
├── notification/     Consumer Kafka (PoC: chỉ ghi log)
├── reporting/        Consumer Kafka (PoC: chỉ ghi log)
└── shared/money/     Value object Money (số tiền + loại tiền)

src/main/resources/
├── application.properties           Cấu hình mặc định (PostgreSQL)
├── application-oracle.properties    Profile Oracle
└── db/migration/{postgresql,oracle}/ Flyway V1..V7, mỗi CSDL một bộ riêng

k8s/                  Manifest Kubernetes (Deployment 3 replica, Service NodePort 30080)
```

---

## 5. Đọc code theo thứ tự nào

Mỗi bước chỉ cần hiểu một ý, sau đó mới sang bước tiếp theo.

| # | File | Cần hiểu được |
|---|---|---|
| 1 | `shared/money/Money.java` | Tiền = số + loại tiền. Không cho cộng VND với USD. Scale cố định là 2 |
| 2 | `ledger/LedgerEntry.java`, `EntryType.java` | Một dòng trong sổ: tài khoản, Nợ/Có, số tiền |
| 3 | `ledger/Transaction.java` | `record()` kiểm tra Nợ = Có. `reverse()` tạo giao dịch đảo |
| 4 | `ledger/LedgerService.java` | Lưu giao dịch và lưu sự kiện vào outbox **trong cùng một transaction DB** |
| 5 | `account/AccountService.java` | `withdraw()`: khóa tài khoản, tính số dư, kiểm tra đủ tiền, ghi sổ |
| 6 | `account/AccountController.java` | REST API gọi vào `AccountService` |
| 7 | `account/AccountBalanceCache*.java` | Cache số dư trong Redis, xóa cache sau khi commit |
| 8 | `outbox/OutboxEventPublisher.java` | Cứ 5 giây đẩy sự kiện chưa gửi lên Kafka |
| 9 | `compliance/AuditComplianceService.java` | Nhận sự kiện, bỏ qua nếu đã xử lý (idempotent) |
| 10 | `src/test/.../ledger/TransactionTest.java` | Test là tài liệu sống, cho thấy các luật được kiểm tra thế nào |

---

## 6. Luồng xử lý: rút tiền

```
POST /accounts/{id}/withdraw
│
├─ PHẦN 1: GHI SỔ (đồng bộ, trong 1 transaction DB)
│  ① AccountController      nhận request, tạo Money
│  ② AccountService.withdraw
│       • SELECT ... FOR UPDATE trên account   → khóa, request khác phải chờ
│       • kiểm tra loại tiền khớp tài khoản
│       • tính số dư từ DB (KHÔNG đọc Redis)
│       • không đủ tiền → 409
│       • tạo 2 bút toán: tài khoản Nợ, đối ứng Có
│  ③ LedgerService.recordTransaction
│       • Transaction.record() → kiểm tra Nợ = Có
│       • lưu Transaction + LedgerEntry
│       • lưu OutboxEvent (cùng transaction)
│  ── COMMIT (tất cả thành công hoặc không có gì được lưu; khóa được nhả) ──
│  ④ AFTER_COMMIT: xóa số dư cũ trong Redis
│  → trả về số dư mới cho client
│
└─ PHẦN 2: BÁO TIN (bất đồng bộ, chạy nền)
   ⑤ OutboxEventPublisher (mỗi 5 giây)
        • lấy OutboxEvent có published_at = null
        • gửi lên Kafka topic "transaction-posted-topic", key = transactionId
        • gửi thành công → ghi published_at
   ⑥ Các consumer (mỗi consumer một group, đều nhận đủ message)
        • AuditComplianceConsumer → ghi compliance_records, chống trùng qua processed_events
        • Fraud / Notification / Reporting → ghi log (PoC)
```

**Các luồng khác:**
- **Nạp tiền:** giống rút tiền nhưng không khóa và không kiểm tra số dư, vì nạp tiền không
  thể làm số dư âm.
- **Xem số dư:** đọc Redis. Nếu không có thì tính từ DB rồi lưu vào Redis (TTL 1 giờ).
- **Đảo giao dịch** (`LedgerService.reverseTransaction`): tạo giao dịch mới với Nợ/Có đổi
  chỗ, sau đó đi tiếp như bước ③. Hiện chưa có REST API cho chức năng này.

---

## 7. Công nghệ và lý do chọn

| Công nghệ | Dùng để | Lý do |
|---|---|---|
| **Java 17, Spring Boot 4** | Nền tảng ứng dụng | Phổ biến trong ngân hàng, hệ sinh thái đầy đủ (JPA, transaction, Kafka, Redis) |
| **PostgreSQL 15** | CSDL chính | Hỗ trợ tốt ACID, `SELECT FOR UPDATE`, `JSONB`, partial index |
| **Oracle 23 Free** (profile `oracle`) | CSDL thay thế | Ngân hàng dùng Oracle rất nhiều. Chứng minh tầng domain không phụ thuộc CSDL: cùng bộ test chạy trên cả hai |
| **Flyway** | Quản lý thay đổi schema | Schema có phiên bản, review được. Hibernate chỉ `validate`, không tự sửa bảng |
| **Spring Data JPA / Hibernate** | Truy cập dữ liệu | Hỗ trợ khóa bi quan qua `@Lock(PESSIMISTIC_WRITE)` và value object qua `@Embeddable` |
| **Kafka** | Phát sự kiện giao dịch | Một sự kiện đến nhiều hệ thống độc lập (mỗi consumer group đọc riêng). Lưu bền, đọc lại được |
| **Redis** | Cache số dư | Tính số dư bằng `SUM` tốn kém khi lịch sử dài. Cache giúp API xem số dư nhanh |
| **Testcontainers** | Test tích hợp với Kafka, Redis | Kafka/Redis thật, cô lập, cổng động, không ảnh hưởng môi trường dev |
| **springdoc-openapi** | Swagger UI | Xem và gọi thử API ngay trên trình duyệt |
| **Docker Compose** | Hạ tầng local | Một lệnh để có đủ Postgres, Kafka, Redis, Oracle |
| **Kubernetes** (Docker Desktop) | Chạy nhiều instance | Kiểm chứng khóa chống rút quá số dư vẫn đúng khi 3 pod cùng nhận request |

### Các quyết định thiết kế quan trọng

**Số dư tính từ ledger, không lưu thành cột**
Ledger là nguồn sự thật duy nhất, nên số dư không thể lệch khỏi lịch sử. Cái giá là phải
`SUM` toàn bộ bút toán, và Redis được dùng để giảm chi phí này khi đọc.

**Khóa bi quan (pessimistic lock) khi rút tiền**
Nếu hai request rút tiền đến cùng lúc, cả hai có thể cùng thấy "đủ tiền", dẫn đến số dư âm.
`SELECT ... FOR UPDATE` buộc các request trên cùng một tài khoản xếp hàng. Không dùng khóa
lạc quan (`@Version`) vì số dư không nằm trên dòng account nên không có gì tự nhiên để gắn
version, và khi nhiều request tranh chấp thì khóa lạc quan phải retry liên tục.

**Transaction là aggregate root**
Luật Nợ = Có được kiểm tra bên trong `Transaction.record()`, nên không có cách nào ghi một
giao dịch lệch, kể cả khi service quên kiểm tra.

**Không sửa, chỉ đảo**
Đây là yêu cầu audit của ngân hàng. Mỗi bút toán đảo có `reversal_of_entry_id` trỏ về bút
toán gốc, và mọi bút toán có `created_by`.

**Value object `Money`**
Dùng `BigDecimal` thay vì `double` để không có sai số nhị phân. Cộng khác loại tiền sẽ báo
lỗi. Scale cố định là 2 cho mọi loại tiền, kể cả VND, để không mất độ chính xác khi tính
lãi hay phí theo %. `RoundingMode.UNNECESSARY` làm lộ ra lỗi thay vì âm thầm làm tròn.

**Outbox pattern**
Nếu vừa ghi DB vừa gửi Kafka ("dual write"), một trong hai có thể thất bại: mất sự kiện
hoặc phát sự kiện cho giao dịch không tồn tại. Ghi sự kiện vào bảng `outbox_events`
**trong cùng transaction** với bút toán, rồi một job riêng gửi lên Kafka, sẽ bảo đảm hai
việc luôn đi cùng nhau. Chọn polling thay vì CDC (Debezium) để đơn giản, chấp nhận độ trễ
tới 5 giây.

**Consumer idempotent**
Outbox và Kafka đảm bảo giao message **ít nhất một lần**, tức là có thể giao trùng.
`AuditComplianceService` lưu `eventId` vào `processed_events` (primary key), nên message
trùng sẽ bị bỏ qua. Nếu hai message trùng được xử lý song song, primary key sẽ chặn một trong hai.

**Xóa cache ở `AFTER_COMMIT`, và rút tiền không đọc cache**
Nếu xóa cache trước khi commit, một request đọc song song có thể nạp lại số dư cũ vào
cache. Rút tiền luôn tính số dư từ DB bên trong khóa, vì đọc cache ở đây sẽ làm vô hiệu
khóa bi quan.

---

## 8. Giới hạn đã biết

Đây là dự án cá nhân để học và minh họa. Các điểm dưới đây chưa được xử lý:

- **Chưa có idempotency key cho API:** client gửi lại request rút tiền (ví dụ do timeout) sẽ
  bị trừ tiền hai lần.
- **Chưa chặn đảo một giao dịch hai lần:** `findByReversalOfTransactionId` đã có nhưng
  chưa được dùng trong `reverseTransaction`.
- **Chưa có xác thực:** `createdBy` do client tự khai báo.
- **Tính số dư bằng `SUM` toàn bộ lịch sử:** chi phí tăng dần theo số giao dịch. Hướng cải
  thiện là lưu số dư tính sẵn, cập nhật dưới cùng khóa, kèm job đối soát.
- **Cache-aside vẫn còn một khe race nhỏ:** request đọc có thể ghi số dư cũ vào cache ngay
  sau khi cache vừa bị xóa. Rút tiền không bị ảnh hưởng vì không đọc cache.
- **Outbox publisher không có khóa phân tán:** khi chạy nhiều pod, sự kiện có thể bị gửi
  trùng. Consumer compliance đã chống trùng, còn các consumer PoC thì chưa.
- **Sự kiện đảo dùng key Kafka khác giao dịch gốc,** nên không đảm bảo thứ tự giữa sự kiện
  gốc và sự kiện đảo.
- **Swagger UI đang bật ở mọi môi trường.** Production nên tắt bằng
  `springdoc.swagger-ui.enabled=false`.
