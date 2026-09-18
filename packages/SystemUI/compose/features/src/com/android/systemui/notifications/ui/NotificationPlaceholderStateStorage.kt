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

package com.android.systemui.notifications.ui

import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.Scale
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.notifications.ui.composable.HeadsUpPlaceholderContentPicker
import com.android.systemui.notifications.ui.composable.NotificationContentPicker
import com.android.systemui.notifications.ui.composable.StackPlaceholderContentPicker
import com.android.systemui.util.state.SynchronouslyObservableStateMap
import java.io.PrintWriter
import javax.inject.Inject

/** Data store for stack placeholder properties that are meant to be bound to the NSSL. */
@SysUISingleton
class NotificationPlaceholderStateStorage
@Inject
constructor(
    private val hunPicker: HeadsUpPlaceholderContentPicker,
    private val stackPicker: StackPlaceholderContentPicker,
    dumpManager: DumpManager,
) : Dumpable {

    init {
        dumpManager.registerNormalDumpable(this)
    }

    /** Y coordinate for the top of the notification stack, including the scroll offset. */
    val stackScrollTop: SynchronouslyObservableStateMap<ContentKey, Float, Float> =
        SynchronouslyObservableStateMap { contentKeyToValuesMap ->
            stackPicker.pickValueFrom(contentKeyToValuesMap) ?: 0f
        }

    /** Set a value for [stackScrollTop] associated by the given [contentKey]. */
    fun setStackScrollTop(contentKey: ContentKey, value: Float) {
        stackScrollTop[contentKey] = value
    }

    /** Removes any stored [stackScrollTop] value for the given [contentKey]. */
    fun resetStackScrollTop(contentKey: ContentKey) {
        stackScrollTop.remove(contentKey)
    }

    /** Vertical bounds for the user visible area of the notification stack. */
    val stackBounds: SynchronouslyObservableStateMap<ContentKey, YSpace, YSpace> =
        SynchronouslyObservableStateMap { contentKeyToValuesMap ->
            stackPicker.pickValueFrom(contentKeyToValuesMap) ?: YSpace.Zero
        }

    /** Set a value for [stackBounds] associated by the given [contentKey]. */
    fun setStackBounds(contentKey: ContentKey, space: YSpace) {
        stackBounds[contentKey] = space
    }

    /** Removes any stored [stackBounds] value for the given [contentKey]. */
    fun resetStackBounds(contentKey: ContentKey) {
        stackBounds.remove(contentKey)
    }

    /** Vertical bounds of the top HUN. */
    val hunBounds: SynchronouslyObservableStateMap<ContentKey, YSpace, YSpace> =
        SynchronouslyObservableStateMap { contentKeyToValuesMap ->
            hunPicker.pickValueFrom(contentKeyToValuesMap) ?: YSpace.Zero
        }

    /** Set a value for [hunBounds] associated by the given [contentKey]. */
    fun setHunBounds(contentKey: ContentKey, space: YSpace) {
        hunBounds[contentKey] = space
    }

    /** Removes any stored [hunBounds] value for the given [contentKey]. */
    fun resetHunBounds(contentKey: ContentKey) {
        hunBounds.remove(contentKey)
    }

    /** Alpha requested by the StackPlaceholder STL element. */
    val stackAlpha: SynchronouslyObservableStateMap<ContentKey, Float?, Float> =
        SynchronouslyObservableStateMap { contentKeyToValuesMap ->
            stackPicker.pickValueFrom(contentKeyToValuesMap) ?: 1f
        }

    /** Set a value for [stackAlpha] associated by the given [contentKey]. */
    fun setStackAlpha(contentKey: ContentKey, value: Float?) {
        stackAlpha[contentKey] = value
    }

    /** Removes any stored [stackAlpha] value for the given [contentKey]. */
    fun resetStackAlpha(contentKey: ContentKey) {
        stackAlpha.remove(contentKey)
    }

    /** Draw scale requested by the StackPlaceholder STL element. */
    val stackScale: SynchronouslyObservableStateMap<ContentKey, Scale?, Scale> =
        SynchronouslyObservableStateMap { contentKeyToValuesMap ->
            stackPicker.pickValueFrom(contentKeyToValuesMap) ?: Scale.Default
        }

    /** Set a value for [stackScale] associated by the given [contentKey]. */
    fun setStackScale(contentKey: ContentKey, value: Scale?) {
        stackScale[contentKey] = value
    }

    /** Removes any stored [stackScale] value for the given [contentKey]. */
    fun resetStackScale(contentKey: ContentKey) {
        stackScale.remove(contentKey)
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("stackScrollTop: $stackScrollTop")
        pw.println("stackBounds: $stackBounds")
        pw.println("hunBounds: $hunBounds")
        pw.println("stackAlpha: $stackAlpha")
        pw.println("stackScale: $stackScale")
    }
}

/** Returns a value from the provided [map] or null. */
private fun <V> NotificationContentPicker.pickValueFrom(map: Map<ContentKey, V>): V? {
    return pickContentFrom(map.keys)?.let { found -> map[found] }
}
