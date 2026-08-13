#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Republishes finbox-android's :extension-api into ~/.m2 so this repo picks it
# up ahead of GitHub Packages (see mavenLocal() in settings.gradle.kts). Only
# needed while iterating on the API locally; otherwise publish it there and
# point finbox.apiVersion at the released version. Run it after every API change
# over there — the version does not move while iterating, so nothing else
# signals that the artifact did.
API_REPO="${FINBOX_ANDROID:-$(cd .. && pwd)/finbox-android}"
API_VERSION=$(sed -n 's/^finbox\.apiVersion=//p' gradle.properties)

if [ ! -x "$API_REPO/gradlew" ]; then
    echo "finbox-android not found at $API_REPO; set FINBOX_ANDROID to its path." >&2
    exit 1
fi

(cd "$API_REPO" && ./gradlew :extension-api:publishToMavenLocal "$@")

# Same coordinates every time, so Gradle happily serves the previous jar from
# its own cache; --refresh-dependencies is what makes the new one take.
./gradlew assembleDebug --refresh-dependencies

echo ""
echo "extension-api $API_VERSION published from $API_REPO and rebuilt here."
