#!/usr/bin/env python3
"""Regenerate repo/index.json from the APKs in repo/apk/.

Each APK is named finbox-<provider>-v<version>.apk (written by the
gradle plugin's copyReleaseApk task).

Icons are published alongside: the app reads an installed parser's icon out
of its APK, but the "available" list has no APK yet, so the largest mipmap of
each module is copied to repo/icon/<provider>.png and linked from the index.

`--targets` prints the assembleRelease tasks worth running, which is how
publish.sh avoids rebuilding a parser that was not changed.
"""
import hashlib
import json
import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
APK_DIR = ROOT / "repo" / "apk"
ICON_DIR = ROOT / "repo" / "icon"
INDEX = ROOT / "repo" / "index.json"
BASE_URL = "https://raw.githubusercontent.com/achmadss/finbox-parser/main/repo"

def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def publish_icon(provider: str) -> str | None:
    """Copy parsers/<provider>'s launcher icon into repo/icon/, return its URL."""
    src = ROOT / "parsers" / provider / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"
    if not src.exists():
        print(f"warning: {provider} has no {src.relative_to(ROOT)}", file=sys.stderr)
        return None
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, ICON_DIR / f"{provider}.png")
    return f"{BASE_URL}/icon/{provider}.png"


def declared() -> dict[str, tuple[str, str, str, int]]:
    """provider -> (module, name, versionName, versionCode), read from the build files.

    gradle.properties and the `finbox {}` blocks between them name the APK every
    parser should have in repo/apk, since the gradle plugin builds it as
    `finbox-<provider>-<apiVersion>.<versionCode>.apk`. That name is the whole
    of the up-to-date check: raising a versionCode makes one file missing, and
    raising finbox.apiVersion makes all of them missing at once, which is what
    an API bump should mean — every parser is compiled against it.
    """
    properties = (ROOT / "gradle.properties").read_text()
    api_version = re.search(r"^finbox\.apiVersion=(.+)$", properties, re.M)
    if not api_version:
        sys.exit("finbox.apiVersion is not set in gradle.properties")

    modules = {}
    for module in sorted((ROOT / "parsers").iterdir()):
        build_file = module / "build.gradle.kts"
        if not build_file.exists():
            continue
        build = build_file.read_text()
        provider = re.search(r'provider\s*=\s*"([^"]+)"', build)
        version_code = re.search(r"versionCode\s*=\s*(\d+)", build)
        # The display name comes from the same block the APK is named after, so
        # a new parser is listed correctly without being registered here too.
        name = re.search(r'^\s*name\s*=\s*"([^"]+)"', build, re.M)
        if not provider or not version_code:
            print(f"warning: {module.name} declares no provider/versionCode", file=sys.stderr)
            continue
        version_name = f"{api_version[1].strip()}.{version_code[1]}"
        modules[provider[1]] = (
            module.name,
            name[1] if name else provider[1].title(),
            version_name,
            int(version_code[1]),
        )
    return modules


def apk_of(provider: str, version_name: str) -> pathlib.Path:
    return APK_DIR / f"finbox-{provider}-{version_name}.apk"


def targets() -> int:
    """Print the assembleRelease task of every parser whose APK is missing.

    A published version is final: the index carries its sha256, and two builds
    of the same source are not byte-identical, so rebuilding one already in
    repo/apk would hand the app a new hash for a version it has.
    """
    for provider, (module, _, version_name, _) in sorted(declared().items()):
        if not apk_of(provider, version_name).exists():
            print(f":parsers:{module}:assembleRelease")
    return 0


def main() -> int:
    modules = declared()
    if not modules:
        print("No parsers found in parsers/.", file=sys.stderr)
        return 1

    current = {apk_of(provider, version) for provider, (_, _, version, _) in modules.items()}
    missing = sorted(apk.name for apk in current if not apk.exists())
    if missing:
        print(f"No APK for {', '.join(missing)}. Build first: ./tools/publish.sh", file=sys.stderr)
        return 1

    # The modules say what is current, so anything else in repo/apk is a version
    # that has been superseded. Leaving one behind would publish the same
    # parser twice, and the app keys its lists by pkg.
    for apk in sorted(APK_DIR.glob("*.apk")):
        if apk not in current:
            print(f"Dropping superseded {apk.name}")
            apk.unlink()

    entries = []
    for provider, (_, name, version_name, version_code) in sorted(modules.items()):
        apk = apk_of(provider, version_name)
        entry = {
            "name": name,
            "provider": provider,
            "pkg": f"dev.achmad.finbox.parser.{provider}",
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
    INDEX.write_text(json.dumps({"parsers": entries}, indent=2) + "\n")
    print(f"Wrote {len(entries)} entries to {INDEX}")
    return 0


if __name__ == "__main__":
    sys.exit(targets() if "--targets" in sys.argv else main())
