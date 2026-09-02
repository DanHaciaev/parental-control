package com.teo.child.data.local

import com.teo.core.model.RuleMode
import com.teo.core.util.DayBoundary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed class BlockDecision {
    data object Allowed : BlockDecision()
    data class Blocked(val reason: String, val packageName: String? = null, val allowRequestMore: Boolean = false) :
        BlockDecision()
}

data class AppBalance(
    val packageName: String,
    val appLabel: String,
    val usedMinutes: Int,
    val limitMinutes: Int,
    val bonusMinutes: Int
) {
    val remainingMinutes: Int get() = (limitMinutes + bonusMinutes - usedMinutes).coerceAtLeast(0)
}

@Singleton
class LocalUsageStore @Inject constructor(
    private val usageDao: UsageDao,
    private val ruleCacheDao: RuleCacheDao
) {
    /** Called every poll tick (2-5s) while [packageName] is the foreground app. */
    suspend fun recordForegroundTick(packageName: String, tickSeconds: Int, timezone: String) {
        val dateKey = DayBoundary.todayKey(timezone)
        val existing = usageDao.get(dateKey, packageName)
        val totalSeconds = (existing?.secondsAccumulated ?: 0) + tickSeconds
        val extraMinutes = totalSeconds / 60
        val remainderSeconds = totalSeconds % 60
        val newMinutes = (existing?.minutesUsedToday ?: 0) + extraMinutes

        usageDao.upsert(
            UsageEntity(
                dateKey = dateKey,
                packageName = packageName,
                minutesUsedToday = newMinutes,
                secondsAccumulated = remainderSeconds,
                lastUpdated = System.currentTimeMillis(),
                // Only flip dirty when the parent-visible minute count actually changed —
                // avoids re-uploading on every sub-minute tick.
                uploaded = if (extraMinutes > 0) false else (existing?.uploaded ?: false)
            )
        )
    }

    suspend fun evaluate(packageName: String, timezone: String): BlockDecision {
        val rule = ruleCacheDao.get(packageName) ?: return BlockDecision.Allowed
        return when (RuleMode.valueOf(rule.mode)) {
            RuleMode.BLOCKED -> BlockDecision.Blocked("Это приложение заблокировано родителем")
            RuleMode.TIME_LIMIT -> {
                val dailyLimit = rule.dailyLimitMinutes ?: return BlockDecision.Allowed
                val dateKey = DayBoundary.todayKey(timezone)
                val bonus = if (rule.bonusDateKey == dateKey) rule.bonusMinutesToday else 0
                val usedMinutes = usageDao.get(dateKey, packageName)?.minutesUsedToday ?: 0
                if (usedMinutes >= dailyLimit + bonus) {
                    BlockDecision.Blocked(
                        "Время на сегодня закончилось",
                        packageName = packageName,
                        allowRequestMore = true
                    )
                } else {
                    BlockDecision.Allowed
                }
            }
        }
    }

    suspend fun getPendingUpload(): List<UsageEntity> = usageDao.getPendingUpload()

    suspend fun markUploaded(entries: List<UsageEntity>) {
        entries.forEach { usageDao.markUploaded(it.dateKey, it.packageName) }
    }

    suspend fun replaceRules(rules: List<RuleCacheEntity>) = ruleCacheDao.replaceAll(rules)

    /** Today's remaining-time balance for every app with a daily limit — drives the child's home screen. */
    fun observeBalances(timezone: String): Flow<List<AppBalance>> {
        val dateKey = DayBoundary.todayKey(timezone)
        return combine(ruleCacheDao.observeAll(), usageDao.observeForDay(dateKey)) { rules, usage ->
            val usageByPackage = usage.associateBy { it.packageName }
            rules
                .filter { it.mode == RuleMode.TIME_LIMIT.name && it.dailyLimitMinutes != null }
                .map { rule ->
                    val bonus = if (rule.bonusDateKey == dateKey) rule.bonusMinutesToday else 0
                    AppBalance(
                        packageName = rule.packageName,
                        appLabel = rule.appLabel,
                        usedMinutes = usageByPackage[rule.packageName]?.minutesUsedToday ?: 0,
                        limitMinutes = rule.dailyLimitMinutes ?: 0,
                        bonusMinutes = bonus
                    )
                }
        }
    }

    /** App labels currently fully blocked — lets the child's home screen say so honestly. */
    fun observeBlockedAppLabels(): Flow<List<String>> =
        ruleCacheDao.observeAll().map { rules ->
            rules.filter { it.mode == RuleMode.BLOCKED.name }.map { it.appLabel }
        }
}
