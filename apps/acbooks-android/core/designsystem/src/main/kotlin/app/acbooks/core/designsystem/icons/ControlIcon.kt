package app.acbooks.core.designsystem.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import app.acbooks.core.designsystem.tokens.AppSizes

@Composable
fun ControlIcon(
    icon: AppIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        icon.controlVector,
        contentDescription,
        modifier.size(if (small) AppSizes.iconSmall else AppSizes.icon),
        tint,
    )
}

internal fun controlWeight(vector: ImageVector): ImageVector {
    val extraWidth = minOf(vector.viewportWidth, vector.viewportHeight) * (0.3f / 24f)
    return ImageVector.Builder(
        name = "${vector.name}.Control",
        defaultWidth = vector.defaultWidth,
        defaultHeight = vector.defaultHeight,
        viewportWidth = vector.viewportWidth,
        viewportHeight = vector.viewportHeight,
        tintColor = vector.tintColor,
        tintBlendMode = vector.tintBlendMode,
        autoMirror = vector.autoMirror,
    ).apply { appendGroup(vector.root, extraWidth) }.build()
}

private fun ImageVector.Builder.appendGroup(group: VectorGroup, extraWidth: Float) {
    addGroup(
        name = group.name,
        rotate = group.rotation,
        pivotX = group.pivotX,
        pivotY = group.pivotY,
        scaleX = group.scaleX,
        scaleY = group.scaleY,
        translationX = group.translationX,
        translationY = group.translationY,
        clipPathData = group.clipPathData,
    )
    for (node in group) {
        when (node) {
            is VectorGroup -> appendGroup(node, extraWidth)
            is VectorPath -> addPath(
                pathData = node.pathData,
                pathFillType = node.pathFillType,
                name = node.name,
                fill = node.fill,
                fillAlpha = node.fillAlpha,
                stroke = node.stroke ?: node.fill,
                strokeAlpha = if (node.stroke == null) node.fillAlpha else node.strokeAlpha,
                strokeLineWidth = node.strokeLineWidth + extraWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = node.strokeLineMiter,
                trimPathStart = node.trimPathStart,
                trimPathEnd = node.trimPathEnd,
                trimPathOffset = node.trimPathOffset,
            )
        }
    }
    clearGroup()
}
