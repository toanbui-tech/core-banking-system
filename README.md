# Core Banking System

Hệ thống ledger ngân hàng lõi (double-entry bookkeeping), triển khai kèm Audit Trail,
DDD Aggregate Root, Outbox Pattern + Kafka, Redis caching, và hỗ trợ chạy song song
2 hệ quản trị CSDL (PostgreSQL và Oracle) qua Spring Profile.

## Yêu cầu hệ thống

- Java 17
- Docker + Docker Compose (chạy PostgreSQL, Kafka, Redis, Oracle)
- Maven Wrapper có sẵn (`./mvnw`), không cần cài Maven riêng

## Khởi động hạ tầng

```bash
docker compose up -d
```

Khởi động cả 4 service: `postgres`, `kafka`, `redis`, `oracle`. Có thể khởi động riêng lẻ,
VD chỉ cần Postgres: `docker compose up -d postgres`.

**Lưu ý:** container `oracle` khởi động **chậm hơn đáng kể** so với 3 service còn lại
(image nặng hơn nhiều dù đã dùng bản `slim-faststart`). Kiểm tra sẵn sàng bằng:

```bash
docker logs -f core-banking-oracle
# đợi tới khi thấy dòng: "DATABASE IS READY TO USE!"
```

### Port mapping (host → container)

Tất cả port host đều lệch so với port mặc định của service, để tránh xung đột với
service cùng loại có thể đã cài sẵn trên máy dev.

| Service    | Container port | Host port |
|------------|-----------------|-----------|
| PostgreSQL | 5432            | 5434      |
| Kafka      | 9092            | 9092      |
| Redis      | 6379            | 6380      |
| Oracle     | 1521            | 1522      |

## Chạy ứng dụng theo profile

Project hỗ trợ **2 profile CSDL chạy song song, không thay thế nhau**: PostgreSQL
(mặc định, không cần khai báo gì thêm) và Oracle (kích hoạt qua Spring Profile `oracle`).

### Profile mặc định — PostgreSQL

```bash
./mvnw spring-boot:run
```

Đọc cấu hình từ `application.properties`, kết nối `localhost:5434`.

### Profile Oracle

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=oracle
```

Đọc thêm cấu hình từ `application-oracle.properties` (override datasource, driver,
đường dẫn Flyway migration), kết nối `localhost:1522/FREEPDB1`.

## Chạy test

Test hiện có (unit + integration) kết nối trực tiếp vào container docker-compose thật
(không dùng Testcontainers cho CSDL chính, chỉ dùng Testcontainers cho Kafka/Redis khi
cần môi trường cô lập/port động — xem các test trong package `compliance`, `account`).

### Trên PostgreSQL (mặc định)

```bash
docker compose up -d postgres kafka redis
./mvnw test
```

### Trên Oracle

```bash
docker compose up -d oracle kafka redis
# đợi "DATABASE IS READY TO USE!" trong log container oracle trước khi chạy
./mvnw test -Dspring.profiles.active=oracle
```

Toàn bộ 45 test hiện có chạy được nguyên vẹn trên cả 2 profile, không cần sửa test
theo profile — sự khác biệt CSDL được xử lý hoàn toàn ở tầng cấu hình/migration,
không rò lên tầng test.

## Cấu trúc Flyway migration theo CSDL

```
src/main/resources/db/migration/
├── postgresql/   V1..V7 — dùng khi chạy profile mặc định
└── oracle/       V1..V7 — dùng khi chạy profile "oracle"
```

Hai bộ migration **tách thư mục hoàn toàn** (không dùng chung 1 thư mục) để Flyway của
mỗi profile chỉ quét đúng migration của CSDL tương ứng, tránh áp nhầm SQL sai dialect.
`spring.flyway.locations` được cấu hình riêng theo từng profile (`application.properties`
trỏ `db/migration/postgresql`, `application-oracle.properties` trỏ `db/migration/oracle`).

Nội dung 2 bộ migration **tương đương về schema nhưng khác cú pháp SQL** do khác biệt
dialect (VD: `UUID` → `RAW(16)`, `JSONB` → `JSON`, `UPDATE ... FROM` → `MERGE INTO`,
partial index → function-based index). Chi tiết từng điểm khác biệt và lý do lựa chọn
kỹ thuật sẽ được ghi trong ADR riêng (chưa viết ở thời điểm này).
