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
     * Total steps for a calendar day — the **largest single source** in Health Connect.
     *
     * Every app writing steps (Samsung Health, the phone's own "Android" counter, a mirror like
     * Health Sync, …) is an overlapping *view of the same steps*, not an additive slice — so any
     * form of summing over-counts. Even Health Connect's own aggregate over-counts here, because it
     * only de-duplicates records that overlap in time: two apps that log the same walk with slightly
     * different timestamps get added together (measured: Samsung Health 6,219 + the phone's 3,807
     * came out as ~8,900 when the real day was ~6,300). The single most complete source — usually
     * Samsung Health, which already merges phone + watch on Samsung devices — is the honest number,
     * so we take the max of the per-source totals.
     *
     * The one thing this can miss is a walk captured only by the watch when Samsung Health didn't
     * sync it — recovered separately: the watch reports its own daily count over the Data Layer and
     * [com.fitpal.app.data.repository.StepRepository] floors the day to it.
     */
    suspend fun readSteps(date: LocalDate): Long {
        if (!isAvailable()) return 0L
        val byOrigin = readStepsByOrigin(date)
        val largestSource = byOrigin.values.maxOrNull() ?: 0L
        // Fall back to the priority-deduped aggregate only if the per-source read came back empty.
        return if (largestSource > 0L) largestSource else aggregateSteps(date)
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
     * The day's steps hour by hour (24 values, local time), from the **single most complete source**
     * — the same rule as [readSteps], since sources are overlapping views of the same steps. A record
     * that spans an hour boundary is split by how much of it falls in each hour. Null when Health
     * Connect isn't available or has nothing for the day (the page then shows only the week).
     */
    suspend fun readStepsByHour(date: LocalDate): List<Long>? {
        if (!isAvailable()) return null
        val (start, end) = dayRange(date)
        val zone = ZoneId.systemDefault()
        val records = mutableListOf<StepsRecord>()
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
                records += response.records
                pageToken = response.pageToken
            } while (pageToken != null)
        }.onFailure { return null }
        val best = records.groupBy { it.metadata.dataOrigin.packageName }
            .maxByOrNull { (_, rs) -> rs.sumOf { it.count } }?.value ?: return null
        val hours = LongArray(24)
        for (r in best) {
            val from = maxOf(r.startTime, start)
            val to = minOf(r.endTime, end)
            val total = (to.toEpochMilli() - from.toEpochMilli()).coerceAtLeast(0L)
            if (total == 0L) {
                hours[from.atZone(zone).hour] += r.count
                continue
            }
            var cursor = from
            while (cursor.isBefore(to)) {
                val local = cursor.atZone(zone)
                val nextHour = local.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant()
                val sliceEnd = minOf(nextHour, to)
                val share = (sliceEnd.toEpochMilli() - cursor.toEpochMilli()).toDouble() / total
                hours[local.hour] += Math.round(r.count * share)
                cursor = sliceEnd
            }
        }
        return hours.toList().takeIf { list -> list.any { it > 0L } }
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
