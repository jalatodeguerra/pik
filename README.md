# Pik

Pik is an offline Android app for tracking your spending and paying off debt.
Everything stays on your phone. There is no account, no server and no internet permission.

Milestone 2 (this version) completes "track spending": first-run setup, accounts (cash and bank),
adding / editing expenses, income and transfers, an activity list by month with search and
filters, a home screen with balances, a Settings screen (accounts, categories, backup, about),
balance checks and backup files. Plan and Debts are placeholders for the next updates.

## Features

- **Settings** (gear icon on Home): manage accounts and categories, save / restore a backup file,
  app version.
- **Accounts**: add, rename, change type (Cash / Bank), edit the starting balance, archive /
  unarchive. An account with transactions can never be deleted (archive it); an unused one can.
  Archived accounts are hidden from Home and the pickers, their transactions still show in Activity.
  There is always at least one active account.
- **Categories** (Expense and Income tabs): add with a name, icon and color, edit, archive /
  unarchive, delete only when unused.
- **Check balance** (Home: tap an account; Settings: the tune button on an account row): type what
  your bank app shows. If it differs from Pik's balance, a signed `ADJUSTMENT` transaction dated
  today (note "Balance check") is added. Adjustments show as "Balance adjustment" in Activity,
  can be deleted there (with confirmation) but are not editable in the Add screen.
- **Activity search and filters**: search the selected month by note, category or account name
  (case-insensitive), filter by type (All / Expenses / Income / Transfers) and by account.
- **App shortcut**: long-press the Pik icon and choose "Add expense" to open the Add screen
  directly (only after first-run setup).

## Stack

- Kotlin 2.1.21, Jetpack Compose (Material 3, BOM 2025.05.01), dark theme only
- Room 2.7.1 (schema exported to `app/schemas`), DataStore Preferences
- Hilt for dependency injection, Navigation Compose
- Fonts: Outfit (headings, numbers) and Inter (body), both under the SIL OFL (see `licenses/`)
- minSdk 26, target/compile SDK 35, Gradle 8.14.3, AGP 8.10.1

## Build

```
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.
Instrumented tests (`app/src/androidTest`, Room migration + balance query) run with
`./gradlew connectedDebugAndroidTest` on a device or emulator.

## Install the APK from GitHub Actions

1. Open the repository on GitHub, go to **Actions** and open the latest successful run.
2. Download the `pik-debug-apk` artifact and unzip it. You get `app-debug.apk`.
3. Copy the APK to the phone and open it. Android will ask you to allow installing from this
   source ("install unknown apps"). Allow it for the app you used to open the file.

## Project structure

```
app/src/main/java/com/pik/app/
  data/db       Room entities, DAOs, converters, PikDatabase
  data/repo     repositories (the only thing ViewModels talk to)
  data/prefs    DataStore preferences (onboardingComplete, lastUsedAccountId, lastBackupEpochDay)
  domain        pure Kotlin, no Android: Money, AmountInput (keypad), BalanceCalculator, BalanceCheck,
                ActivityFilter, DateFormats
  backup        backup file model and JSON (de)serialization with validation (org.json only)
  di            Hilt modules
  ui/theme      colors, fonts, category colors and icons
  ui/components shared composables
  ui/nav        routes, floating bottom bar, nav host
  ui/screens    firstrun, add, activity, home, placeholder (Plan, Debts)
```

Layers: Compose screens -> ViewModels (one `StateFlow` UI state per screen) -> repositories -> Room.

## Money and date rules

- Money is always a `Long` number of **euro cents**. Never `Double` or `Float`.
- Only EUR. Amounts are shown Italian style with the symbol after: `1.234,56 €`
  (thousands `.`, decimals `,`, non-breaking space, `€`). Formatting is done by hand in
  `domain/Money.kt` and never depends on the phone's locale.
- A transaction date is a **calendar day** (`java.time.LocalDate`), stored as epoch day.
  It is not a timestamp. Only `createdAt` / `updatedAt` are epoch milliseconds.
- Account balance is calculated, never stored:
  starting balance + income - expense - transfers out + transfers in + adjustments.
- Debt payments will count as spending (later milestone); `debtId` is already reserved.

## Backup

### Backup file (Settings -> Backup)

"Save backup file" writes one JSON file, named `pik-backup-YYYY-MM-DD.json`, to a place you pick
with the Android file picker (Storage Access Framework: no storage permission needed). It is a
plain, readable file you can keep in your cloud storage or email to yourself:

```
{ "formatVersion": 1, "appVersion": "0.2.0", "exportedAt": "2026-11-10T09:30:00Z",
  "accounts": [...], "categories": [...], "transactions": [...] }
```

Every column of every row is included, money is integer cents and dates are ISO `yyyy-MM-dd`.
"Restore from file" validates the file first (format version, ids, and that every transaction
points to an existing account / category) and shows how many accounts, categories and transactions
it holds. After you confirm ("This replaces everything in Pik with the backup") the database is
emptied and refilled with the original ids in a single Room transaction, so a failure changes
nothing. The date of the last saved file is shown under Backup ("Last saved: 10 Nov 2026").

### Automatic backup

Android Auto Backup to Google is enabled. The Room database and the DataStore file are
included for cloud backup and device-to-device transfer (`res/xml/backup_rules.xml` and
`res/xml/data_extraction_rules.xml`). Restoring on a new phone brings back all data.
