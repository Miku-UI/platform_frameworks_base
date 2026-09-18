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

package com.android.systemui.shared.customization.data.content

import android.content.ContentResolver
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.shared.customization.data.content.CustomizationProviderContract as Contract
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class CustomizationProviderClientImplTest : SysuiTestCase() {

    @Mock private lateinit var context: Context
    @Mock private lateinit var contentResolver: ContentResolver

    private lateinit var underTest: CustomizationProviderClientImpl

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(context.contentResolver).thenReturn(contentResolver)
        underTest =
            CustomizationProviderClientImpl(
                context = context,
                backgroundDispatcher = StandardTestDispatcher(),
            )
    }

    @Test
    fun refreshAffordances_notifiesChange() {
        underTest.refreshAffordances()

        verify(contentResolver)
            .notifyChange(Contract.LockScreenQuickAffordances.AffordanceTable.URI, null)
    }
}
