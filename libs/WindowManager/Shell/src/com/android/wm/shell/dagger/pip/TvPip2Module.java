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

package com.android.wm.shell.dagger.pip;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.WindowManagerShellWrapper;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.pip.PipAppOpsListener;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.dagger.WMShellBaseModule;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.pip.PipParamsChangedForwarder;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip.tv.TvPipBoundsAlgorithm;
import com.android.wm.shell.pip.tv.TvPipBoundsController;
import com.android.wm.shell.pip.tv.TvPipBoundsState;
import com.android.wm.shell.pip.tv.TvPipMenuController;
import com.android.wm.shell.pip.tv.TvPipNotificationController;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.phone.PipTransitionState;
import com.android.wm.shell.pip2.tv.TvPipController;
import com.android.wm.shell.pip2.tv.TvPipScheduler;
import com.android.wm.shell.pip2.tv.TvPipTaskListener;
import com.android.wm.shell.pip2.tv.TvPipTransition;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import dagger.Module;
import dagger.Provides;

import java.util.Optional;

/**
 * Provides TV specific dependencies for Pip2.
 */
@Module(includes = {
        WMShellBaseModule.class
})
public abstract class TvPip2Module {

    @WMSingleton
    @Provides
    static TvPipTransition provideTvPipTransition(
            Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            TvPipBoundsState tvPipBoundsState,
            TvPipMenuController tvPipMenuController,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            TvPipTaskListener tvPipTaskListener,
            PipTransitionState pipTransitionState) {
        return new TvPipTransition(context, pipSurfaceTransactionHelper, shellInit,
                shellTaskOrganizer, transitions, tvPipBoundsState, tvPipMenuController,
                tvPipBoundsAlgorithm, tvPipTaskListener, pipTransitionState);
    }

    @WMSingleton
    @Provides
    static Optional<TvPipController.TvPipImpl> providePip2(
            Context context,
            ShellInit shellInit,
            ShellController shellController,
            TvPipBoundsState tvPipBoundsState,
            PipDisplayLayoutState pipDisplayLayoutState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            TvPipBoundsController tvPipBoundsController,
            PipTransitionState pipTransitionState,
            PipAppOpsListener pipAppOpsListener,
            TvPipScheduler tvPipScheduler,
            TvPipMenuController tvPipMenuController,
            PipMediaController pipMediaController,
            TvPipNotificationController pipNotificationController,
            TaskStackListenerImpl taskStackListener,
            PipParamsChangedForwarder pipParamsChangedForwarder,
            DisplayController displayController,
            WindowManagerShellWrapper wmShell,
            @ShellMainThread Handler mainHandler,
            @ShellMainThread ShellExecutor mainExecutor) {
        if (!PipFlags.isPip2ExperimentEnabled()) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(
                    TvPipController.create(
                            context,
                            shellInit,
                            shellController,
                            tvPipBoundsState,
                            pipDisplayLayoutState,
                            tvPipBoundsAlgorithm,
                            tvPipBoundsController,
                            pipTransitionState,
                            pipAppOpsListener,
                            tvPipScheduler,
                            tvPipMenuController,
                            pipMediaController,
                            pipNotificationController,
                            taskStackListener,
                            pipParamsChangedForwarder,
                            displayController,
                            wmShell,
                            mainHandler,
                            mainExecutor));
        }
    }

    @WMSingleton
    @Provides
    static TvPipTaskListener provideTvPipTaskListener(
            Context context,
            ShellTaskOrganizer shellTaskOrganizer,
            PipTransitionState pipTransitionState,
            TvPipBoundsState tvPipBoundsState,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            PipParamsChangedForwarder pipParamsChangedForwarder,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new TvPipTaskListener(context, shellTaskOrganizer, pipTransitionState,
                tvPipBoundsState, tvPipBoundsAlgorithm, pipParamsChangedForwarder, mainExecutor);
    }

    @WMSingleton
    @Provides
    static TvPipScheduler provideTvPipScheduler(
            Context context,
            PipTransitionState pipTransitionState,
            PipTransitionController pipTransitionController,
            TvPipBoundsState tvPipBoundsState,
            @ShellMainThread ShellExecutor mainExecutor) {
        return new TvPipScheduler(context, pipTransitionState, pipTransitionController,
                tvPipBoundsState, mainExecutor);
    }

    @WMSingleton
    @Provides
    static PipTransitionState providePipTransitionState(@ShellMainThread Handler handler) {
        return new PipTransitionState(handler, /* pipDesktopState= */ null);
    }

    @WMSingleton
    @Provides
    static PipSurfaceTransactionHelper providePipSurfaceTransactionHelper(
            Context context,
            @NonNull ShellInit shellInit,
            PipDisplayLayoutState pipDisplayLayoutState) {
        return new PipSurfaceTransactionHelper(context, shellInit, pipDisplayLayoutState);
    }
}
