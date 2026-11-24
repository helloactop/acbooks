package app.acbooks.reader

import app.acbooks.reader.typography.EpubTocLabels

internal data class ReaderContentsEntry(
    val href: String,
    val title: String?,
    val depth: Int,
    val hasChildren: Boolean = false,
)

internal data class ReaderContentsItem(val sourceIndex: Int, val entry: ReaderContentsEntry)

/**
 * Repair conservative, resource-local flat TOCs for display only. Keep the
 * source indices so every row still navigates with its original Readium Link.
 */
internal fun normalizeReaderContents(entries: List<ReaderContentsEntry>): List<ReaderContentsItem> {
    val result = mutableListOf<ReaderContentsItem>()
    var root: ReaderContentsEntry? = null
    for ((index, raw) in entries.withIndex()) {
        val entry = raw.copy(title = EpubTocLabels.displayTitle(raw.title))
        val resource = entry.href.substringBefore('#')
        val anchored = entry.href.substringAfter('#', "").isNotEmpty()
        val parent = root
        val sameResource = parent != null && parent.href.substringBefore('#') == resource
        // Legacy NCX exports sometimes put numeric note backlinks directly
        // after the chapter entry. Do not hide standalone or nested TOCs
        // merely because they contain a number.
        if (anchored && sameResource && parent != null &&
            entry.depth == parent.depth && !entry.hasChildren &&
            EpubTocLabels.isNoteLabel(entry.title)
        ) continue

        val depth = if (anchored && sameResource && parent != null &&
            entry.depth == parent.depth && !parent.hasChildren &&
            EpubTocLabels.isNumberedSubsection(entry.title)
        ) entry.depth + 1 else entry.depth
        result.add(ReaderContentsItem(index, entry.copy(depth = depth)))
        if (!anchored && entry.title != null && !EpubTocLabels.isNoteLabel(entry.title)) {
            root = entry
        } else if (!sameResource || (parent != null && entry.depth < parent.depth)) {
            root = null
        }
    }
    return result
}

/** At most one row is selected. If Readium reports only the resource, select
 * its chapter rather than guessing which anchored subsection is visible. */
internal fun selectedReaderContentsIndex(hrefs: List<String>, currentHref: String?): Int {
    if (currentHref == null) return -1
    val resource = currentHref.substringBefore('#')
    val fragment = currentHref.substringAfter('#', "")
    if (fragment.isNotEmpty()) {
        val exact = hrefs.indexOf(currentHref)
        if (exact >= 0) return exact
    }
    val root = hrefs.indexOf(resource)
    if (root >= 0) return root
    val candidates = hrefs.indices.filter { hrefs[it].substringBefore('#') == resource }
    return candidates.singleOrNull() ?: -1
}
