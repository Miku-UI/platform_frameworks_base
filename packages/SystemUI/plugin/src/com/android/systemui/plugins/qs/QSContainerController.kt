package com.android.systemui.plugins.qs

interface QSContainerController {
    fun setCustomizerShowing(showing: Boolean, animationDuration: Long)

    fun setDetailShowing(showing: Boolean)
}
