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
package com.android.wm.shell.hierarchy.properties

import android.content.pm.UserInfo
import android.view.Display.DEFAULT_DISPLAY
import android.window.TransitionInfo
import com.android.wm.shell.dagger.hierarchy.WmSyncedProperty
import com.android.wm.shell.hierarchy.updates.HierarchyChangeFlags
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_FOCUS
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_IS_FOLDED
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_KEYGUARD
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_USER
import com.android.wm.shell.hierarchy.updates.HierarchySnapshot.Companion.CHANGED_USER_PROFILES
import com.android.wm.shell.transition.FocusTransitionObserver

/**
 * Tracks the currently focused state in the hierarchy.
 */
class FocusState {
    var globallyFocusedDisplayId: Int = DEFAULT_DISPLAY
    var globallyFocusedTaskId: Int = -1
    var perDisplayFocusedTaskId: MutableMap<Int, Int> = mutableMapOf()

    fun copyFrom(other: FocusState) {
        globallyFocusedDisplayId = other.globallyFocusedDisplayId
        globallyFocusedTaskId = other.globallyFocusedTaskId
        perDisplayFocusedTaskId.clear()
        perDisplayFocusedTaskId.putAll(other.perDisplayFocusedTaskId)
    }

    fun diff(other: FocusState, chgs: HierarchyChangeFlags) {
        chgs.compareAndSet(globallyFocusedDisplayId, other.globallyFocusedDisplayId, CHANGED_FOCUS)
        chgs.compareAndSet(globallyFocusedTaskId, other.globallyFocusedTaskId, CHANGED_FOCUS)
        chgs.compareAndSet(perDisplayFocusedTaskId, other.perDisplayFocusedTaskId, CHANGED_FOCUS)
    }

    override fun toString(): String {
        return "FOCUS display=$globallyFocusedDisplayId task=$globallyFocusedTaskId"
    }
}

/**
 * Tracks the current user & profiles.
 */
class UserState {
    var currentUserId = 0
    var currentUserProfiles = mutableListOf<UserInfo>()

    fun copyFrom(other: UserState) {
        currentUserId = other.currentUserId
        currentUserProfiles.clear()
        currentUserProfiles.addAll(other.currentUserProfiles)
    }

    fun diff(other: UserState, chgs: HierarchyChangeFlags) {
        chgs.compareAndSet(currentUserId, other.currentUserId, CHANGED_USER)
        chgs.compareAndSet(currentUserProfiles, other.currentUserProfiles, CHANGED_USER_PROFILES)
    }

    override fun toString(): String {
        val profileUsers = currentUserProfiles
            .map { it.id }
            .joinToString(prefix = "[", postfix = "]")
        return "USER curUserId=$currentUserId profiles=$profileUsers"
    }
}

/**
 * Tracks the device state.
 */
class DeviceState {
    var keyguardState = KeyguardState.Unlocked
    var isFolded = false

    // This is just a setting that's usually used on demand, so we don't need to propagate changes
    // of this setting to the modes for now
    var onFoldSetting = OnFoldSetting.Sleep

    fun copyFrom(other: DeviceState) {
        keyguardState = other.keyguardState
        isFolded = other.isFolded
    }

    fun diff(other: DeviceState, chgs: HierarchyChangeFlags) {
        chgs.compareAndSet(keyguardState, other.keyguardState, CHANGED_KEYGUARD)
        chgs.compareAndSet(isFolded, other.isFolded, CHANGED_IS_FOLDED)
    }

    override fun toString(): String {
        val keyguard =
            if (keyguardState != KeyguardState.Unlocked) "keyguard=${keyguardState.name}" else ""
        val isFolded = if (isFolded) "isFolded" else ""
        val stateStr = listOf(keyguard, isFolded)
            .filter { it.isNotEmpty() }
            .joinToString()
        return if (stateStr.isNotEmpty()) "DEVICE $stateStr" else ""
    }

    // The current state of the keyguard
    enum class KeyguardState {
        Unlocked,
        Locked,
        Occluded,
    }

    // The current setting for what to do when a foldable device is folded
    enum class OnFoldSetting {
        StayAwake,
        SelectiveStayAwake,
        Sleep,
    }
}

/**
 * Properties for the root container in the hierarchy.
 */
class RootContainerProperties : ContainerProperties() {

    // The global focus state of the hierarchy
    @WmSyncedProperty
    val focusState = FocusState()

    // The current user info
    val userState = UserState()

    // The current device info
    val deviceState = DeviceState()

    private val focusTransitionObserver = FocusTransitionObserver()

    /**
     * Updates the root of the hierarchy from any ongoing transition.
     */
    fun updateFromTransition(info: TransitionInfo) {
        focusTransitionObserver.updateFocusState(info)
        focusState.globallyFocusedDisplayId = focusTransitionObserver.globallyFocusedDisplayId
        focusState.globallyFocusedTaskId = focusTransitionObserver.globallyFocusedTaskId
        focusTransitionObserver.setFocusedTaskIdsPerDisplay(focusState.perDisplayFocusedTaskId)
    }

    /** @see ContainerProperties.copyFrom */
    override fun copyFrom(other: ContainerProperties) {
        val otherRoot = other as RootContainerProperties
        focusState.copyFrom(otherRoot.focusState)
        userState.copyFrom(otherRoot.userState)
        deviceState.copyFrom(otherRoot.deviceState)
        super.copyFrom(other)
    }

    /** @see ContainerProperties.copy */
    override fun copy(): RootContainerProperties {
        return RootContainerProperties().apply {
            copyFrom(this@RootContainerProperties)
        }
    }

    /** @see ContainerProperties.propsToString */
    override fun diff(other: ContainerProperties, chgs: HierarchyChangeFlags) {
        super.diff(other, chgs)
        val otherRoot = other as RootContainerProperties
        focusState.diff(otherRoot.focusState, chgs)
        userState.diff(otherRoot.userState, chgs)
        deviceState.diff(otherRoot.deviceState, chgs)
    }

    /** @see ContainerProperties.propsToString */
    override fun propsToString(): String {
        return super.propsToString() + " | " +
                listOf(userState, focusState, deviceState).joinToString(separator = " ")
    }

    /** @see ContainerProperties.getTypeName */
    override fun getTypeName(): String {
        return "Root"
    }
}