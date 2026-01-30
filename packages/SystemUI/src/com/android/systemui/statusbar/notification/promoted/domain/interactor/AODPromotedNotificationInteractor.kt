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

package com.android.systemui.statusbar.notification.promoted.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.keyguard.domain.interactor.BiometricUnlockInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.shared.model.BiometricUnlockMode
import com.android.systemui.statusbar.notification.promoted.ShowPromotedNotificationsOnAOD
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.policy.domain.interactor.SensitiveNotificationProtectionInteractor
import com.android.systemui.util.asIndenting
import com.android.systemui.util.kotlin.FlowDumperImpl
import com.android.systemui.util.println
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@SysUISingleton
class AODPromotedNotificationInteractor
@Inject
constructor(
    promotedNotificationsInteractor: PromotedNotificationsInteractor,
    keyguardInteractor: KeyguardInteractor,
    sensitiveNotificationProtectionInteractor: SensitiveNotificationProtectionInteractor,
    showPromotedNotificationsOnAOD: ShowPromotedNotificationsOnAOD,
    dumpManager: DumpManager,
    biometricUnlockInteractor: BiometricUnlockInteractor,
) : FlowDumperImpl(dumpManager) {

    private val promotedNotifsAODEnabled = showPromotedNotificationsOnAOD.isEnabled

    /**
     * Whether the system is unlocked, not screensharing such that private notification content is
     * allowed to show on the aod, and a biometric is not about to dismiss the keyguard
     */
    private val canShowPrivateNotificationContent: Flow<Boolean> =
        combine(
            keyguardInteractor.hasTrust,
            sensitiveNotificationProtectionInteractor.isSensitiveStateActive,
            biometricUnlockInteractor.unlockState,
        ) { hasTrust, isSensitive, biometricUnlockState ->
            hasTrust &&
                !isSensitive &&
                !BiometricUnlockMode.dismissesKeyguard(biometricUnlockState.mode)
        }

    /** The content to show as the promoted notification on AOD */
    val content: Flow<PromotedNotificationContentModel?> =
        if (!promotedNotifsAODEnabled) {
            flowOf(null)
        } else {
            combine(
                    promotedNotificationsInteractor.aodPromotedNotification,
                    canShowPrivateNotificationContent,
                ) { promotedContent, showPrivateContent ->
                    if (showPrivateContent) promotedContent?.privateVersion
                    else promotedContent?.publicVersion
                }
                .distinctUntilNewInstance()
        }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        super.dump(pw, args)
        pw.asIndenting().println("showPromotedNotificationsOnAOD", promotedNotifsAODEnabled)
    }

    val isPresent: Flow<Boolean> = content.map { it != null }.dumpWhileCollecting("isPresent")

    /**
     * Returns flow where all subsequent repetitions of the same object instance are filtered out.
     */
    private fun <T> Flow<T>.distinctUntilNewInstance() = distinctUntilChanged { a, b -> a === b }
}
