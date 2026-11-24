package app.acbooks.reader

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import app.acbooks.reader.typography.DeepEpubTypography
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercise the actual bundled stylesheet in Android's rendering engine. */
class DeepEpubTypographyWebViewTest {
    @Test
    fun advancedLayoutStylesHeadingAndOriginalLayoutRestoresIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val css = context.assets.open(DeepEpubTypography.ASSET_PATH).bufferedReader().use { it.readText() }
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded.countDown()
                }
            }
            webView.loadDataWithBaseURL(
                null,
                """<!DOCTYPE html><html dir="ltr" style="--USER__advancedSettings: readium-advanced-on">
                   <head><style>html { font-size: 20px; } h1 { text-align: center; margin-top: 80px; font-size: 20px; }</style>
                   <style>$css</style></head><body><section><h1 id="title">Chapter</h1><p id="number" data-acbooks-heading="number">第二章</p><p id="semantic" data-acbooks-heading="chapter" style="text-align:center;font-size:20px">Semantic title</p><p>Text</p></section></body></html>""",
                "text/html", "UTF-8", null,
            )
        }
        try {
            assertTrue("WebView did not load", loaded.await(15, TimeUnit.SECONDS))
            assertEquals(
                "\"start|0px|33px\"",
                evaluate(webView, """(() => { const s = getComputedStyle(document.getElementById('title')); return [s.textAlign,s.marginTop,s.fontSize].join('|'); })()"""),
            )
            assertEquals(
                "\"start|33px|17px\"",
                evaluate(webView, """(() => { const s = getComputedStyle(document.getElementById('semantic')); const n = getComputedStyle(document.getElementById('number')); return [s.textAlign,s.fontSize,n.fontSize].join('|'); })()"""),
            )
            assertEquals(
                "\"center|80px|20px\"",
                evaluate(webView, """(() => { document.documentElement.style.removeProperty('--USER__advancedSettings'); const s = getComputedStyle(document.getElementById('title')); return [s.textAlign,s.marginTop,s.fontSize].join('|'); })()"""),
            )
            assertEquals(
                "\"center|20px\"",
                evaluate(webView, """(() => { const s = getComputedStyle(document.getElementById('semantic')); return [s.textAlign,s.fontSize].join('|'); })()"""),
            )
        } finally {
            instrumentation.runOnMainSync { webView.destroy() }
        }
    }

    private fun evaluate(webView: WebView, script: String): String? {
        val completed = CountDownLatch(1)
        var result: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(script) {
                result = it
                completed.countDown()
            }
        }
        assertTrue("JavaScript did not complete", completed.await(15, TimeUnit.SECONDS))
        return result
    }
}
