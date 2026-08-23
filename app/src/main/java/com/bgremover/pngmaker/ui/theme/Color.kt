package com.bgremover.pngmaker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Vibrant brand palette.
 *
 * The identity is a three-stop ramp — violet → fuchsia → cyan — with amber and emerald as
 * supporting accents. The Material scheme below is derived from that ramp so the surfaces
 * stay calm while the gradients carry the colour. Values mirror `res/values/colors.xml`.
 */

// ---------------------------------------------------------------- brand ramp
// These five are theme-independent: they read correctly on light and dark surfaces and are
// the only colours used inside gradients.
val BrandViolet = Color(0xFF7C3AED)
val BrandFuchsia = Color(0xFFEC4899)
val BrandCyan = Color(0xFF06B6D4)
val BrandAmber = Color(0xFFF59E0B)
val BrandEmerald = Color(0xFF10B981)

// ---------------------------------------------------------------- light scheme
val LightPrimary = Color(0xFF6D28D9)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEDE2FF)
val LightOnPrimaryContainer = Color(0xFF250858)

val LightSecondary = Color(0xFFC2185B)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFFFD9E7)
val LightOnSecondaryContainer = Color(0xFF3E0020)

val LightTertiary = Color(0xFF0E7490)
val LightOnTertiary = Color(0xFFFFFFFF)

val LightBackground = Color(0xFFFCF9FF)
val LightOnBackground = Color(0xFF1B1524)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1B1524)
val LightSurfaceVariant = Color(0xFFEDE4F6)
val LightOnSurfaceVariant = Color(0xFF574F62)
val LightOutline = Color(0xFF867C93)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

// ---------------------------------------------------------------- dark scheme
val DarkPrimary = Color(0xFFC9A9FF)
val DarkOnPrimary = Color(0xFF3A0B87)
val DarkPrimaryContainer = Color(0xFF5B21B6)
val DarkOnPrimaryContainer = Color(0xFFEDE2FF)

val DarkSecondary = Color(0xFFFF9CC5)
val DarkOnSecondary = Color(0xFF5C0033)
val DarkSecondaryContainer = Color(0xFF8C0046)
val DarkOnSecondaryContainer = Color(0xFFFFD9E7)

val DarkTertiary = Color(0xFF6EE1F5)
val DarkOnTertiary = Color(0xFF00363F)

val DarkBackground = Color(0xFF0B0713)
val DarkOnBackground = Color(0xFFEBE2F5)
val DarkSurface = Color(0xFF17102A)
val DarkOnSurface = Color(0xFFEBE2F5)
val DarkSurfaceVariant = Color(0xFF3B3350)
val DarkOnSurfaceVariant = Color(0xFFCFC3E0)
val DarkOutline = Color(0xFF978CA8)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

/** Checkerboard used to visualise transparency. */
val CheckerLight = Color(0xFFF4F1F8)
val CheckerDark = Color(0xFFDCD5E4)
val CheckerLightOnDarkTheme = Color(0xFF2F2743)
val CheckerDarkOnDarkTheme = Color(0xFF241D35)
