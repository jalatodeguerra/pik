# Pik

Pik is an offline Android app for tracking your spending and paying off debt.
Everything stays on your phone. There is no account, no server and no internet permission.

Milestone 2 completed "track spending": first-run setup, accounts (cash and bank), adding /
editing expenses, income and transfers, an activity list by month with search and filters, a home
screen with balances, a Settings screen (accounts, categories, backup, about), balance checks and
backup files. Milestone 3 added the Plan tab: payday month, budgets and "safe to spend". Milestone 4
added debts: a Debts tab with a payoff plan, debt payments, interest and fee entries, a "What if I pay
more?" slider, TAN / TAEG, loan types and instalments. Milestone 5 (this version) adds recurring bills,
subscriptions, income and debt instalments, a "Due" list, daily reminders and backup format 5.

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
- **Month starts on** (Settings -> Plan): day 1-28, default 1. If you are paid on the 27th, choose
  27 and Home and Plan use periods from the 27th to the 26th (see "Payday month" below).
- **Plan tab**: period header with previous / next arrows, "Safe to spend", the overall budget,
  category budgets with progress bars, and a "Not budgeted" list of categories you spent in without a
  limit. Past periods show totals and the budgets that applied then. "Set budgets" opens the budget
  editor.
- **Home** shows a "Safe to spend" card (tap to open Plan). "You have" (cash and bank) and
  "You owe" (the total remaining of your debts, tap to open the Debts tab) stay separate and are never
  added together.
- **Debts tab**: a paid-off ring, the total you still owe, the debt-free month and the interest still
  to pay, the payoff order (highest interest or smallest balance, with months and interest for each), a
  "What if I pay more?" slider and the list of debts. See "Debts" below.
- **Recurring** (Home quick link and a card on Plan): bills, subscriptions, regular income and debt
  instalments, a "Due soon / Overdue" list on Home, a week strip, monthly and yearly totals and a daily
  reminder. See "Recurring" below.
- **App shortcut**: long-press the Pik icon and choose "Add expense" to open the Add screen
  directly (only after first-run setup).

## Debts

### Schema version 3

Two tables are added by `MIGRATION_2_3` (existing tables are untouched):

- `debts`: `id`, `name`, `startingBalanceCents` (what you owed when you added it), `originalAmountCents`
  (optional, the size of an old loan, for "% paid off"), `aprBasisPoints` (1250 = 12,50 %),
  `minPaymentCents`, `dueDay` (1-28), `archived`, `sortOrder`, `createdAt`.
- `debt_entries`: `id`, `debtId` (foreign key to `debts`, ON DELETE CASCADE, indexed), `type`
  (`INTEREST`, `FEE` or `ADJUSTMENT`), `amountCents` (signed: positive raises the debt, negative lowers
  it), `date` (calendar day), `note`, `createdAt`.

`transactions.debtId` already existed and has **no** foreign key (adding one needs a table rebuild).
Instead a debt that has payments cannot be deleted (archive it); a debt without payments can be
deleted and takes its entries with it.

### Schema version 4: TAN, TAEG, loan types, instalments

`MIGRATION_3_4` only adds columns to `debts` (existing debts keep everything):

```sql
ALTER TABLE `debts` ADD COLUMN `taegBasisPoints` INTEGER
ALTER TABLE `debts` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'OTHER'
ALTER TABLE `debts` ADD COLUMN `totalInstallments` INTEGER
ALTER TABLE `debts` ADD COLUMN `installmentsPaidBefore` INTEGER
```

- **TAN** (tasso annuo nominale) is the existing `aprBasisPoints` column (kept, so no table rebuild); it
  is the only rate used for interest maths. **TAEG** (tasso annuo effettivo globale, total yearly cost
  including fees) is the optional `taegBasisPoints`. It is shown ("TAN 7,50% · TAEG 9,12%") and used to
  rank debts for the "Highest interest" payoff order (TAEG when set, otherwise TAN). The edit screen shows a
  soft warning (it does not block saving) when the TAEG is lower than the TAN.
- **Loan type** (`type`, enum `DebtType`): car loan, personal loan, mortgage, consumer credit, student loan,
  family or friends, other (default). Each has an icon, shown in the list, the detail page and as chips on
  the edit page.
