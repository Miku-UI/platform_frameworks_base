package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.tween
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ElementMatcher
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.UserActionDistance
import com.android.systemui.media.remedia.ui.compose.Media.Elements.mediaCarousel
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ui.composable.ShadeHeader
import kotlin.time.Duration.Companion.milliseconds

fun TransitionBuilder.shadeToQuickSettingsTransition(
    durationScale: Double = 1.0,
    animateQsTilesAsShared: () -> Boolean = { true },
) {
    spec = tween(durationMillis = (DefaultDuration * durationScale).inWholeMilliseconds.toInt())
    distance = UserActionDistance { fromContent, _, _ ->
        val distance =
            Notifications.Elements.NotificationScrim.targetOffset(Scenes.Shade)?.y
                ?: return@UserActionDistance 0f
        val fromContentSize = checkNotNull(fromContent.targetSize())
        fromContentSize.height - distance
    }

    translate(Notifications.Elements.NotificationScrim, Edge.Bottom)
    timestampRange(endMillis = 83) { fade(Elements.FooterActions) }

    fractionRange(start = 0.43f) { fade(Elements.QuickSettingsContent) }

    anchoredTranslate(Elements.QuickSettingsContent, Elements.GridAnchor)

    sharedElement(Elements.TileElementMatcher, enabled = animateQsTilesAsShared())

    // This will animate between 0f (QQS) and 0.5, fading in the QQS tiles when coming back
    // from non first page QS. The QS content ends fading out at 0.43f, so there's a brief
    // overlap, but because they are really faint, it looks better than complete black without
    // overlap.
    fractionRange(end = 0.5f) { fade(QqsTileElementMatcher) }
    anchoredTranslate(QqsTileElementMatcher, Elements.GridAnchor)
    fade(mediaCarousel)

    val translationY = ShadeHeader.Dimensions.CollapsedHeightForTransitions
    translate(ShadeHeader.Elements.CollapsedContentStart, y = translationY)
    translate(ShadeHeader.Elements.CollapsedContentEnd, y = translationY)
    translate(
        ShadeHeader.Elements.ExpandedContent,
        y = -(ShadeHeader.Dimensions.ExpandedHeight - translationY),
    )
    translate(ShadeHeader.Elements.ShadeCarrierGroup, y = -translationY)

    fractionRange(end = .14f) {
        fade(ShadeHeader.Elements.CollapsedContentStart)
        fade(ShadeHeader.Elements.CollapsedContentEnd)
    }

    fractionRange(start = .58f) {
        fade(ShadeHeader.Elements.ExpandedContent)
        fade(ShadeHeader.Elements.ShadeCarrierGroup)
    }
}

private val DefaultDuration = 500.milliseconds

private val QqsTileElementMatcher =
    object : ElementMatcher {
        override fun matches(key: ElementKey, content: ContentKey): Boolean {
            return content == Scenes.Shade && Elements.TileElementMatcher.matches(key, content)
        }
    }
