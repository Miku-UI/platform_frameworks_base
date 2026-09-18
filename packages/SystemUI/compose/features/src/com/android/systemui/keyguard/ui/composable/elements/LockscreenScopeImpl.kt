/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.keyguard.ui.composable.elements

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.AnimatedState
import com.android.compose.animation.scene.BaseContentScope
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ElementScope
import com.android.compose.animation.scene.Key
import com.android.compose.animation.scene.MovableElementContentScope
import com.android.compose.animation.scene.MovableElementKey
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.SharedValueType
import com.android.compose.animation.scene.ValueKey
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementContext
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementFactory
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope.LockscreenScopeFactory

@Immutable
class LockscreenScopeImpl<TScope : BaseContentScope>(
    override val sceneContainerLayoutState: SceneTransitionLayoutState,
    override val contentScope: TScope,
    override val factory: LockscreenElementFactory,
    override val context: LockscreenElementContext,
) : LockscreenScope<TScope> {
    override val scopeFactory: LockscreenScopeFactory by lazy {
        LockscreenScopeFactoryImpl(sceneContainerLayoutState, factory, context)
    }

    @Composable
    override fun Element(
        key: ElementKey,
        modifier: Modifier,
        content: @Composable BoxScope.() -> Unit,
    ) {
        contentScope.Element(key, modifier, content)
    }

    @Composable
    override fun ElementWithValues(
        key: ElementKey,
        modifier: Modifier,
        content: @Composable ElementScope<LockscreenScope<ElementContentScope>>.() -> Unit,
    ) {
        contentScope.ElementWithValues(key, modifier) { ElementScopeImpl(this).content() }
    }

    @Composable
    override fun MovableElement(
        key: MovableElementKey,
        modifier: Modifier,
        content: @Composable ElementScope<LockscreenScope<MovableElementContentScope>>.() -> Unit,
    ) {
        contentScope.MovableElement(key, modifier) { ElementScopeImpl(this).content() }
    }

    @Immutable
    private inner class ElementScopeImpl<TInnerScope : BaseContentScope>(
        private val elementScope: ElementScope<TInnerScope>
    ) : ElementScope<LockscreenScope<TInnerScope>> {
        @Composable
        override fun <T> animateElementValueAsState(
            value: T,
            key: ValueKey,
            type: SharedValueType<T, *>,
            canOverflow: Boolean,
        ): AnimatedState<T> {
            return elementScope.animateElementValueAsState(value, key, type, canOverflow)
        }

        @Composable
        override fun content(content: @Composable LockscreenScope<TInnerScope>.() -> Unit) {
            val scopeFactory = this@LockscreenScopeImpl.scopeFactory
            elementScope.content { scopeFactory.create(this).content() }
        }
    }

    @Composable
    override fun LockscreenElement(key: Key, modifier: Modifier) {
        factory.LockscreenElement(this, key, modifier)
    }

    private class LockscreenScopeFactoryImpl(
        private val sceneContainerLayoutState: SceneTransitionLayoutState,
        private val factory: LockscreenElementFactory,
        private val context: LockscreenElementContext,
    ) : LockscreenScopeFactory {
        override fun <T : BaseContentScope> create(scope: T): LockscreenScope<T> {
            return create(scope, context)
        }

        override fun <T : BaseContentScope> create(
            scope: T,
            context: LockscreenElementContext,
        ): LockscreenScope<T> {
            return LockscreenScopeImpl(sceneContainerLayoutState, scope, factory, context)
        }
    }
}
