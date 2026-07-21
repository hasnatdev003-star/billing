package com.reusable.subscription.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create a single instance of DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "billing_prefs")

class BillingDataStore(private val context: Context) {
    companion object {
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
    }

    val isPremium: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_PREMIUM] ?: false
        }

    suspend fun savePremiumStatus(isPremium: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_PREMIUM] = isPremium
        }
    }
}
