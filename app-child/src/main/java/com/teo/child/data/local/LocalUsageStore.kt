package com.teo.child.data.local

import com.teo.core.model.RuleMode
import com.teo.core.util.DayBoundary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Mirrors [com.teo.core.model.AppRule.effectiveLimitMinutes]: a per-weekday override (when present)
 *  wins for that ISO day (1=Monday..7=Sunday), falling back to the flat [RuleCacheEntity.dailyLimitMinutes]
 *  — a missing/sentinel (-1) day in the CSV falls back the same way. */
private fun RuleCacheEntity.effectiveLimitMinutes(isoDayOfWeek: Int): Int? {
    val fromWeekly = weeklyLimitMinutesCsv
        ?.split(",")
        ?.getOrNull(isoDayOfWeek - 1)
        ?.toIntOrNull()
        ?.takeIf { it >= 0 }
    return fromWeekly ?: dailyLimitMinutes
}

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
    private val ruleCacheDao: RuleCacheDao,
    private val hourlyUsageDao: HourlyUsageDao
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
                val dateKey = DayBoundary.todayKey(timezone)
                val dailyLimit = rule.effectiveLimitMinutes(DayBoundary.isoDayOfWeek(timezone))
                    ?: return BlockDecision.Allowed
                val bonus = if (rule.bonusDateKey == dateKey) rule.bonusMinutesToday else 0
                val usedMinutes = usageDao.get(dateKey, packageName)?.minutesUsedToday ?: 0
                if (usedMinutes >= dailyLimit + bonus) {
                    return BlockDecision.Blocked(
                        "Время на сегодня закончилось",
                        packageName = packageName,
                        allowRequestMore = true
                    )
                }
                BlockDecision.Allowed
            }
        }
    }

    /** Sum of minutes used today across apps tagged TIME_LIMIT — backs the total daily cap, which
     *  (once exhausted) triggers a full-device block, not just blocking those apps individually. */
    suspend fun getTotalTimedMinutesForDay(dateKey: String): Int = usageDao.getTotalTimedMinutesForDay(dateKey) ?: 0

    suspend fun getPendingUpload(): List<UsageEntity> = usageDao.getPendingUpload()

    suspend fun markUploaded(entries: List<UsageEntity>) {
        entries.forEach { usageDao.markUploaded(it.dateKey, it.packageName) }
    }

    /** Total-device-usage-per-hour, aggregated across all foreground apps — backs the statistics bar chart. */
    suspend fun recordHourlyTick(tickSeconds: Int, timezone: String) {
        val dateKey = DayBoundary.todayKey(timezone)
        val hour = DayBoundary.currentHour(timezone)
        val existing = hourlyUsageDao.get(dateKey, hour)
        val totalSeconds = (existing?.secondsAccumulated ?: 0) + tickSeconds
        val extraMinutes = totalSeconds / 60
        val remainderSeconds = totalSeconds % 60
        val newMinutes = (existing?.minutesUsed ?: 0) + extraMinutes

        hourlyUsageDao.upsert(
            HourlyUsageEntity(
                dateKey = dateKey,
                hour = hour,
                minutesUsed = newMinutes,
                secondsAccumulated = remainderSeconds,
                lastUpdated = System.currentTimeMillis(),
                uploaded = if (extraMinutes > 0) false else (existing?.uploaded ?: false)
            )
        )
    }

    suspend fun getPendingHourlyUpload(): List<HourlyUsageEntity> = hourlyUsageDao.getPendingUpload()

    suspend fun markHourlyUploaded(entries: List<HourlyUsageEntity>) {
        entries.forEach { hourlyUsageDao.markUploaded(it.dateKey, it.hour) }
    }

    suspend fun replaceRules(rules: List<RuleCacheEntity>) = ruleCacheDao.replaceAll(rules)

    /** Today's remaining-time balance for every app with a daily limit — drives the child's home screen.
     *  Re-subscribes on day rollover (via [DayBoundary.dateKeyFlow]) instead of pinning a single
     *  [DayBoundary.todayKey] for the life of the flow, which would otherwise freeze this on
     *  whatever day the child app process happened to start until it's killed and relaunched. */
    fun observeBalances(timezone: String): Flow<List<AppBalance>> =
        DayBoundary.dateKeyFlow(timezone).flatMapLatest { dateKey ->
            val isoDayOfWeek = LocalDate.parse(dateKey).dayOfWeek.value
            combine(ruleCacheDao.observeAll(), usageDao.observeForDay(dateKey)) { rules, usage ->
                val usageByPackage = usage.associateBy { it.packageName }
                rules
                    .filter { it.mode == RuleMode.TIME_LIMIT.name }
                    .mapNotNull { rule ->
                        val limit = rule.effectiveLimitMinutes(isoDayOfWeek) ?: return@mapNotNull null
                        val bonus = if (rule.bonusDateKey == dateKey) rule.bonusMinutesToday else 0
                        AppBalance(
                            packageName = rule.packageName,
                            appLabel = rule.appLabel,
                            usedMinutes = usageByPackage[rule.packageName]?.minutesUsedToday ?: 0,
                            limitMinutes = limit,
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
