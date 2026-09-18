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

package com.android.systemui.communal.widgets

import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.phone.centralSurfacesOptional

val Kosmos.communalTransitionAnimatorControllerFactory by
    Kosmos.Fixture<CommunalTransitionAnimatorController.Factory> {
        object : CommunalTransitionAnimatorController.Factory {
            override fun create(
                delegate: ActivityTransitionAnimator.Controller
            ): CommunalTransitionAnimatorController {
                return CommunalTransitionAnimatorController(
                    delegate,
                    centralSurfacesOptional,
                    communalSceneInteractor,
                )
            }
        }
    }
