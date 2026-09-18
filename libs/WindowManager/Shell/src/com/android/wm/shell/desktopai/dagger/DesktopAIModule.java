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

package com.android.wm.shell.desktopai.dagger;

import android.annotation.NonNull;

import com.android.window.flags.Flags;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.desktopai.api.ITriggerManager;
import com.android.wm.shell.desktopai.api.ITriggerSource;
import com.android.wm.shell.desktopai.api.IUserContextService;
import com.android.wm.shell.desktopai.core.CujHandlerRegistry;
import com.android.wm.shell.desktopai.core.DesktopAiOrchestrator;
import com.android.wm.shell.desktopai.core.MockUserContextService;
import com.android.wm.shell.desktopai.core.OverviewTriggerSource;
import com.android.wm.shell.desktopai.core.TriggerManager;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import dagger.Module;
import dagger.Provides;

import java.util.List;
import java.util.Optional;

import javax.inject.Provider;

/**
 * Provides DesktopAI implementation components to Dagger dependency Graph.
 */
@Module(includes = ExtensionsModule.class)
public class DesktopAIModule {

    @WMSingleton
    @Provides
    static ITriggerManager provideITriggerManager(@NonNull ShellInit shellInit,
            @NonNull OverviewTriggerSource overviewTriggerSource) {
        // TODO(b/476413663): Introduce MultiBinding for additional TriggerSource
        final List<ITriggerSource> triggerSources = List.of(overviewTriggerSource);
        return new TriggerManager(shellInit, triggerSources);
    }

    @WMSingleton
    @Provides
    static OverviewTriggerSource provideOverviewTriggerSource(
            @NonNull ShellController shellController) {
        return new OverviewTriggerSource(shellController);
    }

    @WMSingleton
    @Provides
    static Optional<DesktopAiInitializer> provideDesktopAiInitializer(
            Provider<DesktopAiInitializer> initializerProvider) {
        if (Flags.desktopAiPlatform()) {
            return Optional.of(initializerProvider.get());
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static IUserContextService provideUserContextService(
            @NonNull ITriggerManager triggerManager) {
        return new MockUserContextService();
    }

    @WMSingleton
    @Provides
    static DesktopAiOrchestrator provideDesktopAIOrchestrator(
            @NonNull ITriggerManager triggerManager,
            @NonNull CujHandlerRegistry cujHandlerRegistry) {
        return new DesktopAiOrchestrator(triggerManager, cujHandlerRegistry);
    }
}
