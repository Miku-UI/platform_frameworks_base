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
package com.android.server.appfunctions.reader

import android.app.appfunctions.AppFunctionManager
import android.app.appfunctions.AppFunctionMetadata
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_CATEGORY
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_NAME
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_VERSION
import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionPackageMetadata
import android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_ENABLED
import android.app.appfunctions.AppFunctionRuntimeMetadata.PROPERTY_PACKAGE_NAME
import android.app.appfunctions.AppFunctionSearchSpec
import android.app.appfunctions.AppFunctionStaticMetadataHelper
import android.app.appfunctions.flags.Flags
import android.app.appsearch.GenericDocument
import android.app.appsearch.SearchResult
import android.os.ParcelableException
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.internal.infra.AndroidFuture
import com.android.server.appfunctions.FutureGlobalSearchSession
import com.android.server.appfunctions.FutureSearchResults
import com.android.server.appfunctions.ServiceConfig
import com.android.server.appfunctions.dynamic.MultiUserDynamicAppFunctionRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class AppFunctionMetadataReaderTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var dynamicRegistry: MultiUserDynamicAppFunctionRegistry

    private lateinit var metadataCache: AppFunctionsMetadataCache

    private lateinit var appFunctionMetadataReader: AppFunctionMetadataReader

    @Before
    fun setup() {
        dynamicRegistry = mock()
        metadataCache = mock()
        appFunctionMetadataReader =
            AppFunctionMetadataReader(
                dynamicRegistry,
                metadataCache,
                object : ServiceConfig {
                    override fun getExecuteAppFunctionCancellationTimeoutMillis(): Long = 0

                    override fun getSearchAppFunctionInternalPageSize(): Int = 100

                    override fun getAppFunctionMetadataChangeDebounceMilliseconds(): Int = 0

                    override fun getAppFunctionAllowlistCacheSize(): Int = 5

                    override fun getAppFunctionEnabledStateChangeDebounceMilliseconds(): Long = 0

                    override fun getAppFunctionEnabledStateChangeMaxDebounceMilliseconds(): Long = 0
                },
            )
    }

    @Test
    fun getAppFunctionStates_missingRuntimeMetadata_returnsEmptyList() = doBlocking {
        val resultMissingRuntime =
            SearchResult.Builder("", "").setGenericDocument(STATIC_METADATA_DOCUMENT).build()
        val testFutureSearchResults =
            object : FutureSearchResults {
                var pageNumber = 0

                override fun getNextPage(): AndroidFuture<List<SearchResult?>?> {
                    return when (pageNumber++) {
                        0 -> AndroidFuture.completedFuture(listOf(resultMissingRuntime))
                        else -> AndroidFuture.completedFuture(listOf())
                    }
                }

                override fun close() {}
            }
        val futureGlobalSearchSession = mock<FutureGlobalSearchSession>()
        whenever(futureGlobalSearchSession.search(any(), any()))
            .thenReturn(AndroidFuture.completedFuture(testFutureSearchResults))

        val functionNames = setOf(AppFunctionName("testPackage", "testFunctionId"))
        val resultFuture =
            appFunctionMetadataReader.getAppFunctionStates(
                futureGlobalSearchSession,
                functionNames,
                0,
            )
        val resultStates = resultFuture.get()

        assertThat(resultStates).isEmpty()
    }

    @Test
    fun getAppFunctionStates_pagination_collectsAllStates() = doBlocking {
        val testFutureSearchResults =
            object : FutureSearchResults {
                var pageNumber = 0

                override fun getNextPage(): AndroidFuture<List<SearchResult?>?> {
                    return when (pageNumber++) {
                        0 ->
                            AndroidFuture.completedFuture(
                                listOf(GET_APP_FUNCTION_STATE_SEARCH_RESULT_ENABLED)
                            )
                        1 ->
                            AndroidFuture.completedFuture(
                                listOf(GET_APP_FUNCTION_STATE_SEARCH_RESULT_DISABLED)
                            )
                        else -> AndroidFuture.completedFuture(listOf())
                    }
                }

                override fun close() {}
            }
        val futureGlobalSearchSession = mock<FutureGlobalSearchSession>()
        whenever(futureGlobalSearchSession.search(any(), any()))
            .thenReturn(AndroidFuture.completedFuture(testFutureSearchResults))

        val function1 = AppFunctionName("testPackage", "testFunctionId")
        val function2 = AppFunctionName("testPackage", "testFunctionId2")
        val functionNames = setOf(function1, function2)
        val resultFuture =
            appFunctionMetadataReader.getAppFunctionStates(
                futureGlobalSearchSession,
                functionNames,
                0,
            )
        val resultStates = resultFuture.get()

        assertThat(resultStates).hasSize(2)
        val state1 = resultStates[0]
        assertThat(state1.functionName).isEqualTo(function1)
        assertThat(state1.isEnabled).isTrue()
        val state2 = resultStates[1]
        assertThat(state2.functionName).isEqualTo(function2)
        assertThat(state2.isEnabled).isFalse()
    }

    private fun doBlocking(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    companion object {
        val STATIC_METADATA_DOCUMENT =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                    "",
                    "testPackage/testFunctionId",
                    "",
                )
                .setPropertyString(PROPERTY_PACKAGE_NAME, "testPackage")
                .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "testCategory")
                .setPropertyString(PROPERTY_SCHEMA_NAME, "testName")
                .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                .setPropertyBoolean(
                    AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                    true,
                )
                .build()
        val STATIC_METADATA_DOCUMENT_2 =
            GenericDocument.Builder<GenericDocument.Builder<*>>(
                    "",
                    "testPackage/testFunctionId2",
                    "",
                )
                .setPropertyString(PROPERTY_PACKAGE_NAME, "testPackage")
                .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "testCategory")
                .setPropertyString(PROPERTY_SCHEMA_NAME, "testName")
                .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
                .setPropertyBoolean(
                    AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                    false,
                )
                .build()
        val RUNTIME_METADATA_DOCUMENT =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString(PROPERTY_PACKAGE_NAME, "testPackage")
                .setPropertyLong(
                    PROPERTY_ENABLED,
                    AppFunctionManager.APP_FUNCTION_STATE_DEFAULT.toLong(),
                )
                .build()
        val RUNTIME_METADATA_DOCUMENT_DISABLED =
            GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                .setPropertyString(PROPERTY_PACKAGE_NAME, "testPackage")
                .setPropertyLong(
                    PROPERTY_ENABLED,
                    AppFunctionManager.APP_FUNCTION_STATE_DISABLED.toLong(),
                )
                .build()

        val GET_APP_FUNCTION_STATE_SEARCH_RESULT_ENABLED =
            SearchResult.Builder("", "")
                .setGenericDocument(STATIC_METADATA_DOCUMENT)
                .addJoinedResult(
                    SearchResult.Builder("", "")
                        .setGenericDocument(RUNTIME_METADATA_DOCUMENT)
                        .build()
                )
                .build()
        val GET_APP_FUNCTION_STATE_SEARCH_RESULT_DISABLED =
            SearchResult.Builder("", "")
                .setGenericDocument(STATIC_METADATA_DOCUMENT_2)
                .addJoinedResult(
                    SearchResult.Builder("", "")
                        .setGenericDocument(RUNTIME_METADATA_DOCUMENT_DISABLED)
                        .build()
                )
                .build()
    }
}
