#!/usr/bin/env bash
# Reports a crash as an annotation and pushes smoke/ to the smoke-results-<api> branch, so the
# screenshots can be looked at without downloading artifacts.
set -u
OUT=smoke
[ -d "$OUT" ] || { echo "no results"; exit 0; }
if [ -s "$OUT/crash.txt" ]; then
    python3 - "$OUT/crash.txt" "${API:-?}" <<'EOF'
import sys
text = open(sys.argv[1], errors="replace").read()[:30000]
esc = text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
print(f"::error title=Crash on API {sys.argv[2]}::{esc}")
EOF
fi
cd "$OUT"
git init -q
git config user.name "smoke-test"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add -A
git commit -qm "Smoke test of ${GITHUB_SHA:0:7} on API ${API:-?}"
git push -qf "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "HEAD:refs/heads/smoke-results-${API:-x}"
