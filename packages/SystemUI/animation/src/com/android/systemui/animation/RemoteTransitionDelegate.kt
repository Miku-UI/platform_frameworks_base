/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.animation

import android.os.IBinder
import android.view.SurfaceControl
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import android.window.WindowAnimationState

/**
 * A component capable of running remote transitions
 *
 * It expands [IRemoteTransition] API to accommodate specialized callbacks extending
 * [IRemoteTransitionFinishedCallback] callback API.
 */
interface RemoteTransitionDelegate<in T : IRemoteTransitionFinishedCallback> {

    fun startAnimation(
        transition: IBinder?,
        info: TransitionInfo?,
        transaction: SurfaceControl.Transaction?,
        finishedCallback: T?,
    )

    fun mergeAnimation(
        transition: IBinder?,
        info: TransitionInfo?,
        transaction: SurfaceControl.Transaction?,
        mergeTarget: IBinder?,
        finishedCallback: T?,
    ) {}

    fun takeOverAnimation(
        transition: IBinder?,
        info: TransitionInfo?,
        transaction: SurfaceControl.Transaction?,
        finishedCallback: T?,
        windowStates: Array<out WindowAnimationState>?,
    ) {}

    fun onTransitionConsumed(transition: IBinder?, aborted: Boolean) {}
}
