#!/usr/bin/env python3
"""Regenerate repo/index.json from the APKs in repo/apk/.

Each APK is named finbox-<provider>-v<version>.apk (written by the
gradle plugin's copyReleaseApk task).
"""
import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
APK_DIR = ROOT / "repo" / "apk"
INDEX = ROOT / "repo" / "index.json"
BASE_URL = "https://raw.githubusercontent.com/achmadss/finbox-extension/main/repo"

NAMES = {
    "bri": "Bank BRI",
    "jago": "Bank Jago",
    "bca": "Bank BCA",
    "gopay": "GoPay",
}


def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    apks = sorted(APK_DIR.glob("*.apk"))
    if not apks:
        print("No APKs found in repo/apk/. Build first: ./gradlew assembleRelease", file=sys.stderr)
        return 1

    entries = []
    for apk in apks:
        provider = apk.stem.split("-")[1]
        version_name = apk.stem.split("-")[2]
        if version_name.startswith("v"):
            version_name = version_name[1:]
        version_code = int(version_name.split(".")[-1])
        entries.append({
            "name": NAMES.get(provider, provider.title()),
            "provider": provider,
            "pkg": f"dev.achmad.finbox.extension.{provider}",
            "version_code": version_code,
            "version_name": version_name,
            "lib_version": "1.0",
            "apk": f"{BASE_URL}/apk/{apk.name}",
            "sha256": sha256(apk),
        })

    INDEX.parent.mkdir(parents=True, exist_ok=True)
    INDEX.write_text(json.dumps({"extensions": entries}, indent=2) + "\n")
    print(f"Wrote {len(entries)} entries to {INDEX}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
