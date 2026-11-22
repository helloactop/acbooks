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
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
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
                root.removeView(loading)
                root.addView(TextView(root.context).apply {
                    setText(R.string.reader_open_failed)
                    gravity = Gravity.CENTER
                }, matchParent())
                return@launch
            }
            publication = opened
            val navigator = owner.lifecycle.withResumed {
                val factory = EpubNavigatorFactory(opened).createFragmentFactory(
                    initialLocator = locator,
                    initialPreferences = EpubPreferences(theme = theme, scroll = false),
                    paginationListener = object : EpubNavigatorFragment.PaginationListener {
                        override fun onPageLoaded() {
                            root.removeView(loading)
                        }
                    },
                )
                val fragment = factory.instantiate(
                    requireContext().classLoader,
                    EpubNavigatorFragment::class.java.name,
                ) as EpubNavigatorFragment
                fragment.addInputListener(DirectionalNavigationAdapter(fragment, animatedTransition = false))
                childFragmentManager.commitNow {
                    setReorderingAllowed(true)
                    replace(R.id.reader_epub_container, fragment)
                }
                fragment
            }
            var lastPosition = locator
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect { position ->
                    if (position != lastPosition) {
                        lastPosition = position
                        library.saveReadingPosition(source.id, position.toJSON().toString())
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
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

private object OfflineHttpClient : HttpClient {
    override suspend fun stream(request: HttpRequest): HttpTry<HttpStreamResponse> =
        Try.failure(HttpError.IO(IOException("Remote publication resources are disabled")))
}

private fun matchParent() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

private fun centered() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
