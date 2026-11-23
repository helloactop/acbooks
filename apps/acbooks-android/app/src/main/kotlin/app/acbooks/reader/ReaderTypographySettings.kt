package app.acbooks.reader

import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * App-owned, persistent typography preferences. Null means "let Readium/the EPUB decide".
 *
 * Readium fontSize is a multiplier (1.0 = 100%), and pageMargins is a factor,
 * not a dp value. Keeping those units here avoids losing precision when saving.
 */
internal data class ReaderTypographySettings(
    val fontFamily: ReaderFontFamily = ReaderFontFamily.BOOK,
    val fontSize: Double? = null,
    val fontWeight: Double? = null,
    val lineHeight: Double? = null,
    val pageMargins: Double? = null,
    val paragraphIndent: Double? = null,
    val paragraphSpacing: Double? = null,
    val letterSpacing: Double? = null,
    val wordSpacing: Double? = null,
    val textAlignment: ReaderTextAlignment? = null,
    val publisherStyles: Boolean? = null,
    val scroll: Boolean = false,
    val appearance: ReaderAppearance = ReaderAppearance.SYSTEM,
    val backgroundArgb: Int? = null,
    val textArgb: Int? = null,
) {
    /** Ignore corrupt or unsupported stored ranges rather than breaking EPUB opening. */
    fun normalized(): ReaderTypographySettings = copy(
        fontSize = fontSize.inRange(0.1..5.0),
        fontWeight = fontWeight.inRange(0.0..2.5),
        lineHeight = lineHeight.inRange(1.0..2.0),
        pageMargins = pageMargins.inRange(0.0..4.0),
        paragraphIndent = paragraphIndent.inRange(0.0..3.0),
        paragraphSpacing = paragraphSpacing.inRange(0.0..2.0),
        letterSpacing = letterSpacing.inRange(0.0..1.0),
        wordSpacing = wordSpacing.inRange(0.0..1.0),
    )
}

/**
 * A complete per-book snapshot overrides the global profile. A missing override
 * inherits global changes; creating one copies the currently effective values.
 */
internal data class ReaderTypographyProfile(
    val global: ReaderTypographySettings = ReaderTypographySettings(),
    val bookOverride: ReaderTypographySettings? = null,
) {
    val effective: ReaderTypographySettings get() = bookOverride ?: global
    val hasBookOverride: Boolean get() = bookOverride != null
}

/**
 * Original layout leaves paragraph/font CSS to the EPUB publisher. Keep theme,
 * screen margins and text scaling, but clear overrides that might survive from
 * optimized layout and contradict the "original" label.
 */
internal fun ReaderTypographySettings.withPublisherLayout(original: Boolean): ReaderTypographySettings =
    if (!original) copy(publisherStyles = false)
    else copy(
        publisherStyles = true,
        fontFamily = ReaderFontFamily.BOOK,
        fontWeight = null,
        lineHeight = null,
        paragraphIndent = null,
        paragraphSpacing = null,
        letterSpacing = null,
        wordSpacing = null,
        textAlignment = null,
    )

private fun Double?.inRange(range: ClosedFloatingPointRange<Double>): Double? =
    this?.takeIf { it.isFinite() && it in range }

internal enum class ReaderFontFamily { BOOK, SERIF, SANS_SERIF, MONOSPACE }
internal enum class ReaderTextAlignment { START, JUSTIFY, LEFT, RIGHT }
internal enum class ReaderAppearance { SYSTEM, LIGHT, DARK, SEPIA }

/** Keep mapping out of Composables so opening a book and future live updates agree. */
@OptIn(ExperimentalReadiumApi::class)
internal fun ReaderTypographySettings.toEpubPreferences(systemDark: Boolean): EpubPreferences {
    val saved = normalized()
    return EpubPreferences(
        theme = when (saved.appearance) {
            ReaderAppearance.SYSTEM -> if (systemDark) Theme.DARK else Theme.LIGHT
            ReaderAppearance.LIGHT -> Theme.LIGHT
            ReaderAppearance.DARK -> Theme.DARK
            ReaderAppearance.SEPIA -> Theme.SEPIA
        },
        scroll = saved.scroll,
        fontFamily = when (saved.fontFamily) {
            ReaderFontFamily.BOOK -> null
            ReaderFontFamily.SERIF -> FontFamily.SERIF
            ReaderFontFamily.SANS_SERIF -> FontFamily.SANS_SERIF
            ReaderFontFamily.MONOSPACE -> FontFamily.MONOSPACE
        },
        fontSize = saved.fontSize,
        fontWeight = saved.fontWeight,
        lineHeight = saved.lineHeight,
        pageMargins = saved.pageMargins,
        paragraphIndent = saved.paragraphIndent,
        paragraphSpacing = saved.paragraphSpacing,
        letterSpacing = saved.letterSpacing,
        wordSpacing = saved.wordSpacing,
        textAlign = when (saved.textAlignment) {
            null -> null
            ReaderTextAlignment.START -> TextAlign.START
            ReaderTextAlignment.JUSTIFY -> TextAlign.JUSTIFY
            ReaderTextAlignment.LEFT -> TextAlign.LEFT
            ReaderTextAlignment.RIGHT -> TextAlign.RIGHT
        },
        publisherStyles = saved.publisherStyles,
        backgroundColor = saved.backgroundArgb?.let(::Color),
        textColor = saved.textArgb?.let(::Color),
    )
}
