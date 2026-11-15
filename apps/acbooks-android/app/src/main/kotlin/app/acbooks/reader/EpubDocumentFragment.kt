package app.acbooks.reader

import android.os.Bundle
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
import app.acbooks.R
import app.acbooks.library.LibraryViewModel
import java.io.File
import java.io.IOException
import kotlinx.coroutines.launch
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

    override fun onCreate(savedInstanceState: Bundle?) {
        childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FrameLayout(inflater.context).apply {
            addView(FragmentContainerView(context).apply { id = R.id.reader_epub_container }, matchParent())
            addView(ProgressBar(context), centered())
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val root = view as FrameLayout
        val loading = root.getChildAt(1)
        val bookId = requireArguments().getString(BOOK_ID).orEmpty()
        val dark = requireArguments().getBoolean(DARK)
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val source = library.openBook(bookId)?.takeIf { it.format == "epub" }
            val opened = source?.let { openPublication(context, it.file) }
            if (source == null || opened == null) {
                root.removeView(loading)
                root.addView(TextView(root.context).apply {
                    setText(R.string.reader_open_failed)
                    gravity = Gravity.CENTER
                }, matchParent())
                return@launch
            }
            publication = opened
            val locator = library.readingPosition(source.id)?.let { saved ->
                runCatching { Locator.fromJSON(JSONObject(saved)) }.getOrNull()
            }
            val factory = EpubNavigatorFactory(opened).createFragmentFactory(
                initialLocator = locator,
                initialPreferences = EpubPreferences(theme = if (dark) Theme.DARK else Theme.LIGHT, scroll = false),
            )
            val navigator = factory.instantiate(
                requireContext().classLoader,
                EpubNavigatorFragment::class.java.name,
            ) as EpubNavigatorFragment
            navigator.addInputListener(DirectionalNavigationAdapter(navigator, animatedTransition = true))
            childFragmentManager.commitNow(allowStateLoss = true) {
                setReorderingAllowed(true)
                replace(R.id.reader_epub_container, navigator)
            }
            root.removeView(loading)
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect { position ->
                    library.saveReadingPosition(source.id, position.toJSON().toString())
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        publication?.close()
        publication = null
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
    val retriever = AssetRetriever(context.contentResolver, OfflineHttpClient)
    val opener = PublicationOpener(DefaultPublicationParser(context, OfflineHttpClient, retriever, pdfFactory = null))
    val asset = retriever.retrieve(file.toUrl(isDirectory = false)).getOrNull() ?: return null
    val publication = opener.open(asset, allowUserInteraction = false).getOrNull() ?: run {
        asset.close()
        return null
    }
    if (publication.isRestricted || !publication.conformsTo(Publication.Profile.EPUB)) {
        publication.close()
        return null
    }
    return publication
}

private object OfflineHttpClient : HttpClient {
    override suspend fun stream(request: HttpRequest): HttpTry<HttpStreamResponse> =
        Try.failure(HttpError.IO(IOException("Remote publication resources are disabled")))
}

private fun matchParent() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

private fun centered() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
