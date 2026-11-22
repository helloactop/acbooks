package app.acbooks.library

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.acbooks.R
import app.acbooks.core.database.LocalBookCard
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.BookOutline
import top.yukonga.miuix.kmp.icon.glass.ClockOutline
import top.yukonga.miuix.kmp.icon.glass.Date
import top.yukonga.miuix.kmp.icon.glass.DigitalStatistics
import top.yukonga.miuix.kmp.icon.glass.ExpandLess
import top.yukonga.miuix.kmp.icon.glass.ExpandMore
import top.yukonga.miuix.kmp.icon.glass.FrequentlyUsed
import top.yukonga.miuix.kmp.icon.glass.Info
import top.yukonga.miuix.kmp.icon.glass.Read
import top.yukonga.miuix.kmp.icon.glass.TimeLapse
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BookDescriptionCard(description: String?, expanded: Boolean, onToggle: () -> Unit) {
    DetailsCard(stringResource(R.string.book_details_description), MiuixIcons.Glass.BookOutline) {
        if (description.isNullOrBlank()) {
            Text(stringResource(R.string.book_details_no_description), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            val style = MiuixTheme.textStyles.body1
            val measurer = rememberTextMeasurer()
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val expandable = measurer.measure(
                    text = description,
                    style = style,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 4,
                    constraints = Constraints(maxWidth = constraints.maxWidth),
                ).didOverflowHeight
                val open = expandable && expanded
                val toggleLabel = stringResource(if (open) R.string.book_details_collapse_description else R.string.book_details_expand_description)
                Column(
                    Modifier.fillMaxWidth().animateContentSize().then(
                        if (expandable) Modifier.clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
                        else Modifier,
                    ),
                ) {
                    Text(
                        text = description,
                        style = style,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = if (open) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (expandable) {
                        Icon(
                            imageVector = if (open) MiuixIcons.Glass.ExpandLess else MiuixIcons.Glass.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.align(Alignment.End).padding(top = 6.dp).size(18.dp),
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BookBibliographyCard(book: LocalBookCard, details: BookDetailsData) {
    val metadata = details.metadata
    val fields = listOf(
        R.string.book_details_book_name to book.title,
        R.string.book_details_author to book.author,
        R.string.book_details_translator to metadata?.translators?.joinToString(", "),
        R.string.book_details_isbn to metadata?.isbn,
        R.string.book_details_publisher to metadata?.publisher,
        R.string.book_details_publish_date to metadata?.publicationDate,
        R.string.book_details_language to metadata?.languages?.joinToString(", "),
        R.string.book_details_category to metadata?.categories?.joinToString(", "),
        R.string.book_details_status to when (details.readingStatus) {
            BookReadingStatus.Unread -> stringResource(R.string.book_details_status_unread)
            BookReadingStatus.Reading -> stringResource(R.string.book_details_status_reading)
            BookReadingStatus.Finished -> stringResource(R.string.book_details_status_finished)
            null -> null
        },
    )
    DetailsCard(stringResource(R.string.book_details_bibliography), MiuixIcons.Glass.Info) {
        InformationRows(fields)
    }
}

@Composable
internal fun BookLibraryCard(book: LocalBookCard, details: BookDetailsData) {
    val formats = details.formats.ifEmpty { listOfNotNull(book.format.takeIf(String::isNotBlank)) }
    DetailsCard(
        stringResource(R.string.book_details_library),
        ImageVector.vectorResource(R.drawable.ic_book_details_shelf),
    ) {
        if (details.shelves.isNotEmpty()) {
            InformationRow(stringResource(R.string.book_details_shelves), details.shelves.joinToString(", "))
            HorizontalDivider()
        }
        if (details.tags.isNotEmpty()) {
            InformationRow(stringResource(R.string.book_details_tags)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    details.tags.forEach { tag ->
                        Text(tag, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.body2)
                    }
                }
            }
            HorizontalDivider()
        }
        InformationRows(
            listOf(
                R.string.book_details_import_date to bookDate(book.importedAt, includeTime = true),
                R.string.book_details_formats to formats.joinToString(", ") { it.uppercase(Locale.ROOT) },
            ),
        )
    }
}

@Composable
internal fun BookReadingStatisticsCard(statistics: BookReadingStatistics?) {
    DetailsCard(stringResource(R.string.book_details_statistics), MiuixIcons.Glass.DigitalStatistics) {
        if (statistics == null) {
            Text(stringResource(R.string.book_details_no_statistics), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
            val minutes = statistics.activeSeconds / 60
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingMetric(
                        MiuixIcons.Glass.ClockOutline,
                        stringResource(R.string.book_details_duration),
                        stringResource(R.string.book_details_hours_minutes, numbers.format(minutes / 60), numbers.format(minutes % 60)),
                        Modifier.weight(1f),
                    )
                    ReadingMetric(
                        MiuixIcons.Glass.Read,
                        stringResource(R.string.book_details_session_count),
                        numbers.format(statistics.sessionCount),
                        Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReadingMetric(
                        MiuixIcons.Glass.Date,
                        stringResource(R.string.book_details_day_count),
                        numbers.format(statistics.readingDays),
                        Modifier.weight(1f),
                    )
                    ReadingMetric(
                        MiuixIcons.Glass.FrequentlyUsed,
                        stringResource(R.string.book_details_last_read),
                        bookDate(statistics.lastReadAt),
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BookReadingProgressCard(location: BookReadingLocation?) {
    val position = when (location) {
        is BookReadingLocation.Epub -> {
            val locator = location.locator
            locator.title?.takeIf(String::isNotBlank)
                ?: locator.locations.position?.let { stringResource(R.string.book_details_epub_position, it) }
                ?: locator.href.toString()
        }
        is BookReadingLocation.Pdf -> {
            val count = location.pageCount
            if (count != null && location.page in 1..count) {
                stringResource(R.string.reader_page, location.page, count)
            } else {
                stringResource(R.string.book_details_pdf_page, location.page)
            }
        }
        null -> null
    }
    DetailsCard(stringResource(R.string.book_details_progress), MiuixIcons.Glass.TimeLapse) {
        val fraction = location?.fraction
        if (fraction == null) {
            Text(stringResource(R.string.book_details_no_progress), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            ReadingProgressBar(fraction)
        }
        if (!position.isNullOrBlank()) {
            InformationRow(stringResource(R.string.book_details_last_position), position)
        }
    }
}

@Composable
internal fun ReadingProgressBar(fraction: Float, showLabel: Boolean = false) {
    val colors = MiuixTheme.colorScheme
    val percent = NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0]).format(fraction)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showLabel) {
                Text(
                    stringResource(R.string.book_details_progress),
                    modifier = Modifier.weight(1f),
                    color = colors.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            Text(
                percent,
                modifier = if (showLabel) Modifier else Modifier.fillMaxWidth(),
                color = colors.onSurface,
                style = MiuixTheme.textStyles.footnote1,
                textAlign = TextAlign.End,
            )
        }
        LinearProgressIndicator(progress = { fraction })
    }
}

@Composable
private fun DetailsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurface)
            Text(title, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
}

@Composable
private fun InformationRows(fields: List<Pair<Int, String?>>) {
    fields.filterNot { (_, value) -> value.isNullOrBlank() }.forEachIndexed { index, (label, value) ->
        if (index > 0) HorizontalDivider()
        InformationRow(stringResource(label), value.orEmpty())
    }
}

@Composable
private fun InformationRow(label: String, value: String) {
    InformationRow(label) {
        Text(
            value,
            modifier = Modifier.fillMaxWidth(),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body2,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun InformationRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            modifier = Modifier.weight(0.36f),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        Column(Modifier.weight(0.64f), horizontalAlignment = Alignment.End) { value() }
    }
}

@Composable
private fun ReadingMetric(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.onSurface)
        Text(value, color = colors.onSurface, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.SemiBold)
        Text(label, color = colors.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
    }
}

@Composable
private fun bookDate(epochMillis: Long, includeTime: Boolean = false): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = if (includeTime) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    else DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    return formatter.format(Date(epochMillis))
}
