package app.acbooks.reader

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import app.acbooks.R
import app.acbooks.library.LibraryViewModel
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse
import org.readium.r2.shared.util.http.HttpTry
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

@OptIn(ExperimentalReadiumApi::class)
internal class EpubDocumentFragment : Fragment() {
    private val library: LibraryViewModel by activityViewModels()
    private var publication: Publication? = null
    private var activeNavigator: EpubNavigatorFragment? = null
    private var chapters: List<ReaderChapter>? = null
    private var currentHref: String? = null

    // Readium's content tap callbacks preserve page-turn and selection gestures.
    internal var onCenterTap: (() -> Unit)? = null

    internal var onContentsChanged: ((List<ReaderChapter>?) -> Unit)? = null
        set(callback) {
            if (field === callback) return
            field = callback
            callback?.invoke(chapters)
        }

    internal var onCurrentHrefChanged: ((String?) -> Unit)? = null
        set(callback) {
            if (field === callback) return
            field = callback
            callback?.invoke(currentHref)
        }

    internal fun goToChapter(link: Link): Boolean =
        activeNavigator?.go(link, animated = false) ?: false

    private val theme: Theme
        get() = if (requireArguments().getBoolean(DARK)) Theme.DARK else Theme.LIGHT

    override fun onCreate(savedInstanceState: Bundle?) {
        childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FrameLayout(inflater.context).apply {
            setBackgroundColor(theme.backgroundColor)
            addView(FragmentContainerView(context).apply { id = R.id.reader_epub_container }, matchParent())
            addView(FrameLayout(context).apply {
                setBackgroundColor(theme.backgroundColor)
                isClickable = true
                addView(ProgressBar(context), centered())
            }, matchParent())
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // On process restoration Readium's dummy navigator must be removed before it resumes.
        removeNavigator()
        val root = view as FrameLayout
        val loading = root.getChildAt(1)
        val bookId = requireArguments().getString(BOOK_ID).orEmpty()
        val context = requireContext().applicationContext
        val owner = viewLifecycleOwner
        owner.lifecycleScope.launch {
            val source = library.openBook(bookId)?.takeIf { it.format == "epub" }
            val locator = source?.let { library.readingPosition(it.id) }?.let { saved ->
                runCatching { Locator.fromJSON(JSONObject(saved)) }.getOrNull()
            }
            val opened = try {
                source?.let { openPublication(context, it.file) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Log.w("Reader", "Could not open EPUB publication", failure)
                null
            }
            if (source == null || opened == null) {
                chapters = emptyList()
                onContentsChanged?.invoke(chapters)
                root.removeView(loading)
                root.addView(TextView(root.context).apply {
                    setText(R.string.reader_open_failed)
                    gravity = Gravity.CENTER
                }, matchParent())
                return@launch
            }
            publication = opened
            chapters = opened.readerChapters()
            onContentsChanged?.invoke(chapters)
            // Load DataStore before creating Readium to avoid a default-layout flash.
            val initialTypography = library.loadReaderTypographyProfile(bookId).effective
            val systemDark = theme == Theme.DARK
            val initialPreferences = initialTypography.toEpubPreferences(systemDark)
            // Match the loading surface to the selected reading theme, not just
            // the app's light/dark mode, to avoid a contrasting background flash.
            val pageBackground = initialPreferences.theme?.backgroundColor ?: theme.backgroundColor
            root.setBackgroundColor(pageBackground)
            loading.setBackgroundColor(pageBackground)
            val navigator = owner.lifecycle.withResumed {
                val factory = EpubNavigatorFactory(opened).createFragmentFactory(
                    initialLocator = locator,
                    initialPreferences = initialPreferences,
                    paginationListener = object : EpubNavigatorFragment.PaginationListener {
                        override fun onPageLoaded() {
                            if (loading.parent === root) root.removeView(loading)
                        }
                    },
                )
                val fragment = factory.instantiate(
                    requireContext().classLoader,
                    EpubNavigatorFragment::class.java.name,
                ) as EpubNavigatorFragment
                fragment.addInputListener(DirectionalNavigationAdapter(fragment, animatedTransition = false))
                fragment.addInputListener(object : InputListener {
                    override fun onTap(event: TapEvent): Boolean {
                        val width = fragment.publicationView.width
                        val height = fragment.publicationView.height
                        if (width <= 0 || height <= 0) return false
                        // Only central taps toggle the chrome; side taps keep turning pages.
                        if (event.point.x < width * 0.30f || event.point.x > width * 0.70f ||
                            event.point.y < height * 0.20f || event.point.y > height * 0.80f
                        ) return false
                        val callback = onCenterTap ?: return false
                        callback()
                        return true
                    }
                })
                childFragmentManager.commitNow {
                    setReorderingAllowed(true)
                    replace(R.id.reader_epub_container, fragment)
                }
                fragment
            }
            activeNavigator = navigator
            var lastPosition: Locator? = null
            var appliedTypography = initialTypography
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Updating the stored settings reflows the current publication without
                // recreating its Fragment or discarding the existing Readium locator.
                launch {
                    library.readerTypographyFor(bookId).collect { profile ->
                        val settings = profile.effective
                        if (settings != appliedTypography) {
                            appliedTypography = settings
                            val preferences = settings.toEpubPreferences(systemDark)
                            root.setBackgroundColor(preferences.theme?.backgroundColor ?: theme.backgroundColor)
                            navigator.submitPreferences(preferences)
                        }
                    }
                }
                navigator.currentLocator.collect { position ->
                    currentHref = position.href.toString()
                    onCurrentHrefChanged?.invoke(currentHref)
                    if (lastPosition != null && position != lastPosition) {
                        library.saveReadingPosition(
                            source.id,
                            position.toJSON().toString(),
                            position.locations.totalProgression?.times(100.0),
                        )
                    }
                    lastPosition = position
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeNavigator = null
        chapters = null
        currentHref = null
        onContentsChanged?.invoke(null)
        onCurrentHrefChanged?.invoke(null)
        onContentsChanged = null
        onCurrentHrefChanged = null
        onCenterTap = null
        val opened = publication
        publication = null
        try {
            removeNavigator()
        } finally {
            opened?.close()
        }
    }

    private fun removeNavigator() {
        val manager = childFragmentManager
        if (manager.isDestroyed) return
        val navigator = manager.findFragmentById(R.id.reader_epub_container) ?: return
        manager.commitNow(allowStateLoss = true) { remove(navigator) }
    }

    companion object {
        private const val BOOK_ID = "bookId"
        private const val DARK = "dark"

        fun arguments(bookId: String, dark: Boolean) = Bundle().apply {
            putString(BOOK_ID, bookId)
            putBoolean(DARK, dark)
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
private suspend fun openPublication(context: android.content.Context, file: File): Publication? {
    var unownedAsset: Asset? = null
    var unownedPublication: Publication? = null
    try {
        return withContext(Dispatchers.IO) {
            val retriever = AssetRetriever(context.contentResolver, OfflineHttpClient)
            val opener = PublicationOpener(DefaultPublicationParser(context, OfflineHttpClient, retriever, pdfFactory = null))
            val asset = retriever.retrieve(file.toUrl(isDirectory = false)).getOrNull() ?: return@withContext null
            unownedAsset = asset
            val publication = opener.open(asset, allowUserInteraction = false).getOrNull() ?: return@withContext null
            unownedPublication = publication
            unownedAsset = null
            publication.takeIf {
                !it.isRestricted && it.conformsTo(Publication.Profile.EPUB) && it.readingOrder.isNotEmpty()
            }
        }?.also { unownedPublication = null }
    } finally {
        // Cancellation can discard a successfully opened publication before it reaches the view.
        unownedPublication?.close()
        unownedAsset?.close()
    }
}

private fun Publication.readerChapters(): List<ReaderChapter> {
    val result = mutableListOf<ReaderChapter>()
    fun addLinks(links: List<Link>, depth: Int) {
        for (link in links) {
            result += ReaderChapter(link, link.title?.takeIf { it.isNotBlank() }, depth)
            addLinks(link.children, depth + 1)
        }
    }
    // Some EPUBs have no navigation document; use their reading order as a fallback.
    addLinks(tableOfContents.ifEmpty { readingOrder }, 0)
    return result
}

private object OfflineHttpClient : HttpClient {
    override suspend fun stream(request: HttpRequest): HttpTry<HttpStreamResponse> =
        Try.failure(HttpError.IO(IOException("Remote publication resources are disabled")))
}

private fun matchParent() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

private fun centered() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
