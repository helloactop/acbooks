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
    val isOptimizedLayout: Boolean get() = publisherStyles == false

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
 * First-use EPUB layout, styled after the clean, dense Chinese reading experience
 * popularized by WeChat Reading. This is an acbooks approximation, not a copy of
 * WeChat Reading's proprietary styles.
 *
 * Readium fontSize and pageMargins are relative multipliers, NOT px or dp.
 * 1.3 is about 130% of the engine's base type size; the actual rendered size
 * depends on the publication and the device.
 */
internal fun defaultReaderTypographySettings(): ReaderTypographySettings =
    ReaderTypographySettings(
        fontFamily = ReaderFontFamily.SANS_SERIF,
        fontSize = 1.3,
        lineHeight = 1.5,
        pageMargins = 1.0,
        paragraphIndent = 2.0,
        paragraphSpacing = 0.0,
        letterSpacing = 0.0,
        textAlignment = ReaderTextAlignment.JUSTIFY,
        publisherStyles = false,
        scroll = false,
        appearance = ReaderAppearance.SYSTEM,
    )

/**
 * A complete per-book snapshot overrides the global profile. A missing override
 * inherits global changes; creating one copies the currently effective values.
 */
internal data class ReaderTypographyProfile(
    val global: ReaderTypographySettings = defaultReaderTypographySettings(),
    val bookOverride: ReaderTypographySettings? = null,
) {
    val effective: ReaderTypographySettings get() = bookOverride ?: global
    val hasBookOverride: Boolean get() = bookOverride != null
}

/**
 * Store the user's optimized typography even while the publisher layout is
 * active. The old implementation erased these fields on every mode switch.
 */
internal fun ReaderTypographySettings.withPublisherLayout(original: Boolean): ReaderTypographySettings =
    if (original) copy(publisherStyles = true)
    else withOptimizedDefaults().copy(publisherStyles = false)

/** Old versions cleared fields on switching to original layout. Recover the
 * default optimized values for missing fields; never overwrite a saved value.
 */
private fun ReaderTypographySettings.withOptimizedDefaults(): ReaderTypographySettings {
    val defaults = defaultReaderTypographySettings()
    return copy(
        fontSize = fontSize ?: defaults.fontSize,
        lineHeight = lineHeight ?: defaults.lineHeight,
        pageMargins = pageMargins ?: defaults.pageMargins,
        paragraphIndent = paragraphIndent ?: defaults.paragraphIndent,
        paragraphSpacing = paragraphSpacing ?: defaults.paragraphSpacing,
        letterSpacing = letterSpacing ?: defaults.letterSpacing,
        textAlignment = textAlignment ?: defaults.textAlignment,
    )
}

/** Only the runtime preferences are masked. Never persist this projection. */
internal fun ReaderTypographySettings.forRendering(): ReaderTypographySettings =
    if (isOptimizedLayout) normalized().withOptimizedDefaults()
    else normalized().copy(
        publisherStyles = true,
        fontFamily = ReaderFontFamily.BOOK,
        fontSize = null,
        fontWeight = null,
        lineHeight = null,
        pageMargins = null,
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
    val saved = forRendering()
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
