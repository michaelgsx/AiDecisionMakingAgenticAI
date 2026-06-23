#!/usr/bin/env bash
# Cursor stop hook: require mirrored JUnit tests and 100% line coverage on changed classes.
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

read -r _ < /dev/null || true

MAIN_PREFIX="backend/src/main/java/"
TEST_PREFIX="backend/src/test/java/"

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
  local class_file="$(basename "$rel" .java)"
  local pkg_dir="$(dirname "$rel")"
  echo "${TEST_PREFIX}${pkg_dir}/${class_file}Test.java"
}

to_class_name() {
  local main_file="$1"
  basename "$main_file" .java
}

failures=()
missing_tests=()
test_classes=()

while IFS= read -r file; do
  [[ -z "$file" ]] && continue
  [[ "$file" != ${MAIN_PREFIX}*.java ]] && continue

  test_file="$(to_test_path "$file")"
  class_name="$(to_class_name "$file")"

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
    if ! mvn -q test -Dtest="$tests_arg" jacoco:report 2>&1; then
      failures+=("mvn test failed for: $tests_arg")
    else
      while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        class_name="$(to_class_name "$file")"
        fqcn="com.aidecision.agentic.$(echo "${file#"$MAIN_PREFIX"}" | sed 's|/|.|g' | sed 's/.java$//')"
        csv_line="$(grep ",$fqcn," target/site/jacoco/jacoco.csv 2>/dev/null | head -1 || true)"
        if [[ -z "$csv_line" ]]; then
          failures+=("$file: no JaCoCo entry for $fqcn (run mvn test jacoco:report)")
          continue
        fi
        missed="$(echo "$csv_line" | cut -d, -f8)"
        covered="$(echo "$csv_line" | cut -d, -f9)"
        total=$((missed + covered))
        if (( total > 0 && missed > 0 )); then
          failures+=("$file: line coverage $covered/$total (need 100%)")
        fi
      done < <(collect_changed | grep "^backend/src/main/java/.*\.java$" || true)
    fi
  )
  fi
fi

if ((${#missing_tests[@]} == 0 && ${#failures[@]} == 0)); then
  exit 0
fi

msg="New/changed Java classes must have unit tests at 100% line coverage before you stop."
if ((${#missing_tests[@]} > 0)); then
  msg+="\n\nMissing test files:"
  for m in "${missing_tests[@]}"; do
    msg+="\n- $m"
  done
fi
if ((${#failures[@]} > 0)); then
  msg+="\n\nCoverage/test failures:"
  for f in "${failures[@]}"; do
    msg+="\n- $f"
  done
fi
msg+="\n\nAdd tests under backend/src/test/java/ (mirror package), then run: cd backend && mvn test -Dtest=<Class>Test jacoco:report"

python3 - <<PY
import json
print(json.dumps({"followup_message": """$msg"""}))
PY

exit 0
