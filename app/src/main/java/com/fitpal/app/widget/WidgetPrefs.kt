package com.fitpal.app.widget

import android.content.Context
import android.content.SharedPreferences

/**
 * Which [WidgetStyle] each placed widget wears, keyed by its app-widget id. Tiny and separate from
 * [com.fitpal.app.data.repository.SettingsRepository] on purpose: providers and the configure screen
 * read it without the Hilt graph, and ids are the launcher's, not ours.
 */
object WidgetPrefs {
    private const val FILE = "fitpal_widgets"
    private const val KEY_STYLE = "style_"
    private const val KEY_PENDING_PIN = "pending_pin_style_"
    private const val KEY_PREVIEWS_VERSION = "generated_previews_version"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun style(context: Context, appWidgetId: Int): WidgetStyle =
        WidgetStyle.fromId(prefs(context).getString(KEY_STYLE + appWidgetId, null))

    fun setStyle(context: Context, appWidgetId: Int, style: WidgetStyle) {
        prefs(context).edit().putString(KEY_STYLE + appWidgetId, style.id).apply()
    }

    fun forget(context: Context, appWidgetIds: IntArray) {
        val edit = prefs(context).edit()
        appWidgetIds.forEach { edit.remove(KEY_STYLE + it) }
        edit.apply()
    }

    /**
     * The look picked in the in-app gallery for a widget that's being pinned right now. The launcher
     * only tells us the new widget's id once it's placed, so the choice waits here until then.
     */
    fun setPendingPinStyle(context: Context, kind: WidgetKind, style: WidgetStyle) {
        prefs(context).edit().putString(KEY_PENDING_PIN + kind.id, style.id).apply()
    }

    /** The app version whose picker previews were last handed to the launcher ([WidgetPreviews]). */
    fun previewsVersion(context: Context): Int = prefs(context).getInt(KEY_PREVIEWS_VERSION, 0)

    fun setPreviewsVersion(context: Context, version: Int) {
        prefs(context).edit().putInt(KEY_PREVIEWS_VERSION, version).apply()
    }

    /** The waiting look without clearing it (the configure screen pre-selects it). */
    fun peekPendingPinStyle(context: Context, kind: WidgetKind): WidgetStyle? =
        prefs(context).getString(KEY_PENDING_PIN + kind.id, null)?.let { WidgetStyle.fromId(it) }

    fun takePendingPinStyle(context: Context, kind: WidgetKind): WidgetStyle? {
        val p = prefs(context)
        val id = p.getString(KEY_PENDING_PIN + kind.id, null) ?: return null
        p.edit().remove(KEY_PENDING_PIN + kind.id).apply()
        return WidgetStyle.fromId(id)
    }
}
