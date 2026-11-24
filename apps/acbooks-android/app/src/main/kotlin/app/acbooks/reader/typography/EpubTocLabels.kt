package app.acbooks.reader.typography

import java.text.Normalizer

/** Shared navigation-label rules for the contents sheet and semantic typography. */
internal object EpubTocLabels {
    private val pageLeader = Regex("""(?:[.．]{3,}|…+|·{3,})\s*[0-9０-９]+\s*$""")
    private val noteLabel = Regex("""^(?:\[\s*[0-9]+\s*]|【\s*[0-9]+\s*】)$""")
    private val subsectionPrefix = Regex("""^\(\s*[零〇一二三四五六七八九十百千万两0-9]+\s*\)\s*\S""")

    fun displayTitle(title: String?): String? =
        title?.trim()?.replace(pageLeader, "")?.trim()?.takeIf { it.isNotEmpty() }

    fun isNoteLabel(title: String?): Boolean =
        title != null && noteLabel.matches(Normalizer.normalize(title, Normalizer.Form.NFKC))

    fun isNumberedSubsection(title: String?): Boolean =
        title != null && subsectionPrefix.containsMatchIn(Normalizer.normalize(title, Normalizer.Form.NFKC))
}
