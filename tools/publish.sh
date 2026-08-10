#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew :extensions:bri:assembleRelease "$@"
python3 tools/update-repo.py

echo ""
echo "Done. Commit repo/apk/* and repo/index.json, then push:"
echo "  git add repo && git commit -m 'Publish extensions' && git push"
