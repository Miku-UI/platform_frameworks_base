package com.android.systemui.scene.ui.composable.transitions

import androidx.compose.ui.unit.dp
import com.android.compose.animation.Easings
import com.android.compose.animation.scene.TransitionBuilder
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.scene.ui.viewmodel.ToBouncerTransitionViewModel

fun TransitionBuilder.lockscreenToBouncerTransition(
    durationScale: Double = 1.0,
    toBouncerTransitionViewModel: ToBouncerTransitionViewModel,
) {
    lockscreenFadeOutTransition(durationScale = durationScale)
    toBouncerTransition(viewModel = toBouncerTransitionViewModel)

    fractionRange(end = 0.3f, easing = Easings.PredictiveBack) {
        fade(LockscreenElementKeys.Region.Upper)
        fade(LockscreenElementKeys.LockIcon)
        fade(LockscreenElementKeys.AmbientIndicationArea)
        fade(LockscreenElementKeys.Region.Lower)
        fade(LockscreenElementKeys.SettingsMenu)
        translate(LockscreenElementKeys.Region.Upper, y = (-48).dp)
        translate(LockscreenElementKeys.Notifications.Stack, y = (-72).dp)
    }
}