- **Instalments (rate)**: for every type except family or friends / other, `minPaymentCents` is shown as
  the "Monthly instalment (rata)". Optional `totalInstallments` (e.g. 60) and `installmentsPaidBefore`
  (paid before the loan was added to Pik, 0 when left empty). Instalments paid so far = paid before + the
  payments logged in Pik for the debt (expense with `debtId`) whose amount is **at least 90 %** of the
  instalment; smaller payments still lower the balance but are not counted as a rata. Left = total - paid
  (never below 0). The end month is the current month + left - 1 when this month's rata is already paid,
  otherwise the current month + left. The list and the detail page show "Instalment 18 of 60 · 42 left ·
  ends March 2030" and a progress bar by instalments (`domain/Installments.kt`, unit tested).

### Balance rules

```
remaining = startingBalance + sum(entries) - sum(EXPENSE transactions with this debtId)
```

Never stored. Shown as 0 with "Paid off" when negative. Interest is stored as entries (what the lender
really charged), not recomputed from the rate over history. Balances drift from the bank, so every
debt has **Check balance**: type what the bank says, and Pik adds an `ADJUSTMENT` entry for the
difference (or says "Already matches").

### Debt payments

A debt payment is an `EXPENSE` transaction from an account with `debtId` set and no category. It
counts as spending (Home, Plan, the overall budget, safe to spend) **and** lowers the debt. It is
started from the debt ("Log payment": amount prefilled with the minimum, account defaults to the last
used one, date), not from the Add screen. In Activity and Home it shows as "Debt payment · <debt name>"
with the blue debt icon. Editing it in the Add screen keeps its debt (the category chips are replaced by
the debt name). The debt page lists payments and entries together, newest first; tapping an entry asks to
delete it, tapping a payment opens it for editing or deleting.

### Payoff simulation

`PayoffSimulator` in `domain/PayoffSimulator.kt` is pure Kotlin. The first simulated month is the
current month. Each month:

1. interest is added to every open debt: `balance x TAN / 12`, rounded half up to the cent;
2. every minimum payment is paid (capped at the balance);
3. the **pool** (the extra per month, plus the minimums of debts that are already paid off, plus any part
   of a minimum that was not needed) goes to the target debt and cascades to the next one in the same
   month. This is the snowball roll-over: a cleared debt's payment is never lost.

The target order is fixed from the starting balances: highest interest first (by TAEG when a debt has one, else by TAN; interest itself always uses the TAN), or smallest balance first
(ties by id). The result has the debt-free month, number of months, total interest and the payoff month of
each debt. `neverPaysOff` is true when a month ends without the total balance going down (the payment does
not cover the interest, and it never will) or after 600 months (50 years). `compareOrders` gives both
orders; `whatIf(extra)` simulates another extra amount and reports the months and interest saved compared
with no extra payment at all.

The extra per month and the payoff order are stored in DataStore (`extraPerMonthCents`, `payoffOrder`) and
saved with "Use this" under the slider and the segmented control on the Debts tab. The slider goes from 0
to 500 euros in 5 euro steps (higher if the saved value is higher).

## Payday month (period)

`domain/Period.kt` is pure Kotlin. Given a date and the start day (1-28) it returns the period
`[start, endInclusive]`: with start day 27, 11 Nov 2026 is in 27 Oct - 26 Nov and 27 Nov starts
27 Nov - 26 Dec. The label is the month name ("November") when the start day is 1, otherwise
"27 Oct - 26 Nov". The period key is the calendar month of its start ("2026-10" for 27 Oct - 26 Nov).
Home's "Money in / out" totals, "Safe to spend" and the Plan tab use the current period.
Activity always keeps calendar months. The setting is stored in DataStore (`monthStartDay`).
Changing it moves where periods begin; budgets are keyed by the period key, so after a change a
budget applies to the period that starts in the same calendar month.

## Budgets

Table `budgets` (schema version 2): `id`, `categoryId` (null = overall budget for total spending,
foreign key to `categories` with ON DELETE CASCADE), `limitCents`, `startPeriod` ("yyyy-MM"),
`createdAt`, with a unique index on `(categoryId, startPeriod)`.

- The limit of a period is the row with the latest `startPeriod <= the period key`, per category.
  Nothing is copied forward. Saving budgets in the editor writes rows for the current period key
  only ("from this period on"); earlier periods keep their old limits. A value equal to the limit
  already in force is not written.
