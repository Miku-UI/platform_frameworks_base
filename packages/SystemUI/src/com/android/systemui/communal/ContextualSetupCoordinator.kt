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

package com.android.systemui.communal

import android.content.Context
import android.content.Intent
import android.util.Log
import com.android.systemui.CoreStartable
import com.android.systemui.Flags.uprightChargingDreamsSetup
import com.android.systemui.communal.domain.definition.SetupTarget
import com.android.systemui.communal.domain.interactor.ContextualSetupInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.plugins.ActivityStarter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A [CoreStartable] responsible for coordinating contextual setup flows.
 *
 * It listens for launch requests from [ContextualSetupInteractor] and launches the appropriate
 * activity or service to initiate the setup flow.
 */
@SysUISingleton
class ContextualSetupCoordinator
@Inject
constructor(
    @Application private val context: Context,
    private val activityStarter: ActivityStarter,
    private val contextualSetupInteractor: ContextualSetupInteractor,
    @Background private val bgScope: CoroutineScope,
) : CoreStartable {

    override fun start() {
        if (!uprightChargingDreamsSetup()) {
            return
        }
        bgScope.launch {
            contextualSetupInteractor.launchRequest.collect { definition ->
                val target = definition.target
                if (target == null) {
                    Log.w(TAG, "Launch request for ${definition.id} ignored: target is null.")
                    return@collect
                }
                Log.d(TAG, "Launch request for ${definition.id} with target $target.")
                launch(target)
            }
        }
    }

    private fun launch(target: SetupTarget) {
        try {
            val intent =
                Intent().apply {
                    component = target.componentName
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }

            when (target) {
                is SetupTarget.Activity -> {
                    activityStarter.startActivity(
                        intent,
                        /* dismissShade= */ false,
                        /* animationController= */ null,
                        /* showOverLockscreenWhenLocked= */ true,
                    )
                }
                is SetupTarget.Service -> {
                    // TODO: Implement service launching.
                    // 1. We need to ensure the service starts with the correct user (since SystemUI
                    // always runs as user0)
                    // 2. We probably need to define a strict lifecycle for the service. e.g. bind
                    // to
                    // it for a set amount of time, to ensure we don't leave services running.
                }
            }
        } catch (e: Exception) {
            // Catch all exceptions to prevent crashing SystemUI if the target is invalid
            // or cannot be started.
            Log.e(TAG, "Failed to launch contextual setup target: $target", e)
        }
    }

    companion object {
        private const val TAG = "ContextualSetupCoordinator"
    }
}
