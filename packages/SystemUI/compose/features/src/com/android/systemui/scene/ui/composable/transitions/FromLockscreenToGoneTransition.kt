package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import com.android.compose.animation.Easings
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.scene.shared.model.Scenes

fun TransitionBuilder.lockscreenToGoneTransition() {
    spec = tween(durationMillis = 500)

    fractionRange(end = 0.3f, easing = Easings.PredictiveBack) {
        // Fade out all lock screen elements, except the status bar.
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