- `limitCents = 0` means "no budget from that period on" (this is how a budget is removed).
- SQLite treats NULLs as distinct in a UNIQUE index, so uniqueness of the overall budget per
  `startPeriod` is enforced in `BudgetRepository` (it looks the row up with `categoryId IS :id`
  and updates instead of inserting a duplicate).
- Progress bar colors: lime, amber from 80 percent of the limit, coral when over. Spending counts
  EXPENSE transactions only (transfers, income and adjustments are excluded). The overall budget
  compares against all expenses, including uncategorised ones and debt payments.
- In "Not budgeted", expenses that pay a debt are their own line, "Debt payments" (blue debt icon),
  separate from "Uncategorised".
- The editor lists non-archived expense categories. A budget on a category that is archived later
  stays in force and is still shown on Plan.

## Safe to spend

One definition, `domain/SafeToSpend.kt`, used by Home and Plan:

```
plannedIncome = max(expected monthly income setting, income received this period + incomeStillDue)
safeToSpend   = plannedIncome - spentSoFar - billsStillDue - debtPaymentsDue
perDay        = safeToSpend / days left in the period (today included); 0 when safeToSpend <= 0
```

`spentSoFar` is the EXPENSE total of the period (transfers and adjustments excluded). Debt payments
are expenses, so what you already paid to debts is inside `spentSoFar`. `billsStillDue` is the
unconfirmed BILL and SUBSCRIPTION occurrences from today to the end of the period and `incomeStillDue` the
unconfirmed INCOME ones (see "Recurring"); DEBT_PAYMENT rules are left out of `billsStillDue`. When `safeToSpend` is negative the screens show an
over-spent state. "Expected monthly income" is optional (Settings -> Plan); when it is not set,
planned income is the income received. Only the current period shows safe to spend.

### debtPaymentsDue

`DebtPaymentsDue` in `domain/SafeToSpend.kt` (unit tested). For every non-archived debt with
`remaining > 0` and the debt payments already logged to it in the current period (`paid`):

```
minDue   = min( max(0, minPayment - paid), remaining )
extraDue = if extraPerMonth > 0:
               min( max(0, extraPerMonth - sum(max(0, paid - minPayment))), remaining still owed after the minimums )
           else 0
debtPaymentsDue = sum(minDue) + extraDue
```

So the money promised to debts is taken off safe to spend once, and when you log a payment it moves
from "still due" to "spent" instead of being subtracted twice. Paying more than the minimum eats into
the extra first. The two `min(..., remaining)` caps are an addition to the plain formula: nothing
more is reserved than what is still owed. Plan shows "X still to pay on debts is already set aside"
when the figure is above 0.

## Recurring

### Schema version 5: `recurring_rules`

`MIGRATION_4_5` adds one table (existing tables are untouched; `transactions.recurringId` already existed
and still has no foreign key):

```sql
CREATE TABLE IF NOT EXISTS `recurring_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `transactionType` TEXT NOT NULL, `amountCents` INTEGER NOT NULL,
  `accountId` INTEGER NOT NULL, `categoryId` INTEGER, `debtId` INTEGER, `note` TEXT NOT NULL,
  `startDate` INTEGER NOT NULL, `intervalCount` INTEGER NOT NULL, `intervalUnit` TEXT NOT NULL,
  `dayOfMonth` INTEGER, `endDate` INTEGER, `maxOccurrences` INTEGER,
  `occurrencesDone` INTEGER NOT NULL DEFAULT 0, `nextDueDate` INTEGER NOT NULL,
  `remindDayBefore` INTEGER NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL,
  FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON DELETE RESTRICT,
  FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON DELETE SET NULL,
  FOREIGN KEY(`debtId`) REFERENCES `debts`(`id`) ON DELETE CASCADE)
