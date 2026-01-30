package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.ui.unit.dp
import com.android.compose.animation.Easings
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.bouncer.ui.composable.Bouncer
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys

fun TransitionBuilder.lockscreenToBouncerTransition(durationScale: Double = 1.0) {
    lockscreenToOverlayTransition(durationScale = durationScale)
    toBouncerTransition()

    fractionRange(end = 0.3f, easing = Easings.PredictiveBack) {
        fade(LockscreenElementKeys.Region.Upper)
        fade(LockscreenElementKeys.LockIcon)
        fade(LockscreenElementKeys.AmbientIndicationArea)
        fade(LockscreenElementKeys.Region.Lower)
        fade(LockscreenElementKeys.SettingsMenu)
        fade(LockscreenElementKeys.BehindScrim)
        translate(LockscreenElementKeys.Region.Upper, y = (-48).dp)
        translate(LockscreenElementKeys.Notifications.Stack, y = (-72).dp)
    }
}

fun TransitionBuilder.bouncerToLockscreenPreview() {
    fractionRange(easing = Easings.PredictiveBack) {
        scaleDraw(Bouncer.Elements.Content, scaleY = 0.8f, scaleX = 0.8f)
    }
}
