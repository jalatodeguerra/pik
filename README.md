# Pik

Pik is an offline Android app for tracking your spending and paying off debt.
Everything stays on your phone. There is no account, no server and no internet permission.

Milestone 1 (this version): first-run setup, accounts (cash and bank), adding / editing
expenses, income and transfers, an activity list by month, and a home screen with balances.
Plan and Debts are placeholders for the next updates.

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
  data/prefs    DataStore preferences (onboardingComplete, lastUsedAccountId)
  domain        pure Kotlin, no Android: Money, AmountInput (keypad), BalanceCalculator, DateFormats
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

Android Auto Backup to Google is enabled. The Room database and the DataStore file are
included for cloud backup and device-to-device transfer (`res/xml/backup_rules.xml` and
`res/xml/data_extraction_rules.xml`). Restoring on a new phone brings back all data.
