# Contributing

Adding a bank means writing one class and publishing one APK. The app needs no
change and no release, and this repo is all you need checked out.

## Setup

The parser API is published to GitHub Packages, which authenticates reads even
for a public package. Once, before your first build:

1. Create a personal access token (classic) with the **`read:packages`** scope.
2. Put it in `~/.gradle/gradle.properties` — never in this repo:

   ```properties
   gpr.user=<your github username>
   gpr.key=<the token>
   ```

`GITHUB_ACTOR` / `GITHUB_TOKEN` in the environment work too, which is what CI
uses.

## Add a source

**1. Create the module.** `extensions/<provider>/build.gradle.kts`:

```kotlin
plugins { id("finbox.plugins.extension") }

finbox {
    name = "Bank Jago"
    provider = "jago"
    versionCode = 1
}

dependencies {
    implementation(project(":lib:receipt"))
}
```

Register it in `settings.gradle.kts`.

**2. Implement `TransactionSource`** in `dev.achmad.finbox.extension.<provider>`,
annotated with `@Source`:

```kotlin
@Source
class JagoSource : TransactionSource {

    override val emailQuery = EmailQuery.from("noreply@jago.com")

    override fun isEmailForProvider(email: EmailMessage): Boolean { /* ... */ }

    override suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction> { /* ... */ }
}
```

Exactly one `@Source` class per module. Getting that wrong — missing
annotation, two of them, an abstract class, constructor arguments — fails the
build rather than the phone.

The class carries no name, version or id. `:compiler` generates a delegate
called `GeneratedSource` that the manifest points at, and the app takes the
identity from the manifest instead. A source's id is derived from its name and
`versionCode`, so both are part of your published contract.

**3. Add a launcher icon** at `src/main/res/mipmap-<density>/ic_launcher.png`.
The app shows it in the extension list, and `tools/update-repo.py` copies the
192px one into `repo/icon/`.

### The three members

`emailQuery` is what the app asks Gmail for. Narrow it to the sender the bank
notifies from, and never put dates in it — the app adds its own window. It only
decides what gets *downloaded*: fetching one message costs twenty quota units
against five for listing five hundred ids, so a whole mailbox is expensive.
`EmailQuery.raw()` takes anything Gmail's search box accepts. `EmailQuery.everything()`
exists but disables narrowing for every other installed source too, so reach
for it only if the bank really sends from unpredictable addresses.

`isEmailForProvider` decides what is genuinely a transaction. A bank sends
statements, OTPs and promotions from the same address as its receipts, and they
must not reach the parser. Match on something a receipt always has and an
advert never does — a reference number, a summary table.

`parseEmail` returns what it reliably found. Return an empty list when the
amount cannot be read: the app drops the email rather than storing a guess.
Leave fields null rather than inventing them.

### What belongs here, and what doesn't

An extension holds the knowledge of one bank's emails. It never fetches, never
schedules, never touches a token or an HTTP client — the app owns all of that,
and hands over an `EmailMessage`.

Conversely the app holds no opinion about parsing. It passes the message parts
exactly as they arrived, html included; turning markup into readable lines is
your call, and `:lib:receipt` does it.

## Use the receipt library

`lib/receipt` is shared by every extension and compiled into each APK. It
covers what banks have in common:

```kotlin
val receipt = Receipt.of(email)     // flattens html to one line per row

receipt.field("Nomor Referensi", "No. Ref")   // label → value, same line or next
receipt.amount("Nominal")                     // "Rp 1.151.800" → 1151800
receipt.date("Tanggal Transaksi")             // honours a stated WIB/WITA/WIT
receipt.statedAmount()                        // the first "Rp …" in prose
detectType(kind, email.subject)               // INCOME / TRANSFER / EXPENSE
```

Two layouts are already handled: label and value on one line (BRI) and on two,
with or without a colon (Jago). If a new bank breaks something here, fix it
here — that is why it is a library and not copied into each parser.

Anything genuinely shared belongs in `lib/`, never in `extension-api`. The API
is provided by the app, so moving it orphans published extensions; a library
ships inside your APK and costs a release of that extension alone.

## Test

```bash
./gradlew :extensions:<provider>:testDebugUnitTest
```

Note `testDebugUnitTest`, not `test` — in an Android module `test` runs nothing
and still reports success.

Test against real emails. Save one as `src/test/resources/<provider>/<case>.txt`,
flattened the way the app hands it over — one line per table row — with names
and account numbers redacted, and assert the parsed amount, date, type and
merchant. `BriSourceTest` and `JagoSourceTest` are worth copying. Cover each
distinct layout the bank sends, and one email that must *not* be claimed.

Plain JUnit on the JVM, no Android. Everything the plugin declares `compileOnly`
is a real `testImplementation` dependency, since tests run without an app to
supply it.

## Publish

```bash
./tools/publish.sh
git add repo && git commit -m "Publish extensions" && git push
```

That builds every release APK, copies them to `repo/apk/` and regenerates
`repo/index.json`, which carries each APK's sha256. Always publish through the
script: a rebuilt APK is not byte-identical, so a hand-edited index publishes a
hash the app will reject on install.

Bump `versionCode` in `finbox {}` for every published change — including one
that only touches `lib/receipt`, since that is compiled into your APK.

## Versions

`finbox.apiVersion` in `gradle.properties` is the parser API to build against.
It becomes the APK's `finbox.extension.lib` metadata and the leading part of
its `versionName` (`1.4.7` = API 1.4, versionCode 7).

The app loads anything from its `MIN_LIB_VERSION` up to the version it ships.
Additions to the API raise only the ceiling, so published extensions keep
working; a change that breaks them raises the floor and every APK below it must
be rebuilt. If your extension stops loading after an app update, that is what
happened — rebuild against the new `finbox.apiVersion` and republish.

Changing the API itself lives in finbox-android, and is the one job that needs
both repos. Two ways round:

**Published.** Bump `apiVersion` there, run its **Publish extension-api**
workflow (`workflow_dispatch`), then point `finbox.apiVersion` here at the new
version. Nothing local required. GitHub Packages will not overwrite a version
that already exists, so the bump is not optional.

**Local, while iterating.** With a finbox-android checkout beside this one:

```bash
./tools/update-api.sh    # or FINBOX_ANDROID=/path/to/finbox-android ./tools/update-api.sh
```

That republishes `:extension-api` into `~/.m2`, which `mavenLocal()` picks up
ahead of GitHub Packages, and rebuilds here with `--refresh-dependencies` —
needed because the coordinates do not change when you iterate. Publish it
properly before anyone else needs to build.

The Kotlin version pinned in the root `build.gradle.kts` must match the one
finbox-android builds the API with, or the published metadata is unreadable
here.
