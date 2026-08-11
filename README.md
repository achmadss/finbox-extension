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
| `compiler/` | KSP processor turning the module's `@Source` class into the generated entry point |
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
   }
   ```

2. Implement `dev.achmad.finbox.extension.TransactionSource` in package
   `dev.achmad.finbox.extension.<provider>` and annotate it with `@Source`.
   `id` must be deterministic (`MD5("name.lowercase()/versionId")`, see
   `BriParser.sourceId`).

   ```kotlin
   @Source
   class BriParser : TransactionSource { /* ... */ }
   ```

   Exactly one `@Source` class per module; the `:compiler` KSP processor
   generates `GeneratedSourceFactory` from it, and that fixed name is what the
   manifest's `finbox.extension.class` points at. To ship several parsers in one
   APK, put `@Source` on a `SourceFactory` instead. Getting any of this wrong
   (missing annotation, two of them, abstract class, constructor arguments) is a
   build error rather than a load failure on someone's phone.
3. Register the module in `settings.gradle.kts`.

## The parser API

`TransactionSource`, `SourceFactory`, `EmailMessage` and `ParsedTransaction`
come from finbox-android's `:extension-api`, published via JitPack and pinned in
`gradle.properties`:

```properties
finbox.apiVersion=1.0
```

The plugin adds it as `compileOnly` — the app supplies the real classes at
runtime through its child-first classloader — and stamps the same value into
each APK's `finbox.extension.lib` metadata, which the app checks on load.
Set the property to a release tag or a finbox-android commit hash; JitPack
resolves both.

Iterating on the API itself? Publish it locally and the `mavenLocal()` entry in
`settings.gradle.kts` will pick it up ahead of JitPack:

```
cd ../finbox-android && ./gradlew :extension-api:publishToMavenLocal
```

The Kotlin version pinned in the root `build.gradle.kts` must match the one
finbox-android builds the API with, or its metadata is unreadable here.

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
