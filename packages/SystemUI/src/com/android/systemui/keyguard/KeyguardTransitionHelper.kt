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

package com.android.systemui.keyguard

import android.app.WindowConfiguration
import android.os.IBinder
import android.service.dreams.Flags
import android.util.Log
import android.util.RotationUtils
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import com.android.systemui.animation.RemoteTransitionHelper
import com.android.wm.shell.shared.CounterRotator

/**
 * RemoteTransitionHelper that initializes changes with particular attention to Keyguard
 * requirements.
 */
class KeyguardTransitionHelper : RemoteTransitionHelper {
    companion object {
        private const val TAG = "DefaultTransitionHelper"
    }

    private val wallpaperRotators = mutableMapOf<IBinder, CounterRotator>()

    override fun setUpAnimation(
        token: IBinder,
        info: TransitionInfo,
        transaction: SurfaceControl.Transaction,
        finishCallback: IRemoteTransitionFinishedCallback?,
    ) {
        info.changes.forEach { change ->
            // Make sure the wallpaper is rotated correctly.
            if ((change.flags and TransitionInfo.FLAG_IS_WALLPAPER) != 0) {
                val rotationDelta =
                    RotationUtils.deltaRotation(change.startRotation, change.endRotation)
                val wallpaperParent = change?.parent
                if (
                    wallpaperParent != null &&
                        rotationDelta != 0 &&
                        change.mode == WindowManager.TRANSIT_TO_BACK
                ) {
                    val parent = info.getChange(wallpaperParent)
                    if (parent != null) {
                        val rotator = CounterRotator()
                        rotator.setup(
                            transaction,
                            parent.leash,
                            rotationDelta,
                            parent.endAbsBounds.width().toFloat(),
                            parent.endAbsBounds.height().toFloat(),
                        )
                        transaction.setLayer(rotator.surface, -1)
                        rotator.addChild(transaction, change.leash)
                        wallpaperRotators[token] = rotator
                    } else {
                        Log.e(
                            TAG,
                            "Malformed: $change has parent=$wallpaperParent, which is not part " +
                                "of the transition info=$info.",
                        )
                    }
                }
            }

            if (RemoteTransitionHelper.OPENING_MODES.contains(change.mode)) {
                transaction.setAlpha(change.leash, 0f)
            } else if (TransitionInfo.isIndependent(change, info)) {
                // Set alpha back to 1 for the independent changes because we will be animating
                // children instead.
                transaction.setAlpha(change.leash, 1f)
            }

            // If the keyguard is going away, hide the dream if one exists.
            if (
                Flags.dismissDreamOnKeyguardDismiss() &&
                    (change.flags and WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY) != 0 &&
                    change.taskInfo?.activityType == WindowConfiguration.ACTIVITY_TYPE_DREAM &&
                    RemoteTransitionHelper.CLOSING_MODES.contains(change.mode)
            ) {
                transaction.hide(change.leash)
            }
        }

        transaction.apply()
    }

    override fun cleanUpAnimation(token: IBinder, transaction: SurfaceControl.Transaction) {
        wallpaperRotators.remove(token)?.cleanUp(transaction)
        transaction.apply()
    }
}
