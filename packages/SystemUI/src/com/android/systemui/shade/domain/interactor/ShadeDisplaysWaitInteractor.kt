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

package com.android.systemui.shade.domain.interactor

import com.android.app.tracing.coroutines.TrackTracer
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.common.ui.view.ChoreographerUtils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.ui.view.WindowRootView
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.notification.row.NotificationRebindingTracker
import javax.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/** Provides a way to wait for specific events related to the shade window. */
@SysUISingleton
class ShadeDisplaysWaitInteractor
@Inject
constructor(
    private val shadeInteractor: ShadeInteractor,
    private val choreographerUtils: ChoreographerUtils,
    private val shadeRootView: WindowRootView,
    private val notificationRebindingTracker: NotificationRebindingTracker,
    @ShadeDisplayAware private val configurationRepository: ConfigurationRepository,
) {

    private val t = TrackTracer(trackName = "ShadeDisplaysWaitInteractor", trackGroup = "shade")

    /**
     * Suspends until the latest `onMovedToDisplay` received by the shade window has a display id
     * matching [newDisplayId].
     *
     * The caller is supposed to wrap this with [withTimeout], if needed.
     */
    suspend fun waitForOnMovedToDisplayDispatchedToView(newDisplayId: Int, logKey: String) {
        t.traceAsync({
            "$logKey#waitForOnMovedToDisplayDispatchedToView(newDisplayId=$newDisplayId)"
        }) {
            configurationRepository.onMovedToDisplay.filter { it == newDisplayId }.first()
            t.instant { "$logKey#onMovedToDisplay received with $newDisplayId" }
        }
    }

    /**
     * Suspends until the next doFrame is done. See [ChoreographerUtils] for details of this
     * behaviour (which has some corner cases).
     *
     * The caller is supposed to wrap this with [withTimeout], if needed.
     */
    suspend fun waitForNextDoFrameDone(newDisplayId: Int, logKey: String) {
        t.traceAsync({ "$logKey#waitForNextFrameDrawn(newDisplayId=$newDisplayId)" }) {
            choreographerUtils.waitUntilNextDoFrameDone(shadeRootView)
        }
    }

    /**
     * Waits for notifications inflations to be done. This assumes that there is at least one
     * notification being re-inflated already (otherwise it returns immediately).
     *
     * The caller is supposed to wrap this with [withTimeout], if needed.
     */
    suspend fun waitForNotificationsRebinding(logKey: String) {
        // here we don't need to wait for rebinding to appear (e.g. going > 0), as it already
        // happened synchronously when the new configuration was received by ViewConfigCoordinator.
        t.traceAsync("$logKey#waiting for notifications rebinding to finish") {
            notificationRebindingTracker.rebindingInProgressCount.first { it == 0 }
        }
    }

    /**
     * Waits for shade to be expanded, indicated by expansion float (1 for expanded, 0 for closed
     * with range of float in between for intermediate levels of opening).
     *
     * The caller is supposed to wrap this with [withTimeout], if needed.
     */
    suspend fun waitForShadeExpanded(logKey: String) {
        t.traceAsync("$logKey#waiting for shade to be fully expanded") {
            shadeInteractor.anyExpansion.first { it == 1f }
        }
    }
}
