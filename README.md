# Core Banking System

Hệ thống ledger ngân hàng lõi (double-entry bookkeeping), triển khai kèm Audit Trail,
DDD Aggregate Root, Outbox Pattern + Kafka, Redis caching, và hỗ trợ chạy song song
2 hệ quản trị CSDL (PostgreSQL và Oracle) qua Spring Profile.

> **Mới xem repo lần đầu?** Đọc [docs/HUONG-DAN.md](docs/HUONG-DAN.md): chạy app trong vài
> phút, thứ tự đọc code, luồng xử lý một giao dịch, và lý do chọn từng công nghệ.
> Sau khi chạy app, API xem và gọi thử được tại http://localhost:8080/swagger-ui.html

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

## Kubernetes

Deploy app Java lên Kubernetes — **không** kèm data layer (Postgres/Oracle/Kafka/Redis
vẫn chạy qua `docker-compose` như bình thường). Mục đích chính: verify Pessimistic
Locking (chống overdraft) giữ vững khi nhiều instance ứng dụng chạy song song.

### Yêu cầu

- Docker Desktop với Kubernetes bật sẵn (Settings → Kubernetes → Enable Kubernetes).
  Không dùng minikube/kind — Docker Desktop K8s dùng chung Docker engine/image cache
  với `docker build`, không cần bước load image riêng vào cluster.
- Hạ tầng data layer đã chạy qua `docker compose up -d` (xem [Khởi động hạ tầng](#khởi-động-hạ-tầng)
  ở trên) — Pod trong K8s gọi ra ngoài cluster qua `host.docker.internal`.

### Build image & deploy

```bash
docker build -t core-banking-system:local .

kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/
```

`Deployment` dùng `imagePullPolicy: Never` — image build local, không qua registry nào.

### Truy cập

`Service` kiểu `NodePort`, truy cập thẳng từ máy host qua `localhost:30080`, không cần
`kubectl port-forward`:

```bash
curl http://localhost:30080/actuator/health

curl -X POST http://localhost:30080/accounts \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"ACC001","accountType":"CHECKING","currency":"VND"}'
```

### Kafka khi chạy trong container/Pod

`localhost` bên trong container không phải là máy host, nên broker Kafka của
`docker-compose` advertise thêm 1 listener riêng cho trường hợp này:

| Client                                                | Bootstrap servers            |
|--------------------------------------------------------|-------------------------------|
| Chạy trực tiếp trên host (`mvn spring-boot:run`, test)  | `localhost:9092`             |
| Chạy trong container/Pod                                | `host.docker.internal:9094`  |

`k8s/configmap.yaml` đã trỏ sẵn `SPRING_KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9094`.

### Startup probe

`startupProbe` cho phép tối đa **120 giây** (`periodSeconds: 5` × `failureThreshold: 24`)
để app kết nối xong DB/Kafka/Redis và readiness pass, trước khi `livenessProbe` bắt đầu
tính — đây là ngưỡng cấu hình trong manifest (biên an toàn), không phải thời gian khởi
động thực tế đo được.

### Verify Pessimistic Locking qua nhiều Pod

```bash
kubectl scale deployment core-banking-app -n core-banking --replicas=3
kubectl get pods -n core-banking -w
```

Bắn nhiều request `withdraw` đồng thời vào cùng 1 account (VD nhiều `curl` chạy song
song trong background), rồi đối chiếu 2 lớp bằng chứng qua log:

```bash
kubectl logs -n core-banking -l app=core-banking-app --prefix -f
```

- **Kết quả nghiệp vụ:** đúng 1 request thành công, các request còn lại nhận HTTP 409,
  balance cuối cùng chính xác — không âm, không trừ lặp.
- **Phân tán thật qua nhiều Pod:** `AccountController` log kèm tên Pod (đọc biến môi
  trường `HOSTNAME` — K8s tự set bằng tên Pod) — xác nhận request thực sự rơi vào nhiều
  Pod khác nhau, không phải 1 Pod xử lý hết do tình cờ route.

Bối cảnh đầy đủ, phương án cân nhắc, và chi tiết kết quả verify được ghi trong ADR
riêng (portfolio docs, chưa đưa vào repo này).

### Giới hạn đã biết

`OutboxEventPublisher` (`@Scheduled`) không có lock phân tán khi nhiều Pod cùng chạy —
3 consumer PoC (Notification/Fraud Detection/Reporting) có thể log trùng nếu nhiều Pod
cùng đọc trúng 1 `OutboxEvent` chưa publish. `AuditComplianceConsumer` không bị ảnh
hưởng vì đã idempotent qua bảng `processed_events`.

## Đóng góp

Tên nhánh, commit message và tiêu đề PR theo luật trong [CONTRIBUTING.md](CONTRIBUTING.md) (Conventional Commits), được kiểm tra tự động trên mỗi PR bởi [`.github/workflows/naming.yml`](.github/workflows/naming.yml).
