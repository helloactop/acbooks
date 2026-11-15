package com.kyant.backdrop.catalog.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule

internal val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }
internal val LocalLiquidBottomTabIsReplica = staticCompositionLocalOf { false }

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    val selection = if (LocalLiquidBottomTabIsReplica.current) Modifier else Modifier.selectable(
        selected = selected,
        interactionSource = null,
        indication = null,
        role = Role.Tab,
        onClick = onClick
    )
    Column(
        modifier
            .clip(Capsule())
            .then(selection)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
