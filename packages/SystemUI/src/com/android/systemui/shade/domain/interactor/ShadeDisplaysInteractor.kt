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

package com.android.systemui.shade.domain.interactor

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.util.Log
import android.window.WindowContext
import androidx.annotation.UiThread
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.ShadeDisplayChangePerformanceTracker
import com.android.systemui.shade.ShadeDisplayLog
import com.android.systemui.shade.ShadeTraceLogger.logMoveShadeWindowTo
import com.android.systemui.shade.ShadeTraceLogger.t
import com.android.systemui.shade.ShadeTraceLogger.traceReparenting
import com.android.systemui.shade.data.repository.MutableShadeDisplaysRepository
import com.android.systemui.shade.data.repository.ShadeDisplaysRepository
import com.android.systemui.shade.display.ShadeExpansionIntent
import com.android.systemui.shade.domain.interactor.ShadeExpandedStateInteractor.ShadeElement
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.stack.NotificationStackRebindingHider
import com.android.systemui.statusbar.phone.ConfigurationForwarder
import com.android.window.flags.Flags
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Handles Shade window display change when [ShadeDisplaysRepository.displayId] changes. */
interface ShadeDisplaysInteractor {
    /** Current display id of the shade window. */
    val displayId: StateFlow<Int>
    /**
     * Target display id of the shade window.
     *
     * Triggers the window move. Once committed, [displayId] will match this.
     */
    val pendingDisplayId: StateFlow<Int>
}

