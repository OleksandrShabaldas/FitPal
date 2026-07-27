package com.fitpal.app.ml

/**
 * Which engine actually produced a meal analysis — surfaced to the UI as a small badge so
 * the user can tell whether the sharper online model or the on-device fallback was used.
 */
enum class AiSource {
    ONLINE, OFFLINE;

    companion object {
        /** Parse a stored name back to an [AiSource], tolerating null/unknown values. */
        fun fromName(name: String?): AiSource? =
            name?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
