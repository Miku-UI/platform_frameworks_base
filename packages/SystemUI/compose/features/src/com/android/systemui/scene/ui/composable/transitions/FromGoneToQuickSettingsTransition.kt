package com.android.systemui.scene.ui.composable.transitions

import com.android.compose.animation.scene.TransitionBuilder

fun TransitionBuilder.goneToQuickSettingsTransition(durationScale: Double = 1.0) {
    toQuickSettingsSceneTransition(durationScale = durationScale)
}
