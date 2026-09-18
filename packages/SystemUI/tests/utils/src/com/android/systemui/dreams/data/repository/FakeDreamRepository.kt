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

package com.android.systemui.dreams.data.repository

import android.content.ComponentName
import android.os.UserHandle
import com.android.systemui.dreams.shared.model.DreamItemModel
import com.android.systemui.dreams.shared.model.DreamPlaylistModel
import com.android.systemui.user.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class FakeDreamRepository(userRepository: UserRepository) : DreamRepository {

    private val dreamStates = mutableMapOf<Int, MutableStateFlow<DreamPlaylistModel>>()

    private val _activeDreamStateCollectors = MutableStateFlow(0)
    val activeDreamStateCollectors: Flow<Int> = _activeDreamStateCollectors

    private val _dreamSwitcherDialogShowing = MutableStateFlow(false)
    override val dreamSwitcherDialogShowing = _dreamSwitcherDialogShowing.asStateFlow()

    private var _isDreamSwitcherEnabled: Boolean = false

    override val isDreamSwitcherEnabled: Boolean
        get() = _isDreamSwitcherEnabled

    override val dreamState: Flow<DreamPlaylistModel> =
        userRepository.selectedUser
            .map { it.userInfo.id }
            .flatMapLatest { userId -> getFlowForUser(userId) }
            .onStart { _activeDreamStateCollectors.value++ }
            .onCompletion { _activeDreamStateCollectors.value-- }

    override suspend fun setActiveDream(componentName: ComponentName, user: UserHandle): Boolean {
        val flow = getFlowForUser(user.identifier)
        val currentModel = flow.value

        val existingIndex = currentModel.dreams.indexOfFirst { it.componentName == componentName }

        val newModel =
            if (existingIndex != -1) {
                currentModel.copy(activeIndex = existingIndex)
            } else {
                val newDream = DreamItemModel(componentName = componentName)
                val newDreams = currentModel.dreams + newDream
                DreamPlaylistModel(dreams = newDreams, activeIndex = newDreams.lastIndex)
            }

        flow.value = newModel
        return true
    }

    override fun setSwitcherDialogShowing(showing: Boolean) {
        _dreamSwitcherDialogShowing.update { showing }
    }

    fun setDreamSwitcherEnabled(enabled: Boolean) {
        _isDreamSwitcherEnabled = enabled
    }

    fun setDreamState(user: UserHandle, state: DreamPlaylistModel) {
        getFlowForUser(user.identifier).value = state
    }

    private fun getFlowForUser(userId: Int): MutableStateFlow<DreamPlaylistModel> {
        return dreamStates.getOrPut(userId) { MutableStateFlow(DreamPlaylistModel.EMPTY) }
    }
}

val DreamRepository.fake: FakeDreamRepository
    get() = this as FakeDreamRepository
