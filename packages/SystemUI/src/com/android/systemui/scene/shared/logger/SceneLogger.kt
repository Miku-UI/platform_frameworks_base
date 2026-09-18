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

package com.android.systemui.scene.shared.logger

import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger
import com.android.internal.logging.UiEventLogger.UiEventEnum
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.dagger.SceneFrameworkLog
import com.android.systemui.scene.data.model.SceneStack
import com.android.systemui.scene.domain.interactor.SceneInteractor
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

@SysUISingleton
class SceneLogger
@Inject
constructor(
    @SceneFrameworkLog private val logBuffer: LogBuffer,
    private val uiEventLogger: UiEventLogger,
    @Background private val backgroundContext: CoroutineContext,
) : ExclusiveActivatable() {

    private val queue = Channel<() -> Unit>(Channel.UNLIMITED)

    override suspend fun onActivated() {
        withContext(backgroundContext) { queue.receiveAsFlow().collect { runnable -> runnable() } }
    }

    private fun withQueue(block: () -> Unit) {
        queue.trySend(block)
    }

    fun logFrameworkEnabled(isEnabled: Boolean) = withQueue {
        fun asWord(isEnabled: Boolean): String {
            return if (isEnabled) "enabled" else "disabled"
        }

        logBuffer.log(
            tag = TAG,
            level = if (isEnabled) LogLevel.INFO else LogLevel.WARNING,
            messageInitializer = { bool1 = isEnabled },
            messagePrinter = { "Scene framework is ${asWord(bool1)}" },
        )

        uiEventLogger.log(
            if (isEnabled) {
                SceneContainerFrameworkEvent.SCENE_FRAMEWORK_ENABLED
            } else {
                SceneContainerFrameworkEvent.SCENE_FRAMEWORK_DISABLED
            }
        )
    }

    fun logSceneChanged(
        from: SceneKey,
        to: SceneKey,
        keyguardState: KeyguardState?,
        reason: String,
        isInstant: Boolean,
    ) = withQueue {
        logBuffer.log(
            tag = TAG,
            level = LogLevel.INFO,
            messageInitializer = {
                str1 = "${from.debugName} → ${to.debugName}"
                str2 = reason
                str3 = keyguardState?.toString()
                bool1 = isInstant
            },
            messagePrinter = {
                buildString {
                    append("Scene changed: $str1")
                    str3?.let { append(" (keyguardState=$it)") }
                    if (isInstant) {
                        append(" (instant)")
                    }
                    append(", reason: $str2")
                }
            },
        )
    }

    fun logSceneChangeCancellation(scene: SceneKey, keyguardState: Any?) = withQueue {
        logBuffer.log(
            tag = TAG,
            level = LogLevel.INFO,
            messageInitializer = {
                str1 = scene.debugName
                str2 = keyguardState?.toString()
            },
            messagePrinter = { "CANCELED scene change. scene: $str1, keyguardState: $str2" },
        )
    }

    fun falsingCheckForContentChange(
        from: ContentKey?,
        to: ContentKey?,
        isAllowedByFalsing: Boolean,
    ) = withQueue {
        logBuffer.log(
            tag = TAG,
            level = LogLevel.DEBUG,
            messageInitializer = {
                str1 = "${from?.debugName ?: "<none>"} → ${to?.debugName ?: "<none>"}"
                bool1 = to is OverlayKey
                bool2 = isAllowedByFalsing
            },
            messagePrinter = {
                buildString {
                    if (bool2) {
                        append("Falsing allows (don't REJECT) ")
                    } else {
                        append("Falsing does not allow (should REJECT in the future) ")
                    }
                    append(
                        if (bool1) {
                            "overlay "
                        } else {
                            "scene "
                        }
                    )
                    append("change $str1")
                }
            },
        )
    }

    fun logContentChangeRejection(
        from: ContentKey?,
        to: ContentKey?,
        originalChangeReason: String?,
        rejectionReason: String,
    ) = withQueue {
        logBuffer.log(
            tag = TAG,
            level = LogLevel.INFO,
            messageInitializer = {
                str1 = "${from?.debugName ?: "<none>"} → ${to?.debugName ?: "<none>"}"
                str2 = rejectionReason
                str3 = originalChangeReason
                bool1 = to is OverlayKey
            },
            messagePrinter = {
                buildString {
                    append("REJECTED ")
                    append(
                        if (bool1) {
                            "overlay "
                        } else {
                            "scene "
                        }
                    )
                    append("change $str1 because \"$str2\"")
                    if (str3 != null) {
                        append(" (original change reason: \"$str3\")")
                    }
                }
            },
        )
    }

    fun logSceneTransition(transitionState: ObservableTransitionState) = withQueue {
        when (transitionState) {
            is ObservableTransitionState.Transition -> {
                logBuffer.log(
                    tag = TAG,
                    level = LogLevel.INFO,
                    messageInitializer = {
                        str1 = transitionState.fromContent.debugName
                        str2 = transitionState.toContent.debugName
                    },
                    messagePrinter = { "Scene transition started: $str1 → $str2" },
                )
            }
            is ObservableTransitionState.Idle -> {
                logBuffer.log(
                    tag = TAG,
                    level = LogLevel.INFO,
                    messageInitializer = {
                        str1 = transitionState.currentScene.debugName
                        str2 = transitionState.currentOverlays.joinToString { it.debugName }
                    },
                    messagePrinter = { "Scene transition idle on: $str1, overlays: [$str2]" },
                )
            }
        }
    }

    fun logOverlayChangeRequested(
        from: OverlayKey? = null,
        to: OverlayKey? = null,
        reason: String,
    ) = withQueue {
        logBuffer.log(
            tag = TAG,
            level = LogLevel.INFO,
            messageInitializer = {
                str1 = from?.debugName
                str2 = to?.debugName
                str3 = reason
            },
            messagePrinter = {
                buildString {
                    append("Overlay change requested: ")
                    if (str1 != null) {
                        append(str1)
                        append(if (str2 == null) " (hidden)" else " → $str2")
                    } else {
                        append("$str2 (shown)")
                    }
                    append(", reason: $str3")
                }
            },
        )
    }

    fun logEvent(event: SceneInteractor.Event) = withQueue {
        logBuffer.log(
            tag = TAG,
            level = LogLevel.INFO,
            messageInitializer = { str1 = "Event: $event" },
            messagePrinter = { "$str1" },
        )
    }

    fun logVisibilityChange(from: Boolean, to: Boolean, reason: String) = withQueue {
        fun asWord(isVisible: Boolean): String {
            return if (isVisible) "visible" else "invisible"
        }

        logBuffer.log(
            tag = TAG,
            level = LogLevel.INFO,
            messageInitializer = {
                str1 = asWord(from)
                str2 = asWord(to)
                str3 = reason
            },
            messagePrinter = { "VISIBILITY CHANGED: $str1 → $str2, reason: $str3" },
        )
    }

    fun logVisibilityRejection(to: Boolean, reason: String) = withQueue {
        fun asWord(isVisible: Boolean): String {
            return if (isVisible) "visible" else "invisible"
        }

        logBuffer.log(
            tag = TAG,
            level = LogLevel.INFO,
            messageInitializer = {
                str1 = asWord(to)
                str2 = reason
            },
            messagePrinter = { "VISIBILITY UNCHANGED: still $str1, new reason: $str2" },
        )
    }

    fun logRemoteUserInputStarted(reason: String) = withQueue {
        logBuffer.log(
            tag = TAG,
            level = LogLevel.INFO,
            messageInitializer = { str1 = reason },
            messagePrinter = { "remote user interaction started, reason: $str1" },
        )
    }

    fun logUserInputFinished(transitionState: TransitionState) = withQueue {
        when (transitionState) {
            is TransitionState.Transition -> {
                logBuffer.log(
                    tag = TAG,
                    level = LogLevel.INFO,
                    messageInitializer = {
                        str1 = transitionState.fromContent.debugName
                        str2 = transitionState.toContent.debugName
                    },
                    messagePrinter = { "User interaction finished during: $str1 → $str2" },
                )
            }
            is TransitionState.Idle -> {
                logBuffer.log(
                    tag = TAG,
                    level = LogLevel.INFO,
                    messageInitializer = {
                        str1 = transitionState.currentScene.debugName
                        str2 = transitionState.currentOverlays.joinToString { it.debugName }
                    },
                    messagePrinter = { "User interaction finished on: $str1, overlays: [$str2]" },
                )
            }
        }
    }

    fun logSceneBackStack(backStack: SceneStack, reason: String) = withQueue {
        logBuffer.log(
            tag = TAG,
            level = LogLevel.INFO,
            messageInitializer = {
                str1 = backStack.toString()
                str2 = reason
            },
            messagePrinter = { "back stack: $str1, reason: $str2" },
        )
    }

    companion object {
        private const val TAG = "SceneFramework"
    }

    private enum class SceneContainerFrameworkEvent(private val id: Int) : UiEventEnum {
        @UiEvent(doc = "The scene framework is enabled") SCENE_FRAMEWORK_ENABLED(2520),
        @UiEvent(doc = "The scene framework is not enabled") SCENE_FRAMEWORK_DISABLED(2521);

        override fun getId(): Int {
            return id
        }
    }
}
