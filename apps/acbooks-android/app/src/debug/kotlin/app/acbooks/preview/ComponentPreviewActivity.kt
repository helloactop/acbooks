package app.acbooks.preview

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.acbooks.R
import app.acbooks.core.datastore.AppearancePreferences
import app.acbooks.core.datastore.ThemeMode
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.components.*
import app.acbooks.core.designsystem.glass.*
import app.acbooks.core.designsystem.theme.AcbooksTheme
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

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

private val presetAccents = listOf("#5D92E8", "#00866A", "#B94870")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComponentPreview(appearance: AppearancePreferences, onTheme: (Boolean) -> Unit) {
    val theme by appearance.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
    val storedAccent by appearance.accent.collectAsStateWithLifecycle(null)
    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SideEffect { onTheme(dark) }
    val scope = rememberCoroutineScope()
    var hex by rememberSaveable { mutableStateOf("") }
    val enteredAccent = hex.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }
    var limit by rememberSaveable { mutableStateOf(GlassCapability.Refraction) }
    var reduceTransparency by rememberSaveable { mutableStateOf(false) }
    var eInk by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var bookmarked by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val accent = storedAccent?.let { Color(it.toColorInt()) } ?: DefaultAccent

    AcbooksTheme(dark, accent) {
        val colors = LocalAppColors.current
        AppGlassScene(
            Modifier.fillMaxSize(),
            reduceTransparency = reduceTransparency,
            eInk = eInk,
            capabilityLimit = limit,
            background = {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            0f to colors.background,
                            0.31f to colors.background,
                            0.32f to colors.accentSoft,
                            0.49f to colors.accentSoft,
                            0.50f to colors.background,
                            1f to colors.surfaceSecondary,
                        ),
                    ),
                )
            },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                val wide = maxWidth >= AppSizes.sheet
                Column(
                    Modifier.align(Alignment.TopCenter).widthIn(max = AppSizes.sheet).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(AppSpacing.page),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                ) {
                    AppTopBar(stringResource(R.string.preview_title))
                    AppSegmentedControl(
                        listOf(stringResource(R.string.preview_system), stringResource(R.string.preview_light), stringResource(R.string.preview_dark)),
                        theme.ordinal, { index -> scope.launch { appearance.setThemeMode(ThemeMode.entries[index]) } },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        listOf("" to stringResource(R.string.preview_system), "en" to "English", "zh-CN" to "简体中文")
                            .forEach { (tag, label) ->
                                AppButton(label, { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                                    kind = ButtonKind.Secondary)
                            }
                    }
                    AppTextField(
                        hex, { hex = it }, stringResource(R.string.preview_hex), clearable = true,
                        errorMessage = if (hex.isNotEmpty() && enteredAccent == null) stringResource(R.string.preview_invalid_hex) else null,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        presetAccents.forEach { preset ->
                            AppButton(preset, { scope.launch { appearance.setAccent(preset) } }, kind = ButtonKind.Secondary)
                        }
                        AppButton(stringResource(R.string.preview_apply),
                            { enteredAccent?.let { scope.launch { appearance.setAccent(it) } } }, enabled = enteredAccent != null)
                        AppButton(stringResource(R.string.preview_reset), {
                            hex = ""
                            scope.launch { appearance.setAccent(null) }
                        }, kind = ButtonKind.Secondary)
                    }
                    AppSegmentedControl(
                        listOf(stringResource(R.string.preview_solid), stringResource(R.string.preview_blur), stringResource(R.string.preview_auto)),
                        limit.ordinal, { limit = GlassCapability.entries[it] },
                    )
                    val capability = when (LocalGlassCapability.current) {
                        GlassCapability.Solid -> R.string.preview_solid
                        GlassCapability.Blur -> R.string.preview_blur
                        GlassCapability.Refraction -> R.string.preview_refraction
                    }
                    Text(stringResource(R.string.preview_capability, stringResource(capability)), color = colors.textPrimary)
                    PreviewSwitch(stringResource(R.string.preview_less_transparency), reduceTransparency) { reduceTransparency = it }
                    PreviewSwitch(stringResource(R.string.preview_eink), eInk) { eInk = it }
                    AppListItem(
                        title = stringResource(R.string.preview_content),
                        trailing = { AppProgress() },
                    )
                    AppSearchField(search, { search = it }, stringResource(R.string.preview_search))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        AppButton(stringResource(R.string.preview_dialog), { dialog = true })
                        AppButton(stringResource(R.string.preview_sheet), { sheet = true }, kind = ButtonKind.Secondary)
                        AppButton(stringResource(R.string.preview_danger), { dialog = true }, kind = ButtonKind.Destructive)
                        AppButton(stringResource(R.string.preview_disabled), {}, enabled = false)
                        AppButton(stringResource(R.string.preview_loading), {}, loading = true)
                        AppIconButton(
                            AppIcons.Favorite.forState(favorite),
                            stringResource(if (favorite) R.string.preview_remove_favorite else R.string.preview_add_favorite),
                            { favorite = !favorite },
                        )
                        AppIconButton(
                            AppIcons.Bookmark.forState(bookmarked),
                            stringResource(if (bookmarked) R.string.preview_remove_bookmark else R.string.preview_add_bookmark),
                            { bookmarked = !bookmarked },
                        )
                        AppMenu(menu, { menu = false }, listOf(
                            AppMenuItem(stringResource(R.string.preview_dialog), { dialog = true }),
                            AppMenuItem(stringResource(R.string.preview_sheet), { sheet = true }),
                        )) {
                            AppIconButton(AppIcons.More, stringResource(R.string.preview_menu), { menu = true })
                        }
                    }
                    val tabs = listOf(
                        AppNavItem(stringResource(R.string.preview_home), AppIcons.Home),
                        AppNavItem(stringResource(R.string.preview_library), AppIcons.Library),
                        AppNavItem(stringResource(R.string.preview_stats), AppIcons.Statistics),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        AppNavigationBar(tabs, activeTab, { activeTab = it }, Modifier.weight(1f), vertical = wide)
                        AppAddButton(stringResource(R.string.preview_add), { sheet = true })
                    }
                }
            }
            AppDialog(dialog, stringResource(R.string.preview_dialog), { dialog = false }) {
                Text(stringResource(R.string.preview_overlay_message))
                AppButton(stringResource(R.string.preview_close), { dialog = false })
            }
            AppSheet(sheet, stringResource(R.string.preview_sheet), { sheet = false }) {
                AppTextField(search, { search = it }, stringResource(R.string.preview_search))
                AppButton(stringResource(R.string.preview_close), { sheet = false })
            }
        }
    }
}

@Composable
private fun PreviewSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        AppSwitch(checked, onChange, label)
        Text(label, color = LocalAppColors.current.textPrimary)
    }
}
