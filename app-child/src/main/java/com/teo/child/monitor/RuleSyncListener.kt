package com.teo.child.monitor

import com.teo.child.data.local.LocalUsageStore
import com.teo.child.data.local.RuleCacheEntity
import com.teo.core.model.AppRule
import com.teo.core.repository.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private fun AppRule.toEntity() = RuleCacheEntity(
    packageName = packageName,
    appLabel = appLabel,
    mode = mode.name,
    dailyLimitMinutes = dailyLimitMinutes,
    bonusMinutesToday = bonusMinutesToday,
    bonusDateKey = bonusDateKey
)

/** Keeps the Room rule mirror in sync with Firestore for as long as the monitor service is alive. */
@Singleton
class RuleSyncListener @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val localUsageStore: LocalUsageStore
) {
    fun start(familyId: String, scope: CoroutineScope) {
        scope.launch {
            // Retries on failure (e.g. a transient PERMISSION_DENIED right after boot, before
            // the auth token is attached) instead of letting an uncaught listener error crash
            // the foreground service — a crash here would repeatedly kill child protection.
            while (true) {
                try {
                    ruleRepository.observeRules(familyId).collect { rules ->
                        localUsageStore.replaceRules(rules.map { it.toEntity() })
                    }
                } catch (e: Exception) {
                    delay(2000)
                }
            }
        }
    }
}
