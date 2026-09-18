package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ElementMatcher
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.UserActionDistance
import com.android.compose.animation.scene.transformation.offsetSharedElementWithAnchor
import com.android.systemui.media.remedia.ui.compose.Media.Elements.MediaCarousel
import com.android.systemui.notifications.ui.composable.Notifications
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.shared.ui.QuickSettings.SHARED_TILE_PICKER_THRESHOLD
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

    fractionRange(start = 0.43f, end = 1f - SHARED_TILE_PICKER_THRESHOLD) {
        fade(Elements.QuickSettingsContent)
    }

    anchoredTranslate(Elements.QuickSettingsContent, Elements.GridAnchor)

    sharedElement(Elements.TileElementMatcher, enabled = animateQsTilesAsShared())

    // This will animate between 0f (QQS) and 0.5, fading in the QQS tiles when coming back
    // from non first page QS. The QS content ends fading out at 0.43f, so there's a brief
    // overlap, but because they are really faint, it looks better than complete black without
    // overlap.
    fractionRange(end = 0.5f) { fade(QqsTileElementMatcher) }
    anchoredTranslate(QqsTileElementMatcher, Elements.GridAnchor)
    fade(MediaCarousel)

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

fun TransitionBuilder.quickSettingsToShadeTransition(
    durationScale: Double = 1.0,
    animateQsTilesAsShared: () -> Boolean = { true },
) {
    reversed {
        shadeToQuickSettingsTransition(
            durationScale = durationScale,
            animateQsTilesAsShared = animateQsTilesAsShared,
        )
    }
    // Translate the HeadsUpNotificationPlaceholder from the StackPlaceholder's start position to
    // its target. This ensures the HUN is "dragged" up by the Stack once the Stack's top edge
    // reaches the HUN's top.
    offsetSharedElementWithAnchor(
        matcher = Notifications.Elements.HeadsUpNotificationPlaceholder,
        anchor = Notifications.Elements.StackPlaceholder,
        offset = { from, to, fromAnchor, _ ->
            val animatedOffset = lerp(fromAnchor, to, transition.progress)
            Offset(animatedOffset.x, minOf(from.y, animatedOffset.y))
        },
    )
}

private val DefaultDuration = 500.milliseconds

private val QqsTileElementMatcher =
    object : ElementMatcher {
        override fun matches(key: ElementKey, content: ContentKey): Boolean {
            return content == Scenes.Shade && Elements.TileElementMatcher.matches(key, content)
        }
    }