@SysUISingleton
class ShadeDisplaysInteractorImpl
@Inject
constructor(
    private val shadePositionRepository: MutableShadeDisplaysRepository,
    @ShadeDisplayAware private val shadeContext: WindowContext,
    @Background private val bgScope: CoroutineScope,
    @Main private val mainThreadContext: CoroutineContext,
    private val shadeDisplayChangePerformanceTracker: ShadeDisplayChangePerformanceTracker,
    private val shadeExpandedInteractor: ShadeExpandedStateInteractor,
    private val shadeExpansionIntent: ShadeExpansionIntent,
    private val activeNotificationsInteractor: ActiveNotificationsInteractor,
    private val notificationStackRebindingHider: NotificationStackRebindingHider,
    @ShadeDisplayAware private val configForwarder: ConfigurationForwarder,
    @ShadeDisplayLog private val logBuffer: LogBuffer,
    private val waitInteractor: ShadeDisplaysWaitInteractor,
) : ShadeDisplaysInteractor, CoreStartable {

    private val hasActiveNotifications: Boolean
        get() = activeNotificationsInteractor.areAnyNotificationsPresentValue

    override val displayId: StateFlow<Int> = shadePositionRepository.displayId

    override val pendingDisplayId: StateFlow<Int> = shadePositionRepository.pendingDisplayId

    override fun start() {
        ShadeWindowGoesAround.isUnexpectedlyInLegacyMode()
        listenForWindowContextConfigChanges()
        bgScope.launchTraced(TAG) {
            shadePositionRepository.pendingDisplayId.collectLatest { displayId ->
                moveShadeWindowTo(displayId)
            }
        }
    }

    private fun listenForWindowContextConfigChanges() {
        shadeContext.registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    configForwarder.onConfigurationChanged(newConfig)
                }

                override fun onLowMemory() {}
            }
        )
    }

    /** Tries to move the shade. If anything wrong happens, fails gracefully without crashing. */
    private suspend fun moveShadeWindowTo(destinationId: Int) {
        logBuffer.log(
            TAG,
            LogLevel.DEBUG,
            { int1 = destinationId },
            { "Trying to move shade window to display with id $int1" },
        )
        logMoveShadeWindowTo(destinationId)
        var currentId = -1
        try {
            // Why using the shade context here instead of the view's Display?
            // The context's display is updated before the view one, so it is a better indicator of
            // which display the shade is supposed to be at. The View display is updated after the
            // first
            // rendering with the new config.
            val currentDisplay = shadeContext.display ?: error("Current shade display is null")
            currentId = currentDisplay.displayId
            if (currentId == destinationId) {
                logBuffer.log(
                    TAG,
                    LogLevel.WARNING,
                    { int1 = currentId },
                    { "Trying to move the shade to a display ($int1) it was already in." },
                )
                return
            }

            withContext(mainThreadContext) {
                traceReparenting {
                    collapseAndExpandShadeIfNeeded(destinationId) {
                        shadeDisplayChangePerformanceTracker.onShadeDisplayChanging(destinationId)
                        reparentToDisplayId(id = destinationId)
                    }
                    checkContextDisplayMatchesExpected(destinationId)
                    shadePositionRepository.onDisplayChangedSucceeded(destinationId)
                    logBuffer.log(
                        TAG,
                        LogLevel.DEBUG,
                        {
                            int1 = currentId
                            int2 = destinationId
                        },
                        { "Shade window successfully moved from display $int1 to $int2" },
                    )
                }
            }
        } catch (e: IllegalStateException) {
            logBuffer.log(
                TAG,
                LogLevel.ERROR,
                {
                    int1 = currentId
                    int2 = destinationId
                },
                { "Unable to move the shade window from display $int1 to $int2" },
                e,
            )
        }
    }

    private suspend fun collapseAndExpandShadeIfNeeded(newDisplayId: Int, reparent: () -> Unit) {
        val previouslyExpandedElement: ShadeElement? =
            shadeExpandedInteractor.currentlyExpandedElement.value

        // The next expanded element depends on the reason why the shade is changing window. e.g. if
        // the trigger was a status bar swipe, based on the swipe location we might want to open
        // quick settings or notifications next (in case of dual shade)
        val nextExpandedElement =
            shadeExpansionIntent.consumeExpansionIntent() ?: previouslyExpandedElement

        // We first collapse the shade only if the element to show after the reparenting is
        // different (e.g. if notifications where visible, but now the user is expanding quick
        // settings in a different display, with dual shade enabled).
        // If we didn't do this, there would be some flicker where the previous element appear for
        // some time.
        val needsToCollapseThenExpand = previouslyExpandedElement != nextExpandedElement

        if (needsToCollapseThenExpand) {
            // We could also consider adding an API to collapse/hide the previous instantaneously in
            // the future.
            previouslyExpandedElement?.collapse(reason = COLLAPSE_EXPAND_REASON)
        }

        val notificationStackHidden =
            if (!hasActiveNotifications) {
                // This covers the case the previous move was cancelled before setting the
                // visibility back. As there are no notifications, nothing can flicker here, and
                // showing them all of a sudden is ok.
                notificationStackRebindingHider.setVisible(visible = true, animated = false)
                false
            } else {
                // Hiding as otherwise there might be flickers as the inflation with new dimensions
                // happens async and views with the old dimensions are not removed until the
                // inflation succeeds.
                notificationStackRebindingHider.setVisible(visible = false, animated = false)
                true
            }

        reparent()

        if (needsToCollapseThenExpand) {
            waitForOnMovedToDisplayDispatchedToView(newDisplayId)
            // Let's make sure a frame has been drawn with the new configuration before expanding
            // the shade again, otherwise we might end up having a flicker.
            waitForNextFrameDrawn(newDisplayId)
            nextExpandedElement?.expand(reason = COLLAPSE_EXPAND_REASON)
        }
        if (notificationStackHidden) {
            if (hasActiveNotifications) {
                // "onMovedToDisplay" is what synchronously triggers the rebinding of views: we need
                // to wait for it to be received.
                waitForOnMovedToDisplayDispatchedToView(newDisplayId)
                waitForNotificationsRebinding()
            }
            notificationStackRebindingHider.setVisible(visible = true, animated = true)
        }
    }

    private suspend fun waitForOnMovedToDisplayDispatchedToView(newDisplayId: Int) {
        withContext(bgScope.coroutineContext) {
            withTimeoutOrNull(WAIT_TIMEOUT) {
                waitInteractor.waitForOnMovedToDisplayDispatchedToView(newDisplayId, TAG)
            }
                ?: errorLog(
                    "Timed out while waiting for onMovedToDisplay to be dispatched to " +
                        "the shade root view"
                )
        }
    }

    private suspend fun waitForNextFrameDrawn(newDisplayId: Int) {
        withContext(bgScope.coroutineContext) {
            withTimeoutOrNull(WAIT_TIMEOUT) {
                waitInteractor.waitForNextDoFrameDone(newDisplayId, TAG)
            } ?: errorLog("Timed out while waiting for the next frame to be drawn.")
        }
    }

    private suspend fun waitForNotificationsRebinding() {
        withContext(bgScope.coroutineContext) {
            withTimeoutOrNull(WAIT_TIMEOUT) { waitInteractor.waitForNotificationsRebinding(TAG) }
                ?: errorLog("Timed out while waiting for inflations to finish")
        }
    }

    private fun checkContextDisplayMatchesExpected(destinationId: Int) {
        if (shadeContext.displayId != destinationId) {
            Log.wtf(
                TAG,
                "Shade context display id doesn't match the expected one after the move. " +
                    "actual=${shadeContext.displayId} expected=$destinationId. " +
                    "This means something wrong happened while trying to move the shade. " +
                    "Flag reparentWindowTokenApi=${Flags.reparentWindowTokenApi()}",
            )
        }
    }

    private fun errorLog(s: String) {
        logBuffer.log(TAG, LogLevel.ERROR, s)
    }

    @UiThread
    private fun reparentToDisplayId(id: Int) {
        t.traceSyncAndAsync({ "reparentToDisplayId(id=$id)" }) {
            shadeContext.reparentToDisplay(id)
        }
    }

    private companion object {
        val WAIT_TIMEOUT = 1.seconds
        const val TAG = "ShadeDisplaysInteractor"
        const val COLLAPSE_EXPAND_REASON = "Shade window move"
    }
}
