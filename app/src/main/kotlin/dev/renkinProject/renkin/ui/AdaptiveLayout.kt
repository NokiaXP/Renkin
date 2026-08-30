package dev.renkinProject.renkin.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.flow.collect

@Immutable
internal data class VerticalHingeBounds(
    val start: Dp,
    val end: Dp
)

@Immutable
internal data class AdaptiveLayoutInfo(
    val windowWidth: Dp,
    val separatingVerticalHinge: VerticalHingeBounds?
)

@Immutable
internal data class HorizontalPaneLayout(
    val leadingWidth: Dp,
    val separatorWidth: Dp,
    val trailingWidth: Dp,
    val avoidsHinge: Boolean
)

@Composable
internal fun AdaptivePaneSeparator(layout: HorizontalPaneLayout) {
    if (layout.avoidsHinge) {
        Spacer(Modifier.width(layout.separatorWidth).fillMaxHeight())
    } else {
        VerticalDivider(Modifier.width(layout.separatorWidth))
    }
}

internal fun horizontalPaneLayout(
    availableWidth: Dp,
    preferredLeadingWidth: Dp,
    minimumLeadingWidth: Dp,
    minimumTrailingWidth: Dp,
    separatingVerticalHinge: VerticalHingeBounds? = null,
    dividerWidth: Dp = 1.dp
): HorizontalPaneLayout? {
    val hinge = separatingVerticalHinge
        ?.takeIf { it.start >= 0.dp && it.end >= it.start && it.end <= availableWidth }
    if (hinge != null) {
        val trailingWidth = availableWidth - hinge.end
        if (hinge.start < minimumLeadingWidth || trailingWidth < minimumTrailingWidth) {
            return null
        }
        return HorizontalPaneLayout(
            leadingWidth = hinge.start,
            separatorWidth = hinge.end - hinge.start,
            trailingWidth = trailingWidth,
            avoidsHinge = true
        )
    }

    val contentWidth = availableWidth - dividerWidth
    if (contentWidth < minimumLeadingWidth + minimumTrailingWidth) return null

    val maximumLeadingWidth = minOf(
        preferredLeadingWidth,
        contentWidth - minimumTrailingWidth
    )
    val leadingWidth = (contentWidth * 0.4f).coerceIn(
        minimumLeadingWidth,
        maximumLeadingWidth
    )
    return HorizontalPaneLayout(
        leadingWidth = leadingWidth,
        separatorWidth = dividerWidth,
        trailingWidth = contentWidth - leadingWidth,
        avoidsHinge = false
    )
}

internal val LocalAdaptiveLayoutInfo = staticCompositionLocalOf {
    AdaptiveLayoutInfo(windowWidth = 0.dp, separatingVerticalHinge = null)
}

@Composable
internal fun ProvideAdaptiveLayoutInfo(
    activity: ComponentActivity,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val windowWidth = remember(activity, configuration, density) {
        with(density) {
            WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(activity)
                .bounds
                .width()
                .toDp()
        }
    }
    val foldingFeature by produceState<FoldingFeature?>(null, activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .collect { layoutInfo ->
                value = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull { feature ->
                        feature.orientation == FoldingFeature.Orientation.VERTICAL &&
                            feature.isSeparating
                    }
            }
    }
    val hinge = foldingFeature?.bounds?.let { bounds ->
        with(density) {
            VerticalHingeBounds(bounds.left.toDp(), bounds.right.toDp())
        }
    }
    val info = AdaptiveLayoutInfo(
        windowWidth = windowWidth,
        separatingVerticalHinge = hinge
    )
    CompositionLocalProvider(LocalAdaptiveLayoutInfo provides info, content = content)
}
