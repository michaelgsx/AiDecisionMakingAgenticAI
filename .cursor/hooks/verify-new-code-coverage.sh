#!/usr/bin/env bash
# Cursor stop hook: require mirrored JUnit tests and 100% line coverage on changed classes.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

if [[ -t 0 ]]; then
  :
else
  cat >/dev/null || true
fi

MAIN_PREFIX="backend/src/main/java/"
TEST_PREFIX="backend/src/test/java/"
PKG_PREFIX="com.aidecision.agentic."

collect_changed() {
  {
    git diff --name-only 2>/dev/null || true
    git diff --name-only --cached 2>/dev/null || true
    git ls-files --others --exclude-standard 2>/dev/null || true
  } | sort -u
}

to_test_path() {
  local main_file="$1"
  local rel="${main_file#"$MAIN_PREFIX"}"
  local class_file
  class_file="$(basename "$rel" .java)"
  local pkg_dir
  pkg_dir="$(dirname "$rel")"
  echo "${TEST_PREFIX}${pkg_dir}/${class_file}Test.java"
}

to_fqcn() {
  local main_file="$1"
  local rel="${main_file#"$MAIN_PREFIX"}"
  rel="${rel%.java}"
  echo "${PKG_PREFIX}${rel//\//.}"
}

emit_followup() {
  python3 -c 'import json, os; print(json.dumps({"followup_message": os.environ["HOOK_MSG"]}))'
}

check_jacoco_line_coverage() {
  local csv="$1"
  local fqcn="$2"
  python3 - "$csv" "$fqcn" <<'PY'
import csv
import sys

csv_path, fqcn = sys.argv[1], sys.argv[2]
missed = covered = 0
found = False
with open(csv_path, newline="") as fh:
    for row in csv.DictReader(fh):
        if row.get("CLASS") == fqcn:
            missed += int(row.get("LINE_MISSED") or 0)
            covered += int(row.get("LINE_COVERED") or 0)
            found = True
if not found:
    print("missing")
    raise SystemExit(2)
total = missed + covered
if total == 0:
    print("ok")
    raise SystemExit(0)
if missed > 0:
    print(f"{covered}/{total}")
    raise SystemExit(1)
print("ok")
PY
}

failures=()
missing_tests=()
test_classes=()

while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  [[ "$file" != ${MAIN_PREFIX}*.java ]] && continue

  test_file="$(to_test_path "$file")"
  class_name="$(basename "$file" .java")"

  if [[ ! -f "$test_file" ]]; then
    missing_tests+=("$file -> $test_file")
    continue
  fi

  test_classes+=("${class_name}Test")
done < <(collect_changed | grep "^backend/src/main/java/.*\.java$" || true)

if ((${#missing_tests[@]} == 0 && ${#test_classes[@]} == 0)); then
  exit 0
fi

if ((${#test_classes[@]} > 0)); then
  if ! command -v mvn >/dev/null 2>&1; then
    failures+=("mvn not found; run tests manually under backend/")
  else
    (
      cd backend
      tests_arg="$(IFS=,; echo "${test_classes[*]}")"
      if ! mvn -q test -Dtest="$tests_arg" >/tmp/agentic-hook-test.log 2>&1; then
        failures+=("mvn test failed for: $tests_arg")
      else
        csv="target/site/jacoco/jacoco.csv"
        if [[ ! -f "$csv" ]]; then
          failures+=("JaCoCo report missing at backend/$csv")
        else
          while IFS= read -r file; do
            [[ -z "$file" ]] && continue
            fqcn="$(to_fqcn "$file")"
            result="$(check_jacoco_line_coverage "$csv" "$fqcn" 2>/dev/null || true)"
            case "$result" in
              ok) ;;
              missing) failures+=("$file: no JaCoCo entry for $fqcn") ;;
              *) failures+=("$file: line coverage $result (need 100%)") ;;
            esac
          done < <(collect_changed | grep "^backend/src/main/java/.*\.java$" || true)
        fi
      fi
    )
  fi
fi

if ((${#missing_tests[@]} == 0 && ${#failures[@]} == 0)); then
  exit 0
fi

msg="New/changed Java classes must have unit tests at 100% line coverage before you stop."
if ((${#missing_tests[@]} > 0)); then
  msg+=$'\n\nMissing test files:'
  for m in "${missing_tests[@]}"; do
    msg+=$'\n- '"$m"
  done
fi
if ((${#failures[@]} > 0)); then
  msg+=$'\n\nCoverage/test failures:'
  for f in "${failures[@]}"; do
    msg+=$'\n- '"$f"
  done
fi
msg+=$'\n\nAdd tests under backend/src/test/java/ (mirror package), then run:'
msg+=$'\n  cd backend && mvn test -Dtest=<Class>Test'

export HOOK_MSG="$msg"
emit_followup
exit 0
