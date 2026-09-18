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

package com.android.systemui.inputmethod.data.repository

import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.inputmethod.data.model.InputMethodModel
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class InputMethodRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val settings = kosmos.fakeSettings

    private val inputMethodManager = mock<InputMethodManager>()
    private val underTest =
        InputMethodRepositoryImpl(
            backgroundDispatcher = kosmos.testDispatcher,
            inputMethodManager = inputMethodManager,
            secureSettings = settings,
        )

    @Test
    fun enabledInputMethods_noImes_emptyFlow() =
        testScope.runTest {
            inputMethodManager.stub {
                on { getEnabledInputMethodListAsUser(eq(USER_HANDLE)) }.thenReturn(listOf())
                on { getEnabledInputMethodSubtypeListAsUser(any(), any(), eq(USER_HANDLE)) }
                    .thenReturn(listOf())
            }

            assertThat(underTest.enabledInputMethods(USER_HANDLE, fetchSubtypes = true).count())
                .isEqualTo(0)
        }

    @Test
    fun selectedInputMethodSubtypes_returnsSubtypeList() =
        testScope.runTest {
            val subtypeId = 123
            val isAuxiliary = true
            val selectedImiId = "imiId"
            val selectedImi = mock<InputMethodInfo> { on { id } doReturn selectedImiId }
            inputMethodManager.stub {
                on { getCurrentInputMethodInfoAsUser(eq(USER_HANDLE)) }.thenReturn(selectedImi)
                on { getEnabledInputMethodListAsUser(eq(USER_HANDLE)) }
                    .thenReturn(listOf(selectedImi))
                on {
                        getEnabledInputMethodSubtypeListAsUser(
                            eq(selectedImiId),
                            any(),
                            eq(USER_HANDLE),
                        )
                    }
                    .thenReturn(
                        listOf(
                            InputMethodSubtype.InputMethodSubtypeBuilder()
                                .setSubtypeId(subtypeId)
                                .setIsAuxiliary(isAuxiliary)
                                .build()
                        )
                    )
            }

            val result = underTest.selectedInputMethodSubtypes(USER_HANDLE)
            assertThat(result).hasSize(1)
            assertThat(result.first().subtypeId).isEqualTo(subtypeId)
            assertThat(result.first().isAuxiliary).isEqualTo(isAuxiliary)
        }

    @Test
    fun selectedInputMethodSubtype_returnsSubtype() =
        testScope.runTest {
            val subtype1 =
                InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(123)
                    .setIsAuxiliary(true)
                    .build()
            val subtype2 =
                InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(456)
                    .setIsAuxiliary(false)
                    .build()
            val selectedImiId = "imiId"
            val selectedImi = mock<InputMethodInfo> { on { id } doReturn selectedImiId }
            inputMethodManager.stub {
                on { getCurrentInputMethodInfoAsUser(eq(USER_HANDLE)) }.thenReturn(selectedImi)
                on {
                        getEnabledInputMethodSubtypeListAsUser(
                            eq(selectedImiId),
                            any(),
                            eq(USER_HANDLE),
                        )
                    }
                    .thenReturn(listOf(subtype1, subtype2))
            }
            settings.putIntForUser(
                Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE,
                subtype2.subtypeId,
                USER_HANDLE.identifier,
            )

            val result by collectLastValue(underTest.selectedInputMethodSubtype(USER_HANDLE))
            val expectedSubtype = InputMethodModel.Subtype(subtypeId = 456, isAuxiliary = false)
            assertThat(result).isEqualTo(expectedSubtype)
        }

    @Test
    fun selectedInputMethodSubtype_returnsSubtypeWithIcon() =
        testScope.runTest {
            val iconResId = R.drawable.ic_android
            val subtype =
                InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(123)
                    .setIsAuxiliary(false)
                    .setSubtypeIconResId(iconResId)
                    .build()
            val selectedImiId = "imiId"
            val selectedImi =
                mock<InputMethodInfo> {
                    on { id } doReturn selectedImiId
                    on { packageName } doReturn context.packageName
                }
            inputMethodManager.stub {
                on { getCurrentInputMethodInfoAsUser(eq(USER_HANDLE)) }.thenReturn(selectedImi)
                on {
                        getEnabledInputMethodSubtypeListAsUser(
                            eq(selectedImiId),
                            any(),
                            eq(USER_HANDLE),
                        )
                    }
                    .thenReturn(listOf(subtype))
            }
            settings.putIntForUser(
                Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE,
                subtype.subtypeId,
                USER_HANDLE.identifier,
            )

            val result by collectLastValue(underTest.selectedInputMethodSubtype(USER_HANDLE))
            assertThat(result!!.icon).isNotNull()
            assertThat(result!!.icon!!.resId).isEqualTo(iconResId)
            assertThat(result!!.icon!!.packageName).isEqualTo(context.packageName)
        }

    @Test
    @EnableFlags(android.view.inputmethod.Flags.FLAG_IME_SUBTYPE_SHORT_LABEL)
    fun selectedInputMethodSubtype_returnsSubtypeWithShortLabel() =
        testScope.runTest {
            val shortLabel = "EN"
            val subtype =
                InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(123)
                    .setIsAuxiliary(false)
                    .setSubtypeShortLabel(shortLabel)
                    .build()
            val selectedImiId = "imiId"
            val selectedImi = mock<InputMethodInfo> { on { id } doReturn selectedImiId }
            inputMethodManager.stub {
                on { getCurrentInputMethodInfoAsUser(eq(USER_HANDLE)) }.thenReturn(selectedImi)
                on {
                        getEnabledInputMethodSubtypeListAsUser(
                            eq(selectedImiId),
                            any(),
                            eq(USER_HANDLE),
                        )
                    }
                    .thenReturn(listOf(subtype))
            }
            settings.putIntForUser(
                Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE,
                subtype.subtypeId,
                USER_HANDLE.identifier,
            )

            val result by collectLastValue(underTest.selectedInputMethodSubtype(USER_HANDLE))
            assertThat(result!!.shortLabel).isEqualTo(shortLabel)
        }

    @Test
    fun showImePicker_forwardsArgs() =
        testScope.runTest {
            underTest.showInputMethodPicker(
                /* showAuxiliarySubtypes= */ true,
                InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT,
                displayId = DISPLAY_ID,
            )

            verify(inputMethodManager)
                .showInputMethodPickerFromSystem(
                    /* showAuxiliarySubtypes = */ eq(true),
                    /* entryPoint = */ eq(InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT),
                    /* displayId = */ eq(DISPLAY_ID),
                )
        }

    @Test
    fun toggleImePicker_forwardsArgs() =
        testScope.runTest {
            underTest.toggleInputMethodPicker(
                /* showAuxiliarySubtypes= */ true,
                InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT,
                displayId = DISPLAY_ID,
            )

            verify(inputMethodManager)
                .toggleInputMethodPickerFromSystem(
                    /* showAuxiliarySubtypes = */ eq(true),
                    /* entryPoint = */ eq(InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT),
                    /* displayId = */ eq(DISPLAY_ID),
                )
        }

    @Test
    fun hideImePicker_forwardsDisplayId() =
        testScope.runTest {
            underTest.hideInputMethodPicker(DISPLAY_ID)
            verify(inputMethodManager).hideInputMethodPickerFromSystem(eq(DISPLAY_ID))
        }

    companion object {
        private val USER_HANDLE = UserHandle.of(100)
        private val DISPLAY_ID = 7
    }
}
