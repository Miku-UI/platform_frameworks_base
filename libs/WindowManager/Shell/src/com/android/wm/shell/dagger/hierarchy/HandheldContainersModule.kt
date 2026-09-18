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
import com.android.wm.shell.dagger.WMShellConcurrencyModule
import com.android.wm.shell.dagger.WMShellCoroutinesModule
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.hierarchy.ContainerHierarchy
import com.android.wm.shell.hierarchy.experimental.AlwaysOnTopMode
import com.android.wm.shell.hierarchy.experimental.HandheldRootMode
import com.android.wm.shell.hierarchy.experimental.MultiContainerMode
import com.android.wm.shell.hierarchy.experimental.testsplit.PipMode
import com.android.wm.shell.hierarchy.experimental.testsplit.SplitMode
import com.android.wm.shell.hierarchy.modes.FormFactorModes
import com.android.wm.shell.hierarchy.modes.handheld.HandheldModeRequester
import com.android.wm.shell.hierarchy.modes.handheld.HandheldModes
import com.android.wm.shell.sysui.ShellInit
import dagger.Lazy
import dagger.Module
import dagger.Provides

/** Provides basic dependencies from {@link com.android.wm.shell.hierarchy} for handheld devices. */
@Module(
    includes =
        [
            WMShellConcurrencyModule::class,
            WMShellCoroutinesModule::class,
            ContainerHierarchyModule::class,
        ]
)
class HandheldContainersModule {

    @WMSingleton
    @Provides
    fun provideHandheldRootMode(): HandheldRootMode {
        return HandheldRootMode()
    }

    @WMSingleton
    @Provides
    fun provideAlwaysOnTopMode(
        context: Context,
        hierarchy: ContainerHierarchy,
        modeRequester: HandheldModeRequester
    ): AlwaysOnTopMode {
        return AlwaysOnTopMode(context, hierarchy, modeRequester)
    }

    @WMSingleton
    @Provides
    fun provideMultiContainerMode(
        context: Context,
        hierarchy: ContainerHierarchy,
        modeRequester: HandheldModeRequester
    ): MultiContainerMode {
        return MultiContainerMode(context, hierarchy, modeRequester)
    }

    @WMSingleton
    @Provides
    fun providePipMode(
        context: Context,
        hierarchy: ContainerHierarchy,
        modeRequester: HandheldModeRequester
    ): PipMode {
        return PipMode(context, hierarchy, modeRequester)
    }

    @WMSingleton
    @Provides
    fun provideSplitMode(
        context: Context,
        hierarchy: ContainerHierarchy,
        modeRequester: HandheldModeRequester
    ): SplitMode {
        return SplitMode(context, hierarchy, modeRequester)
    }

    @WMSingleton
    @Provides
    fun provideHandheldModeRequester(
        alwaysOnTopModeLazy: Lazy<AlwaysOnTopMode>,
        multiContainerModeLazy: Lazy<MultiContainerMode>,
        splitModeLazy: Lazy<SplitMode>,
        pipModeLazy: Lazy<PipMode>
    ): HandheldModeRequester {
        return HandheldModeRequester(
            alwaysOnTopModeLazy,
            multiContainerModeLazy,
            splitModeLazy,
            pipModeLazy
        )
    }

    // This provides the override FormFactorModes in ContainerHierarchyModule
    @WMSingleton
    @Provides
    fun provideOverrideFormFactorModes(
        hierarchy: ContainerHierarchy,
        rootMode: HandheldRootMode,
        alwaysOnTopMode: AlwaysOnTopMode,
        multiContainerMode: MultiContainerMode,
        splitMode: SplitMode,
        pipMode: PipMode,
        shellInit: ShellInit,
    ): FormFactorModes {
        return HandheldModes(
            hierarchy,
            rootMode,
            alwaysOnTopMode,
            multiContainerMode,
            splitMode,
            pipMode,
            shellInit,
        )
    }
}
