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
| `compiler/` | KSP processor turning the module's `@Parser` class into the generated entry point |
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

2. Implement `dev.achmad.finbox.extension.TransactionParser` in package
   `dev.achmad.finbox.extension.<provider>` and annotate it with `@Parser`:

   ```kotlin
   @Parser
   class BriParser : TransactionParser {
       override fun isEmailForProvider(email: EmailMessage): Boolean { /* ... */ }
       override suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction> { /* ... */ }
   }
   ```

   Behaviour only — the parser carries no name, version or id. The `:compiler`
   KSP processor generates `GeneratedParser`, a delegate with a fixed name that
   the manifest's `finbox.extension.class` points at; the app takes the identity
   from the manifest instead (id is `MD5("name.lowercase()/versionCode")`,
   stable across releases so stored transactions keep matching).

   Exactly one `@Parser` class per module — one provider per APK. Getting this
   wrong (missing annotation, two of them, abstract class, constructor
   arguments) is a build error rather than a load failure on someone's phone.
3. Register the module in `settings.gradle.kts`.

## The parser API

`TransactionParser`, `EmailMessage` and `ParsedTransaction` come from
finbox-android's `:extension-api`, published via JitPack and pinned in
`gradle.properties`:

```properties
finbox.apiVersion=1.0
```

The plugin adds it as `compileOnly` — the app supplies the real classes at
runtime through its child-first classloader — and stamps the same value into
each APK's `finbox.extension.lib` metadata, which the app checks on load.
Set the property to a release tag or a finbox-android commit hash; JitPack
resolves both.

Iterating on the API itself? `tools/update-api.sh` republishes it from a
sibling finbox-android checkout into `~/.m2` and rebuilds here — the
`mavenLocal()` entry in `settings.gradle.kts` picks it up ahead of JitPack:

```
./tools/update-api.sh            # or FINBOX_ANDROID=/path/to/finbox-android ./tools/update-api.sh
```

The version stays `1.0` across API changes, so Gradle would otherwise keep
serving the cached jar; the script passes `--refresh-dependencies` for you.

The Kotlin version pinned in the root `build.gradle.kts` must match the one
finbox-android builds the API with, or its metadata is unreadable here.

## Tests

```
./gradlew :extensions:<provider>:testDebugUnitTest
```

Plain JUnit on the JVM. Everything the plugin declares `compileOnly` is a real
`testImplementation` dependency, since tests run without the app to supply it.
KSP is disabled for test compilations — the `@Parser` class lives in `main`.

## Versioning

`finbox { versionCode }` becomes the APK's real `versionCode`, and its
`versionName` is `<apiVersion>.<versionCode>` — 1.0.1, 1.0.2, ... The app reads
both from the package itself rather than from custom metadata, and treats a
missing `versionName` as a load error instead of substituting a default.

`finbox.extension.lib` metadata states the API version explicitly; if it is ever
absent the app falls back to everything before the last dot of the versionName,
which is the same value by construction. `tools/update-repo.py` derives
`lib_version` in `index.json` the same way.

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
