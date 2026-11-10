package com.pik.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    suspend fun setOnboardingComplete(value: Boolean) {
        store.edit { it[KEY_ONBOARDING_COMPLETE] = value }
    }

    suspend fun setLastUsedAccountId(accountId: Long) {
        store.edit { it[KEY_LAST_USED_ACCOUNT_ID] = accountId }
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboardingComplete")
        val KEY_LAST_USED_ACCOUNT_ID = longPreferencesKey("lastUsedAccountId")
    }
}
