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

package com.android.wm.shell.apptoweb.data

import android.content.Context
import android.testing.AndroidTestingRunner
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.wm.shell.util.GMAIL_PACKAGE_NAME
import com.android.wm.shell.util.YOUTUBE_PACKAGE_NAME
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
class AppToWebDatastoreRepositoryTest {
    private val testContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testDatastore: DataStore<AppToWebProto>
    private lateinit var datastoreRepository: AppToWebDatastoreRepository
    private lateinit var datastoreScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        datastoreScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        testDatastore =
            DataStoreFactory.create(
                serializer = AppToWebDatastoreRepository.Companion.AppToWebProtoSerializer,
                scope = datastoreScope,
            ) {
                testContext.dataStoreFile(APP_TO_WEB_DATASTORE_TEST_FILE)
            }
        datastoreRepository = AppToWebDatastoreRepository(testDatastore)
    }

    @After
    fun tearDown() {
        File(ApplicationProvider.getApplicationContext<Context>().filesDir, "datastore")
            .deleteRecursively()

        datastoreScope.cancel()
    }

    @Test
    fun test_addFirstRunPromptAckedPackage() =
        runTest(StandardTestDispatcher()) {
            val userId = 0
            val promptAckedPackagesByUserId = mutableMapOf<Int, MutableSet<String>>()
            promptAckedPackagesByUserId[userId] = mutableSetOf(GMAIL_PACKAGE_NAME)
            datastoreRepository.updateFirstRunPromptAckedPackages(promptAckedPackagesByUserId)
            assertThat(
                    datastoreRepository
                        .getAppToWebProto()
                        .appToWebRepoByUserMap[userId]
                        ?.firstRunPromptAckedPackagesList
                )
                .contains(GMAIL_PACKAGE_NAME)
            assertThat(
                    datastoreRepository
                        .getAppToWebProto()
                        .appToWebRepoByUserMap[userId]
                        ?.getFirstRunPromptAckedPackagesCount()
                )
                .isEqualTo(1)

            promptAckedPackagesByUserId[userId]?.add(YOUTUBE_PACKAGE_NAME)
            datastoreRepository.updateFirstRunPromptAckedPackages(promptAckedPackagesByUserId)
            assertThat(
                    datastoreRepository
                        .getAppToWebProto()
                        .appToWebRepoByUserMap[userId]
                        ?.firstRunPromptAckedPackagesList
                )
                .containsAtLeast(GMAIL_PACKAGE_NAME, YOUTUBE_PACKAGE_NAME)
            assertThat(
                    datastoreRepository
                        .getAppToWebProto()
                        .appToWebRepoByUserMap[userId]
                        ?.getFirstRunPromptAckedPackagesCount()
                )
                .isEqualTo(2)
        }

    companion object {
        private const val APP_TO_WEB_DATASTORE_TEST_FILE = "app_to_web_test.pb"
    }
}
