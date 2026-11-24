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
                   <head><style>html { font-size: 20px; } h1 { text-align: center !important; margin-top: 80px !important; font-size: 20px !important; } #wrapper { padding-top: 70px !important; min-height: 200px !important; } #poem h2 { text-align: center; font-size: 19px; } #nested { display:block; text-align:center; font-size:0.5em; } #empty { margin-top:100px; margin-bottom:100px; } #picture { text-indent:2em; text-align:left; } #image { width:900px; }</style>
                   <style>$css</style></head><body><div id="empty" data-acbooks-spacer="true"></div><section id="wrapper" data-acbooks-leading="true"><h1 id="title" data-acbooks-leading="true"><span id="nested">Chapter</span></h1><p id="number" data-acbooks-heading="number">第二章</p><p id="semantic" data-acbooks-heading="chapter" style="text-align:center;font-size:20px">Semantic title</p><p>Text</p></section><div id="picture" data-acbooks-image-block="true"><img id="image" data-acbooks-image="true" width="900" height="600" src="data:image/svg+xml,%3Csvg%20xmlns=%27http://www.w3.org/2000/svg%27%20width=%27900%27%20height=%27600%27/%3E" /></div><section id="poem" data-acbooks-preserve="true"><h2 id="verse">Poem</h2></section></body></html>""",
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
                "\"0px|0px|center|19px\"",
                evaluate(webView, """(() => { const w = getComputedStyle(document.getElementById('wrapper')); const p = getComputedStyle(document.getElementById('verse')); return [w.paddingTop,w.minHeight,p.textAlign,p.fontSize].join('|'); })()"""),
            )
            assertEquals(
                "\"start|33px|0px|0px|center|0px|block|100%\"",
                evaluate(webView, """(() => { const n = getComputedStyle(document.getElementById('nested')); const e = getComputedStyle(document.getElementById('empty')); const p = getComputedStyle(document.getElementById('picture')); const i = getComputedStyle(document.getElementById('image')); return [n.textAlign,n.fontSize,e.marginTop,e.marginBottom,p.textAlign,p.textIndent,i.display,i.maxWidth].join('|'); })()"""),
            )
            assertEquals(
                "\"center|80px|20px\"",
                evaluate(webView, """(() => { document.documentElement.style.removeProperty('--USER__advancedSettings'); const s = getComputedStyle(document.getElementById('title')); return [s.textAlign,s.marginTop,s.fontSize].join('|'); })()"""),
            )
            assertEquals(
                "\"center|20px\"",
                evaluate(webView, """(() => { const s = getComputedStyle(document.getElementById('semantic')); return [s.textAlign,s.fontSize].join('|'); })()"""),
            )
            assertEquals(
                "\"70px|200px\"",
                evaluate(webView, """(() => { const w = getComputedStyle(document.getElementById('wrapper')); return [w.paddingTop,w.minHeight].join('|'); })()"""),
            )
            assertEquals(
                "\"center|10px|100px|100px|left|40px\"",
                evaluate(webView, """(() => { const n = getComputedStyle(document.getElementById('nested')); const e = getComputedStyle(document.getElementById('empty')); const p = getComputedStyle(document.getElementById('picture')); return [n.textAlign,n.fontSize,e.marginTop,e.marginBottom,p.textAlign,p.textIndent].join('|'); })()"""),
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
