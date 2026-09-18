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

package com.android.systemui.contextualcursor.domain.interactor

import android.app.contextualsearch.ContextualSearchManager
import com.android.hardware.input.Flags.enableContextualCursorDesktopEntrypoints
import com.android.systemui.CoreStartable
import com.android.systemui.LauncherProxyService
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.statusbar.policy.domain.interactor.DeviceProvisioningInteractor
import com.android.systemui.statusbar.policy.domain.interactor.UserSetupInteractor
import com.android.systemui.user.domain.interactor.UserLogoutInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@SysUISingleton
class ContextualCursorInteractor
@Inject
constructor(
    private val deviceEntryInteractor: DeviceEntryInteractor,
    private val deviceProvisioningInteractor: DeviceProvisioningInteractor,
    private val userLogoutInteractor: UserLogoutInteractor,
    private val userSetupInteractor: UserSetupInteractor,
    private val gestureDetectionInteractor: GestureDetectionInteractor,
    launcherProxyService: LauncherProxyService,
    @param:Background private val backgroundScope: CoroutineScope,
) : CoreStartable {
    private val shakeGestureListener = ShakeGestureListener(launcherProxyService)

    override fun start() {
        if (!enableContextualCursorDesktopEntrypoints()) {
            return
        }

        backgroundScope.launch {
            /** The combination indicates user has been in a proper logged-in status */
            combine(
                    userSetupInteractor.isUserSetUp,
                    userLogoutInteractor.isLogoutEnabled,
                    deviceProvisioningInteractor.isDeviceProvisioned,
                    deviceEntryInteractor.isDeviceEntered,
                ) { isUserSetup, isLoggedIn, isDeviceProvisioned, isDeviceEntered ->
                    isUserSetup && isLoggedIn && isDeviceProvisioned && isDeviceEntered
                }
                .distinctUntilChanged()
                .collect { shouldListen ->
                    if (shouldListen) {
                        gestureDetectionInteractor.addShakeGestureListener(shakeGestureListener)
                    } else {
                        gestureDetectionInteractor.removeShakeGestureListener(shakeGestureListener)
                    }
                }
        }
    }

    private class ShakeGestureListener(private val launcherProxyService: LauncherProxyService) :
        GestureDetectionInteractor.OnShakeGestureListener {
        override fun onShakeGestureDetected() {
            // TODO: b/484184229 - temporarily invoke contextual search before the actual
            //  launch method is landed.]
            launcherProxyService.proxy?.invokeContextualSearch(
                ContextualSearchManager.ENTRYPOINT_SYSTEM_ACTION,
                /* config= */ null,
            )
        }
    }
}
