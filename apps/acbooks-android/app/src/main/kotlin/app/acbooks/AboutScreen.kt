package app.acbooks

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SOURCE_URL = "https://github.com/helloactop/acbooks"
private const val DEPENDENCIES_URL = "${SOURCE_URL}/blob/main/LICENSE.md"
private const val LICENSE_URL = "${SOURCE_URL}/blob/main/LICENSE"
private const val FEEDBACK_URL = "${SOURCE_URL}/issues"

private fun Modifier.aboutFade(
    scrollProgress: () -> Float,
    start: Float,
    end: Float,
): Modifier = graphicsLayer {
    val progress = ((scrollProgress() - start) / (end - start)).coerceIn(0f, 1f)
    alpha = 1f - progress
    scaleX = 1f - progress * 0.05f
    scaleY = 1f - progress * 0.05f
}

@Composable
internal fun AboutScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberLayerBackdrop()
    val direction = LocalLayoutDirection.current
    val scrollDistance = with(LocalDensity.current) { 300.dp.toPx() }
    // Keep frequently changing scroll state in graphicsLayer, not composition.
    val scrollProgress: () -> Float = {
        if (listState.firstVisibleItemIndex > 0) 1f
        else (listState.firstVisibleItemScrollOffset / scrollDistance).coerceIn(0f, 1f)
    }

    val appIcon = remember(context) {
        val drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        bitmap.asImageBitmap()
    }
    val appVersion = remember(context) {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
        "v${info.versionName ?: "?"} (${info.longVersionCode})"
    }
    val colors = MiuixTheme.colorScheme

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                GlassTopAppBar(
                    title = "",
                    expandable = false,
                    progressiveBlur = true,
                    scrollBehavior = scrollBehavior,
                    backdrop = backdrop,
                    navigationIcon = {
                        GlassIconButton(onClick = onBack) {
                            Icon(MiuixIcons.Glass.Back, stringResource(R.string.navigate_back))
                        }
                    },
                )
                // The title emerges as the large hero disappears, as in Mishka.
                Text(
                    text = stringResource(R.string.about_title),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(56.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .graphicsLayer {
                            alpha = ((scrollProgress() - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        },
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(colors.surface)
                .padding(
                    start = padding.calculateStartPadding(direction),
                    end = padding.calculateEndPadding(direction),
                )
                .consumeWindowInsets(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Pure Compose gradient: graceful fallback without runtime shaders.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .graphicsLayer { alpha = 1f - scrollProgress() }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(colors.primary.copy(alpha = 0.24f), Color.Transparent),
                        ),
                    ),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 12.dp,
                ),
            ) {
                item(key = "hero") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(108.dp)
                                .aboutFade(scrollProgress, 0.36f, 0.60f),
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .aboutFade(scrollProgress, 0.20f, 0.44f),
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 35.sp,
                        )
                        Text(
                            text = appVersion,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .aboutFade(scrollProgress, 0.05f, 0.25f),
                            color = colors.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                        )
                    }
                }
                groupedCardItems(
                    groupKey = "about-resources",
                    title = R.string.about_resources,
                    rows = listOf(
                        SettingsCardItem("source") {
                            ArrowPreference(
                                title = stringResource(R.string.about_source),
                                summary = "github.com/helloactop/acbooks",
                                titleColor = BasicComponentDefaults.titleColor(color = LocalContentColor.current),
                                onClick = { onOpenUrl(SOURCE_URL) },
                            )
                        },
                        SettingsCardItem("dependencies") {
                            ArrowPreference(
                                title = stringResource(R.string.about_dependencies),
                                summary = stringResource(R.string.about_dependencies_summary),
                                titleColor = BasicComponentDefaults.titleColor(color = LocalContentColor.current),
                                onClick = { onOpenUrl(DEPENDENCIES_URL) },
                            )
                        },
                        SettingsCardItem("license") {
                            ArrowPreference(
                                title = stringResource(R.string.about_license),
                                summary = "GPL-3.0-only",
                                titleColor = BasicComponentDefaults.titleColor(color = LocalContentColor.current),
                                onClick = { onOpenUrl(LICENSE_URL) },
                            )
                        },
                        SettingsCardItem("feedback") {
                            ArrowPreference(
                                title = stringResource(R.string.about_feedback),
                                summary = "GitHub Issues",
                                titleColor = BasicComponentDefaults.titleColor(color = LocalContentColor.current),
                                onClick = { onOpenUrl(FEEDBACK_URL) },
                            )
                        },
                    ),
                )
            }
        }
    }
}
