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

package com.android.systemui.ambientcue.data.repository

import android.graphics.Rect
import com.android.systemui.plugins.cuebar.ActionModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

val AmbientCueRepository.fake
    get() = this as FakeAmbientCueRepository

class FakeAmbientCueRepository : AmbientCueRepository {
    private val _actions = MutableStateFlow(emptyList<ActionModel>())
    override val actions: StateFlow<List<ActionModel>> = _actions.asStateFlow()

    private val _isRootViewAttached: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val isRootViewAttached: StateFlow<Boolean> = _isRootViewAttached.asStateFlow()

    override val isImeVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val _globallyFocusedTaskId = MutableStateFlow(0)
    override val globallyFocusedTaskId: StateFlow<Int> = _globallyFocusedTaskId.asStateFlow()

    private val targetTaskId: MutableStateFlow<Int> = MutableStateFlow(0)
    override val isDeactivated: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val _isTaskBarVisible = MutableStateFlow(false)
    override val isTaskBarVisible: StateFlow<Boolean> = _isTaskBarVisible.asStateFlow()

    private val _isGestureNav = MutableStateFlow(false)
    override val isGestureNav: StateFlow<Boolean> = _isGestureNav.asStateFlow()

    private val _recentsButtonPosition = MutableStateFlow<Rect?>(null)
    override val recentsButtonPosition: StateFlow<Rect?> = _recentsButtonPosition.asStateFlow()

    private val _isAmbientCueEnabled = MutableStateFlow(false)
    override val isAmbientCueEnabled: StateFlow<Boolean> = _isAmbientCueEnabled.asStateFlow()

    private val _ambientCueTimeoutMs = MutableStateFlow(0)
    override val ambientCueTimeoutMs: StateFlow<Int> = _ambientCueTimeoutMs.asStateFlow()

    fun setActions(actions: List<ActionModel>) {
        _actions.update { actions }
    }

    fun setGloballyFocusedTaskId(taskId: Int) {
        _globallyFocusedTaskId.update { taskId }
    }

    fun updateRootViewAttached() {
        _isRootViewAttached.update {
            actions.value.isNotEmpty() &&
                !isDeactivated.value &&
                targetTaskId.value == globallyFocusedTaskId.value
        }
    }

    fun setTaskBarVisible(visible: Boolean) {
        _isTaskBarVisible.update { visible }
    }

    fun setIsGestureNav(isGestureNav: Boolean) {
        _isGestureNav.update { isGestureNav }
    }

    fun setRecentsButtonPosition(recentsButtonPosition: Rect) {
        _recentsButtonPosition.update { recentsButtonPosition }
    }

    fun setAmbientCueEnabled(isEnabled: Boolean) {
        _isAmbientCueEnabled.update { isEnabled }
    }

    fun setAmbientCueTimeoutMs(timeoutMs: Int) {
        _ambientCueTimeoutMs.update { timeoutMs }
    }
}
