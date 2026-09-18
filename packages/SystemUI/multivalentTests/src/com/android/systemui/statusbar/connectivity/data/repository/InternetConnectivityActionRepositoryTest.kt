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

package com.android.systemui.statusbar.connectivity.data.repository

import android.content.Intent
import android.provider.Settings
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@SmallTest
class InternetConnectivityActionRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.internetConnectivityActionRepository }

    @Test
    fun eventEmitsWhenIntentIsReceived() =
        kosmos.runTest {
            val event by collectLastValue(underTest.internetConnectivityActionEvent)

            assertThat(kosmos.broadcastDispatcher.numReceiversRegistered).isEqualTo(1)

            // Simulate a broadcast.
            val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            kosmos.broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, intent)

            assertThat(event).isNotNull()
        }
}
