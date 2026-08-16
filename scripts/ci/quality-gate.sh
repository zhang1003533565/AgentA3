#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

if [[ -n "${PYTHON_BIN:-}" ]]; then
  python_bin="$PYTHON_BIN"
elif [[ -x ai-servers/.venv/bin/python ]]; then
  python_bin="ai-servers/.venv/bin/python"
else
  python_bin="python3"
fi
if [[ "$python_bin" = /* ]]; then
  python_exec="$python_bin"
elif [[ "$python_bin" == */* ]]; then
  python_exec="$repo_root/$python_bin"
else
  python_exec="$python_bin"
fi

command -v mvn >/dev/null 2>&1 || {
  echo "[FAIL] Maven is required" >&2
  exit 1
}
command -v node >/dev/null 2>&1 || {
  echo "[FAIL] Node.js is required" >&2
  exit 1
}
command -v npm >/dev/null 2>&1 || {
  echo "[FAIL] npm is required" >&2
  exit 1
}
command -v docker >/dev/null 2>&1 || {
  echo "[FAIL] Docker with Compose is required" >&2
  exit 1
}
"$python_exec" -c "import pytest" >/dev/null 2>&1 || {
  echo "[FAIL] pytest is not installed for $python_bin" >&2
  exit 1
}

echo "[1/6] AppBackend Maven tests"
(cd AppBackend && mvn -q test)

echo "[2/6] ai-servers pytest"
(cd ai-servers && "$python_exec" -m pytest -q)

echo "[3/6] AppWeb Node tests, lint and production build"
web_tests=()
while IFS= read -r test_file; do
  web_tests+=("$test_file")
done < <(find AppWeb/src -type f -name '*.test.js' -print | LC_ALL=C sort)
if [[ "${#web_tests[@]}" -eq 0 ]]; then
  echo "[FAIL] no AppWeb tests found" >&2
  exit 1
fi
node --test "${web_tests[@]}"
(cd AppWeb && npm run lint && npm run build)

echo "[4/6] mini_program_app Node tests"
app_tests=()
while IFS= read -r test_file; do
  app_tests+=("$test_file")
done < <(find mini_program_app -type f -name '*.test.js' -print | LC_ALL=C sort)
if [[ "${#app_tests[@]}" -eq 0 ]]; then
  echo "[FAIL] no mini_program_app tests found" >&2
  exit 1
fi
node --test "${app_tests[@]}"

echo "[5/6] Submission knowledge, factual-evaluation and load-test contracts"
"$python_exec" scripts/deploy/test_release_contracts.py
"$python_exec" scripts/knowledge/test_validate_python_course.py
"$python_exec" scripts/eval/test_run_factual_eval.py
"$python_exec" scripts/eval/test_run_load.py
"$python_exec" scripts/knowledge/validate_python_course.py
"$python_exec" scripts/eval/run_factual_eval.py --validate-only
"$python_exec" scripts/eval/run_load.py --validate-only

echo "[6/6] Docker Compose submission manifest"
docker compose --env-file deploy/.env.example -f deploy/compose.submission.yml config --quiet

echo "Quality gate passed."
