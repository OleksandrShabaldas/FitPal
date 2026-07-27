package com.fitpal.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Thin wrapper over Health Connect for reading steps. Samsung Health (which the watch
 * feeds) writes steps into Health Connect — an on-device store — and we read them here,
 * so the whole thing stays offline / private.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** The permissions we need the user to grant in the Health Connect UI. */
    val permissions: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasAllPermissions(): Boolean {
        if (!isAvailable()) return false
        return runCatching {
            client().permissionController.getGrantedPermissions().containsAll(permissions)
        }.getOrDefault(false)
    }

    private fun dayRange(date: LocalDate): Pair<Instant, Instant> {
        val zone = ZoneId.systemDefault()
        return date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()
    }

    /**
     * Total steps for a calendar day.
     *
     * Health Connect's own aggregate de-duplicates overlapping records by *app priority* —
     * which on Samsung phones often means the phone's built-in counter wins over the watch
     * (so a watch's 2000 steps collapse to the phone's 200). Naive summing across apps would
     * instead double-count a walk both devices saw.
     *
     * So we do a **time-merge**: split the day into one-minute slots, and for each slot take the
     * MOST any single source recorded for it, then sum the slots. Overlapping coverage (phone +
     * watch on the same walk) counts once at the higher value; complementary coverage (each
     * device active at different times) adds up. We return the larger of this and Health
     * Connect's own aggregate, so we never under-count.
     */
    suspend fun readSteps(date: LocalDate): Long {
        if (!isAvailable()) return 0L
        val aggregate = aggregateSteps(date)
        val merged = mergedStepsAcrossSources(date)
        return maxOf(aggregate, merged)
    }

    /**
     * Combine every step source for the day without double-counting overlap. Each source's
     * records are spread across the minute-slots they cover; per slot we keep the max across
     * sources; then we sum. See [readSteps].
     */
    private suspend fun mergedStepsAcrossSources(date: LocalDate): Long {
        val (start, end) = dayRange(date)
        val dayStartMin = start.epochSecond / 60L
        val slotCount = 24 * 60  // minutes in a day
        val bySource = HashMap<String, DoubleArray>()
        runCatching {
            var pageToken: String? = null
            do {
                val response = client().readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageToken = pageToken
                    )
                )
                response.records.forEach { record ->
                    val pkg = record.metadata.dataOrigin.packageName
                    val slots = bySource.getOrPut(pkg) { DoubleArray(slotCount) }
                    spreadRecord(record, dayStartMin, slotCount, slots)
                }
                pageToken = response.pageToken
            } while (pageToken != null)
        }
        if (bySource.isEmpty()) return 0L
        var total = 0.0
        for (slot in 0 until slotCount) {
            var best = 0.0
            for (slots in bySource.values) if (slots[slot] > best) best = slots[slot]
            total += best
        }
        return total.roundToLong()
    }

    /** Spread one record's steps evenly across the minute-slots it covers (clamped to the day). */
    private fun spreadRecord(record: StepsRecord, dayStartMin: Long, slotCount: Int, slots: DoubleArray) {
        val s = (record.startTime.epochSecond / 60L - dayStartMin).toInt().coerceIn(0, slotCount - 1)
        val e = (record.endTime.epochSecond / 60L - dayStartMin).toInt().coerceIn(s, slotCount - 1)
        val span = (e - s + 1)
        val perSlot = record.count.toDouble() / span
        for (i in s..e) slots[i] += perSlot
    }

    /** Health Connect's priority-deduplicated total. */
    private suspend fun aggregateSteps(date: LocalDate): Long {
        val (start, end) = dayRange(date)
        return runCatching {
            client().aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )[StepsRecord.COUNT_TOTAL] ?: 0L
        }.getOrDefault(0L)
    }

    /**
     * Steps for the day grouped by the app that wrote them (package name → total). Used both
     * to pick the most complete source and to show the user where their steps come from.
     */
    suspend fun readStepsByOrigin(date: LocalDate): Map<String, Long> {
        if (!isAvailable()) return emptyMap()
        val (start, end) = dayRange(date)
        val sums = linkedMapOf<String, Long>()
        runCatching {
            var pageToken: String? = null
            do {
                val response = client().readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageToken = pageToken
                    )
                )
                response.records.forEach { record ->
                    val pkg = record.metadata.dataOrigin.packageName
                    sums[pkg] = (sums[pkg] ?: 0L) + record.count
                }
                pageToken = response.pageToken
            } while (pageToken != null)
        }
        return sums
    }
}
