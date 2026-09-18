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

import android.app.appfunctions.AppFunctionName
import android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_METADATA_DB
import android.app.appfunctions.AppFunctionStaticMetadataHelper.APP_FUNCTION_STATIC_NAMESPACE
import android.app.appfunctions.AppFunctionStaticMetadataHelper.STATIC_SCHEMA_TYPE
import android.app.appfunctions.IObserveAppFunctionChangesCallback
import android.app.appfunctions.flags.Flags
import android.app.appsearch.observer.DocumentChangeInfo
import android.app.appsearch.observer.SchemaChangeInfo
import android.os.Binder
import android.os.IBinder
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import com.android.server.appfunctions.CallerIdentity
import com.android.server.appfunctions.FakeScheduledExecutorService
import com.android.server.appfunctions.FutureGlobalSearchSession
import com.android.server.appfunctions.ServiceConfig
import com.android.server.appfunctions.VisibilityHelper
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.ScheduledExecutorService
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class InternalObserverCallbackRouterTest {
    private val mockFutureAppGlobalSearchSession: FutureGlobalSearchSession = mock()
    private val mockServiceConfig: ServiceConfig = mock()
    private val mockVisibilityHelper: VisibilityHelper = mock()

    private fun createRouter(
        debounceExecutor: ScheduledExecutorService,
        metadataDebounceMs: Int = 0,
        enabledStateDebounceMs: Long = 0,
        enabledStateMaxDebounceMs: Long = 0,
    ): InternalObserverCallbackRouter {
        whenever(mockServiceConfig.getAppFunctionMetadataChangeDebounceMilliseconds())
            .thenReturn(metadataDebounceMs)
        whenever(mockServiceConfig.getAppFunctionEnabledStateChangeDebounceMilliseconds())
            .thenReturn(enabledStateDebounceMs)
        whenever(mockServiceConfig.getAppFunctionEnabledStateChangeMaxDebounceMilliseconds())
            .thenReturn(enabledStateMaxDebounceMs)
        // Return packages without filtering.
        whenever(mockVisibilityHelper.filterVisiblePackages(any(), any())).thenAnswer { invocation
            ->
            invocation.getArgument<Set<String>>(0)
        }
        // Return app functions without filtering.
        whenever(mockVisibilityHelper.filterVisibleAppFunctions(any(), any())).thenAnswer {
            invocation ->
            invocation.getArgument<Set<AppFunctionName>>(0)
        }

        return if (debounceExecutor is FakeScheduledExecutorService) {
            InternalObserverCallbackRouter(
                mockFutureAppGlobalSearchSession,
                mockServiceConfig,
                mockVisibilityHelper,
                debounceExecutor,
                InternalObserverCallbackRouter.TimeSource { debounceExecutor.clockTimeNanos },
                MoreExecutors.directExecutor(),
            )
        } else {
            InternalObserverCallbackRouter(
                mockFutureAppGlobalSearchSession,
                mockServiceConfig,
                mockVisibilityHelper,
            )
        }
    }

    @Test
    fun onSchemaChanged_routesToAllCallbacks() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 100)
        callbacksRouter.addCallback(callback1)
        callbacksRouter.addCallback(callback2)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-$PKG_1", "$STATIC_SCHEMA_TYPE-$PKG_2"),
            )
        )

        fakeDebounceExecutor.fastForwardTime(101L)
        val expectedPackages = listOf(PKG_1, PKG_2)
        assertThat(callback1.changedPackageNames).containsExactlyElementsIn(expectedPackages)
        assertThat(callback2.changedPackageNames).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onSchemaChanged_consequentUpdates_debouncedCorrectly() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 100)
        callbacksRouter.addCallback(callback1)

        // First change
        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-$PKG_1"),
            )
        )

        // Fast forward time, but not enough to trigger debounce
        fakeDebounceExecutor.fastForwardTime(50L)
        assertThat(callback1.changedPackageNames).isNull()

        // Second change for the same package
        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-$PKG_1"),
            )
        )

        // Fast forward time beyond debounce duration
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).containsExactly(PKG_1)

        // Reset and check that it was only called once
        callback1.changedPackageNames = null
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onSchemaChanged_consequentUpdatesForMultiplePackages_debouncedCorrectly() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 100)
        callbacksRouter.addCallback(callback1)

        // First change
        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-$PKG_1"),
            )
        )

        // Fast forward time, but not enough to trigger debounce
        fakeDebounceExecutor.fastForwardTime(50L)
        assertThat(callback1.changedPackageNames).isNull()

        // Second change for another package
        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-$PKG_2"),
            )
        )

        // Fast forward time beyond debounce duration
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).containsExactlyElementsIn(listOf(PKG_1, PKG_2))

        // Reset and check that it was only called once
        callback1.changedPackageNames = null
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onDocumentChanged_routesToAllCallbacks() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()
        val callback3 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 100)
        callbacksRouter.addCallback(callback1)
        callbacksRouter.addCallback(callback2)
        callbacksRouter.addCallback(callback3)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_1",
                setOf("$PKG_1/id1"),
            )
        )
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_2",
                setOf("$PKG_2/id2"),
            )
        )

        fakeDebounceExecutor.fastForwardTime(101L)
        val expectedPackages = listOf(PKG_1, PKG_2)
        assertThat(callback1.changedPackageNames).containsExactlyElementsIn(expectedPackages)
        assertThat(callback2.changedPackageNames).containsExactlyElementsIn(expectedPackages)
        assertThat(callback3.changedPackageNames).containsExactlyElementsIn(expectedPackages)
    }

    @Test
    fun onDocumentChanged_malformedId_skipsResult() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 100)
        callbacksRouter.addCallback(callback1)

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                STATIC_SCHEMA_TYPE,
                setOf("$PKG_1/", PKG_1, ""),
            )
        )

        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onDocumentChanged_consequentUpdates_debouncedCorrectly() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 100)
        callbacksRouter.addCallback(callback1)

        // First change
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_1",
                setOf("$PKG_1/id1"),
            )
        )

        // Fast forward time, but not enough to trigger debounce
        fakeDebounceExecutor.fastForwardTime(50L)
        assertThat(callback1.changedPackageNames).isNull()

        // Second change for the same package
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_1",
                setOf("$PKG_1/id2"),
            )
        )

        // Fast forward time beyond debounce duration
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).containsExactly(PKG_1)

        // Reset and check that it was only called once
        callback1.changedPackageNames = null
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onDocumentChanged_consequentUpdatesForMultiplePackages_debouncedCorrectly() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 100)
        callbacksRouter.addCallback(callback1)

        // First change
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_1",
                setOf("$PKG_1/id1"),
            )
        )

        // Fast forward time, but not enough to trigger debounce
        fakeDebounceExecutor.fastForwardTime(50L)
        assertThat(callback1.changedPackageNames).isNull()

        // Second change for another package
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_2",
                setOf("$PKG_2/id2"),
            )
        )

        // Fast forward time beyond debounce duration
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).containsExactlyElementsIn(listOf(PKG_1, PKG_2))

        // Reset and check that it was only called once
        callback1.changedPackageNames = null
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun onMixedChanges_consequentUpdatesForMultiplePackages_debouncedCorrectly() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 100)
        callbacksRouter.addCallback(callback1)

        // First change (schema)
        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-$PKG_1"),
            )
        )

        // Fast forward time, but not enough to trigger debounce
        fakeDebounceExecutor.fastForwardTime(50L)
        assertThat(callback1.changedPackageNames).isNull()

        // Second change (document) for another package
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_2",
                setOf("$PKG_2/id2"),
            )
        )

        // Fast forward time beyond debounce duration
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).containsExactlyElementsIn(listOf(PKG_1, PKG_2))

        // Reset and check that it was only called once
        callback1.changedPackageNames = null
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
    }

    @Test
    fun removeCallback_doesNotRoute() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 100)
        callbacksRouter.addCallback(callback1)
        callbacksRouter.addCallback(callback2)
        callbacksRouter.removeCallback(callback1)

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-$PKG_1"),
            )
        )
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
        assertThat(callback2.changedPackageNames).containsExactly(PKG_1)

        // Reset for next check
        callback2.changedPackageNames = null

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_1",
                setOf("$PKG_1/id1"),
            )
        )
        fakeDebounceExecutor.fastForwardTime(101L)
        assertThat(callback1.changedPackageNames).isNull()
        assertThat(callback2.changedPackageNames).containsExactly(PKG_1)
    }

    @Test
    fun shutdown_attemptInvokingCallback_failsGracefully() {
        val callback1 = TestInternalCallback()
        val fakeDebounceExecutor = FakeScheduledExecutorService()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 0)
        callbacksRouter.addCallback(callback1)

        callbacksRouter.shutDown()

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-$PKG_1"),
            )
        )
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_1",
                setOf("$PKG_1/id1"),
            )
        )
        callbacksRouter.onEnabledStatesChanged(setOf(AppFunctionName(PKG_1, "id1")))

        assertThat(callback1.changedPackageNames).isNull()
        assertThat(callback1.stateChangedFunctionNames).isNull()
    }

    @Test
    fun onEnabledStatesChanged_routesToAllCallbacks() {
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()
        val fakeExecutor = FakeScheduledExecutorService()

        val callbacksRouter = createRouter(fakeExecutor, enabledStateDebounceMs = 0)
        callbacksRouter.addCallback(callback1)
        callbacksRouter.addCallback(callback2)

        val changedFunctions = setOf(AppFunctionName(PKG_1, "id1"), AppFunctionName(PKG_2, "id2"))
        callbacksRouter.onEnabledStatesChanged(changedFunctions)
        fakeExecutor.fastForwardTime(1)

        assertThat(callback1.stateChangedFunctionNames).containsExactlyElementsIn(changedFunctions)
        assertThat(callback2.stateChangedFunctionNames).containsExactlyElementsIn(changedFunctions)
    }

    @Test
    fun onEnabledStatesChanged_consequentUpdates_debouncedCorrectly() {
        val callback1 = TestInternalCallback()
        val fakeExecutor = FakeScheduledExecutorService()

        val callbacksRouter =
            createRouter(
                fakeExecutor,
                enabledStateDebounceMs = 100,
                enabledStateMaxDebounceMs = 200,
            )
        callbacksRouter.addCallback(callback1)

        val changedFunctions1 = setOf(AppFunctionName(PKG_1, "id1"))
        val changedFunctions2 = setOf(AppFunctionName(PKG_1, "id2"))

        // First change
        callbacksRouter.onEnabledStatesChanged(changedFunctions1)

        // Fast forward time, but not enough to trigger debounce
        fakeExecutor.fastForwardTime(50L)
        assertThat(callback1.stateChangedFunctionNames).isNull()

        // Second change
        callbacksRouter.onEnabledStatesChanged(changedFunctions2)

        // Fast forward time beyond initial debounce duration but within new debounce duration
        fakeExecutor.fastForwardTime(51L)
        assertThat(callback1.stateChangedFunctionNames).isNull()

        // Fast forward time beyond new debounce duration
        fakeExecutor.fastForwardTime(50L)
        val expectedFunctions = setOf(AppFunctionName(PKG_1, "id1"), AppFunctionName(PKG_1, "id2"))
        assertThat(callback1.stateChangedFunctionNames).containsExactlyElementsIn(expectedFunctions)
    }

    @Test
    fun onEnabledStatesChanged_consequentUpdates_maxDebounceReached() {
        val callback1 = TestInternalCallback()
        val fakeExecutor = FakeScheduledExecutorService()

        val callbacksRouter =
            createRouter(
                fakeExecutor,
                enabledStateDebounceMs = 100,
                enabledStateMaxDebounceMs = 200,
            )
        callbacksRouter.addCallback(callback1)

        val changedFunctions1 = setOf(AppFunctionName(PKG_1, "id1"))
        val changedFunctions2 = setOf(AppFunctionName(PKG_1, "id2"))
        val changedFunctions3 = setOf(AppFunctionName(PKG_1, "id3"))

        // First change. Deadline at 200ms.
        callbacksRouter.onEnabledStatesChanged(changedFunctions1)

        // Forward 80ms.
        fakeExecutor.fastForwardTime(80L)

        // Second change. Timer would reset to +100ms (180ms total), which is < 200ms deadline.
        callbacksRouter.onEnabledStatesChanged(changedFunctions2)

        // Forward 80ms. Total 160ms.
        fakeExecutor.fastForwardTime(80L)

        // Third change. Timer would reset to +100ms (260ms total).
        // But deadline is at 200ms. So it should be scheduled at 200ms (40ms from now).
        callbacksRouter.onEnabledStatesChanged(changedFunctions3)

        // Forward 50ms. Total 210ms. Deadline passed.
        fakeExecutor.fastForwardTime(50L)

        val expectedFunctions =
            setOf(
                AppFunctionName(PKG_1, "id1"),
                AppFunctionName(PKG_1, "id2"),
                AppFunctionName(PKG_1, "id3"),
            )
        assertThat(callback1.stateChangedFunctionNames).containsExactlyElementsIn(expectedFunctions)
    }

    @Test
    fun onSchemaChanged_filtersInvisiblePackages() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 0)

        val callerId1 = CallerIdentity("caller1", UserHandle.of(0), 1001, 1001)
        callbacksRouter.addCallback(callback1, callerId1)
        whenever(mockVisibilityHelper.filterVisiblePackages(eq(ALL_PACKAGES), eq(callerId1)))
            .thenReturn(setOf(PKG_1))

        val callerId2 = CallerIdentity("caller2", UserHandle.of(0), 1002, 1002)
        callbacksRouter.addCallback(callback2, callerId2)
        whenever(mockVisibilityHelper.filterVisiblePackages(eq(ALL_PACKAGES), eq(callerId2)))
            .thenReturn(setOf(PKG_2))

        callbacksRouter.onSchemaChanged(
            SchemaChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                setOf("$STATIC_SCHEMA_TYPE-$PKG_1", "$STATIC_SCHEMA_TYPE-$PKG_2"),
            )
        )

        fakeDebounceExecutor.fastForwardTime(1L)
        assertThat(callback1.changedPackageNames).containsExactly(PKG_1)
        assertThat(callback2.changedPackageNames).containsExactly(PKG_2)
    }

    @Test
    fun onDocumentChanged_filtersInvisiblePackages() {
        val fakeDebounceExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeDebounceExecutor, metadataDebounceMs = 0)

        val callerId1 = CallerIdentity("caller1", UserHandle.of(0), 1001, 1001)
        callbacksRouter.addCallback(callback1, callerId1)
        whenever(mockVisibilityHelper.filterVisiblePackages(eq(setOf(PKG_1)), eq(callerId1)))
            .thenReturn(setOf(PKG_1))
        whenever(mockVisibilityHelper.filterVisiblePackages(eq(setOf(PKG_2)), eq(callerId1)))
            .thenReturn(emptySet())

        val callerId2 = CallerIdentity("caller2", UserHandle.of(0), 1002, 1002)
        callbacksRouter.addCallback(callback2, callerId2)
        whenever(mockVisibilityHelper.filterVisiblePackages(eq(setOf(PKG_1)), eq(callerId2)))
            .thenReturn(emptySet())
        whenever(mockVisibilityHelper.filterVisiblePackages(eq(setOf(PKG_2)), eq(callerId2)))
            .thenReturn(setOf(PKG_2))

        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_1",
                setOf("$PKG_1/id1"),
            )
        )
        fakeDebounceExecutor.fastForwardTime(1L)
        assertThat(callback1.changedPackageNames).containsExactly(PKG_1)
        assertThat(callback2.changedPackageNames).isNull()

        callback1.reset()
        callback2.reset()
        callbacksRouter.onDocumentChanged(
            DocumentChangeInfo(
                "android",
                APP_FUNCTION_STATIC_METADATA_DB,
                APP_FUNCTION_STATIC_NAMESPACE,
                "$STATIC_SCHEMA_TYPE-$PKG_2",
                setOf("$PKG_2/id2"),
            )
        )
        fakeDebounceExecutor.fastForwardTime(1L)
        assertThat(callback1.changedPackageNames).isNull()
        assertThat(callback2.changedPackageNames).containsExactly(PKG_2)
    }

    @Test
    fun onEnabledStatesChanged_filtersInvisibleFunctions() {
        val fakeExecutor = FakeScheduledExecutorService()
        val callback1 = TestInternalCallback()
        val callback2 = TestInternalCallback()

        val callbacksRouter = createRouter(fakeExecutor, enabledStateDebounceMs = 0)

        val callerId1 = CallerIdentity("caller1", UserHandle.of(0), 1001, 1001)
        callbacksRouter.addCallback(callback1, callerId1)
        val callerId2 = CallerIdentity("caller2", UserHandle.of(0), 1002, 1002)
        callbacksRouter.addCallback(callback2, callerId2)

        val func1 = AppFunctionName(PKG_1, "id1")
        val func2 = AppFunctionName(PKG_2, "id2")
        val changedFunctions = setOf(func1, func2)

        whenever(
                mockVisibilityHelper.filterVisibleAppFunctions(eq(changedFunctions), eq(callerId1))
            )
            .thenReturn(setOf(func1))
        whenever(
                mockVisibilityHelper.filterVisibleAppFunctions(eq(changedFunctions), eq(callerId2))
            )
            .thenReturn(setOf(func2))

        callbacksRouter.onEnabledStatesChanged(changedFunctions)
        fakeExecutor.fastForwardTime(1L)

        assertThat(callback1.stateChangedFunctionNames).containsExactly(func1)
        assertThat(callback2.stateChangedFunctionNames).containsExactly(func2)
    }

    private fun InternalObserverCallbackRouter.addCallback(
        callback: IObserveAppFunctionChangesCallback
    ) {
        addCallback(callback, CallerIdentity("testPackage", UserHandle.of(0), 0, 0))
    }

    private fun InternalObserverCallbackRouter.removeCallback(
        callback: IObserveAppFunctionChangesCallback
    ) {
        removeCallback(callback, CallerIdentity("testPackage", UserHandle.of(0), 0, 0))
    }

    class TestInternalCallback : IObserveAppFunctionChangesCallback {
        var changedPackageNames: List<String>? = null
        var stateChangedFunctionNames: List<AppFunctionName>? = null
        val binder = Binder((binderId++).toString())

        override fun onPackagesChanged(changedPackageNames: List<String>) {
            this.changedPackageNames = changedPackageNames
        }

        override fun onAppFunctionStatesChanged(appFunctions: List<AppFunctionName>) {
            stateChangedFunctionNames = appFunctions
        }

        override fun asBinder(): IBinder {
            return binder
        }

        fun reset() {
            changedPackageNames = null
            stateChangedFunctionNames = null
        }

        companion object {
            var binderId = 1
        }
    }

    companion object {
        private const val PKG_1 = "testPackage1"
        private const val PKG_2 = "testPackage2"
        private val ALL_PACKAGES = setOf(PKG_1, PKG_2)
    }
}
