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
package com.android.systemui.locationbutton.data.repository

import android.app.permissionui.LocationButtonSession
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.locationbutton.shared.model.ButtonModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LocationButtonRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val underTest = kosmos.locationButtonRepository

    @Test
    fun setButtonState_addsStateToMap() {
        val sessionId = 1
        val buttonModel = createTestButtonModel()
        underTest.setButtonState(sessionId, buttonModel)

        assertThat(underTest.getButtonState(sessionId)).isEqualTo(buttonModel)
    }

    @Test
    fun getButtonState_nonExistentSession_returnsNull() {
        val retrievedModel = underTest.getButtonState(999)

        assertThat(retrievedModel).isNull()
    }

    @Test
    fun removeButtonState_removesStateFromMap() {
        val sessionId = 1
        val buttonModel = createTestButtonModel()
        underTest.setButtonState(sessionId, buttonModel)
        underTest.removeButtonState(sessionId)

        assertThat(underTest.getButtonState(sessionId)).isNull()
    }

    @Test
    fun updateButtonState_updatesExistingState() {
        val sessionId = 1
        val initialModel = createTestButtonModel()
        underTest.setButtonState(sessionId, initialModel)
        val newWidth = 200
        val newHeight = 100

        underTest.updateButtonState(sessionId) { it.copy(width = newWidth, height = newHeight) }

        val updatedModel = underTest.getButtonState(sessionId)
        assertThat(updatedModel).isNotNull()
        assertThat(updatedModel!!.width).isEqualTo(newWidth)
        assertThat(updatedModel.height).isEqualTo(newHeight)
        assertThat(updatedModel.cornerRadius).isEqualTo(initialModel.cornerRadius)
    }

    @Test
    fun clear_removesAllStates() {
        underTest.setButtonState(1, createTestButtonModel())
        underTest.setButtonState(2, createTestButtonModel())
        underTest.clear()

        assertThat(underTest.getButtonState(1)).isNull()
        assertThat(underTest.getButtonState(2)).isNull()
    }

    @Test
    fun setButtonState_differentSessionIds_addsMultipleStates() {
        val sessionId1 = 1
        val sessionId2 = 2
        val buttonModel1 = createTestButtonModel()
        val buttonModel2 = createTestButtonModel(width = 200)
        underTest.setButtonState(sessionId1, buttonModel1)
        underTest.setButtonState(sessionId2, buttonModel2)

        assertThat(underTest.getButtonState(sessionId1)).isEqualTo(buttonModel1)
        assertThat(underTest.getButtonState(sessionId2)).isEqualTo(buttonModel2)
    }

    @Test
    fun setButtonState_sameSessionId_overwritesState() {
        val sessionId = 1
        val buttonModel1 = createTestButtonModel()
        val buttonModel2 = createTestButtonModel(width = 200)
        underTest.setButtonState(sessionId, buttonModel1)
        underTest.setButtonState(sessionId, buttonModel2)

        assertThat(underTest.getButtonState(sessionId)).isEqualTo(buttonModel2)
    }

    private fun createTestButtonModel(width: Int = 100): ButtonModel {
        return ButtonModel(
            width = width,
            height = 50,
            paddingLeft = 0,
            paddingTop = 0,
            paddingRight = 0,
            paddingBottom = 0,
            backgroundColor = Color.Black,
            strokeColor = Color.White,
            strokeWidth = 2,
            cornerRadius = 10f,
            pressedCornerRadius = 8f,
            iconTint = Color.White,
            textResId = null,
            textColor = Color.White,
            configuration = Configuration(),
            density = 1.0f,
            textType = LocationButtonSession.TEXT_TYPE_NONE,
        )
    }
}
