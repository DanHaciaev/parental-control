package com.teo.child.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
     * re-checking live permission state, so Device Admin being turned off later (via the
     * PIN-gated uninstall flow, or a genuine bypass) doesn't bounce the child back into onboarding.
     */
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[onboardingCompleteKey] ?: false }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { prefs -> prefs[onboardingCompleteKey] = true }
    }

    private val installBaselineSyncedKey = booleanPreferencesKey("install_baseline_synced")

    /** Whether the one-time "grandfather in every already-installed app as approved" baseline scan
     *  has ever run. Without this, every service restart (frequent — see Samsung battery-kill notes
     *  throughout this codebase) would re-run that same grandfathering logic for any app not yet in
     *  Firestore, silently auto-approving a real post-setup install that the ACTION_PACKAGE_ADDED
     *  receiver missed simply because it wasn't registered yet at that exact moment. */
    val installBaselineSynced: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[installBaselineSyncedKey] ?: false }

    suspend fun setInstallBaselineSynced() {
        context.dataStore.edit { prefs -> prefs[installBaselineSyncedKey] = true }
    }

    private val protectionPinHashKey = stringPreferencesKey("protection_pin_hash")
    private val protectionPinSaltKey = stringPreferencesKey("protection_pin_salt")

    /** Local mirror of Family.protectionPinHash/Salt, refreshed on every family-doc update while
     *  online (see MonitorForegroundService.observeFamilySettings) — the PIN challenge screen
     *  verifies against this instead of a live Firestore call, since the one time this whole
     *  feature actually matters most (airplane mode just got turned on to dodge every other block)
     *  is exactly when there's no network left to make that call with. */
    suspend fun getCachedProtectionPin(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val hash = prefs[protectionPinHashKey]
        val salt = prefs[protectionPinSaltKey]
        return if (hash.isNullOrEmpty() || salt.isNullOrEmpty()) null else hash to salt
    }

    suspend fun setCachedProtectionPin(hash: String, salt: String) {
        context.dataStore.edit { prefs ->
            prefs[protectionPinHashKey] = hash
            prefs[protectionPinSaltKey] = salt
        }
    }

    private val stepBaselineCountKey = longPreferencesKey("step_baseline_count")
    private val stepBaselineDateKey = stringPreferencesKey("step_baseline_date")

    /** TYPE_STEP_COUNTER reports a cumulative total since the device's last boot, not "steps
     *  today" — this baseline is the reading at the start of [dateKey], so today's steps are
     *  simply (current reading - baseline). See StepCounterTracker. */
    suspend fun getStepBaseline(): Pair<Long, String> {
        val prefs = context.dataStore.data.first()
        return (prefs[stepBaselineCountKey] ?: -1L) to (prefs[stepBaselineDateKey] ?: "")
    }

    suspend fun setStepBaseline(steps: Long, dateKey: String) {
        context.dataStore.edit { prefs ->
            prefs[stepBaselineCountKey] = steps
            prefs[stepBaselineDateKey] = dateKey
        }
    }
}
