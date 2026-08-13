#!/usr/bin/env python3
"""Regenerate repo/index.json from the APKs in repo/apk/.

Each APK is named finbox-<provider>-v<version>.apk (written by the
gradle plugin's copyReleaseApk task).

Icons are published alongside: the app reads an installed extension's icon out
of its APK, but the "available" list has no APK yet, so the largest mipmap of
each module is copied to repo/icon/<provider>.png and linked from the index.
"""
import hashlib
import json
import pathlib
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
APK_DIR = ROOT / "repo" / "apk"
ICON_DIR = ROOT / "repo" / "icon"
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


def publish_icon(provider: str) -> str | None:
    """Copy extensions/<provider>'s launcher icon into repo/icon/, return its URL."""
    src = ROOT / "extensions" / provider / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"
    if not src.exists():
        print(f"warning: {provider} has no {src.relative_to(ROOT)}", file=sys.stderr)
        return None
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, ICON_DIR / f"{provider}.png")
    return f"{BASE_URL}/icon/{provider}.png"


def main() -> int:
    apks = sorted(APK_DIR.glob("*.apk"))
    if not apks:
        print("No APKs found in repo/apk/. Build first: ./gradlew assembleRelease", file=sys.stderr)
        return 1

    # One entry per provider: the app keys its lists by pkg, so a superseded APK
    # left behind by an earlier build would publish the same extension twice.
    newest: dict[str, tuple[int, pathlib.Path]] = {}
    for apk in apks:
        provider = apk.stem.split("-")[1]
        version_code = int(apk.stem.split("-")[2].lstrip("v").split(".")[-1])
        if version_code > newest.get(provider, (0, None))[0]:
            newest[provider] = (version_code, apk)
    for apk in apks:
        if apk not in (a for _, a in newest.values()):
            print(f"Dropping superseded {apk.name}")
            apk.unlink()

    entries = []
    for provider, (version_code, apk) in sorted(newest.items()):
        version_name = apk.stem.split("-")[2].lstrip("v")
        entry = {
            "name": NAMES.get(provider, provider.title()),
            "provider": provider,
            "pkg": f"dev.achmad.finbox.extension.{provider}",
            "version_code": version_code,
            "version_name": version_name,
            "lib_version": version_name.rsplit(".", 1)[0],
            "apk": f"{BASE_URL}/apk/{apk.name}",
            "sha256": sha256(apk),
        }
        icon = publish_icon(provider)
        if icon:
            entry["icon"] = icon
        entries.append(entry)

    INDEX.parent.mkdir(parents=True, exist_ok=True)
    INDEX.write_text(json.dumps({"extensions": entries}, indent=2) + "\n")
    print(f"Wrote {len(entries)} entries to {INDEX}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
