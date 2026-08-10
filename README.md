# finbox-extension

Parser extensions for [finbox-android](https://github.com/achmadss/finbox-android),
the email transaction importer. Each extension is a small Android APK that
recognizes one financial provider's emails and converts them into a
standardized transaction format. No launcher activity; loaded in-process by the
app via a child-first classloader.

## Layout

| Path | Purpose |
|---|---|
| `build-logic/` | `finbox.plugins.extension` Gradle plugin (manifest generation, versioning, APK copy) |
| `core/` | Parser API stubs (`compileOnly`): `TransactionSource`, `SourceFactory`, `EmailMessage`, `ParsedTransaction` — **keep in sync** with `:extension-api` in finbox-android |
| `extensions/<provider>/` | One module per provider (bri, jago, bca, gopay, ...) |
| `repo/` | Published artifacts: APKs + `index.json` served to the app |
| `tools/` | `update-repo.py` (index generation), `publish.sh` (build + index) |

## Adding a parser

1. `mkdir extensions/<provider>` with `build.gradle.kts`:

   ```kotlin
   plugins { id("finbox.plugins.extension") }

   finbox {
       name = "Bank BRI"
       provider = "bri"
       versionCode = 1
       className = "dev.achmad.finbox.extension.bri.BriParser"
   }
   ```

2. Implement `dev.achmad.finbox.extension.TransactionSource` in package
   `dev.achmad.finbox.extension.<provider>`. `id` must be deterministic
   (`MD5("name.lowercase()/versionId")`, see `BriParser.sourceId`).
3. Register the module in `settings.gradle.kts`.

## Publishing

```bash
./tools/publish.sh        # builds, copies APKs to repo/apk/, regenerates index.json
git add repo && git commit -m "Publish extensions" && git push
```

The app fetches `repo/index.json` from the `main` branch and verifies each APK
against its `sha256` before installing. Bump `versionCode` in the module's
`finbox {}` block for updates.

## libVersion

`finbox.extension.lib` in the manifest must be `1.0` (checked by the app's
loader, which rejects unknown versions with a clear error). Bump it only when
the parser API in `core/` changes incompatibly — the app must ship the matching
version too.
