#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Always regenerate the index from the APKs just built: index.json carries each
# APK's sha256, which the app verifies on install, so a rebuild without a
# refresh publishes a hash that no longer matches.
./gradlew assembleRelease "$@"
python3 tools/update-repo.py

echo ""
echo "Done. Commit repo/apk/* and repo/index.json, then push:"
echo "  git add repo && git commit -m 'Publish extensions' && git push"
