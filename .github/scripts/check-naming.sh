#!/usr/bin/env bash
# Kiểm tra luật đặt tên nhánh và commit — xem CONTRIBUTING.md.
#
# Cách dùng:
#   check-naming.sh branch <tên-nhánh>
#   check-naming.sh commit "<dòng đầu commit message hoặc tiêu đề PR>"
#   check-naming.sh range <base-sha> <head-sha>   # mọi commit trong khoảng, bỏ qua merge commit
#
# Thoát với mã 1 nếu có vi phạm. Chạy trong GitHub Actions thì in thêm
# annotation ::error:: để lỗi hiện ngay trên tab "Checks" của PR.
set -euo pipefail

TYPES='feat|fix|refactor|perf|test|docs|build|ci|chore|style|revert'
MAX_HEADER=72
MAX_BRANCH=60

BRANCH_RE="^((${TYPES}|hotfix)/[a-z0-9]+(-[a-z0-9]+)*|release/v?[0-9]+\.[0-9]+\.[0-9]+)$"
# Nhánh do công cụ tự tạo, không bắt theo luật trên
BRANCH_EXEMPT_RE='^(main|claude/.+|dependabot/.+)$'

# <type>(<scope>)!: <subject> — scope và ! không bắt buộc
HEADER_RE="^(${TYPES})(\([a-z0-9]+(-[a-z0-9]+)*\))?!?: [^ A-Z].*$"
# Commit do GitHub/git tự sinh
HEADER_EXEMPT_RE='^(Merge (pull request|branch|remote-tracking branch) |Revert ")'

errors=0

fail() {
  errors=$((errors + 1))
  if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
    echo "::error::$1"
  else
    echo "✗ $1" >&2
  fi
}

check_branch() {
  local name="$1"
  [[ "$name" =~ $BRANCH_EXEMPT_RE ]] && return 0
  if [[ ! "$name" =~ $BRANCH_RE ]]; then
    fail "Tên nhánh '$name' sai luật. Đúng: <type>/<mo-ta-kebab-case>, VD feat/transfer-api, fix/42-deposit-counterparty-check. type: ${TYPES//|/, }, hotfix; hoặc release/1.2.0."
  elif (( ${#name} > MAX_BRANCH )); then
    fail "Tên nhánh '$name' dài ${#name} ký tự, tối đa $MAX_BRANCH."
  fi
}

check_header() {
  local header="$1" where="$2"
  [[ "$header" =~ $HEADER_EXEMPT_RE ]] && return 0
  if [[ ! "$header" =~ $HEADER_RE ]]; then
    fail "$where '$header' sai luật. Đúng: <type>(<scope>): <mô tả tiếng Anh, bắt đầu bằng chữ thường>, VD 'fix(account): reject deposit from foreign-currency counterparty'. type: ${TYPES//|/, }."
    return 0
  fi
  if (( ${#header} > MAX_HEADER )); then
    fail "$where '$header' dài ${#header} ký tự, tối đa $MAX_HEADER."
  fi
  if [[ "$header" == *. ]]; then
    fail "$where '$header' không được kết thúc bằng dấu chấm."
  fi
}

case "${1:-}" in
  branch)
    check_branch "${2:?thiếu tên nhánh}"
    ;;
  commit)
    check_header "${2:?thiếu commit message}" "Tiêu đề"
    ;;
  range)
    base="${2:?thiếu base sha}" head="${3:?thiếu head sha}"
    while IFS= read -r line; do
      sha="${line%% *}" header="${line#* }"
      check_header "$header" "Commit ${sha}"
    done < <(git log --no-merges --format='%h %s' "${base}..${head}")
    ;;
  *)
    echo "Cách dùng: $0 branch <tên> | commit <message> | range <base> <head>" >&2
    exit 2
    ;;
esac

if (( errors > 0 )); then
  echo "Có $errors vi phạm luật đặt tên. Xem CONTRIBUTING.md." >&2
  exit 1
fi
echo "✓ Đúng luật đặt tên."
