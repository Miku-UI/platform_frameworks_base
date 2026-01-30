package com.android.systemui.unfold

import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import java.util.concurrent.CopyOnWriteArrayList

class FakeUnfoldTransitionProvider : UnfoldTransitionProgressProvider, TransitionProgressListener {

    // CopyOnWriteArrayList to safely iterate while callbacks are added/removed, see b/412727194
    private val listeners = CopyOnWriteArrayList<TransitionProgressListener>()

    override fun destroy() {
        listeners.clear()
    }

    override fun addCallback(listener: TransitionProgressListener) {
        listeners.add(listener)
    }

    override fun removeCallback(listener: TransitionProgressListener) {
        listeners.remove(listener)
    }

    override fun onTransitionStarted() {
        listeners.forEach { it.onTransitionStarted() }
    }

    override fun onTransitionFinished() {
        listeners.forEach { it.onTransitionFinished() }
    }

    override fun onTransitionFinishing() {
        listeners.forEach { it.onTransitionFinishing() }
    }

    override fun onTransitionProgress(progress: Float) {
        listeners.forEach { it.onTransitionProgress(progress) }
    }
}
