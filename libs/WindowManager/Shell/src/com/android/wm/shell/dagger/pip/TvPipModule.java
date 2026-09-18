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

import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.pip.Pip;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip2.tv.TvPipController;
import com.android.wm.shell.pip2.tv.TvPipTransition;
import com.android.wm.shell.shared.pip.PipFlags;

import dagger.Module;
import dagger.Provides;

import java.util.Optional;

/**
 * Selects between the TV legacy and new PiP implementation.
 */
@Module(includes = {
        TvPip1Module.class,
        TvPip2Module.class
})
public abstract class TvPipModule {
    @WMSingleton
    @Provides
    static PipTransitionController providePipTransitionController(
            com.android.wm.shell.pip.tv.TvPipTransition legacyPipTransition,
            TvPipTransition newPipTransition) {
        if (PipFlags.isPip2ExperimentEnabled()) {
            return newPipTransition;
        } else {
            return legacyPipTransition;
        }
    }

    @WMSingleton
    @Provides
    static Optional<Pip> providePip(
            Optional<com.android.wm.shell.pip.tv.TvPipController.TvPipImpl> pip1,
            Optional<TvPipController.TvPipImpl> pip2) {
        if (PipFlags.isPip2ExperimentEnabled()) {
            return Optional.ofNullable(pip2.orElse(null));
        } else {
            return Optional.ofNullable(pip1.orElse(null));
        }
    }
}
