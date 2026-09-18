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
package com.android.wm.shell.dagger.hierarchy

import android.content.Context
import android.hardware.devicestate.DeviceStateManager
import android.os.Handler
import com.android.wm.shell.Flags
import com.android.wm.shell.RootDisplayAreaOrganizer
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.dagger.WMShellConcurrencyModule
import com.android.wm.shell.dagger.WMShellCoroutinesModule
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.ContainerHierarchyCommandHandler
import com.android.wm.shell.hierarchy.ContainerHierarchyController
import com.android.wm.shell.hierarchy.modes.FormFactorModes
import com.android.wm.shell.hierarchy.updates.HierarchyUpdateRequester
import com.android.wm.shell.hierarchy.updates.HierarchyUpdateRequesterImpl
import com.android.wm.shell.hierarchy.updates.HierarchyUpdater
import com.android.wm.shell.hierarchy.updates.InitialHierarchyPopulator
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import dagger.Module
import dagger.Provides
import java.util.Optional

/**
 * Provides basic dependencies from {@link com.android.wm.shell.hierarchy}, these dependencies are
 * only accessible from components within the WM subcomponent.
 *
 * This module requires certain form-factor dependencies to be declared (ie. FormFactorModes) in
 * their form-factor specific modules.
 */
@Module(
    includes = [
        WMShellConcurrencyModule::class,
        WMShellCoroutinesModule::class
    ]
)
abstract class ContainerHierarchyModule {
    companion object {
        @WMSingleton
        @Provides
        fun provideContainerHierarchy(): ContainerHierarchy {
            return ContainerHierarchy();
        }

        @WMSingleton
        @Provides
        fun provideContainerHierarchyCommandHandler(
            hierarchy: ContainerHierarchy,
            formFactorModes: FormFactorModes,
            shellInit: ShellInit,
            shellCommandHandler: ShellCommandHandler,
        ): ContainerHierarchyCommandHandler {
            return ContainerHierarchyCommandHandler(
                hierarchy,
                formFactorModes,
                shellInit,
                shellCommandHandler
            )
        }

        @WMSingleton
        @Provides
        fun provideHierarchyUpdateRequester(
            displayController: DisplayController,
            transitions: Transitions,
            hierarchy: ContainerHierarchy,
            updater: HierarchyUpdater,
            shellTaskOrganizer: ShellTaskOrganizer,
        ): HierarchyUpdateRequester {
            return HierarchyUpdateRequesterImpl(
                displayController,
                transitions,
                hierarchy,
                updater,
                shellTaskOrganizer,
            )
        }

        @WMSingleton
        @Provides
        fun provideHierarchyUpdater(
            appContext: Context,
            shellController: ShellController,
            shellTaskOrganizer: ShellTaskOrganizer,
            transitions: Transitions,
            displayInsetsController: DisplayInsetsController,
            deviceStateManager: DeviceStateManager,
            hierarchy: ContainerHierarchy,
            formFactorModes: FormFactorModes,
            shellInit: ShellInit,
            @ShellMainThread mainExector: ShellExecutor,
            @ShellMainThread mainHandler: Handler,
        ): HierarchyUpdater {
            return HierarchyUpdater(
                appContext,
                shellController,
                shellTaskOrganizer,
                transitions,
                displayInsetsController,
                deviceStateManager,
                hierarchy,
                formFactorModes,
                shellInit,
                mainExector,
                mainHandler,
            )
        }

        // NOTE: To be removed once b/463244413 is fixed
        @WMSingleton
        @Provides
        fun provideInitialHierarchyPopulator(
            shellTaskOrganizer: ShellTaskOrganizer,
            rootDisplayAreaOrganizer: RootDisplayAreaOrganizer,
            taskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
            hierarchy: ContainerHierarchy,
            updater: HierarchyUpdater,
            shellInit: ShellInit,
        ): InitialHierarchyPopulator {
            return InitialHierarchyPopulator(
                shellTaskOrganizer,
                rootDisplayAreaOrganizer,
                taskDisplayAreaOrganizer,
                hierarchy,
                updater,
                shellInit,
            )
        }

        @WMSingleton
        @Provides
        fun provideContainerHierarchyController(
            shellInit: ShellInit,
            shellCommandHandler: ShellCommandHandler,
            displayController: DisplayController,
            hierarchy: ContainerHierarchy,
        ): Optional<ContainerHierarchyController> {
            if (!Flags.enableShellModes()) {
                return Optional.empty();
            }
            return Optional.of(
                ContainerHierarchyController(
                    shellInit,
                    shellCommandHandler,
                    displayController,
                    hierarchy,
                )
            )
        }

        @WMSingleton
        @Provides
        fun provideContainerHierarchyDependency(
            initialHierarchyPopulator: InitialHierarchyPopulator,
        ): Optional<ContainerHierarchyDependency> {
            // Depending on InitialHierarchyPopulator (and indirectly HierarchyUpdater) ensures that
            // we add the root task hook and populate the hierarchy before any other shell component
            // creates their own root tasks.
            return Optional.empty()
        }

        //
        // Misc
        //

        @WMSingleton
        @ContainerHierarchyCreateTriggerOverride
        @Provides
        fun provideIndependentContainerHierarchyComponentsToCreate(
            initialContainerHierarchyPopulator: InitialHierarchyPopulator,
            containerHierarchyUpdater: HierarchyUpdater,
            containerHierarchyUpdateRequester: HierarchyUpdateRequester,
            containerHierarchyCommandHandler: ContainerHierarchyCommandHandler,
            containerHierarchyController: Optional<ContainerHierarchyController>
        ): Object {
            return Object();
        }
    }
}