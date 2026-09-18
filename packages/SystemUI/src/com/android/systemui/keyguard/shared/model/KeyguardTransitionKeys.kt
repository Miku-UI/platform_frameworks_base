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

package com.android.systemui.keyguard.shared.model

import com.android.compose.animation.scene.TransitionKey

/** Named transitions for special case keyguard scene transitions. */
object KeyguardTransitionKeys {

    /**
     * This transition involves an animation over the lockscreen (notification launch, occlusion,
     * etc). In this case, we'll leave the lockscreen UI visible so that animation can play over it.
     */
    val WithAnimationOverLockscreen = TransitionKey("WithNotificationLaunchAnimation")

    val AodToGoneTransition = TransitionKey("AodToGoneTransition")
}
