/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.unfold

import android.os.Handler
import com.android.systemui.unfold.config.UnfoldTransitionConfig
import com.android.systemui.unfold.dagger.UnfoldBg
import com.android.systemui.unfold.dagger.UnfoldMain
import com.android.systemui.unfold.progress.FixedTimingTransitionProgressProvider
import com.android.systemui.unfold.progress.MainThreadUnfoldTransitionProgressProvider
import com.android.systemui.unfold.progress.PhysicsBasedUnfoldTransitionProgressProvider
import com.android.systemui.unfold.progress.UnfoldTransitionProgressForwarder
import com.android.systemui.unfold.updates.DeviceFoldStateProvider
import com.android.systemui.unfold.updates.FoldStateProvider
import com.android.systemui.unfold.updates.RotationChangeProvider
import com.android.systemui.unfold.updates.hinge.EmptyHingeAngleProvider
import com.android.systemui.unfold.updates.hinge.HingeAngleProvider
import com.android.systemui.unfold.updates.hinge.HingeSensorAngleProvider
import com.android.systemui.unfold.util.ATraceLoggerTransitionProgressListener
import com.android.systemui.unfold.util.ScaleAwareTransitionProgressProvider
import com.android.systemui.unfold.util.UnfoldKeyguardVisibilityManager
import com.android.systemui.unfold.util.UnfoldKeyguardVisibilityManagerImpl
import com.android.systemui.unfold.util.UnfoldKeyguardVisibilityProvider
import dagger.Module
import dagger.Provides
import java.util.Optional
import javax.inject.Provider
import javax.inject.Singleton

@Module(
    includes =
        [
            UnfoldSharedInternalModule::class,
            UnfoldRotationProviderInternalModule::class,
            HingeAngleProviderInternalModule::class,
            FoldStateProviderModule::class,
        ]
)
class UnfoldSharedModule {
    @Provides
    @Singleton
    fun unfoldKeyguardVisibilityProvider(
        impl: UnfoldKeyguardVisibilityManagerImpl
    ): UnfoldKeyguardVisibilityProvider = impl

    @Provides
    @Singleton
    fun unfoldKeyguardVisibilityManager(
        impl: UnfoldKeyguardVisibilityManagerImpl
    ): UnfoldKeyguardVisibilityManager = impl
}

/**
 * Needed as methods inside must be public, but their parameters can be internal (and, a public
 * method can't have internal parameters). Making the module internal and included in a public one
 * fixes the issue.
 */
@Module
internal class UnfoldSharedInternalModule {
    @Provides
    @Singleton
    fun unfoldTransitionProgressProvider(
        tracingListener: ATraceLoggerTransitionProgressListener.Factory,
        mainThreadUnfoldTransitionProgressProviderFactory:
        MainThreadUnfoldTransitionProgressProvider.Factory,
        @UnfoldBg bgProvider: Provider<Optional<UnfoldTransitionProgressProvider>>,
    ): Optional<UnfoldTransitionProgressProvider> {
        // Wrap the background progress provider to emit events on the main thread, so when
        // injecting the unqualified progress provider dependency, it will invoke the callbacks on
        // the main thread
        val mainThreadProvider: Optional<UnfoldTransitionProgressProvider> =
            bgProvider.get().map {
                mainThreadUnfoldTransitionProgressProviderFactory.create(it)
            }
        mainThreadProvider.ifPresent {
            it.addCallback(tracingListener.create("MainThreadFromBgProgress"))
        }
        return mainThreadProvider
    }

