#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Nothing gets published that does not parse.
./gradlew testDebugUnitTest "$@"

# Only the parsers whose versionCode has no APK yet: a rebuild is not
# byte-identical, so rebuilding an already-published version would change its
# sha256 under the app for no reason. Bump versionCode to republish.
targets=$(python3 tools/update-repo.py --targets)
if [ -n "$targets" ]; then
    # shellcheck disable=SC2086
    ./gradlew $targets "$@"
else
    echo "Every parser is already published at its current versionCode."
fi

# Always regenerate the index from the APKs in repo/apk: index.json carries each
# APK's sha256, which the app verifies on install, so a rebuild without a
# refresh publishes a hash that no longer matches.
python3 tools/update-repo.py

echo ""
echo "Done. Commit repo/apk/* and repo/index.json, then push:"
echo "  git add repo && git commit -m 'Publish parsers' && git push"
