package com.bgremover.pngmaker.data.model

/** Which segmentation model the user prefers. */
enum class EngineMode {
    /** Try general subject segmentation, fall back to the bundled person model. */
    AUTO,

    /** Google Play services general subject segmentation (products, animals, objects). */
    SUBJECT,

    /** Bundled selfie/person segmentation — always available, fully offline. */
    PERSON;

    companion object {
        fun fromName(value: String?): EngineMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/**
 * Upper bound on the exported image, expressed in megapixels. Segmentation always runs on
 * a downscaled working copy; this only limits the final composite so a huge photo cannot
 * exhaust the heap.
 */
enum class OutputResolution(val maxPixels: Long, val label: String) {
    FAST(4_000_000L, "Fast — up to 4 MP"),
    BALANCED(8_000_000L, "Balanced — up to 8 MP"),
    ORIGINAL(16_000_000L, "Maximum — up to 16 MP");

    companion object {
        fun fromName(value: String?): OutputResolution =
            entries.firstOrNull { it.name == value } ?: ORIGINAL
    }
}

/** How much the cut-out edge is feathered, in mask pixels. */
enum class EdgeSoftness(val blurRadius: Int, val low: Float, val high: Float) {
    SHARP(0, 0.45f, 0.55f),
    NATURAL(2, 0.35f, 0.65f),
    SOFT(4, 0.25f, 0.75f);

    companion object {
        fun fromName(value: String?): EdgeSoftness =
            entries.firstOrNull { it.name == value } ?: NATURAL
    }
}

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromName(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class AppSettings(
    val engineMode: EngineMode = EngineMode.AUTO,
    val outputResolution: OutputResolution = OutputResolution.ORIGINAL,
    val edgeSoftness: EdgeSoftness = EdgeSoftness.NATURAL,
    val keepRecents: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Off by default: the app's identity is its own gradient ramp, and wallpaper-derived
    // colours would quietly replace it on Android 12+. Users who prefer that can opt in.
    val dynamicColor: Boolean = false
)
