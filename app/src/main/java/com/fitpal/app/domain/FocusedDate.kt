package com.fitpal.app.domain

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single "where in time am I looking" cursor shared between Home and Analytics.
 *
 * Home browses one day at a time; Analytics looks at the week/month that day falls in. Keeping both
 * on one shared date means moving to a previous week on Home shows that week's analytics, and tapping
 * a day in analytics jumps Home to it. Resets to today on a fresh app launch (singleton recreated).
 */
@Singleton
class FocusedDate @Inject constructor() {
    val date = MutableStateFlow(LocalDate.now())
    fun set(d: LocalDate) { date.value = d }
}
