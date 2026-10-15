package com.lukeneedham.poetrybook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

/*
 * The app's typefaces. Use these, never a font family written out at the use site, so the
 * look can be changed in one place.
 */

/** Titles: the bookshelf, settings, sheets, and headings in general. */
val TitleFont: FontFamily = FontFamily.Serif

/** The poems themselves, book names, and page and contents numbers. */
val PoemFont: FontFamily = FontFamily.Serif

/** Everything else: buttons, descriptions, hints. */
val BodyFont: FontFamily = FontFamily.Default

/** Material's type scale with the app's typefaces: display, headline and title styles are [TitleFont], the rest [BodyFont]. */
internal val AppTypography: Typography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.copy(fontFamily = TitleFont),
        displayMedium = it.displayMedium.copy(fontFamily = TitleFont),
        displaySmall = it.displaySmall.copy(fontFamily = TitleFont),
        headlineLarge = it.headlineLarge.copy(fontFamily = TitleFont),
        headlineMedium = it.headlineMedium.copy(fontFamily = TitleFont),
        headlineSmall = it.headlineSmall.copy(fontFamily = TitleFont),
        titleLarge = it.titleLarge.copy(fontFamily = TitleFont),
        titleMedium = it.titleMedium.copy(fontFamily = TitleFont),
        titleSmall = it.titleSmall.copy(fontFamily = TitleFont),
        bodyLarge = it.bodyLarge.copy(fontFamily = BodyFont),
        bodyMedium = it.bodyMedium.copy(fontFamily = BodyFont),
        bodySmall = it.bodySmall.copy(fontFamily = BodyFont),
        labelLarge = it.labelLarge.copy(fontFamily = BodyFont),
        labelMedium = it.labelMedium.copy(fontFamily = BodyFont),
        labelSmall = it.labelSmall.copy(fontFamily = BodyFont),
    )
}
