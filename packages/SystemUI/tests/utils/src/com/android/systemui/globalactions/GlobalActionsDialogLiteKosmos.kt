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

package com.android.systemui.globalactions

import android.view.accessibility.accessibilityManager
import com.android.internal.logging.uiEventLogger
import com.android.internal.widget.lockPatternUtils
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.animation.dialogTransitionAnimator
import com.android.systemui.colorextraction.fakeSysuiColorExtractor
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.shade.fakeShadeController
import com.android.systemui.statusbar.phone.fakeLightBarController
import com.android.systemui.statusbar.phone.systemUIDialogDotFactory
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.topui.topUiController
import com.android.systemui.user.domain.interactor.fakeSelectedUserInteractor
import org.mockito.kotlin.mock

/** Provides a mock */
val Kosmos.globalActionsDialogLite: GlobalActionsDialogLite by Kosmos.Fixture { mock() }

val Kosmos.actionsDialogLiteDelegateFactory:
    GlobalActionsDialogLite.ActionsDialogLiteDelegate.Factory by
    Kosmos.Fixture {
        GlobalActionsDialogLite.ActionsDialogLiteDelegate.Factory {
            context,
            adapter,
            overflowAdapter,
            powerAdapter,
            statusBarWindowController,
            keyguardShowing,
            onRefreshCallback,
            rescheduleBurnInTimeout ->
            GlobalActionsDialogLite.ActionsDialogLiteDelegate(
                context,
                adapter,
                overflowAdapter,
                powerAdapter,
                statusBarWindowController,
                keyguardShowing,
                onRefreshCallback,
                rescheduleBurnInTimeout,
                fakeSysuiColorExtractor,
                fakeLightBarController,
                keyguardStateController,
                topUiController,
                uiEventLogger,
                fakeShadeController,
                keyguardUpdateMonitor,
                lockPatternUtils,
                fakeSelectedUserInteractor,
                accessibilityManager,
                dialogTransitionAnimator,
                systemUIDialogDotFactory,
                deviceEntryInteractor,
            )
        }
    }
