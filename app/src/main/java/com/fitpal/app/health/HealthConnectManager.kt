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
     * Total steps for a calendar day — Health Connect's own **priority-deduplicated** aggregate.
     *
     * Health Connect resolves overlapping records from different apps by priority; it does NOT sum
     * them. So a mirror app like Health Sync, which copies Samsung Health's steps into Health
     * Connect, is counted once — not added on top. (An earlier version summed the per-minute max
     * across every source, which double-counted those mirrors: Samsung Health 9,600 + Health Sync
     * 9,000 came out as ~17,000 instead of ~10,000.)
     *
     * The one thing priority-dedup can drop is watch steps the phone's counter outranks — but those
     * are recovered separately, not by summing here: the watch reports its own daily count over the
     * Data Layer and [com.fitpal.app.data.repository.StepRepository] floors the day to it.
     */
    suspend fun readSteps(date: LocalDate): Long {
        if (!isAvailable()) return 0L
        return aggregateSteps(date)
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
