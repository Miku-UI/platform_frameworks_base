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

package com.android.systemui.plugins.keyguard.ui.composable.elements

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.BaseContentScope
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ElementScope
import com.android.compose.animation.scene.Key
import com.android.compose.animation.scene.MovableElementContentScope
import com.android.compose.animation.scene.MovableElementKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayoutScope
import com.android.compose.animation.scene.SceneTransitionsBuilder
import com.android.compose.animation.scene.rememberMutableSceneTransitionLayoutState
import com.android.compose.animation.scene.transitions as buildTransitions
import com.android.compose.lifecycle.LaunchedEffectWithLifecycle

@Immutable
/** Combined context for lockscreen elements. Contains relevant rendering parameters. */
interface LockscreenScope<out TScope : BaseContentScope> {
    /** Inner STL Content Scope */
    val contentScope: TScope

    /** Factory used to build LockscreenElements */
    val factory: LockscreenElementFactory

    /** Context used by element and factory to build LockscreenElements */
    val context: LockscreenElementContext

    /** Creates a copy of this LockscreenScope with the specified inner scope */
    fun <T : BaseContentScope> createChildScope(scope: T): LockscreenScope<T>

    @Composable
    /** Creates an element by delegating to [ContentScope] */
    fun Element(key: ElementKey, modifier: Modifier, content: @Composable BoxScope.() -> Unit)

    @Composable
    /** Creates an element by delegating to [ContentScope] */
    fun ElementWithValues(
        key: ElementKey,
        modifier: Modifier,
        content: @Composable ElementScope<LockscreenScope<ElementContentScope>>.() -> Unit,
    )

    @Composable
    /** Creates a movable element by delegating to [ContentScope] */
    fun MovableElement(
        key: MovableElementKey,
        modifier: Modifier,
        content: @Composable ElementScope<LockscreenScope<MovableElementContentScope>>.() -> Unit,
    )

    @Composable
    /** Creates the LockscreenElement at the key by delegating to [LockscreenElementFactory] */
    fun LockscreenElement(key: Key, modifier: Modifier)

    /**
     * Scope type for building nested scenes. Since the scope is different from the parent, the
     * caller must also use the new [LockscreenElementContext] that is passed to the callback.
     */
    @Immutable
    interface NestedSceneScope {
        fun scene(sceneKey: SceneKey, content: @Composable LockscreenScope<ContentScope>.() -> Unit)
    }

    @Immutable
    class NestedSceneScopeImpl(
        private val parentScope: LockscreenScope<*>,
        private val transitionScope: SceneTransitionLayoutScope<ContentScope>,
    ) : NestedSceneScope {
        override fun scene(
            sceneKey: SceneKey,
            content: @Composable LockscreenScope<ContentScope>.() -> Unit,
        ) {
            transitionScope.scene(sceneKey) { parentScope.createChildScope(this).content() }
        }
    }

    companion object {
        /**
         * Method for building nested scenes with a correct inner context. This allows for the use
         * of STL transitions with lockscreen elements that are properly scoped by the factory.
         */
        @Composable
        fun LockscreenScope<ContentScope>.NestedScenes(
            sceneKey: SceneKey,
            transitions: SceneTransitionsBuilder.() -> Unit,
            modifier: Modifier = Modifier,
            content: NestedSceneScope.() -> Unit,
        ) {
            val coroutineScope = rememberCoroutineScope()
            val sceneState =
                rememberMutableSceneTransitionLayoutState(
                    initialScene = sceneKey,
                    transitions = buildTransitions { transitions() },
                )

            LaunchedEffectWithLifecycle(sceneState, sceneKey, coroutineScope) {
                sceneState.setTargetScene(sceneKey, coroutineScope)
            }

            contentScope.NestedSceneTransitionLayout(sceneState, modifier) {
                NestedSceneScopeImpl(this@NestedScenes, this).content()
            }
        }

        @Composable
        fun LockscreenScope<*>.Element(key: ElementKey, content: @Composable BoxScope.() -> Unit) {
            Element(key, Modifier, content)
        }

        @Composable
        fun LockscreenScope<*>.ElementWithValues(
            key: ElementKey,
            content: @Composable ElementScope<LockscreenScope<ElementContentScope>>.() -> Unit,
        ) {
            ElementWithValues(key, Modifier, content)
        }

        @Composable
        fun LockscreenScope<*>.MovableElement(
            key: MovableElementKey,
            content:
                @Composable
                ElementScope<LockscreenScope<MovableElementContentScope>>.() -> Unit,
        ) {
            MovableElement(key, Modifier, content)
        }

        @Composable
        fun LockscreenScope<*>.LockscreenElement(key: Key) {
            LockscreenElement(key, Modifier)
        }
    }
}
