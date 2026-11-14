package app.acbooks.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import app.acbooks.core.designsystem.R
import app.acbooks.core.designsystem.icons.AppIcons
import app.acbooks.core.designsystem.glass.AppMaterial
import app.acbooks.core.designsystem.glass.AppSurface
import app.acbooks.core.designsystem.theme.AppShapes
import app.acbooks.core.designsystem.theme.LocalAppColors
import app.acbooks.core.designsystem.tokens.AppSizes
import app.acbooks.core.designsystem.tokens.AppSpacing
import app.acbooks.core.designsystem.tokens.AppTypography

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    clearable: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = LocalAppColors.current
    var focused by remember { mutableStateOf(false) }
    val border = when {
        !enabled -> colors.divider
        errorMessage != null -> colors.destructiveText
        focused -> colors.accentBorder
        else -> colors.controlBorder
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(label, style = AppTypography.small, color = colors.textPrimary)
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            material = if (enabled) AppMaterial.Control else AppMaterial.Content,
            shape = AppShapes.medium,
            tint = colors.surfaceSecondary,
            border = border,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value, onValueChange,
                    Modifier.weight(1f).heightIn(min = AppSizes.touch)
                        .onFocusChanged { focused = it.isFocused }
                        .semantics {
                            contentDescription = label
                            if (errorMessage != null) error(errorMessage)
                        },
                    enabled = enabled, singleLine = true, keyboardOptions = keyboardOptions,
                    textStyle = AppTypography.body.copy(color = if (enabled) colors.textPrimary else colors.textSecondary),
                    cursorBrush = SolidColor(colors.accentText),
                    decorationBox = { field ->
                        androidx.compose.foundation.layout.Box(
                            Modifier.heightIn(min = AppSizes.touch).padding(horizontal = AppSpacing.md),
                            contentAlignment = Alignment.CenterStart,
                        ) { field() }
                    },
                )
                if (clearable && enabled && value.isNotEmpty()) {
                    AppIconButton(AppIcons.Clear, stringResource(R.string.app_clear), { onValueChange("") })
                }
            }
        }
        if (errorMessage != null) Text(errorMessage, style = AppTypography.caption, color = colors.destructiveText)
    }
}

@Composable
fun AppSearchField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    AppTextField(value, onValueChange, label, modifier, clearable = true,
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))
}
