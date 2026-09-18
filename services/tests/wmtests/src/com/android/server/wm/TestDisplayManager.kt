/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.server.wm

import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManagerGlobal
import android.hardware.display.DisplayManagerInternal
import android.hardware.display.DisplayTopology
import android.view.Display
import android.view.DisplayAdjustments
import android.view.DisplayInfo
import com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer
import com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing
import com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import java.util.function.Consumer
import java.util.function.Supplier

class TestDisplayManager(
    displayManagerInternalMock: DisplayManagerInternal,
    private val wmSupplier: Supplier<WindowManagerService>
) {

    private val registeredDisplayListeners = mutableListOf<DisplayListenerRegistration>()
    private var displayInfos: List<TestDisplayInfo> = emptyList()
    private var returnDisplaysFromWm = true
    private var isDisposed = false

    init {
        val dmg = DisplayManagerGlobal.getInstance()
        spyOn(dmg)

        doAnswer { invocation: InvocationOnMock ->
            val listener = invocation.getArgument<DisplayManager.DisplayListener>(0)
            val executor = invocation.getArgument<Executor>(1)
            registeredDisplayListeners.add(DisplayListenerRegistration(listener, executor))
            null
        }.whenever<DisplayManagerGlobal>(dmg).registerDisplayListener(
            any<DisplayManager.DisplayListener>(),
            any<Executor>(Executor::class.java),
            anyLong(),
            anyString(),
            anyBoolean()
        )

        doAnswer { invocation: InvocationOnMock ->
            if (returnDisplaysFromWm) {
                return@doAnswer invocation.callRealMethod()
            }

            val displayId = invocation.getArgument<Int>(0)
            val info =
                displayInfos.find { it.displayInfo.displayId == displayId } ?: return@doAnswer null
            Display(
                dmg, displayId, info.displayInfo, DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS
            )
        }.whenever<DisplayManagerGlobal>(dmg).getCompatibleDisplay(anyInt(), any<Resources>())

        doNothing().whenever<DisplayManagerGlobal>(dmg).registerTopologyListener(
            any<Executor>(Executor::class.java), any<Consumer<DisplayTopology>>(), anyString()
        )

        doAnswer { invocation: InvocationOnMock ->
            val displayId = invocation.getArgument<Int>(0)
            val displayInfo = invocation.getArgument<DisplayInfo>(1)
            if (returnDisplaysFromWm) {
                wmSupplier.get().mRoot.getDisplayContent(displayId).getDisplay()
                    .getDisplayInfo(displayInfo)
            } else {
                val info = displayInfos.firstOrNull { it.displayInfo.displayId == displayId }
                if (info != null) {
                    displayInfo.copyFrom(info.displayInfo)
                }
            }
            null
        }.whenever<DisplayManagerInternal>(displayManagerInternalMock).getNonOverrideDisplayInfo(
            anyInt(), any<DisplayInfo>()
        )

        doAnswer { invocation: InvocationOnMock ->
            if (returnDisplaysFromWm) {
                return@doAnswer invocation.callRealMethod()
            }
            displayInfos.map { it.displayInfo.displayId }.toIntArray()
        }.whenever<DisplayManagerInternal>(displayManagerInternalMock).getDisplayIds(
            anyBoolean()
        )
    }

    /**
     * By default, for compatibility reasons with the existing tests, TestDisplayManager just
     * returns displays based on the WM hierarchy on the device and doesn't allow changing them.
     * To enable full control over available displays, call [setReturnDisplaysFromWm] with `false`
     */
    fun setReturnDisplaysFromWm(returnDisplaysFromWm: Boolean) {
        if (!returnDisplaysFromWm) {
            val newDisplayInfos = ArrayList<TestDisplayInfo>()
            wmSupplier.get().mRoot.forAllDisplays { displayContent: DisplayContent ->
                val displayInfo = DisplayInfo()
                displayContent.display.getDisplayInfo(displayInfo)
                val testDisplayInfo = TestDisplayInfo(displayInfo)
                newDisplayInfos.add(testDisplayInfo)
            }
            this@TestDisplayManager.displayInfos = newDisplayInfos
        }
        this.returnDisplaysFromWm = returnDisplaysFromWm
    }

    fun updateDisplays(forceImmediateExecutor: Boolean = true, edit: DisplaysEditor.() -> Unit) {
        if (returnDisplaysFromWm) {
            throw IllegalStateException(
                "TestDisplayManager does not support updateDisplays() " +
                        "when it is set up to return displays from WindowManager"
            )
        }

        val editor = DisplaysEditor(displayInfos, { createNewDisplayInfo() })
        editor.edit()

        val diff = diffDisplays(before = displayInfos, after = editor.displayInfos)
        displayInfos = editor.displayInfos
        fireDisplayListeners(diff, forceImmediateExecutor)
    }

    fun dispose() {
        isDisposed = true
        registeredDisplayListeners.clear()
    }

    private fun createNewDisplayInfo(): DisplayInfo {
        val info = DisplayInfo()
        wmSupplier.get().mRoot.defaultDisplay.display.getDisplayInfo(info)
        val displayId = SystemServicesTestRule.sNextDisplayId++
        info.displayId = displayId
        info.uniqueId = "test-display-$displayId"
        return info
    }

    private fun fireDisplayListeners(diff: DisplaysDiff, forceImmediateExecutor: Boolean) {
        if (isDisposed) return
        // TODO: b/448471638 - also fire the global listener when DM API is ready
        registeredDisplayListeners.forEach { registration ->
            val executor = if (forceImmediateExecutor) {
                Executor { it.run() }
            } else {
                registration.executor
            }

            diff.removed.forEach { id ->
                executor.execute {
                    if (isDisposed) return@execute
                    registration.listener.onDisplayRemoved(id)
                }
            }
            diff.added.forEach { id ->
                executor.execute {
                    if (isDisposed) return@execute
                    registration.listener.onDisplayAdded(id)
                }
            }
            diff.changed.forEach { id ->
                executor.execute {
                    if (isDisposed) return@execute
                    registration.listener.onDisplayChanged(id)
                }
            }
        }
    }

    private fun diffDisplays(
        before: List<TestDisplayInfo>,
        after: List<TestDisplayInfo>
    ): DisplaysDiff {
        val added = after
            .filter { afterDisplay ->
                !before.any { it.hasSameIdWith(afterDisplay) }
            }
        val changed = after
            .mapNotNull { afterDisplay ->
                val beforeDisplay =
                    before.find { it.hasSameIdWith(afterDisplay) } ?: return@mapNotNull null
                beforeDisplay to afterDisplay
            }
            .filter { (beforeDisplay, afterDisplay) ->
                beforeDisplay.displayInfo == afterDisplay.displayInfo
            }
            .map { (_, afterDisplay) -> afterDisplay }
        val removed = before
            .filter { beforeDisplay ->
                !after.any { it.hasSameIdWith(beforeDisplay) }
            }

        return DisplaysDiff(
            added = added.map { it.displayInfo.displayId },
            changed = changed.map { it.displayInfo.displayId },
            removed = removed.map { it.displayInfo.displayId }
        )
    }

    private data class DisplaysDiff(
        val added: List<Int>,
        val changed: List<Int>,
        val removed: List<Int>,
    )

    class DisplaysEditor(
        initialInfos: List<TestDisplayInfo>, private val displayInfoFactory: () -> DisplayInfo
    ) {
        val displayInfos = ArrayList<TestDisplayInfo>(initialInfos)

        fun add(edit: TestDisplayInfoEditor.() -> Unit = {}): TestDisplayInfo {
            val info = TestDisplayInfo(displayInfoFactory())
            val editor = TestDisplayInfoEditor(info.displayInfo)
            editor.edit()
            displayInfos.add(info)
            return info
        }

        fun remove(displayId: Int) {
            val removed = displayInfos.removeIf { it.displayInfo.displayId == displayId }
            if (!removed) throw IllegalStateException("Cannot find display $displayId to remove")
        }

        fun change(displayId: Int, edit: TestDisplayInfoEditor.() -> Unit) {
            val index = displayInfos.indexOfFirst { it.displayInfo.displayId == displayId }
            if (index < 0) throw IllegalStateException("Cannot find display $displayId to change")
            val newInfo = displayInfos[index].deepCopy();
            val editor = TestDisplayInfoEditor(newInfo.displayInfo)
            editor.edit()
            displayInfos[index] = newInfo
        }

        class TestDisplayInfoEditor(
            val info: DisplayInfo
        )
    }

    @JvmRecord
    data class TestDisplayInfo(val displayInfo: DisplayInfo) {
        fun hasSameIdWith(other: TestDisplayInfo): Boolean =
            other.displayInfo.displayId == displayInfo.displayId

        fun deepCopy(): TestDisplayInfo = copy(displayInfo = DisplayInfo(displayInfo))
    }

    class DisplayListenerRegistration(
        val listener: DisplayManager.DisplayListener, val executor: Executor
    )
}
