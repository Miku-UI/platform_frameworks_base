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

package com.android.wm.shell.dagger.pinnedlayer

import android.content.Context
import android.view.SurfaceControl
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.dagger.WMShellBaseModule
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.NormalAppLayerController
import com.android.wm.shell.desktopmode.ShellDesktopState
import com.android.wm.shell.desktopmode.WindowDragTransitionHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerFlags
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerPermissionObserver
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerPresentationController
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerUiState
import com.android.wm.shell.pinnedlayer.phone.PinnedWindowRepositionAnimationHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedWindowRepositionAnimator
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import dagger.Module
import dagger.Provides
import java.util.Optional

@Module(includes = [WMShellBaseModule::class])
object PinnedLayerModule {

    @WMSingleton
    @Provides
    fun providePinnedLayerHandler(
        shellInit: ShellInit,
        transitions: Transitions,
        pinnedLayerController: Optional<PinnedLayerController>,
        pinnedLayerUiState: Optional<PinnedLayerUiState>,
        // observer is unused here, but required to inject to make sure it is created so it can
        // register itself as a listener.
        pinnedLayerPermissionObserver: Optional<PinnedLayerPermissionObserver>,
        normalAppLayerController: Optional<NormalAppLayerController>,
        desktopTasksController: Optional<DesktopTasksController>,
        desktopUserRepositories: Optional<DesktopUserRepositories>,
    ): Optional<PinnedLayerHandler> {
        if (PinnedLayerFlags.isPinnedLayerEnabled()) {
            return Optional.of(
                PinnedLayerHandler(
                    shellInit = shellInit,
                    transitions = transitions,
                    pinnedLayerController = pinnedLayerController.get(),
                    pinnedLayerUiState = pinnedLayerUiState.get(),
                    normalLayerController = normalAppLayerController.get(),
                    desktopUserRepositories = desktopUserRepositories.orElse(null),
                    desktopTasksController = desktopTasksController.orElse(null),
                )
            )
        }
        return Optional.empty()
    }

    @WMSingleton
    @Provides
    fun providePinnedLayerController(
        context: Context,
        shellInit: ShellInit,
        transitions: Transitions,
        displayController: DisplayController,
        shellTaskOrganizer: ShellTaskOrganizer,
        desktopState: ShellDesktopState,
        windowDragTransitionHandler: WindowDragTransitionHandler,
        windowRepositionAnimationHandler: PinnedWindowRepositionAnimationHandler,
        windowRepositionAnimator: PinnedWindowRepositionAnimator,
        transactionPool: TransactionPool,
        rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
        multiDisplayDragMoveIndicatorController: MultiDisplayDragMoveIndicatorController,
    ): Optional<PinnedLayerController> {
        if (PinnedLayerFlags.isPinnedLayerEnabled()) {
            return Optional.of(
                PinnedLayerController(
                    shellInit = shellInit,
                    transitions = transitions,
                    taskDisplayAreaOrganizer = rootTaskDisplayAreaOrganizer,
                    shellTaskOrganizer = shellTaskOrganizer,
                    presentationController =
                        PinnedLayerPresentationController(context, displayController, desktopState),
                    windowDragTransitionHandler = windowDragTransitionHandler,
                    windowRepositionAnimationHandler = windowRepositionAnimationHandler,
                    windowRepositionAnimator = windowRepositionAnimator,
                    transactionPool = transactionPool,
                    desktopState = desktopState,
                    multiDisplayDragMoveIndicatorController =
                        multiDisplayDragMoveIndicatorController,
                )
            )
        }
        return Optional.empty()
    }

    @WMSingleton
    @Provides
    fun providePinnedLayerUiState(): Optional<PinnedLayerUiState> {
        if (PinnedLayerFlags.isPinnedLayerEnabled()) {
            return Optional.of(PinnedLayerUiState())
        }
        return Optional.empty()
    }

    @WMSingleton
    @Provides
    fun providePinnedLayerPermissionObserver(
        context: Context,
        @ShellMainThread mainShellExecutor: ShellExecutor,
        pinnedLayerController: Optional<PinnedLayerController>,
    ): Optional<PinnedLayerPermissionObserver> =
        pinnedLayerController.map { controller ->
            PinnedLayerPermissionObserver(
                context = context,
                mainShellExecutor = mainShellExecutor,
                pinnedLayerController = controller,
            )
        }

    @WMSingleton
    @Provides
    fun providePinnedWindowRepositionAnimationHandler(
        transitions: Transitions,
        windowRepositionAnimator: PinnedWindowRepositionAnimator,
    ): PinnedWindowRepositionAnimationHandler {
        return PinnedWindowRepositionAnimationHandler(
            transitions = transitions,
            windowRepositionAnimator = windowRepositionAnimator,
        )
    }

    @WMSingleton
    @Provides
    fun providePinnedWindowRepositionAnimator(): PinnedWindowRepositionAnimator {
        return PinnedWindowRepositionAnimator { SurfaceControl.Transaction() }
    }
}
