package com.fitpal.wear.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.fitpal.wear.QuickLogReceiver
import com.fitpal.wear.data.WearStats

/**
 * Quick-log water complication: shows how much you've drunk today and, when tapped, logs a preset
 * amount to the phone (no app open). Supports SHORT_TEXT and RANGED_VALUE watch-face slots.
 */
class WaterComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("0.8L").build(),
            contentDescription = PlainComplicationText.Builder("Water logged today").build()
        ).setTitle(PlainComplicationText.Builder("+250").build()).build()
        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
            value = 800f, min = 0f, max = 2000f,
            contentDescription = PlainComplicationText.Builder("Water logged today").build()
        ).setText(PlainComplicationText.Builder("800ml").build()).build()
        else -> null
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val snapshot = WearStats.latest(this)
        val water = snapshot?.waterMl ?: 0
        val goal = (snapshot?.waterGoalMl ?: 2000).coerceAtLeast(1)
        val quickMl = snapshot?.waterPresets?.firstOrNull() ?: 250
        val tap = QuickLogReceiver.pendingIntent(this, quickMl)

        return when (request.complicationType) {
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = water.coerceAtMost(goal).toFloat(),
                min = 0f,
                max = goal.toFloat(),
                contentDescription = PlainComplicationText.Builder("Water $water of $goal ml").build()
            )
                .setText(PlainComplicationText.Builder(format(water)).build())
                .setTapAction(tap)
                .build()

            else -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(format(water)).build(),
                contentDescription = PlainComplicationText.Builder("Water logged today").build()
            )
                .setTitle(PlainComplicationText.Builder("+$quickMl").build())
                .setTapAction(tap)
                .build()
        }
    }

    private fun format(ml: Int): String =
        if (ml >= 1000) String.format("%.1fL", ml / 1000f) else "${ml}ml"
}
