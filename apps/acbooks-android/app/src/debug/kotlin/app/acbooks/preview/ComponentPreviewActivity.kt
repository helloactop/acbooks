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
            val updateSystemBars: (Boolean) -> Unit = { dark ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            if (intent.getBooleanExtra("buttons", false)) {
                val dark = intent.getBooleanExtra("dark", false)
                val limit = when (intent.getStringExtra("glass")) {
                    "solid" -> GlassCapability.Solid
                    "blur" -> GlassCapability.Blur
                    else -> GlassCapability.Refraction
                }
                SideEffect { updateSystemBars(dark) }
                ButtonPreview(dark, limit)
            } else {
                ComponentPreview(appearance, updateSystemBars)
            }
        }
    }
}

@Composable
private fun ButtonPreview(dark: Boolean, capability: GlassCapability) {
    AcbooksTheme(dark, if (dark) Color(0xFF36C2A5) else DefaultAccent) {
        val colors = LocalAppColors.current
        AppGlassScene(
            Modifier.fillMaxSize(),
            capabilityLimit = capability,
            background = {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(
                    0f to colors.background,
                    0.30f to colors.background,
                    0.31f to colors.accentSoft,
                    0.46f to colors.accentSoft,
                    0.47f to colors.background,
                    0.56f to colors.background,
                    0.57f to colors.accentSoft,
                    1f to colors.background,
                )))
            },
        ) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(AppSpacing.page),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg),
            ) {
                Text("Liquid Glass buttons", style = AppTypography.title, color = colors.textPrimary)
                Text(LocalGlassCapability.current.name, style = AppTypography.small, color = colors.textSecondary)
                for (sizes in IconButtonSize.entries.chunked(3)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (size in sizes) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                            ) {
                                Box(Modifier.height(IconButtonSize.Dock.diameter), contentAlignment = Alignment.Center) {
                                    LiquidGlassIconButton(AppIcons.Add, size.name, {}, size = size)
                                }
                                Text(size.name, style = AppTypography.subhead, color = colors.textPrimary)
                                Text(
                                    "${size.diameter.value.toInt()} / ${size.icon.value.toInt()} dp",
                                    style = AppTypography.caption, color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
                LiquidGlassButton("Compact · 32 dp", {}, size = TextButtonSize.Compact)
                LiquidGlassButton("Primary · 44 dp", {}, Modifier.fillMaxWidth(), kind = ButtonKind.Primary)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonSizeTokens.dialogGap),
                ) {
                    LiquidGlassButton("Cancel", {}, Modifier.weight(1f), size = TextButtonSize.Dialog)
                    LiquidGlassButton(
                        "Delete", {}, Modifier.weight(1f), size = TextButtonSize.Dialog, kind = ButtonKind.Destructive,
                    )
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
    var libraryFilter by rememberSaveable { mutableIntStateOf(0) }
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
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Column(
                    Modifier.align(Alignment.TopCenter).widthIn(max = AppSizes.sheet).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(AppSpacing.page)
                        .padding(bottom = AppSizes.navigation + AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                ) {
                    AppTopBar(stringResource(R.string.preview_title))
                    val filters = listOf(stringResource(R.string.preview_library_all), "EPUB", "PDF")
                    AppLibraryHeader(
                        stringResource(R.string.library_title), 0,
                        onEdit = { dialog = true },
                        moreItems = listOf(AppMenuItem(stringResource(R.string.preview_dialog), { dialog = true })),
                    )
                    AppLibraryTools(
                        filterLabel = filters[libraryFilter],
                        filterItems = filters.mapIndexed { index, label -> AppMenuItem(label, { libraryFilter = index }) },
                        onSearch = { sheet = true },
                        onCategories = { sheet = true },
                        modifier = Modifier.padding(horizontal = AppSpacing.page),
                    )
                    AppSegmentedControl(
                        listOf(stringResource(R.string.preview_system), stringResource(R.string.preview_light), stringResource(R.string.preview_dark)),
                        theme.ordinal, { index -> scope.launch { appearance.setThemeMode(ThemeMode.entries[index]) } },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        listOf("" to stringResource(R.string.preview_system), "en" to "English", "zh-CN" to "简体中文")
                            .forEach { (tag, label) ->
                                LiquidGlassButton(label, { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                                    kind = ButtonKind.Secondary)
                            }
                    }
                    AppTextField(
                        hex, { hex = it }, stringResource(R.string.preview_hex), clearable = true,
                        errorMessage = if (hex.isNotEmpty() && enteredAccent == null) stringResource(R.string.preview_invalid_hex) else null,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        presetAccents.forEach { preset ->
                            LiquidGlassButton(preset, { scope.launch { appearance.setAccent(preset) } }, kind = ButtonKind.Secondary)
                        }
                        LiquidGlassButton(stringResource(R.string.preview_apply),
                            { enteredAccent?.let { scope.launch { appearance.setAccent(it) } } }, enabled = enteredAccent != null)
                        LiquidGlassButton(stringResource(R.string.preview_reset), {
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
                        LiquidGlassButton(stringResource(R.string.preview_dialog), { dialog = true })
                        LiquidGlassButton(stringResource(R.string.preview_sheet), { sheet = true }, kind = ButtonKind.Secondary)
                        LiquidGlassButton(stringResource(R.string.preview_danger), { dialog = true }, kind = ButtonKind.Destructive)
                        LiquidGlassButton(stringResource(R.string.preview_disabled), {}, enabled = false)
                        LiquidGlassButton(stringResource(R.string.preview_loading), {}, loading = true)
                        LiquidGlassIconButton(
                            AppIcons.Favorite.forState(favorite),
                            stringResource(if (favorite) R.string.preview_remove_favorite else R.string.preview_add_favorite),
                            { favorite = !favorite },
                        )
                        LiquidGlassIconButton(
                            AppIcons.Bookmark.forState(bookmarked),
                            stringResource(if (bookmarked) R.string.preview_remove_bookmark else R.string.preview_add_bookmark),
                            { bookmarked = !bookmarked },
                        )
                        AppMenu(menu, { menu = false }, listOf(
                            AppMenuItem(stringResource(R.string.preview_dialog), { dialog = true }),
                            AppMenuItem(stringResource(R.string.preview_sheet), { sheet = true }),
                        )) {
                            LiquidGlassIconButton(AppIcons.More, stringResource(R.string.preview_menu), { menu = true })
                        }
                    }
                }
                AppBottomDock(
                    selected = activeTab,
                    onSelect = { activeTab = it },
                    onAdd = { sheet = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .widthIn(max = AppSizes.sheet).fillMaxWidth()
                        .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                )
            }
            AppDialog(
                dialog,
                stringResource(R.string.preview_dialog),
                { dialog = false },
                actions = listOf(
                    AppDialogAction(stringResource(R.string.preview_confirm), AppDialogActionRole.Primary) { dialog = false },
                    AppDialogAction(stringResource(R.string.preview_close), AppDialogActionRole.Cancel) { dialog = false },
                ),
            ) {
                Text(stringResource(R.string.preview_overlay_message))
            }
            AppSheet(
                sheet,
                stringResource(R.string.preview_sheet),
                { sheet = false },
                actions = listOf(
                    AppDialogAction(stringResource(R.string.preview_close), AppDialogActionRole.Cancel) { sheet = false },
                ),
            ) {
                AppTextField(search, { search = it }, stringResource(R.string.preview_search))
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
