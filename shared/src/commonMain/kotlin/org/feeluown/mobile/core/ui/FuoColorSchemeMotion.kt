package org.feeluown.mobile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private const val MIN_THEME_TRANSITION_CONTRAST_RATIO = 4.5
private const val CONTRAST_SEARCH_ITERATIONS = 14

@Composable
internal fun rememberAnimatedColorScheme(
    target: ColorScheme,
    labelPrefix: String,
): ColorScheme {
    // Performance: animate only the few colors that drive the perceived theme
    // transition. Animating every ColorScheme slot previously spawned 50+
    // concurrent color animations per theme/cover change, each producing
    // recomposition and redraw work on every frame.
    val animationSpec = remember(FuoMotion.themeColorTransitionMillis) {
        tween<Color>(durationMillis = FuoMotion.themeColorTransitionMillis)
    }

    val primary = animatedThemeColor(target.primary, animationSpec, "$labelPrefix primary")
    val surface = animatedThemeColor(target.surface, animationSpec, "$labelPrefix surface")
    val surfaceContainer = animatedThemeColor(
        target.surfaceContainer,
        animationSpec,
        "$labelPrefix surfaceContainer",
    )

    // Contrast-bearing colors must stay readable against their animated
    // background; everything else snaps directly to the target scheme.
    return target.copy(
        primary = primary,
        onPrimary = animatedContrastThemeColor(
            target.onPrimary,
            animationSpec,
            "$labelPrefix onPrimary",
            primary,
        ),
        onPrimaryContainer = animatedContrastThemeColor(
            target.onPrimaryContainer,
            animationSpec,
            "$labelPrefix onPrimaryContainer",
            target.primaryContainer,
        ),
        onSurface = animatedContrastThemeColor(
            target.onSurface,
            animationSpec,
            "$labelPrefix onSurface",
            surface,
        ),
        onSurfaceVariant = animatedContrastThemeColor(
            target.onSurfaceVariant,
            animationSpec,
            "$labelPrefix onSurfaceVariant",
            target.surfaceVariant,
        ),
        surface = surface,
        surfaceContainer = surfaceContainer,
        surfaceTint = surface,
    )
}

@Composable
private fun animatedContrastThemeColor(
    target: Color,
    animationSpec: FiniteAnimationSpec<Color>,
    label: String,
    background: Color,
): Color = animatedContrastThemeColor(
    target = target,
    animationSpec = animationSpec,
    label = label,
    backgrounds = listOf(background),
)

@Composable
private fun animatedContrastThemeColor(
    target: Color,
    animationSpec: FiniteAnimationSpec<Color>,
    label: String,
    backgrounds: List<Color>,
): Color {
    val animated = animatedThemeColor(target, animationSpec, label)
    return ensureThemeContrast(animated, backgrounds)
}

@Composable
private fun animatedThemeColor(
    target: Color,
    animationSpec: FiniteAnimationSpec<Color>,
    label: String,
): Color {
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = animationSpec,
        label = label,
    )
    return animated
}

internal fun ensureThemeContrast(
    foreground: Color,
    backgrounds: List<Color>,
    minimumRatio: Double = MIN_THEME_TRANSITION_CONTRAST_RATIO,
): Color {
    if (backgrounds.isEmpty() || backgrounds.all { colorContrastRatio(foreground, it) >= minimumRatio }) {
        return foreground
    }

    fun minimumContrast(candidate: Color): Double = backgrounds.minOf {
        colorContrastRatio(candidate, it)
    }

    val blackContrast = minimumContrast(Color.Black)
    val whiteContrast = minimumContrast(Color.White)
    val anchor = if (blackContrast >= whiteContrast) Color.Black else Color.White
    val anchorContrast = maxOf(blackContrast, whiteContrast)
    if (anchorContrast < minimumRatio) {
        return anchor
    }

    var low = 0f
    var high = 1f
    repeat(CONTRAST_SEARCH_ITERATIONS) {
        val fraction = (low + high) / 2f
        if (minimumContrast(lerp(foreground, anchor, fraction)) >= minimumRatio) {
            high = fraction
        } else {
            low = fraction
        }
    }
    return lerp(foreground, anchor, high)
}
