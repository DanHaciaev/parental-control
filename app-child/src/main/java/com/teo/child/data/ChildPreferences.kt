package com.teo.child.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "child_prefs")

/** Local record of which family this device is paired to — read once at startup to skip pairing. */
@Singleton
class ChildPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val familyIdKey = stringPreferencesKey("family_id")
    private val onboardingCompleteKey = booleanPreferencesKey("onboarding_complete")

    val familyId: Flow<String?> = context.dataStore.data.map { prefs -> prefs[familyIdKey] }

    suspend fun setFamilyId(familyId: String) {
        context.dataStore.edit { prefs -> prefs[familyIdKey] = familyId }
    }

    /**
     * Set once, the first time all permissions are granted. Routing checks this instead of
     * re-checking live permission state, so a later remote RELEASE_PROTECTION (which turns
     * Device Admin back off on purpose) doesn't bounce the child back into onboarding.
     */
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[onboardingCompleteKey] ?: false }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { prefs -> prefs[onboardingCompleteKey] = true }
    }
}
