package app.lychee

/**
 * Features that may later depend on an upgrade. Every check goes through [isEnabled], so
 * billing can plug in here alone; for now everything is on.
 */
enum class Feature {
    GLIDE_TYPING,
    HANDWRITING,
    OFFLINE_VOICE,
    WRITTEN_CHINESE,
    ENGLISH_TO_CHINESE,
    CUSTOM_FONTS,
    CUSTOM_EMOJI_FONTS,
}

object Features {
    @JvmStatic
    fun isEnabled(@Suppress("UNUSED_PARAMETER") feature: Feature): Boolean = true
}
