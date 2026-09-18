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

package com.android.systemui

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.BuildSpec
import com.android.systemui.kairos.CoalescingPolicy
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.RootKairosNetwork
import com.android.systemui.kairos.TransactionScope
import com.android.systemui.kairos.effect
import com.android.systemui.kairos.launchKairosNetwork
import com.android.systemui.kairos.util.NameTag
import com.android.systemui.kairos.util.nameTag
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A Kairos-powered class that needs late-initialization within a Kairos [BuildScope].
 *
 * If your class is a [SysUISingleton], you can leverage Dagger to automatically initialize your
 * instance after SystemUI has initialized:
 * ```kotlin
 * class MyClass : KairosActivatable { ... }
 *
 * @dagger.Module
 * interface MyModule {
 *     @Binds
 *     @IntoSet
 *     fun bindKairosActivatable(impl: MyClass): KairosActivatable
 * }
 * ```
 *
 * Alternatively, you can utilize Dagger's [dagger.assisted.AssistedInject]:
 * ```kotlin
 * class MyClass @AssistedInject constructor(...) : KairosActivatable {
 *     @AssistedFactory
 *     interface Factory {
 *         fun create(...): MyClass
 *     }
 * }
 *
 * // When you need an instance:
 *
 * class OtherClass @Inject constructor(
 *     private val myClassFactory: MyClass.Factory,
 * ) {
 *     fun BuildScope.foo() {
 *         val myClass = activated { myClassFactory.create() }
 *         ...
 *     }
 * }
 * ```
 *
 * @see activated
 */
fun interface KairosActivatable {
    /** Initializes any Kairos fields that require a [BuildScope] in order to be constructed. */
    fun BuildScope.activate()
}

/**
 * Convenience method to [activate][KairosActivatable.activate] a [KairosActivatable] within
 * [kairosNetwork].
 *
 * Shorthand for:
 * ```
 *     kairosNetwork.activateSpec { this@activateIn.run { activate() } }
 * ```
 */
suspend fun KairosActivatable.activateIn(kairosNetwork: KairosNetwork) {
    kairosNetwork.activateSpec { activate() }
}

/** Constructs [KairosActivatable] instances. */
fun interface KairosActivatableFactory<T : KairosActivatable> {
    fun BuildScope.create(): T
}

/** Instantiates, [activates][KairosActivatable.activate], and returns a [KairosActivatable]. */
fun <T : KairosActivatable> BuildScope.activated(factory: KairosActivatableFactory<T>): T =
    factory.run { create() }.apply { activate() }

/** Initializes [KairosActivatables][KairosActivatable] after SystemUI is initialized. */
@SysUISingleton
class KairosCoreStartable
private constructor(
    private val appScope: CoroutineScope,
    private val activatables: Provider<Set<@JvmSuppressWildcards KairosActivatable>>,
    private val unwrappedNetwork: RootKairosNetwork,
) : CoreStartable, KairosNetwork by unwrappedNetwork {

    @Inject
    constructor(
        @Application appScope: CoroutineScope,
        activatables: Provider<Set<@JvmSuppressWildcards KairosActivatable>>,
        @Background bgDispatcher: CoroutineDispatcher,
    ) : this(
        appScope = appScope,
        activatables = activatables,
        unwrappedNetwork =
            appScope.launchKairosNetwork(
                context = bgDispatcher,
                coalescingPolicy = CoalescingPolicy.Eager,
            ),
    )

    private val started = CompletableDeferred<Unit>()

    override fun start() {
        appScope.launch {
            // Many of our Dagger-provided classes are not safe to init off of the main thread, so
            // query the [Provider] here outside of [activateSpec].
            val activatableSet = activatables.get()
            unwrappedNetwork.activateSpec(nameTag("KairosCoreStartable")) {
                for (activatable in activatableSet) {
                    activatable.run { activate() }
                }
                effect(name = nameTag("KairosCoreStartable::notifyStarted")) {
                    started.complete(Unit)
                }
            }
        }
    }

    override suspend fun activateSpec(name: NameTag?, spec: BuildSpec<*>) {
        started.await()
        unwrappedNetwork.activateSpec(name, spec)
    }

    override suspend fun <R> transact(block: TransactionScope.() -> R): R {
        started.await()
        return unwrappedNetwork.transact(block)
    }
}

@Module
interface KairosCoreStartableModule {
    @Binds
    @IntoMap
    @ClassKey(KairosCoreStartable::class)
    fun bindCoreStartable(impl: KairosCoreStartable): CoreStartable

    @Multibinds fun kairosActivatables(): Set<@JvmSuppressWildcards KairosActivatable>

    @Binds fun bindKairosNetwork(impl: KairosCoreStartable): KairosNetwork
}
