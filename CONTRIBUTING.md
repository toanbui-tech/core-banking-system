# Luật đặt tên nhánh và commit

Repo dùng chuẩn [Conventional Commits](https://www.conventionalcommits.org/). Luật này
được kiểm tra tự động trên mỗi Pull Request bởi workflow
[`naming.yml`](.github/workflows/naming.yml): sai luật thì PR báo đỏ, ghi rõ chỗ sai.

Cùng một luật áp dụng cho cả `core-banking-system` và `banking-portfolio-docs`.

---

## 1. Type (dùng chung cho nhánh và commit)

| Type | Dùng khi | Ví dụ |
|---|---|---|
| `feat` | Thêm tính năng mới | thêm API chuyển tiền |
| `fix` | Sửa bug | chặn nạp tiền từ tài khoản khác loại tiền |
| `refactor` | Đổi cấu trúc code, **không** đổi hành vi | tách `AccountService` |
| `perf` | Tăng hiệu năng | thêm bảng snapshot số dư |
| `test` | Chỉ thêm/sửa test | test rút tiền đồng thời |
| `docs` | Chỉ sửa tài liệu (README, docs/, comment) | cập nhật hướng dẫn chạy |
| `build` | Build, dependency, Dockerfile, `pom.xml` | nâng Spring Boot |
| `ci` | GitHub Actions, pipeline | thêm workflow kiểm tra |
| `chore` | Việc vặt không thuộc nhóm nào ở trên | sửa `.gitignore` |
| `style` | Format, khoảng trắng — không đổi logic | chạy formatter |
| `revert` | Hoàn tác một commit trước đó | |

---

## 2. Tên nhánh

```
<type>/<mo-ta-ngan>
<type>/<so-issue>-<mo-ta-ngan>
```

- Chữ thường, số và dấu gạch ngang `-` (kebab-case). Không dấu cách, không `_`, không chữ hoa, không tiếng Việt có dấu.
- Tối đa 60 ký tự.
- Ngoài các type ở bảng trên, nhánh còn có thể dùng:
  - `hotfix/...` — sửa gấp lỗi đang chạy thật
  - `release/1.2.0` — chuẩn bị phát hành

| ✅ Đúng | ❌ Sai | Vì sao sai |
|---|---|---|
| `feat/transfer-api` | `feature/transfer-api` | `feature` không phải type |
| `fix/42-deposit-counterparty-check` | `fix/Deposit_Check` | chữ hoa, dấu `_` |
| `refactor/split-account-service` | `toan-dev` | thiếu type |
| `release/1.2.0` | `feat/` | thiếu mô tả |

Được miễn: `main`, `claude/*` (nhánh do Claude Code tự tạo), `dependabot/*`.

---

## 3. Commit message

```
<type>(<scope>): <mô tả>

<body — không bắt buộc: giải thích VÌ SAO thay đổi>
```

**Dòng đầu (bắt buộc đúng luật):**
- `type` lấy từ bảng ở mục 1.
- `scope` không bắt buộc, viết thường kebab-case: phần nào của hệ thống bị ảnh hưởng.
- Mô tả bằng **tiếng Anh**, thể mệnh lệnh (`add`, `fix`, `remove` — không phải `added`, `fixes`),
  bắt đầu bằng **chữ thường**, **không** kết thúc bằng dấu chấm.
- Cả dòng tối đa **72 ký tự**.
- Thay đổi phá vỡ tương thích (đổi API, đổi schema): thêm `!` trước dấu `:`,
  VD `feat(api)!: rename deposit endpoint`.

**Scope gợi ý cho repo này:**

| Scope | Phạm vi |
|---|---|
| `account` | Account, AccountService, AccountController |
| `ledger` | Transaction, LedgerEntry, LedgerService |
| `money` | Money, CurrencyConverter |
| `outbox` | OutboxEvent, OutboxEventPublisher |
| `cache` | Redis, AccountBalanceCache |
| `compliance`, `fraud`, `notification`, `reporting` | Các Kafka consumer |
| `db` | Flyway migration (Postgres/Oracle) |
| `api` | REST, Swagger |
| `k8s` | Manifest Kubernetes |
| `deps` | Dependency |

| ✅ Đúng | ❌ Sai | Vì sao sai |
|---|---|---|
| `fix(account): reject deposit from foreign-currency counterparty` | `Fix deposit bug` | thiếu type, viết hoa |
| `feat(api): add list accounts endpoint` | `feat(api): Added endpoint.` | chữ hoa, thì quá khứ, dấu chấm |
| `test(ledger): cover double reversal` | `feat:add test` | thiếu dấu cách sau `:` |
| `build(deps): bump spring boot to 4.1.2` | `update` | thiếu type và mô tả |
| `refactor(ledger)!: rename entry type column` | `feat(Ledger): ...` | scope viết hoa |

Được miễn: commit `Merge pull request ...`, `Merge branch ...` và `Revert "..."` do GitHub/git tự sinh.

**Tiêu đề Pull Request** theo đúng luật của dòng đầu commit (khi dùng *Squash and merge*,
tiêu đề PR trở thành commit trên `main`).

---

## 4. Tự kiểm tra trước khi push

```bash
# Tên nhánh hiện tại
bash .github/scripts/check-naming.sh branch "$(git branch --show-current)"

# Các commit chưa có trên main
bash .github/scripts/check-naming.sh range origin/main HEAD

# Thử một message trước khi commit
bash .github/scripts/check-naming.sh commit "feat(account): add transfer endpoint"
```

Sửa commit sai **trước khi** mở PR:

```bash
git commit --amend -m "fix(account): reject negative amount"   # commit mới nhất
git rebase -i origin/main                                        # nhiều commit: đổi "pick" thành "reword"
```

Lịch sử commit trước khi áp dụng luật này (VD `Add Redis caching ...`) giữ nguyên, không cần sửa —
CI chỉ kiểm tra các commit mới trong từng PR.
