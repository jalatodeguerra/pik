package com.pik.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pik.app.domain.PayoffOrder
import com.pik.app.domain.Period
import com.pik.app.domain.importing.ImportAppConfig
import com.pik.app.domain.importing.ImportSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// File: files/datastore/pik_prefs.preferences_pb (included in the backup rules).
private val Context.pikDataStore: DataStore<Preferences> by preferencesDataStore(name = "pik_prefs")

@Singleton
class PikPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store: DataStore<Preferences> get() = context.pikDataStore

    val onboardingComplete: Flow<Boolean> = store.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }

    val lastUsedAccountId: Flow<Long?> = store.data.map { it[KEY_LAST_USED_ACCOUNT_ID] }

    /** Calendar day (epoch day) of the last backup file saved, or null when never. */
    val lastBackupEpochDay: Flow<Long?> = store.data.map { it[KEY_LAST_BACKUP_EPOCH_DAY] }

    suspend fun setLastBackupEpochDay(epochDay: Long) {
        store.edit { it[KEY_LAST_BACKUP_EPOCH_DAY] = epochDay }
    }

    /** Day of the month (1..28) on which the "month" starts. Default 1 = calendar months. */
    val monthStartDay: Flow<Int> = store.data.map { Period.clampStartDay(it[KEY_MONTH_START_DAY] ?: 1) }

    /** Expected monthly income in cents, or null when not set. */
    val expectedIncomeCents: Flow<Long?> = store.data.map { it[KEY_EXPECTED_INCOME_CENTS] }

    suspend fun setMonthStartDay(day: Int) {
        store.edit { it[KEY_MONTH_START_DAY] = Period.clampStartDay(day) }
    }

    /** null clears the setting. */
    suspend fun setExpectedIncomeCents(cents: Long?) {
        store.edit {
            if (cents == null) it.remove(KEY_EXPECTED_INCOME_CENTS) else it[KEY_EXPECTED_INCOME_CENTS] = cents
        }
    }

    /** Extra money per month (cents) that goes to debts on top of the minimums. Default 0. */
    val extraPerMonthCents: Flow<Long> = store.data.map { (it[KEY_EXTRA_PER_MONTH_CENTS] ?: 0L).coerceAtLeast(0L) }

    /** Which debt gets the extra money first. Default: highest interest. */
    val payoffOrder: Flow<PayoffOrder> = store.data.map { prefs ->
        prefs[KEY_PAYOFF_ORDER]?.let { name -> PayoffOrder.entries.firstOrNull { it.name == name } }
            ?: PayoffOrder.HIGHEST_INTEREST
    }

    suspend fun setExtraPerMonthCents(cents: Long) {
        store.edit { it[KEY_EXTRA_PER_MONTH_CENTS] = cents.coerceAtLeast(0L) }
    }

    suspend fun setPayoffOrder(order: PayoffOrder) {
        store.edit { it[KEY_PAYOFF_ORDER] = order.name }
    }

    /** Writes all plan settings at once (backup restore). */
    suspend fun restorePlanSettings(
        monthStartDay: Int,
        expectedIncomeCents: Long?,
        extraPerMonthCents: Long,
        payoffOrder: PayoffOrder,
    ) {
        store.edit {
            it[KEY_MONTH_START_DAY] = Period.clampStartDay(monthStartDay)
            if (expectedIncomeCents == null) it.remove(KEY_EXPECTED_INCOME_CENTS) else it[KEY_EXPECTED_INCOME_CENTS] = expectedIncomeCents
            it[KEY_EXTRA_PER_MONTH_CENTS] = extraPerMonthCents.coerceAtLeast(0L)
            it[KEY_PAYOFF_ORDER] = payoffOrder.name
        }
    }

    /** Hour of the day (0..23) when the daily reminder should arrive. Default 9 (9:00). */
    val reminderHour: Flow<Int> = store.data.map { (it[KEY_REMINDER_HOUR] ?: DEFAULT_REMINDER_HOUR).coerceIn(0, 23) }

    suspend fun setReminderHour(hour: Int) {
        store.edit { it[KEY_REMINDER_HOUR] = hour.coerceIn(0, 23) }
    }

    /** True when the app asks for the fingerprint / phone PIN to open. Default false. */
    val lockEnabled: Flow<Boolean> = store.data.map { it[KEY_LOCK_ENABLED] ?: false }

    suspend fun setLockEnabled(value: Boolean) {
        store.edit { it[KEY_LOCK_ENABLED] = value }
    }

    /** The payment apps whose notifications Pik reads, each with the Pik account its payments belong to. */
    val importApps: Flow<List<ImportAppConfig>> = store.data.map { prefs ->
        (prefs[KEY_IMPORT_APPS] ?: emptySet()).mapNotNull { ImportAppConfig.decode(it) }.sortedBy { it.packageName }
    }

    /** Turns reading an app on (or changes its source / account). */
    suspend fun setImportApp(packageName: String, source: ImportSource, accountId: Long?) {
        store.edit { prefs ->
            val current = (prefs[KEY_IMPORT_APPS] ?: emptySet())
                .filterNot { ImportAppConfig.decode(it)?.packageName == packageName }
            prefs[KEY_IMPORT_APPS] = (current + ImportAppConfig(packageName, source, accountId).encode()).toSet()
        }
    }

    /** Turns reading an app off. */
    suspend fun removeImportApp(packageName: String) {
        store.edit { prefs ->
            val current = (prefs[KEY_IMPORT_APPS] ?: emptySet())
                .filterNot { ImportAppConfig.decode(it)?.packageName == packageName }
            if (current.isEmpty()) prefs.remove(KEY_IMPORT_APPS) else prefs[KEY_IMPORT_APPS] = current.toSet()
        }
    }

    /** Replaces all import app settings at once (backup restore). */
    suspend fun restoreImportApps(apps: List<ImportAppConfig>) {
        store.edit { prefs ->
            if (apps.isEmpty()) prefs.remove(KEY_IMPORT_APPS) else prefs[KEY_IMPORT_APPS] = apps.map { it.encode() }.toSet()
        }
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        store.edit { it[KEY_ONBOARDING_COMPLETE] = value }
    }

    suspend fun setLastUsedAccountId(accountId: Long) {
        store.edit { it[KEY_LAST_USED_ACCOUNT_ID] = accountId }
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboardingComplete")
        val KEY_LAST_USED_ACCOUNT_ID = longPreferencesKey("lastUsedAccountId")
        val KEY_LAST_BACKUP_EPOCH_DAY = longPreferencesKey("lastBackupEpochDay")
        val KEY_MONTH_START_DAY = intPreferencesKey("monthStartDay")
        val KEY_EXPECTED_INCOME_CENTS = longPreferencesKey("expectedIncomeCents")
        val KEY_EXTRA_PER_MONTH_CENTS = longPreferencesKey("extraPerMonthCents")
        val KEY_PAYOFF_ORDER = stringPreferencesKey("payoffOrder")
        val KEY_LOCK_ENABLED = booleanPreferencesKey("lockEnabled")
        val KEY_REMINDER_HOUR = intPreferencesKey("reminderHour")
        val KEY_IMPORT_APPS = stringSetPreferencesKey("importApps")
        const val DEFAULT_REMINDER_HOUR = 9
    }
}
