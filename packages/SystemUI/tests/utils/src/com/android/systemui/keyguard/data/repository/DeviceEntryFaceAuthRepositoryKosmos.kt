/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.data.repository

import android.content.applicationContext
import android.hardware.face.FaceManager
import com.android.internal.logging.uiEventLogger
import com.android.systemui.biometrics.faceManager
import com.android.systemui.bouncer.domain.interactor.alternateBouncerInteractor
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.deviceentry.data.repository.DeviceEntryFaceAuthRepository
import com.android.systemui.deviceentry.data.repository.DeviceEntryFaceAuthRepositoryImpl
import com.android.systemui.deviceentry.domain.interactor.faceAuthLogger
import com.android.systemui.display.domain.interactor.displayStateInteractor
import com.android.systemui.dump.dumpManager
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.log.sessionTracker
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.statusbar.phone.KeyguardBypassController
import com.android.systemui.statusbar.phone.keyguardBypassController
import com.android.systemui.user.data.repository.fakeUserRepository

fun Kosmos.deviceEntryFaceAuthRepositoryImpl(
    fmOverride: FaceManager? = faceManager,
    bypassControllerOverride: KeyguardBypassController? = keyguardBypassController
): DeviceEntryFaceAuthRepositoryImpl {
    return DeviceEntryFaceAuthRepositoryImpl(
        fmOverride,
        fakeUserRepository,
        bypassControllerOverride,
        backgroundScope,
        testDispatcher,
        testDispatcher,
        fakeExecutor,
        sessionTracker,
        uiEventLogger,
        faceAuthLogger,
        fakeBiometricSettingsRepository,
        fakeDeviceEntryFingerprintAuthRepository,
        fakeKeyguardRepository,
        powerInteractor,
        keyguardInteractor,
        alternateBouncerInteractor,
        { sceneInteractor },
        faceDetectBuffer,
        faceAuthBuffer,
        keyguardTransitionInteractor,
        displayStateInteractor,
        dumpManager,
    )
}

var Kosmos.deviceEntryFaceAuthRepository: DeviceEntryFaceAuthRepository by
    Kosmos.Fixture { fakeDeviceEntryFaceAuthRepository }
val Kosmos.fakeDeviceEntryFaceAuthRepository by
    Kosmos.Fixture { FakeDeviceEntryFaceAuthRepository() }

val Kosmos.faceAuthBuffer by Fixture<TableLogBuffer> { logcatTableLogBuffer(this, "face auth") }

val Kosmos.faceDetectBuffer by Fixture<TableLogBuffer> { logcatTableLogBuffer(this, "face detect") }
