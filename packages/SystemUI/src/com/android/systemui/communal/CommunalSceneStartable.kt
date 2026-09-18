/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.CoreStartable
import com.android.systemui.communal.domain.interactor.CommunalSceneInteractor
import com.android.systemui.communal.domain.interactor.CommunalSettingsInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.NotificationShadeWindowController
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/**
 * A [CoreStartable] responsible for automatically navigating between communal scenes when certain
 * conditions are met.
 */
@SysUISingleton
class CommunalSceneStartable
@Inject
constructor(
    private val communalSettingsInteractor: CommunalSettingsInteractor,
    private val communalSceneInteractor: CommunalSceneInteractor,
    private val notificationShadeWindowController: NotificationShadeWindowController,
    @Background private val bgScope: CoroutineScope,
    @Main private val mainDispatcher: CoroutineDispatcher,
) : CoreStartable {
    override fun start() {
        if (!communalSettingsInteractor.isCommunalFlagEnabled()) {
            return
        }

        bgScope.launch {
            communalSceneInteractor.isIdleOnCommunal.collectLatest {
                withContext(mainDispatcher) {
                    notificationShadeWindowController.setGlanceableHubShowing(it)
                }
            }
        }
    }
}
