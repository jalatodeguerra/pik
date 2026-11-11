<div align="center">

# Pik

**Track your spending. Pay off your debt. Keep your data on your phone.**

An offline personal finance app for Android, built with Kotlin and Jetpack Compose.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![Version](https://img.shields.io/badge/version-0.7.0-C6F36B)
![Status](https://img.shields.io/badge/status-in%20development-FFC857)

</div>

---

Pik is a money tracker for one person. You log what you spend and earn, set budgets for your payday
month, and Pik tells you how much is **safe to spend** after bills and debt payments. It also helps you
plan your way out of debt, with a payoff plan you can play with.

There is no account to create, no server and no internet permission. Everything lives in a local
database on your phone, with a backup file you control and optional Android backup to your Google account.

## Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Install on a phone](#install-on-a-phone)
- [Project structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Features

**Everyday tracking**
- 💳 **Accounts and balances**: cash and bank accounts, with a "check balance" that matches Pik to what your bank app shows.
- ➕ **Fast entry**: expenses, income and transfers with a built-in number keypad, plus an "Add expense" app shortcut.
- 📋 **Activity**: every transaction by month, with search and filters by type, account and category.

**Planning**
- 📅 **Payday month**: start your month on the day you get paid (for example the 27th), not just on the 1st.
- 🎯 **Budgets**: an overall budget and per-category limits, with progress bars that turn amber and coral as you get close.
- 🟢 **Safe to spend**: what is left for the rest of the period after spending, bills still due and debt payments, with a per-day figure.

**Debts**
- 🏦 **Loans and debts** with TAN and TAEG, loan types (car loan, mortgage, personal loan, consumer credit and more) and instalment tracking ("rata 18 di 60").
- 📉 **Payoff plan**: highest interest first or smallest balance first, with your debt-free month and the interest still to pay.
- 🎚️ **What if I pay more?**: a slider that shows how many months and how much interest an extra payment saves.

**Bills and reminders**
- 🔁 **Recurring**: bills, subscriptions, regular income and debt instalments, with a "Due soon / Overdue" list.
- 🔔 **Reminders** the day before something is due.

**Insights and privacy**
- 📊 **Insights**: income vs spending for the last 6 months, a donut of where the money went, and quick stats.
- 🔒 **Fingerprint lock** with your phone's PIN as fallback; Pik's screens are hidden from Recent apps.
- 💾 **Backup and restore** to a readable JSON file, plus **CSV export** for Excel or Google Sheets.
- 🇮🇹 **English and Italian**, following the phone language. Amounts are shown Italian style: `1.234,56 €`.
- 🌙 **Dark theme**.

## Screenshots

> Screenshots are coming soon.

<!--
Add phone screenshots to docs/screenshots/ and uncomment this table.

| Home | Plan | Debts | Insights |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/home.png" width="200" alt="Home"> | <img src="docs/screenshots/plan.png" width="200" alt="Plan"> | <img src="docs/screenshots/debts.png" width="200" alt="Debts"> | <img src="docs/screenshots/insights.png" width="200" alt="Insights"> |
-->

## Tech stack

| Area | Tools |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Storage | Room (SQLite) with exported schemas and tested migrations, DataStore Preferences |
| Dependency injection | Hilt |
| Background work | WorkManager (daily reminders) |
| Security | AndroidX Biometric |
| Charts | Hand-drawn with Compose `Canvas`, no chart library |
| Build | Gradle 8.14 (Kotlin DSL), Android Gradle Plugin 8.10, KSP |
| Fonts | [Outfit](https://fonts.google.com/specimen/Outfit) and [Inter](https://fonts.google.com/specimen/Inter) |

Minimum Android version is 8.0 (API 26); the app targets API 35. Money is always stored as whole euro
cents (`Long`), never as floating point.

## Getting started

### Requirements

- [Android Studio](https://developer.android.com/studio) (recent version), or a JDK 17+ and the Android SDK (API 35)
- An Android phone or emulator running Android 8.0 or newer

### Build

```bash
git clone https://github.com/jalatodeguerra/pik.git
cd pik

# Build a debug APK
./gradlew assembleDebug

# Run unit tests and lint
./gradlew testDebugUnitTest lintDebug

# Run instrumented tests (Room migrations) on a connected device or emulator
./gradlew connectedDebugAndroidTest
```

On Windows use `gradlew.bat` instead of `./gradlew`. The debug APK is written to
`app/build/outputs/apk/debug/app-debug.apk`.

Or open the project in Android Studio and press **Run**.

### Release build

`./gradlew assembleRelease` builds a minified release APK. It is signed only when a `keystore.properties`
file exists in the project root; see [Release build](docs/TECHNICAL.md#release-build) for how to set one up.
Never commit your keystore or its passwords.

## Install on a phone

1. Build the APK as shown above (or get one from a release).
2. With the phone connected over USB debugging: `adb install app/build/outputs/apk/debug/app-debug.apk`.
3. Or copy the APK to the phone and open it. Android asks you to allow installing apps from that source.

## Project structure

```
app/src/main/java/com/pik/app/
├── data/        Room database, DAOs, repositories, DataStore preferences
├── domain/      Pure Kotlin logic: money, periods, budgets, safe to spend, payoff simulator, recurrence, CSV
├── backup/      Backup file model and JSON validation
├── reminders/   WorkManager reminder worker and scheduling
├── lock/        Fingerprint lock
├── di/          Hilt modules
└── ui/          Theme, navigation and Compose screens
```

Screens talk to ViewModels, ViewModels talk to repositories, and repositories talk to Room. The `domain`
package has no Android code and is covered by unit tests.

For the details (database schema, the safe to spend formula, how the payoff simulation works, backup format,
CSV format) see **[docs/TECHNICAL.md](docs/TECHNICAL.md)**.

## Roadmap

- [x] Accounts, transactions, Activity with search and filters
- [x] Backup file and Android Auto Backup
- [x] Payday month, budgets and safe to spend
- [x] Debts with TAN / TAEG, loan types, instalments and a payoff plan
- [x] Italian translation
- [x] Recurring bills, subscriptions and reminders
- [x] Insights charts, fingerprint lock, CSV export
- [ ] Screenshots and a first tagged release
- [ ] Light theme
- [ ] Import from bank statements or notifications
- [ ] More currencies

## Contributing

Pik is a personal project and isn't looking for contributions right now, but issues with ideas or bugs
are welcome.

## License

No license has been chosen yet, so all rights are reserved for now.

The Outfit and Inter fonts are bundled under the [SIL Open Font License](licenses/).
