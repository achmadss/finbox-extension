# finbox-extension

Parsers for [finbox](https://github.com/achmadss/finbox-android), the app that
turns the receipts your bank emails you into a spending ledger.

One provider, one APK. An extension holds nothing but the knowledge of how a
particular bank writes its notification mails: which address they come from,
which of them are really transactions, and where the amount, date and merchant
sit inside them. The app supplies the mailbox; a parser supplies the meaning.

They are ordinary Android APKs with no launcher activity, published to
`repo/` and installed by the app, which verifies each against its hash and
loads it in-process through a child-first classloader. Adding a bank needs no
app release.

## Available

| Provider | Emails it reads |
|---|---|
| Bank BNI | wondr receipts — QRIS, transfers, TapCash top ups |
| Bank BRI | BRImo receipts — QRIS, transfers, BRIZZI top ups |
| Bank Jago | payments, transfers, Jago Partner, debit card purchases |
| Bank Mandiri | Livin' receipts — QR payments, e-money top ups, SBN orders |

## Layout

| Path | Purpose |
|---|---|
| `extensions/<provider>/` | One module per provider |
| `lib/receipt/` | Shared receipt reading — labels, money, dates — compiled into each APK |
| `build-logic/` | The Gradle plugin every extension applies |
| `compiler/` | KSP processor that generates each APK's entry point |
| `repo/` | Published APKs and the `index.json` the app fetches |
| `tools/` | Publishing and API scripts |

Writing one? See [CONTRIBUTING.md](CONTRIBUTING.md).
