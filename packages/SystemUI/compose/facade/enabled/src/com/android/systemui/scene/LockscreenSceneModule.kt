/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.systemui.scene

import android.view.View
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.KeyguardViewConfigurator
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.qualifiers.KeyguardRootView
import com.android.systemui.keyguard.ui.composable.LockscreenContent
import com.android.systemui.keyguard.ui.composable.LockscreenScene
import com.android.systemui.keyguard.ui.composable.elements.ElementProviderModule
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElements
import com.android.systemui.keyguard.ui.viewmodel.LockscreenBehindScrimViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenContentViewModel
import com.android.systemui.keyguard.ui.viewmodel.LockscreenFrontScrimViewModel
import com.android.systemui.scene.ui.composable.Scene
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Provider

@Module(includes = [ElementProviderModule::class])
interface LockscreenSceneModule {

    @Binds @IntoSet fun lockscreenScene(scene: LockscreenScene): Scene

    companion object {
        @Provides
        @SysUISingleton
        @KeyguardRootView
        fun viewProvider(configurator: Provider<KeyguardViewConfigurator>): () -> View {
            return { configurator.get().getKeyguardRootView() }
        }

        @Provides
        fun providesLockscreenContent(
            viewModelFactory: LockscreenContentViewModel.Factory,
            lockscreenFrontScrimViewModelFactory: LockscreenFrontScrimViewModel.Factory,
            lockscreenBehindScrimViewModelFactory: LockscreenBehindScrimViewModel.Factory,
            lockscreenElements: LockscreenElements,
            clockInteractor: KeyguardClockInteractor,
            interactionJankMonitor: InteractionJankMonitor,
        ): LockscreenContent {
            return LockscreenContent(
                viewModelFactory,
                lockscreenFrontScrimViewModelFactory,
                lockscreenBehindScrimViewModelFactory,
                lockscreenElements,
                clockInteractor,
                interactionJankMonitor,
            )
        }
    }
}
