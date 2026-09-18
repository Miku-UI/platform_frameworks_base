/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.window.ui

import android.util.Log
import android.view.Choreographer
import android.view.View
import androidx.annotation.MainThread
import com.android.app.tracing.coroutines.TrackTracer
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.ui.view.WindowRootView
import com.android.systemui.statusbar.BlurUtils
import com.android.systemui.util.Assert
import com.android.systemui.window.logging.BlurLogger
import com.android.systemui.window.shared.model.BlurEffect
import dagger.Module
import dagger.Provides
import javax.inject.Named

typealias BlurAppliedListener = (BlurEffect) -> Unit

/**
 * Defines interface for classes that use [Choreographer] to make sure there is only one blur radius
 * change is applied per frame.
 */
interface BlurChoreographer {
    /** Apply the specific blur effect */
    fun applyBlur(blurEffect: BlurEffect)

    /**
     * Set/reset early wakeup flag on SurfaceFlinger, the system compositor. Setting true will
     * switch SurfaceFlinger to a longer work duration until it is reset here. This should be set to
     * true at the earliest point in the CUJ that will require non-zero cross-window blur, ideally
     * much before the first non-zero blur radius is applied using [applyBlur].
     *
     * This should be reset to false after the CUJ that requires blur has completed.
     */
    fun setPersistentEarlyWakeup(persistentEarlyWakeup: Boolean)

    /**
     * Register a listener that gets invoked after blur request is forwarded to the system
     * compositor for the current frame.
     */
    fun registerOnBlurAppliedListener(listener: BlurAppliedListener)

    /** Unregister listeners registered using [registerOnBlurAppliedListener] */
    fun clearOnBlurAppliedListener()
}

class NoopBlurChoreographer : BlurChoreographer {
    override fun applyBlur(blurEffect: BlurEffect) = Unit

    override fun setPersistentEarlyWakeup(persistentEarlyWakeup: Boolean) = Unit

    override fun registerOnBlurAppliedListener(listener: BlurAppliedListener) = Unit

    override fun clearOnBlurAppliedListener() = Unit
}

/** Implementation of [BlurChoreographer] that applies the background blur for the [rootView]. */
class DefaultBlurChoreographer(
    private val rootView: View,
    private val traceTag: String = "windowBlur",
    private val blurUtils: BlurUtils,
    private val choreographer: Choreographer,
    private val logger: BlurLogger,
) : BlurChoreographer {
    private var wasUpdateScheduledForThisFrame = false
    private var lastScheduledBlurEffect = BlurEffect(radius = 0f, scale = 1.0f)
    private var blurAppliedListener: BlurAppliedListener? = null

    private val newFrameCallback =
        Choreographer.FrameCallback {
            wasUpdateScheduledForThisFrame = false
            blurUtils.applyBlur(
                rootView.viewRootImpl,
                lastScheduledBlurEffect.radius.toInt(),
                false,
                lastScheduledBlurEffect.scale,
            )
            TrackTracer.instantForGroup(
                traceTag,
                "appliedBlurRadius",
                lastScheduledBlurEffect.radius.toInt(),
            )
            blurAppliedListener?.invoke(lastScheduledBlurEffect)
        }

    @MainThread
    override fun applyBlur(blurEffect: BlurEffect) {
        Assert.isMainThread()
        val newBlurRadius = blurEffect.radius.toInt()
        // Expectation is that we schedule only one frame callback per frame
        if (wasUpdateScheduledForThisFrame) {
            // Update this value so that the frame callback picks up this value when it runs
            if (lastScheduledBlurEffect != blurEffect) {
                Log.w(TAG, "Multiple blur values emitted in the same frame")
            }
            lastScheduledBlurEffect = blurEffect
            return
        } else if (lastScheduledBlurEffect == blurEffect) {
            logger.logSkipApplyBlur(
                blurEffect,
                wasUpdateScheduledForThisFrame,
                lastScheduledBlurEffect,
            )
            return
        }
        TrackTracer.instantForGroup(traceTag, "preparedBlurRadius", newBlurRadius)
        lastScheduledBlurEffect = blurEffect
        wasUpdateScheduledForThisFrame = true
        blurUtils.prepareBlur(newBlurRadius)
        choreographer.postFrameCallback(newFrameCallback)
    }

    override fun setPersistentEarlyWakeup(persistentEarlyWakeup: Boolean) {
        blurUtils.setPersistentEarlyWakeup(persistentEarlyWakeup, rootView.viewRootImpl)
    }

    override fun registerOnBlurAppliedListener(listener: BlurAppliedListener) {
        blurAppliedListener = listener
    }

    override fun clearOnBlurAppliedListener() {
        blurAppliedListener = null
    }

    private companion object {
        private const val TAG = "BlurChoreographer"
    }
}

@Module
object BlurChoreographerModule {
    @Provides
    @Named("ShadeWindowBlurChoreographer")
    @SysUISingleton
    fun providesBlurChoreographer(
        rootView: WindowRootView,
        blurUtils: BlurUtils,
        choreographer: Choreographer?,
        logger: BlurLogger,
    ): BlurChoreographer {
        return if (choreographer != null) {
            DefaultBlurChoreographer(rootView, "windowBlur", blurUtils, choreographer, logger)
        } else {
            NoopBlurChoreographer()
        }
    }
}
