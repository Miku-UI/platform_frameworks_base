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

package com.android.systemui.notifications.intelligence.rules.data.repository

import android.content.applicationContext
import android.content.packageManager
import android.content.pm.ApplicationInfo
import android.content.pm.UserInfo
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.notifications.content.icon.mockAppIconProvider
import com.android.systemui.notifications.intelligence.rules.shared.model.AppModel
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.model.SelectedUserModel
import com.android.systemui.user.data.model.SelectionStatus
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class InstalledAppsRepositoryTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by Kosmos.Fixture { realInstalledAppsRepository }

    @Test
    fun lookupApp_notAvailable_null() =
        kosmos.runTest {
            whenever(packageManager.getNameForUid(any())).thenReturn(null)

            val result = underTest.lookupApp(uid = 1, applicationContext)

            assertThat(result).isNull()
        }

    @Test
    fun lookupApp_hasApp_returnsModel() =
        kosmos.runTest {
            val uid = 1
            val packageName = "app.1"
            whenever(packageManager.getNameForUid(uid)).thenReturn(packageName)

            val drawable1 = mock<Drawable>()
            val appInfo1 =
                mock<ApplicationInfo>().apply {
                    this.uid = uid
                    whenever(this.loadLabel(eq(packageManager))).thenReturn("App 1")
                    this.packageName = packageName
                }
            whenever(
                    packageManager.getApplicationInfoAsUser(eq(packageName), any<Int>(), any<Int>())
                )
                .thenReturn(appInfo1)
            whenever(mockAppIconProvider.getOrFetchAppIcon(eq(packageName), any(), any<String>()))
                .thenReturn(drawable1)

            val result = underTest.lookupApp(uid, applicationContext)

            assertThat(result)
                .isEqualTo(
                    AppModel(
                        uid = uid,
                        label = "App 1",
                        icon = drawable1,
                        packageName = packageName,
                    )
                )
        }

    @Test
    fun fetchInstalledApps_getsAll_forUser() =
        kosmos.runTest {
            kosmos.fakeUserRepository.selectedUser.value =
                SelectedUserModel(SELECTED_USER_INFO, SelectionStatus.SELECTION_COMPLETE)

            val drawable1 = mock<Drawable>()
            val appInfo1 =
                mock<ApplicationInfo>().apply {
                    this.uid = 1
                    whenever(this.loadLabel(eq(packageManager))).thenReturn("App 1")
                    this.packageName = "app.1"
                }
            whenever(
                    mockAppIconProvider.getOrFetchAppIcon(
                        eq("app.1"),
                        eq(SELECTED_USER_INFO.userHandle),
                        any<String>(),
                    )
                )
                .thenReturn(drawable1)

            val drawable2 = mock<Drawable>()
            val appInfo2 =
                mock<ApplicationInfo>().apply {
                    this.uid = 2
                    whenever(this.loadLabel(eq(packageManager))).thenReturn("App 2")
                    this.packageName = "app.2"
                }
            whenever(
                    mockAppIconProvider.getOrFetchAppIcon(
                        eq("app.2"),
                        eq(SELECTED_USER_INFO.userHandle),
                        any<String>(),
                    )
                )
                .thenReturn(drawable2)

            val drawable3 = mock<Drawable>()
            val appForOtherUser =
                mock<ApplicationInfo>().apply {
                    this.uid = 3
                    whenever(this.loadLabel(eq(packageManager))).thenReturn("App 3")
                    this.packageName = "app.3"
                }
            whenever(
                    mockAppIconProvider.getOrFetchAppIcon(
                        eq("app.3"),
                        eq(OTHER_USER_INFO.userHandle),
                        any<String>(),
                    )
                )
                .thenReturn(drawable3)

            whenever(
                    packageManager.getInstalledApplicationsAsUser(any<Int>(), eq(SELECTED_USER_ID))
                )
                .thenReturn(listOf(appInfo1, appInfo2))
            whenever(packageManager.getInstalledApplicationsAsUser(any<Int>(), eq(OTHER_USER_ID)))
                .thenReturn(listOf(appForOtherUser))

            val result = underTest.fetchInstalledApps(applicationContext)

            assertThat(result)
                .containsExactly(
                    AppModel(uid = 1, label = "App 1", icon = drawable1, packageName = "app.1"),
                    AppModel(uid = 2, label = "App 2", icon = drawable2, packageName = "app.2"),
                )
                .inOrder()
        }

    companion object {
        private const val SELECTED_USER_ID = 12
        private val SELECTED_USER_INFO = UserInfo(SELECTED_USER_ID, "selected user", 0)
        private const val OTHER_USER_ID = SELECTED_USER_ID + 2
        private val OTHER_USER_INFO = UserInfo(OTHER_USER_ID, "other user", 0)
    }
}
