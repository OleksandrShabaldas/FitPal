package com.fitpal.app.ml

/**
 * Which AI produced a generation: the [engine] that answered (online vs the on-device fallback)
 * and, when we know it, the exact [model] that did the work.
 *
 * The model matters because "online" isn't one thing — [GeminiClient] cascades through the user's
 * configured models and falls to the next one when it's out of quota, so the badge would otherwise
 * hide which model actually answered.
 *
 * Surfaced by [com.fitpal.app.ui.component.AiSourceBadge] and persisted next to logged meals,
 * saved reviews, collection foods and exercise entries (engine and model in separate columns, so
 * older rows that only stored the engine still read back fine).
 */
data class AiSource(val engine: Engine, val model: String? = null) {

    enum class Engine { ONLINE, OFFLINE }

    val isOnline: Boolean get() = engine == Engine.ONLINE
    val isOffline: Boolean get() = engine == Engine.OFFLINE

    /** The value stored in the DB's `aiSource` column — the model lives in its own column. */
    val storedName: String get() = engine.name

    /** "Online AI" / "On-device AI" — the coarse answer, shown alone when the model is unknown. */
    val engineLabel: String get() = if (isOnline) "Online AI" else "On-device AI"

    /** What the badge shows: "Gemini 3 Flash Preview", or the engine when we don't know the model. */
    val modelLabel: String get() = model?.let { prettyModelName(it) } ?: engineLabel

    companion object {
        /** The one on-device model — see [ModelManager.specs]. */
        const val ON_DEVICE_MODEL = "Gemma 3n E4B"

        fun online(model: String? = null) = AiSource(Engine.ONLINE, model.orNullIfBlank())

        /** The on-device engine only ever runs the one model, so its name is the default. */
        fun offline(model: String? = ON_DEVICE_MODEL) = AiSource(Engine.OFFLINE, model.orNullIfBlank())

        /** Parse a stored engine name (+ model) back to an [AiSource], tolerating null/unknown values. */
        fun fromName(name: String?, model: String? = null): AiSource? =
            name?.let { runCatching { Engine.valueOf(it) }.getOrNull() }
                ?.let { AiSource(it, model.orNullIfBlank()) }

        /**
         * Turn a raw model id into something readable: "gemini-3-flash-preview" →
         * "Gemini 3 Flash Preview". Anything that already reads like a name (it has spaces, e.g.
         * "Gemma 3n E4B") is left exactly as it is, so a model id we've never seen still shows up
         * honestly instead of being mangled.
         */
        fun prettyModelName(id: String): String {
            val raw = id.trim()
            if (raw.isEmpty() || raw.any { it.isWhitespace() }) return raw
            return raw.split('-', '_')
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    // Leave version-ish and already-capitalised parts alone ("3", "2.5", "E4B").
                    if (part.any { it.isDigit() || it.isUpperCase() }) part
                    else part.replaceFirstChar { it.uppercase() }
                }
        }

        private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    }
}
