package com.fitpal.app.ui.screen.caffeine

import com.fitpal.app.data.repository.MealRepository
import com.fitpal.app.domain.Caffeine
import com.fitpal.app.domain.CaffeineDose
import com.fitpal.app.domain.CaffeineSettings
import com.fitpal.app.ui.component.CaffeineView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** The caffeine tracker for one day, plus which logged item each of the day's doses came from. */
data class CaffeineDay(val view: CaffeineView, val items: List<Pair<Long, CaffeineDose>>)

/**
 * The caffeine tracker for [date], from what was logged: that day's doses and the day before's (a
 * late coffee is still in you after midnight). An item whose caffeine hasn't been checked yet falls
 * back to a typical amount for its name, marked as an estimate. Home and the Caffeine page both read
 * this, so they can't disagree.
 */
fun caffeineDayFlow(mealRepository: MealRepository, settings: CaffeineSettings, date: LocalDate): Flow<CaffeineDay> {
    val dayIso = date.toString()
    return mealRepository.caffeineRowsInRange(date.minusDays(1).toString(), dayIso).map { rows ->
        val withMg = rows.mapNotNull { r -> Caffeine.dose(r.timestamp, r.name, r.grams, r.caffeineMg)?.let { r to it } }
        val day = withMg.filter { it.first.date == dayIso }
        val dayDoses = day.map { it.second }
        CaffeineDay(
            view = CaffeineView(
                settings = settings,
                doses = withMg.map { it.second },
                dayDoses = dayDoses,
                dayTotalMg = dayDoses.sumOf { it.mg.toDouble() }.toFloat(),
                isToday = date == LocalDate.now()
            ),
            items = day.map { it.first.itemId to it.second }
        )
    }
}

/** Caffeine (mg) per day of the week starting [weekStart], null for days still to come. */
fun caffeineWeekFlow(mealRepository: MealRepository, weekStart: LocalDate): Flow<List<Float?>> =
    mealRepository.caffeineRowsInRange(weekStart.toString(), weekStart.plusDays(6).toString()).map { rows ->
        val byDay = rows.mapNotNull { r -> Caffeine.dose(r.timestamp, r.name, r.grams, r.caffeineMg)?.let { r.date to it.mg } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, mgs) -> mgs.sum() }
        val today = LocalDate.now()
        (0L until 7L).map { i ->
            val d = weekStart.plusDays(i)
            if (d.isAfter(today)) null else (byDay[d.toString()] ?: 0f)
        }
    }
