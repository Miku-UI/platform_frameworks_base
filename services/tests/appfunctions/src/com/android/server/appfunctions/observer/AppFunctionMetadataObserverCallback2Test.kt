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
package com.android.server.appfunctions.observer

import android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_METADATA_DB
import android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE
import android.app.appfunctions.AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE
import android.app.appfunctions.flags.Flags
import android.app.appsearch.observer.DocumentChangeInfo
import android.app.appsearch.observer.SchemaChangeInfo
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.internal.infra.AndroidFuture
import com.android.server.appfunctions.MetadataSyncAdapter
import com.android.server.appfunctions.reader.AppFunctionMetadataReader
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class AppFunctionMetadataObserverCallback2Test {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private val mockMetadataSyncAdapter = mock<MetadataSyncAdapter>()
    private val appFunctionMetadataReader = mock<AppFunctionMetadataReader>()
    private val userHandle = mock<UserHandle>()

    @Before
    fun setup() {
        whenever(
                mockMetadataSyncAdapter.submitSyncRequest(
                    /* shouldSetRuntimeMetadataSchemaUnconditionally= */ false
                )
            )
            .thenReturn(AndroidFuture.completedFuture(true))
    }

    @Test
    fun onSchemaChanged_staticDbChange_invokesRouter() {
        val internalCallbackRouter = mock<InternalObserverCallbackRouter>()
        val observer =
            AppFunctionMetadataObserverCallback2(
                mockMetadataSyncAdapter,
                internalCallbackRouter,
                appFunctionMetadataReader,
                userHandle,
            )
        val schemaChangeInfo =
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-testPackage1"),
            )

        observer.onSchemaChanged(schemaChangeInfo)

        verify(internalCallbackRouter).onSchemaChanged(eq(schemaChangeInfo))
        verifyNoMoreInteractions(internalCallbackRouter)
    }

    @Test
    fun onSchemaChanged_staticDbChange_multipleSchemas_invokesRouter() {
        val internalCallbackRouter = mock<InternalObserverCallbackRouter>()
        val observer =
            AppFunctionMetadataObserverCallback2(
                mockMetadataSyncAdapter,
                internalCallbackRouter,
                appFunctionMetadataReader,
                userHandle,
            )
        val schemaChangeInfo =
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf(
                    "$STATIC_SCHEMA_TYPE-testPackage1",
                    "anotherSchema-testPackage1",
                    "anotherSchema-testPackage2",
                ),
            )

        observer.onSchemaChanged(schemaChangeInfo)

        verify(internalCallbackRouter).onSchemaChanged(eq(schemaChangeInfo))
        verifyNoMoreInteractions(internalCallbackRouter)
    }

    @Test
    fun onSchemaChanged_databaseMismatch_skipsResult() {
        val internalCallbackRouter = mock<InternalObserverCallbackRouter>()

        val observer =
            AppFunctionMetadataObserverCallback2(
                mockMetadataSyncAdapter,
                internalCallbackRouter,
                appFunctionMetadataReader,
                userHandle,
            )

        observer.onSchemaChanged(
            SchemaChangeInfo("android", "another_db", setOf("$STATIC_SCHEMA_TYPE-testPackage1"))
        )

        verifyNoInteractions(internalCallbackRouter)
    }

    @Test
    fun onDocumentChanged_staticDbChange_invokesRouter() {
        val internalCallbackRouter = mock<InternalObserverCallbackRouter>()

        val observer =
            AppFunctionMetadataObserverCallback2(
                mockMetadataSyncAdapter,
                internalCallbackRouter,
                appFunctionMetadataReader,
                userHandle,
            )
        val documentChangeInfo =
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-testPackage1",
                setOf("testPackage1/id1", "testPackage1/id5"),
            )

        observer.onDocumentChanged(documentChangeInfo)

        verify(internalCallbackRouter).onDocumentChanged(eq(documentChangeInfo))
        verifyNoMoreInteractions(internalCallbackRouter)
    }

    @Test
    fun onDocumentChanged_databaseMismatch_skipsResult() {
        val internalCallbackRouter = mock<InternalObserverCallbackRouter>()

        val observer =
            AppFunctionMetadataObserverCallback2(
                mockMetadataSyncAdapter,
                internalCallbackRouter,
                appFunctionMetadataReader,
                userHandle,
            )

        observer.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                "another_db",
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-testPackage1",
                setOf("testPackage1/id1"),
            )
        )

        verifyNoInteractions(internalCallbackRouter)
    }

    @Test
    fun onDocumentChanged_namespaceMismatch_skipsResult() {
        val internalCallbackRouter = mock<InternalObserverCallbackRouter>()

        val observer =
            AppFunctionMetadataObserverCallback2(
                mockMetadataSyncAdapter,
                internalCallbackRouter,
                appFunctionMetadataReader,
                userHandle,
            )

        observer.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                "another_namespace",
                "$STATIC_SCHEMA_TYPE-testPackage1",
                setOf("testPackage1/id1", "testPackage1/id11"),
            )
        )

        verifyNoInteractions(internalCallbackRouter)
    }

    @Test
    fun onDocumentChanged_schemaMismatch_invokesRouter() {
        val internalCallbackRouter = mock<InternalObserverCallbackRouter>()

        val observer =
            AppFunctionMetadataObserverCallback2(
                mockMetadataSyncAdapter,
                internalCallbackRouter,
                appFunctionMetadataReader,
                userHandle,
            )
        val documentChangeInfo =
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "AnotherSchema-testPackage1",
                setOf("testPackage1/id1", "testPackage1/id11"),
            )

        observer.onDocumentChanged(documentChangeInfo)

        verify(internalCallbackRouter).onDocumentChanged(eq(documentChangeInfo))
        verifyNoMoreInteractions(internalCallbackRouter)
    }
}