-- plus indices on accountId, categoryId and debtId
```

- `kind` is `BILL`, `SUBSCRIPTION`, `INCOME` or `DEBT_PAYMENT`. `transactionType` is `INCOME` for income and
  `EXPENSE` for everything else. A debt payment has a `debtId` and no category.
- The schedule is "every `intervalCount` `intervalUnit` (`WEEK`, `MONTH`, `YEAR`) from `startDate`", on
  `dayOfMonth` for months and years, ending on `endDate` and / or after `maxOccurrences` occurrences.
- `occurrencesDone` counts the occurrences already confirmed **or skipped**; `nextDueDate` is the date of the
  next one (the date of the last one when the series is over, and then `active` is false).
- An account used by a rule cannot be deleted (RESTRICT; Settings says the account is in use), a deleted
  category clears the rule's category, a deleted debt deletes its rule. Deleting a rule keeps the
  transactions it created.

### Dates: `RecurrenceCalculator`

`domain/RecurrenceCalculator.kt` is pure Kotlin (unit tested: 31st, leap years, every 2 weeks, quarterly,
end by date and by count). Occurrence number *n* of a series is computed from the start and *n*, never from
the previous date, so a clamped day does not drift: day 31 gives 31 Jan, 28 Feb, 31 Mar, 30 Apr, 31 May ...
In a month that is too short the last day is used (28 or 29 Feb for 29, 30 and 31). A yearly 29 Feb is 28 Feb
in non-leap years. "Quarterly" is every 3 months. A start date that is after the chosen day of the month
(start 20 Jan, day 5) moves the first occurrence to 5 Feb (`normalized`).

History is not backfilled: when a rule is created, or its start date, interval or unit change, the
occurrences before the first day of the current month count as done. Changing only the amount, the day, the
end or the name keeps what is done.

### Confirming and the Due list

Nothing is logged automatically. Every occurrence that is not confirmed or skipped and has a date on or before
today + 7 days is in the **Due** list (Home card "Due soon" / "Overdue", and the Recurring screen). After days
without opening the app every missed occurrence (date before today) shows as overdue. Only the oldest pending
occurrence of a rule can be handled (the later ones are greyed out until then). Tapping an item opens a small
sheet: editable amount (bills vary, Italian input), account (the rule's, or any active account) and date
(default: the due date).

- **Confirm** creates the transaction with `recurringId` (and `debtId` for a debt payment, which has no
  category; a bill or income keeps the rule's category and uses the rule's note or, when empty, its name) and
  moves the rule to the next occurrence.
- **Skip** moves on without a transaction.
- A series that ends (end date or count) becomes inactive after the last occurrence is handled.

### Recurring screen

A week strip (today and the next 6 days, an amber dot for each item due that day), the Due list, the monthly
and yearly **outgoing** totals (bills, subscriptions and debt payments; weekly x 52, monthly x 12, yearly x 1,
divided by the interval, rounded to the cent) and the rules grouped by kind (Bills, Subscriptions, Debt payments,
Income) with the amount, the frequency ("Every month on the 5th" / "Ogni mese il 5") and the next date. The
add / edit screen has: name, kind chips, amount, account, category (expense or income categories by kind; hidden
for debt payments), start date, repeat (every N weeks / months / years), day of the month, ends (never / on a
date / after N times), "Remind me the day before" and a note. Amber is the recurring color.

### Debts own their schedule

On the debt edit screen, **Remind me and track this payment** (on by default for a new debt, shown when the
instalment is above 0) creates a `DEBT_PAYMENT` rule: amount = the instalment / minimum payment, day = the
debt's due day, name = the debt's name, account = the last used one. Editing the debt's instalment, due day or
name updates its rule (the rule's day always comes from the debt); archiving the debt deactivates the rule and
unarchiving it activates it again (months that passed meanwhile are not asked for); turning the switch off
deletes the rule. A new rule starts this month when the due day is still ahead and this month's instalment was
not paid yet, otherwise next month, so linking a debt never creates overdue items. The rule can be opened from
the Recurring screen to change the account, the reminder and the note; amount and day are changed in the debt.

### Safe to spend and recurring

```
billsStillDue    = sum of BILL + SUBSCRIPTION occurrences dated today .. end of the period, not confirmed
incomeStillDue   = sum of INCOME occurrences dated today .. end of the period, not confirmed
plannedIncome    = max(expected income setting, received income + incomeStillDue)
safeToSpend      = plannedIncome - spentSoFar - billsStillDue - debtPaymentsDue
```

`DEBT_PAYMENT` rules are excluded from `billsStillDue`: debts are already counted through `debtPaymentsDue`, so
nothing is counted twice. A confirmed bill is an expense: it moves from "still due" to `spentSoFar`. Overdue
(unconfirmed, dated before today) bills and subscriptions still count in `billsStillDue`, because they still
have to be paid; overdue income that was never confirmed is not counted.
A weekly bill counts once for every date left in the period. Plan shows "X of bills and subscriptions still due
this period is already set aside" and, when relevant, "Includes X of income you expect later".

### Reminders

A WorkManager periodic work (`pik_daily_reminder`, every 24 hours with a 2 hour flex window) runs
`ReminderWorker` (a `@HiltWorker`; `PikApp` is the `Configuration.Provider` with `HiltWorkerFactory`, and the
default `WorkManagerInitializer` is removed in the manifest). Once a day it posts **one** notification in the
"Reminders" channel with the items due **tomorrow** whose rule has "Remind me the day before", plus the number of
overdue items. Nothing is posted when nothing is due tomorrow or when notifications are not allowed. Tapping it
opens the Recurring screen.

- The work is scheduled on every app start (`KEEP`) and again with `UPDATE` when the **Reminder time** (Settings
  -> Reminders, default 9:00) changes. The time is the delay before the first run; Android may run it later
  ("Android can delay it"), and phones with aggressive battery savers (OnePlus) can stop it.
- **POST_NOTIFICATIONS** (Android 13+) is asked only when the user first turns on "Remind me the day before",
  never at launch. If it is denied, the switch shows a hint and Settings shows "Allow notifications" (opens the
  app notification settings) while any reminder is on.
- Settings -> Reminders -> **If reminders don't arrive** opens the app's system settings
  (`ACTION_APPLICATION_DETAILS_SETTINGS`) with a short explanation: go to Battery and allow background activity.

## Stack

- Kotlin 2.1.21, Jetpack Compose (Material 3, BOM 2025.05.01), dark theme only
- Room 2.7.1 (schema exported to `app/schemas`, now at version 5), DataStore Preferences
- Hilt for dependency injection, Navigation Compose
- Fonts: Outfit (headings, numbers) and Inter (body), both under the SIL OFL (see `licenses/`)
- WorkManager 2.10.1 and Hilt-Work 1.2.0 for the daily reminder
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
  data/prefs    DataStore preferences (onboardingComplete, lastUsedAccountId, lastBackupEpochDay,
                monthStartDay, expectedIncomeCents, extraPerMonthCents, payoffOrder, reminderHour)
  domain        pure Kotlin, no Android: Money, AmountInput (keypad), BalanceCalculator, BalanceCheck,
                ActivityFilter, DateFormats (locale aware), Installments, Period, BudgetCalculator, SafeToSpendCalculator,
                DebtCalculator / DebtPaymentsDue, PayoffSimulator, Apr, RecurrenceCalculator / RecurringPlanner,
                ReminderTime
  backup        backup file model and JSON (de)serialization with validation (org.json only)
  reminders     ReminderWorker (daily notification), ReminderScheduler (WorkManager), ReminderPermissions
  di            Hilt modules
  ui/theme      colors, fonts, category colors and icons
  ui/components shared composables
  ui/nav        routes, floating bottom bar, nav host
  ui/screens    firstrun, add, activity, home, plan (Plan, budget editor), debts, recurring (list, edit, confirm
                sheet), settings
```

