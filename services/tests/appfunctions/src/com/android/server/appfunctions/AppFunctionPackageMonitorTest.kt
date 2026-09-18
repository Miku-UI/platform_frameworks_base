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

package com.android.server.appfunctions

import android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_DEFAULT
import android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_DISABLED
import android.app.appfunctions.AppFunctionManager.APP_FUNCTION_STATE_ENABLED
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionRuntimeMetadata
import android.app.appfunctions.flags.Flags
import android.app.appsearch.AppSearchManager
import android.app.appsearch.PutDocumentsRequest
import android.app.appsearch.SetSchemaRequest
import android.content.Context
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.appfunctions.observer.AppFunctionMetadataObserver
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
class AppFunctionPackageMonitorTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val userHandle = UserHandle.of(10)
    private lateinit var mockContext: Context
    private lateinit var appSearchManager: AppSearchManager
    private lateinit var mockMetadataObserver: AppFunctionMetadataObserver

    private lateinit var monitor: AppFunctionPackageMonitor

    @Before
    fun setup() {
        mockContext = spy(InstrumentationRegistry.getInstrumentation().targetContext)
        appSearchManager = mockContext.getSystemService(AppSearchManager::class.java)!!
        doReturn(mockContext).whenever(mockContext).createContextAsUser(any(), anyInt())
        mockMetadataObserver = mock<AppFunctionMetadataObserver>()
        monitor = AppFunctionPackageMonitor(mockContext, userHandle, mockMetadataObserver)
    }

    @Test
    fun onPackageDataCleared_runtimeStateDefault_doesNotNotifyObserver() {
        setupAppSearchSearchResults(
            TEST_PACKAGE_NAME,
            listOf(
                AppFunctionRuntimeMetadata.Builder(TEST_PACKAGE_NAME, TEST_FUNCTION_ID_1)
                    .setEnabled(APP_FUNCTION_STATE_DEFAULT)
                    .build()
            ),
        )

        monitor.onPackageDataCleared(TEST_PACKAGE_NAME, 1000)

        verify(mockMetadataObserver, never()).onEnabledStatesChanged(any(), any())
    }

    @Test
    fun onPackageDataCleared_runtimeStateEnabled_notifiesObserverIfDisabledByDefault() {
        setupAppSearchSearchResults(
            TEST_PACKAGE_NAME,
            listOf(
                AppFunctionRuntimeMetadata.Builder(TEST_PACKAGE_NAME, TEST_FUNCTION_ID_1)
                    .setEnabled(APP_FUNCTION_STATE_ENABLED)
                    .build()
            ),
        )

        monitor.onPackageDataCleared(TEST_PACKAGE_NAME, 1000)

        val captor = argumentCaptor<Set<AppFunctionName>>()
        verify(mockMetadataObserver).onEnabledStatesChanged(any(), captor.capture())

        val notifiedFunctions = captor.firstValue
        assertThat(notifiedFunctions)
            .containsExactly(AppFunctionName(TEST_PACKAGE_NAME, TEST_FUNCTION_ID_1))
    }

    @Test
    fun onPackageDataCleared_runtimeStateDisabled_notifiesObserver() {
        setupAppSearchSearchResults(
            TEST_PACKAGE_NAME,
            listOf(
                AppFunctionRuntimeMetadata.Builder(TEST_PACKAGE_NAME, TEST_FUNCTION_ID_1)
                    .setEnabled(APP_FUNCTION_STATE_DISABLED)
                    .build()
            ),
        )

        monitor.onPackageDataCleared(TEST_PACKAGE_NAME, 1000)

        val captor = argumentCaptor<Set<AppFunctionName>>()
        verify(mockMetadataObserver).onEnabledStatesChanged(any(), captor.capture())

        val notifiedFunctions = captor.firstValue
        assertThat(notifiedFunctions)
            .containsExactly(AppFunctionName(TEST_PACKAGE_NAME, TEST_FUNCTION_ID_1))
    }

    @Test
    fun onPackageDataCleared_multipleChanges_notifiesObserverIfNotDefault() {
        setupAppSearchSearchResults(
            TEST_PACKAGE_NAME,
            listOf(
                AppFunctionRuntimeMetadata.Builder(TEST_PACKAGE_NAME, TEST_FUNCTION_ID_1)
                    .setEnabled(APP_FUNCTION_STATE_ENABLED)
                    .build(),
                AppFunctionRuntimeMetadata.Builder(TEST_PACKAGE_NAME, TEST_FUNCTION_ID_2)
                    .setEnabled(APP_FUNCTION_STATE_DISABLED)
                    .build(),
                AppFunctionRuntimeMetadata.Builder(TEST_PACKAGE_NAME, TEST_FUNCTION_ID_3)
                    .setEnabled(APP_FUNCTION_STATE_DEFAULT)
                    .build(),
            ),
        )

        monitor.onPackageDataCleared(TEST_PACKAGE_NAME, 1000)

        val captor = argumentCaptor<Set<AppFunctionName>>()
        verify(mockMetadataObserver).onEnabledStatesChanged(any(), captor.capture())

        val notifiedFunctions = captor.firstValue
        assertThat(notifiedFunctions)
            .containsExactly(
                AppFunctionName(TEST_PACKAGE_NAME, TEST_FUNCTION_ID_1),
                AppFunctionName(TEST_PACKAGE_NAME, TEST_FUNCTION_ID_2),
            )
    }

    private fun setupAppSearchSearchResults(
        packageName: String,
        documents: List<AppFunctionRuntimeMetadata>,
    ) {
        val searchContext =
            AppSearchManager.SearchContext.Builder(
                    AppFunctionRuntimeMetadata.APP_FUNCTION_RUNTIME_METADATA_DB
                )
                .build()
        FutureAppSearchSessionImpl(appSearchManager, Runnable::run, searchContext).use { session ->
            val setSchemaRequest =
                SetSchemaRequest.Builder()
                    .addSchemas(
                        AppFunctionRuntimeMetadata.createParentAppFunctionRuntimeSchema(),
                        AppFunctionRuntimeMetadata.createAppFunctionRuntimeSchema(packageName),
                    )
                    .setForceOverride(true)
                    .build()
            session.setSchema(setSchemaRequest).get()

            val putRequest = PutDocumentsRequest.Builder().addGenericDocuments(documents).build()
            session.put(putRequest).get()
        }
    }

    private companion object {
        const val TEST_PACKAGE_NAME = "com.example.app"
        const val TEST_FUNCTION_ID_1 = "id1"
        const val TEST_FUNCTION_ID_2 = "id2"
        const val TEST_FUNCTION_ID_3 = "id3"
    }
}
