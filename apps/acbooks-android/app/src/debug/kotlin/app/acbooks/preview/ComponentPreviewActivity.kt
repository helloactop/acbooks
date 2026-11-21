package app.acbooks.preview

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.R
import app.acbooks.core.datastore.AppearancePreferences
import app.acbooks.core.datastore.ThemeMode
import app.acbooks.core.designsystem.R as UiR
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.BookOutline
import top.yukonga.miuix.kmp.icon.glass.BookmarkFill
import top.yukonga.miuix.kmp.icon.glass.BookmarkOutline
import top.yukonga.miuix.kmp.icon.glass.DigitalStatistics
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.FavoriteFill
import top.yukonga.miuix.kmp.icon.glass.FavoriteOutline
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.window.WindowListPopup

@AndroidEntryPoint
class ComponentPreviewActivity : AppCompatActivity() {
    @Inject lateinit var appearance: AppearancePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComponentPreview(appearance) { dark ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComponentPreview(appearance: AppearancePreferences, onTheme: (Boolean) -> Unit) {
    val mode by appearance.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val accent by appearance.accent.collectAsStateWithLifecycle(null)
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SideEffect { onTheme(dark) }
    val theme = remember(dark, accent) {
        val scheme = when {
            accent == null -> if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light
            dark -> ColorSchemeMode.MonetDark
            else -> ColorSchemeMode.MonetLight
        }
        ThemeController(colorSchemeMode = scheme, keyColor = accent?.let { Color(it.toColorInt()) })
    }
    val scope = rememberCoroutineScope()
    var hex by rememberSaveable { mutableStateOf("") }
    val enteredAccent = hex.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var bookmarked by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val backdrop = rememberLayerBackdrop()

    MiuixTheme(theme) {
        Scaffold(
            topBar = { SmallTopAppBar(title = stringResource(R.string.preview_title)) },
            bottomBar = {
                GlassNavigationBar(
                    items = listOf(
                        GlassNavigationItem(MiuixIcons.Glass.HomeOutline, stringResource(UiR.string.app_dock_home)),
                        GlassNavigationItem(MiuixIcons.Glass.BookOutline, stringResource(UiR.string.app_dock_library)),
                        GlassNavigationItem(MiuixIcons.Glass.DigitalStatistics, stringResource(UiR.string.app_dock_statistics)),
                    ),
                    selectedIndex = activeTab,
                    onSelect = { activeTab = it },
                    backdrop = backdrop,
                    trailingAction = {
                        GlassFloatingActionButton(onClick = { sheet = true }, backdrop = backdrop) {
                            Icon(MiuixIcons.Glass.Add, stringResource(UiR.string.app_dock_add))
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).layerBackdrop(backdrop)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row {
                    IconButton(onClick = { dialog = true }, minWidth = 48.dp, minHeight = 48.dp) {
                        Icon(MiuixIcons.Glass.Edit, stringResource(UiR.string.app_library_edit))
                    }
                    Box {
                        IconButton(onClick = { menu = true }, minWidth = 48.dp, minHeight = 48.dp) {
                            Icon(MiuixIcons.Glass.More, stringResource(UiR.string.app_library_more))
                        }
                        WindowListPopup(show = menu, onDismissRequest = { menu = false }) {
                            ListPopupColumn {
                                BasicComponent(
                                    title = stringResource(R.string.preview_dialog),
                                    onClick = { menu = false; dialog = true },
                                )
                                BasicComponent(
                                    title = stringResource(R.string.preview_sheet),
                                    onClick = { menu = false; sheet = true },
                                )
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf(R.string.preview_system, R.string.preview_light, R.string.preview_dark)
                    ThemeMode.entries.forEachIndexed { index, entry ->
                        TextButton(
                            stringResource(labels[index]),
                            { scope.launch { appearance.setThemeMode(entry) } },
                            colors = if (entry == mode) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to stringResource(R.string.preview_system), "en" to "English", "zh-CN" to "简体中文")
                        .forEach { (tag, label) ->
                            TextButton(label, { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) })
                        }
                }
                TextField(hex, { hex = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.preview_hex), singleLine = true)
                if (hex.isNotBlank() && enteredAccent == null) {
                    Text(stringResource(R.string.preview_invalid_hex), color = MiuixTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#5D92E8", "#00866A", "#B94870").forEach { preset ->
                        TextButton(preset, { scope.launch { appearance.setAccent(preset) } })
                    }
                    TextButton(
                        stringResource(R.string.preview_apply),
                        { enteredAccent?.let { scope.launch { appearance.setAccent(it) } } },
                        enabled = enteredAccent != null,
                    )
                    TextButton(stringResource(R.string.preview_reset), {
                        hex = ""
                        scope.launch { appearance.setAccent(null) }
                    })
                }
                Card {
                    BasicComponent(title = stringResource(R.string.preview_loading), endActions = { CircularProgressIndicator() })
                }
                TextField(search, { search = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.preview_search), singleLine = true)
                Row {
                    IconButton(onClick = { favorite = !favorite }, minWidth = 48.dp, minHeight = 48.dp) {
                        Icon(
                            if (favorite) MiuixIcons.Glass.FavoriteFill else MiuixIcons.Glass.FavoriteOutline,
                            stringResource(if (favorite) R.string.preview_remove_favorite else R.string.preview_add_favorite),
                        )
                    }
                    IconButton(onClick = { bookmarked = !bookmarked }, minWidth = 48.dp, minHeight = 48.dp) {
                        Icon(
                            if (bookmarked) MiuixIcons.Glass.BookmarkFill else MiuixIcons.Glass.BookmarkOutline,
                            stringResource(if (bookmarked) R.string.preview_remove_bookmark else R.string.preview_add_bookmark),
                        )
                    }
                    TextButton(stringResource(R.string.preview_disabled), {}, enabled = false)
                }
            }
            GlassDialog(show = dialog, title = stringResource(R.string.preview_dialog), backdrop = backdrop, onDismissRequest = { dialog = false }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(stringResource(R.string.preview_close), { dialog = false }, Modifier.weight(1f))
                    TextButton(
                        stringResource(R.string.preview_confirm), { dialog = false }, Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
            GlassBottomSheet(show = sheet, title = stringResource(R.string.preview_sheet), backdrop = backdrop, onDismissRequest = { sheet = false }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(search, { search = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.preview_search), singleLine = true)
                    TextButton(stringResource(R.string.preview_close), { sheet = false }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
