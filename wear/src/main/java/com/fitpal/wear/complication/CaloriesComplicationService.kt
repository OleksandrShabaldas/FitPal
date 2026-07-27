package com.fitpal.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.fitpal.wear.data.WearStats
import com.fitpal.wear.presentation.MainActivity

/**
 * Read-only "calories left today" complication. Shows the eat-back remaining figure from the last
 * synced snapshot; tapping opens the watch app's stats screen. Separate from the water
 * complication (per the design decision) so each does one thing.
 */
class CaloriesComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("640").build(),
            contentDescription = PlainComplicationText.Builder("Calories left today").build()
        ).setTitle(PlainComplicationText.Builder("kcal").build()).build()
        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
            value = 1360f, min = 0f, max = 2000f,
            contentDescription = PlainComplicationText.Builder("Calories left today").build()
        ).setText(PlainComplicationText.Builder("640").build()).build()
        else -> null
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val snapshot = WearStats.latest(this)
        val consumed = snapshot?.caloriesConsumed ?: 0
        val target = (snapshot?.caloriesTarget ?: 0)
        val left = snapshot?.caloriesLeft ?: 0
        val tap = openApp()

        return when (request.complicationType) {
            ComplicationType.RANGED_VALUE -> {
                val max = (target + (snapshot?.totalBurned ?: 0)).coerceAtLeast(1)
                RangedValueComplicationData.Builder(
                    value = consumed.coerceIn(0, max).toFloat(),
                    min = 0f,
                    max = max.toFloat(),
                    contentDescription = PlainComplicationText.Builder("Calories left today").build()
                )
                    .setText(PlainComplicationText.Builder("$left").build())
                    .setTapAction(tap)
                    .build()
            }

            else -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("$left").build(),
                contentDescription = PlainComplicationText.Builder("Calories left today").build()
            )
                .setTitle(PlainComplicationText.Builder("kcal").build())
                .setTapAction(tap)
                .build()
        }
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
