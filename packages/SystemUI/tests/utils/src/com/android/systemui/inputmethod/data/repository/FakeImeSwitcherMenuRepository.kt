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

package com.android.systemui.inputmethod.data.repository

import android.annotation.UserIdInt
import android.util.SparseArray
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel
import com.android.systemui.inputmethod.shared.model.ModelChangeListener
import java.util.concurrent.CopyOnWriteArrayList

/** Fake implementation of [ImeSwitcherMenuRepository] that records interactions for tests. */
@SysUISingleton
class FakeImeSwitcherMenuRepository : ImeSwitcherMenuRepository {

    /** The stored models for each userId. */
    private val models = SparseArray<ImeSwitcherMenuModel>()

    /** The listeners to be notified when the model tied to a user has changed. */
    private val modelChangeListeners = CopyOnWriteArrayList<ModelChangeListener>()

    /**
     * The values given in the [notifyVisibilityChanged] calls, stored in the format (visible,
     * displayId, userId).
     */
    var visibilityChangedCalls = mutableListOf<Triple<Boolean, Int, Int>>()

    /**
     * The values given in the [notifyImeAndSubtypeSelected] calls, stored in the format (imeId,
     * subtypeIndex, userId).
     */
    var imeAndSubtypeSelectedCalls = mutableListOf<Triple<String, Int, Int>>()

    override fun getModel(@UserIdInt userId: Int): ImeSwitcherMenuModel? = models[userId]

    override fun registerModelChangeListener(listener: ModelChangeListener) {
        modelChangeListeners.add(listener)
    }

    override fun unregisterModelChangeListener(listener: ModelChangeListener) {
        modelChangeListeners.remove(listener)
    }

    override fun notifyVisibilityChanged(visible: Boolean, displayId: Int, userId: Int) {
        visibilityChangedCalls.add(Triple(visible, displayId, userId))
    }

    override fun notifyImeAndSubtypeSelected(imeId: String, subtypeIndex: Int, userId: Int) {
        imeAndSubtypeSelectedCalls.add(Triple(imeId, subtypeIndex, userId))
    }

    override fun shouldShowSettingsButton(userId: Int): Boolean = true

    /**
     * Sets the model for the given user.
     *
     * @param userId the ID of the user whose model to set.
     * @param model the model to set, or `null` if the model was removed.
     */
    fun setModel(@UserIdInt userId: Int, model: ImeSwitcherMenuModel?) {
        if (model != null) {
            models.put(userId, model)
        } else {
            models.remove(userId)
        }
        modelChangeListeners.forEach { it.onChanged(userId, model) }
    }
}

val ImeSwitcherMenuRepository.fake
    get() = this as FakeImeSwitcherMenuRepository
