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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.Key
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.KeyguardBlueprintLog
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementContext
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import dagger.Lazy
import javax.inject.Inject

@SysUISingleton
/** The entry point for invoking composable lockscreen elements from keys */
class LockscreenElements
@Inject
constructor(
    private val builder: LockscreenElementFactoryImpl.Builder,
    private val keyguardClockViewModel: KeyguardClockViewModel,
    private val elementProviders: Lazy<Set<@JvmSuppressWildcards LockscreenElementProvider>>,
    @KeyguardBlueprintLog private val blueprintLog: LogBuffer,
) {
    private val logger = Logger(blueprintLog, LockscreenElements::class.simpleName!!)

    @Composable
    fun ContentScope.LockscreenElement(
        key: Key,
        modifier: Modifier = Modifier,
        ctx: LockscreenElementContext = LockscreenElementContext(),
    ) {
        Elements(ctx) { LockscreenElement(key, modifier) }
    }

    @Composable
    fun ContentScope.Elements(
        ctx: LockscreenElementContext = LockscreenElementContext(),
        content: @Composable LockscreenScope<ContentScope>.() -> Unit,
    ) {
        val elementFactory = rememberElementFactory()
        with(LockscreenScopeImpl(this.layoutState, this, elementFactory, ctx)) { content() }
    }

    @Composable
    fun rememberElementFactory(): LockscreenElementFactoryImpl {
        val currentClock by keyguardClockViewModel.currentClock.collectAsStateWithLifecycle()
        val providers =
            listOf(
                *elementProviders.get().toTypedArray(),
                currentClock?.smallClock?.layout,
                currentClock?.largeClock?.layout,
            )

        return remember(providers) {
            val elements =
                providers
                    .filterNotNull()
                    .flatMap { provider -> provider.elements }
                    .sortedBy { element -> element.source.priority }

            builder.create(
                buildMap {
                    for (element in elements) {
                        val prevElement = this[element.key]
                        if (
                            prevElement != null &&
                                prevElement.source.priority >= element.source.priority
                        ) {
                            logger.e({
                                "'$str3' has conflicting priority: $str1 ($int1) vs $str2 ($int2)"
                            }) {
                                str1 = "$prevElement"
                                int1 = prevElement.source.priority
                                str2 = "$element"
                                int2 = element.source.priority
                                str3 = "${element.key}"
                            }

                            // This shouldn't happen, but cheap to double-check
                            if (prevElement.source.priority > element.source.priority) {
                                continue
                            }
                        }

                        this[element.key] = element
                    }
                }
            )
        }
    }
}
