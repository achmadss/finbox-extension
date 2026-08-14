# Contributing

Adding a bank means writing one class and publishing one APK. The app needs no
change and no release, and this repo is all you need checked out.

Clone it and build — there is nothing to set up, no account and no token. The
parser API comes from JitPack, which builds it from finbox-android on demand.
The first build of a version JitPack has not seen takes a minute or two while
it does that.

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
receipt.splitDate()                           // a day and a clock on two rows (BNI, Mandiri)
receipt.statedAmount()                        // the first "Rp …" in prose
detectType(kind, email.subject)               // INCOME / TRANSFER / EXPENSE
```

Two layouts are already handled: label and value on one line (BRI, BNI,
Mandiri) and on two, with or without a colon (Jago). If a new bank breaks something here, fix it
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

That runs the tests, builds the release APKs that are missing, and regenerates
`repo/index.json`, which carries each APK's sha256. Always publish through the
script: a rebuilt APK is not byte-identical, so a hand-edited index publishes a
hash the app will reject on install.

Missing is decided by name. The plugin writes
`repo/apk/finbox-<provider>-<apiVersion>.<versionCode>.apk`, so what gets built
follows from the two things that name it:

- **Bump `versionCode`** in `finbox {}` and that extension alone is rebuilt. Do
  it for every published change, including one that only touches `lib/receipt`,
  since that is compiled into your APK.
- **Bump `finbox.apiVersion`** and every extension is rebuilt, which is what an
  API change means — they are all compiled against it.
- **Change neither** and nothing is built. That is the point: an untouched
  version keeps the sha256 the app already knows.

Anything left in `repo/apk/` that no module currently declares is deleted, so a
superseded APK cannot publish the same extension twice.

Or let CI do it — Actions → **Publish** → Run workflow, or `gh workflow run
publish.yml`. It runs the same script and commits `repo/`. Deliberately manual:
publishing is a decision, and a merge to `main` is not one.

The workflow restores the debug keystore from the `DEBUG_KEYSTORE` secret
(`base64 -i ~/.android/debug.keystore | gh secret set DEBUG_KEYSTORE`). Release
APKs are signed with the debug config, and Android refuses an update signed by a
different key than the installed APK, so a runner's own generated key would make
every published extension uninstallable over the old one.

## Versions

`finbox.apiVersion` in `gradle.properties` is the parser API to build against.
It becomes the APK's `finbox.extension.lib` metadata and the leading part of
its `versionName` (`1.4.7` = API 1.4, versionCode 7).

The app loads anything from its `MIN_LIB_VERSION` up to the version it ships.
Additions to the API raise only the ceiling, so published extensions keep
working; a change that breaks them raises the floor and every APK below it must
be rebuilt. If your extension stops loading after an app update, that is what
happened — rebuild against the new `finbox.apiVersion` and republish.

Changing the API itself lives in finbox-android. Push it there, then set
`finbox.apiVersion` here to a tag or a commit hash — JitPack resolves both and
builds that revision the first time anyone asks for it. A commit hash is the
honest choice while an API is still moving; tag it once it settles.

JitPack is the only source: no `mavenLocal()`, on purpose. A jar published on
one machine would build there and nowhere else, and the first person to notice
would be a contributor whose clone does not compile.

The Kotlin version pinned in the root `build.gradle.kts` must match the one
finbox-android builds the API with, or the published metadata is unreadable
here.