Layers: Compose screens -> ViewModels (one `StateFlow` UI state per screen) -> repositories -> Room.

## Italian and English

All texts are in `res/values/strings.xml` (English) and `res/values-it/strings.xml` (Italian); every string
must exist in both, because lint (`MissingTranslation`) is an error. The app follows the phone language;
on Android 13+ Settings -> Language opens the per-app language screen (`res/xml/locales_config.xml` lists
`en` and `it`, referenced by `android:localeConfig`), on older versions the system language settings.
`DateFormats` uses the current locale for month and day names ("3 nov 2026", "Novembre 2026" as a heading,
"marzo 2030" inside a sentence); it takes a `Locale` so tests cover both. Amounts stay `1.234,56 €` in both
languages. The default categories are seeded once when a new install finishes setup, in Italian when the
phone language is Italian; existing categories are never renamed.

## Money and date rules

- Money is always a `Long` number of **euro cents**. Never `Double` or `Float`.
- Only EUR. Amounts are shown Italian style with the symbol after: `1.234,56 €`
  (thousands `.`, decimals `,`, non-breaking space, `€`). Formatting is done by hand in
  `domain/Money.kt` and never depends on the phone's locale.
- A transaction date is a **calendar day** (`java.time.LocalDate`), stored as epoch day.
  It is not a timestamp. Only `createdAt` / `updatedAt` are epoch milliseconds.
