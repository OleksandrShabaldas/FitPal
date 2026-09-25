package com.fitpal.app.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Where a meal's time came from. Stored on [com.fitpal.app.data.local.entity.MealLogEntity.timeSource]
 * as [dbValue] (null = the moment it was logged — the default for every older row).
 */
enum class TimeSource(val dbValue: String?) {
    /** The moment it was logged — no one said otherwise. */
    LOGGED(null),
    /** The photo's own capture time. Trusted as proof when fasting (no pass needed). */
    PHOTO("photo"),
    /** A time the user picked by hand. */
    MANUAL("manual");

    companion object {
        fun fromDb(value: String?): TimeSource = entries.firstOrNull { it.dbValue == value } ?: LOGGED
    }
}

/**
 * When a not-yet-logged meal was eaten, as chosen on a logging screen. [time] null = "right now"
 * (or, when logging to another day, now's clock time on that day). [photoSuggestion] is a photo
 * capture time we deliberately didn't apply on our own — the photo is older than a recent meal would
 * be (re-using an old photo shouldn't silently back-date today's meal) — offered as a one-tap option.
 */
data class EatenAt(
    val time: LocalTime? = null,
    val source: TimeSource = TimeSource.LOGGED,
    val photoSuggestion: LocalDateTime? = null
) {
    /** Minute of the day this meal will be logged at, given the clock right now. */
    fun minuteOfDay(now: LocalTime = LocalTime.now()): Int = (time ?: now).let { it.hour * 60 + it.minute }

    /** True when the time is the photo's own (proof for fasting). */
    val isPhotoTime: Boolean get() = source == TimeSource.PHOTO && time != null

    /** Epoch millis to store for a meal logged on [date] at this time (now's clock time when unset). */
    fun epochMillis(date: LocalDate, now: LocalDateTime = LocalDateTime.now()): Long {
        val clock = time ?: now.toLocalTime()
        // Logging "now" for today keeps the exact instant (seconds included) so same-minute meals
        // still order correctly; any other day/time is pinned to the picked minute.
        val dateTime = if (time == null && date == now.toLocalDate()) now else date.atTime(clock.withSecond(0).withNano(0))
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    companion object {
        /**
         * How far back a photo's capture time still counts as "the meal you're logging now" and is
         * applied automatically. Covers a late-evening dinner logged after midnight; anything older
         * is more likely a re-used photo, so it's only offered.
         */
        val AUTO_APPLY_WINDOW: Duration = Duration.ofHours(16)

        /**
         * Decide what to do with a photo's capture time when logging to [logDate]: apply it (returns
         * the date to log to + the time), or only suggest it (returns null, caller keeps the
         * suggestion). [explicitDate] = the user came from a specific day on Home, so a photo from
         * another day must not move the meal.
         */
        fun autoApply(
            photoTaken: LocalDateTime,
            logDate: LocalDate,
            explicitDate: Boolean,
            now: LocalDateTime = LocalDateTime.now()
        ): Pair<LocalDate, LocalTime>? {
            if (photoTaken.isAfter(now.plusMinutes(5))) return null            // clock skew / bad EXIF
            if (Duration.between(photoTaken, now) > AUTO_APPLY_WINDOW) return null
            val photoDate = photoTaken.toLocalDate()
            if (explicitDate && photoDate != logDate) return null
            return photoDate to photoTaken.toLocalTime().withSecond(0).withNano(0)
        }
    }
}

/** A logged meal's stored time (epoch millis + its source column) as an [EatenAt] for display. */
fun eatenAtOf(timestamp: Long, timeSource: String?): EatenAt = EatenAt(
    time = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        .toLocalTime().withSecond(0).withNano(0),
    source = TimeSource.fromDb(timeSource)
)

/**
 * The meal-time state a logging screen's view model holds — shared so the five logging screens don't
 * each re-implement "now / the photo's time / a picked time".
 */
class EatenAtState {
    private val _value = MutableStateFlow(EatenAt())
    val value: StateFlow<EatenAt> = _value
    val current: EatenAt get() = _value.value

    /** The user picked a time by hand. */
    fun pick(time: LocalTime) {
        _value.value = _value.value.copy(time = time.withSecond(0).withNano(0), source = TimeSource.MANUAL)
    }

    /** Apply the photo's own capture time (trusted as proof for fasting). */
    fun usePhotoTime(time: LocalTime) {
        _value.value = EatenAt(time.withSecond(0).withNano(0), TimeSource.PHOTO)
    }

    /** Remember an older photo time to offer, without applying it. */
    fun suggestPhoto(taken: LocalDateTime) {
        _value.value = _value.value.copy(photoSuggestion = taken)
    }

    /** Back to "now". */
    fun reset() { _value.value = EatenAt() }

    /** Restore a saved state (e.g. adopting a background job). */
    fun set(value: EatenAt) { _value.value = value }
}

/**
 * What the fasting check decided about a log — handed to the log action so the saved meal records
 * it. [overrideTime] is set when the user spent an "ate earlier" pass on a specific time (the meal is
 * logged at that time); [loggedDuringFast] marks a "log anyway" during a fast.
 */
data class LogDecision(
    val overrideTime: LocalTime? = null,
    val loggedDuringFast: Boolean = false
) {
    companion object {
        val NONE = LogDecision()
    }
}