    @Provides
    @Singleton
    @UnfoldBg
    fun unfoldBgTransitionProgressProvider(
        config: UnfoldTransitionConfig,
        scaleAwareProviderFactory: ScaleAwareTransitionProgressProvider.Factory,
        tracingListener: ATraceLoggerTransitionProgressListener.Factory,
        physicsBasedUnfoldTransitionProgressProvider:
            PhysicsBasedUnfoldTransitionProgressProvider.Factory,
        fixedTimingTransitionProgressProvider: Provider<FixedTimingTransitionProgressProvider>,
        foldStateProvider: FoldStateProvider,
        @UnfoldBg bgHandler: Handler,
    ): Optional<UnfoldTransitionProgressProvider> {
        return createOptionalUnfoldTransitionProgressProvider(
            config = config,
            scaleAwareProviderFactory = scaleAwareProviderFactory,
            tracingListener = tracingListener.create("BgThread"),
            physicsBasedUnfoldTransitionProgressProvider =
                physicsBasedUnfoldTransitionProgressProvider,
            fixedTimingTransitionProgressProvider = fixedTimingTransitionProgressProvider,
            foldStateProvider = foldStateProvider,
            progressHandler = bgHandler,
        )
    }

    private fun createOptionalUnfoldTransitionProgressProvider(
        config: UnfoldTransitionConfig,
        scaleAwareProviderFactory: ScaleAwareTransitionProgressProvider.Factory,
        tracingListener: ATraceLoggerTransitionProgressListener,
        physicsBasedUnfoldTransitionProgressProvider:
            PhysicsBasedUnfoldTransitionProgressProvider.Factory,
        fixedTimingTransitionProgressProvider: Provider<FixedTimingTransitionProgressProvider>,
        foldStateProvider: FoldStateProvider,
        progressHandler: Handler,
    ): Optional<UnfoldTransitionProgressProvider> {
        if (!config.isEnabled) {
            return Optional.empty()
        }
        val baseProgressProvider =
            if (config.isHingeAngleEnabled) {
                physicsBasedUnfoldTransitionProgressProvider.create(
                    foldStateProvider,
                    progressHandler
                )
            } else {
                fixedTimingTransitionProgressProvider.get()
            }

        return Optional.of(
            scaleAwareProviderFactory.wrap(baseProgressProvider).apply {
                // Always present callback that logs animation beginning and end.
                addCallback(tracingListener)
            }
        )
    }

    @Provides
    @Singleton
    fun provideProgressForwarder(
        config: UnfoldTransitionConfig,
        progressForwarder: Provider<UnfoldTransitionProgressForwarder>
    ): Optional<UnfoldTransitionProgressForwarder> {
        if (!config.isEnabled) {
            return Optional.empty()
        }
        return Optional.of(progressForwarder.get())
    }
}

/**
 * Provides [FoldStateProvider].
 */
@Module
internal class FoldStateProviderModule {

    @Provides
    @Singleton
    fun provideFoldStateProvider(
        factory: DeviceFoldStateProvider.Factory,
        hingeAngleProvider: HingeAngleProvider,
        @UnfoldBg rotationChangeProvider: RotationChangeProvider,
        @UnfoldBg bgHandler: Handler,
    ): FoldStateProvider =
        factory.create(
            hingeAngleProvider,
            rotationChangeProvider,
            progressHandler = bgHandler
        )
}

/** Provides bindings for [HingeAngleProvider]. */
@Module
internal class HingeAngleProviderInternalModule {
    @Provides
    fun hingeAngleProvider(
        config: UnfoldTransitionConfig,
        @UnfoldBg handler: Handler,
        hingeAngleSensorProvider: HingeSensorAngleProvider.Factory
    ): HingeAngleProvider {
        return if (config.isHingeAngleEnabled) {
            hingeAngleSensorProvider.create(handler)
        } else {
            EmptyHingeAngleProvider
        }
    }
}

@Module
internal class UnfoldRotationProviderInternalModule {
    @Provides
    @Singleton
    @UnfoldMain
    fun provideRotationChangeProvider(
        rotationChangeProviderFactory: RotationChangeProvider.Factory,
        @UnfoldMain callbackHandler: Handler,
    ): RotationChangeProvider {
        return rotationChangeProviderFactory.create(callbackHandler)
    }

    @Provides
    @Singleton
    @UnfoldBg
    fun provideBgRotationChangeProvider(
        rotationChangeProviderFactory: RotationChangeProvider.Factory,
        @UnfoldBg callbackHandler: Handler,
    ): RotationChangeProvider {
        // For UnfoldBg RotationChangeProvider we use bgHandler as callbackHandler
        return rotationChangeProviderFactory.create(callbackHandler)
    }
}