- Account balance is calculated, never stored:
  starting balance + income - expense - transfers out + transfers in + adjustments.
- A debt payment is an expense with a `debtId`; it counts as spending and lowers the debt.

## Database migrations

The database holds real data, so `fallbackToDestructiveMigration` is never used. Version 2 adds the
`budgets` table through `MIGRATION_1_2` (`data/db/Migrations.kt`), registered in `DatabaseModule`;
existing tables are untouched. Version 3 adds `debts` and `debt_entries` through `MIGRATION_2_3`, also
registered in `DatabaseModule`. Version 4 adds the TAEG, loan type and instalment columns to `debts`
through `MIGRATION_3_4` (add-column only, see "Schema version 4"). Version 5 adds `recurring_rules` through
`MIGRATION_4_5` (see "Schema version 5"). `MigrationTest` (instrumented) creates a version 1 database (and a version 2
one) with an account, a category and a transaction, migrates it (1 -> 3, 2 -> 3, 3 -> 4, 4 -> 5, 1 -> 4 and 1 -> 5) and
checks that the rows survived and that the new tables exist and accept rows (for `recurring_rules` also the column
default and the three foreign key actions). Every future schema change needs a migration
and a test like this. After changing an entity, build once so Room writes the new `app/schemas/.../N.json`
and commit it: the migration tests read it.

## Backup

### Backup file (Settings -> Backup)

"Save backup file" writes one JSON file, named `pik-backup-YYYY-MM-DD.json`, to a place you pick
with the Android file picker (Storage Access Framework: no storage permission needed). It is a
plain, readable file you can keep in your cloud storage or email to yourself:

```
{ "formatVersion": 5, "appVersion": "0.5.0", "exportedAt": "2026-11-10T09:30:00Z",
  "accounts": [...], "categories": [...], "transactions": [...], "budgets": [...],
  "debts": [...], "debtEntries": [...], "recurringRules": [...],
  "settings": { "monthStartDay": 27, "expectedIncomeCents": 250000,
                "extraPerMonthCents": 5000, "payoffOrder": "HIGHEST_INTEREST" } }
```

`formatVersion` 2 added `budgets`, 3 added `debts`, `debtEntries` and the optional `settings` object, 4 added
`taegBasisPoints`, `type`, `totalInstallments` and `installmentsPaidBefore` to each debt (`aprBasisPoints` is the
TAN, 5 added the `recurringRules` array (every column of `recurring_rules`). Restore still accepts `formatVersion` 1 to 4
files (missing debt fields become: no TAEG, type OTHER, no instalments; no recurring rules); files from version 1 and 2 (the restored database then has no budgets and/or no
debts, and a stray `debtId` in an old file is dropped). A budget must point to an existing category (or have
a null category for the overall budget), have a non-negative limit, a `yyyy-MM` start period and be unique
per category and start period. A recurring rule must point to an account (and category / debt) in the file, have an interval of 1-120, a day of the
month of 1-31 (or none), a kind that matches its transaction type (INCOME makes INCOME, the rest EXPENSE), and a debt
exactly when it is a DEBT_PAYMENT. A debt entry must point to a debt in the file, a transaction with a `debtId`
must point to a debt in the file, debts need a due day 1-28, a rate of 0-100 % and non-negative amounts.
`settings` (month start day, expected income, extra per month, payoff order) are DataStore values: they are
written to the file on save and, when the file has them, restored after the database. A file without
`settings` leaves the current ones alone.

Every column of every row is included, money is integer cents and dates are ISO `yyyy-MM-dd`.
"Restore from file" validates the file first (format version, ids, and that every transaction
points to an existing account / category / debt) and shows how many accounts, categories, transactions
and debts it holds. After you confirm ("This replaces everything in Pik with the backup") the database is
emptied and refilled with the original ids in a single Room transaction, so a failure changes
nothing. The date of the last saved file is shown under Backup ("Last saved: 10 Nov 2026").

### Automatic backup

Android Auto Backup to Google is enabled. The Room database and the DataStore file are
included for cloud backup and device-to-device transfer (`res/xml/backup_rules.xml` and
`res/xml/data_extraction_rules.xml`). Restoring on a new phone brings back all data.
