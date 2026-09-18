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

package com.android.systemui.screencapture.common.data.repository

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.mockedContext
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.media.projection.AppContentProjectionService
import android.media.projection.MediaProjectionAppContent
import android.os.Bundle
import android.os.UserHandle
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.screencapture.common.repository.FakeAppContentProjectionCallback
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureAppContentRepositoryImplTest : SysuiTestCase() {

    private val kosmos =
        testKosmosNew().apply {
            mockedContext.stub {
                on {
                    bindServiceAsUser(
                        any<Intent>(),
                        any<ServiceConnection>(),
                        any<Int>(),
                        any<UserHandle>(),
                    )
                } doReturn true
                on { packageManager } doReturn mock<PackageManager>()
            }
        }

    private val fakeUserHandle = UserHandle.of(123)
    private val serviceConnectionCaptor = argumentCaptor<ServiceConnection>()

    private var result: Result<RawAppContent>? = null

    @Before
    fun setUp() {
        kosmos.stubPackageManager()
    }

    private fun Kosmos.stubPackageManager(resolveInfo: List<ResolveInfo>? = null) {
        val defaultResolveInfo =
            ResolveInfo().apply {
                serviceInfo =
                    ServiceInfo().apply {
                        packageName = "FakePackage"
                        name = "FakeService"
                        exported = true
                        permission = Manifest.permission.MANAGE_MEDIA_PROJECTION
                    }
            }
        whenever(
                mockedContext.packageManager.queryIntentServicesAsUser(
                    any<Intent>(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            )
            .thenReturn(resolveInfo ?: listOf(defaultResolveInfo))
        clearInvocations(mockedContext)
    }

    @Test
    fun appContentFor_whenCollectionStarts_bindsToService() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val intentCaptor = argumentCaptor<Intent>()
            verifyNoInteractions(mockedContext)
            assertThat(result).isNull()

            // Act
            val job = startCollection(repository)

            // Assert
            val pm = mockedContext.packageManager
            verify(mockedContext)
                .bindServiceAsUser(
                    intentCaptor.capture(),
                    any(),
                    eq(Context.BIND_AUTO_CREATE),
                    eq(fakeUserHandle),
                )
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(intentCaptor.allValues).hasSize(1)
            with(intentCaptor.lastValue) {
                assertThat(action).isEqualTo(AppContentProjectionService.SERVICE_INTERFACE)
                assertThat(`package`).isEqualTo("FakePackage")
                assertThat(component?.packageName).isEqualTo("FakePackage")
                assertThat(component?.className).isEqualTo("FakeService")
            }
            assertThat(result).isNull()

            // Cleanup
            job.cancel()
        }

    @Test
    fun appContentsFor_serviceNotFound_emitsFailure() =
        kosmos.runTest {
            // Arrange
            val packageName = "FakePackage"
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            stubPackageManager(emptyList())
            val pm = mockedContext.packageManager

            // Act
            val job = startCollection(repository, packageName = packageName)

            // Assert
            assertThat(result?.isFailure).isTrue()
            assertThat(result?.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
            assertThat(result?.exceptionOrNull()?.message)
                .isEqualTo("Package: $packageName does not declare an AppContentProjectionService")
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verify(mockedContext, times(2)).packageManager
            verify(mockedContext, never())
                .bindServiceAsUser(
                    any<Intent>(),
                    any<ServiceConnection>(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verifyNoMoreInteractions(mockedContext)

            // Cleanup
            job.cancel()
        }

    @Test
    fun appContentsFor_serviceNotExported_emitsFailure() =
        kosmos.runTest {
            // Arrange
            val packageName = "FakePackage"
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val notExportedService =
                ResolveInfo().apply {
                    serviceInfo =
                        ServiceInfo().apply {
                            this.packageName = packageName
                            this.name = "FakeService"
                            this.exported = false
                        }
                }
            stubPackageManager(listOf(notExportedService))

            // Act
            val job = startCollection(repository, packageName = packageName)

            // Assert
            assertThat(result?.isFailure).isTrue()
            assertThat(result?.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
            assertThat(result?.exceptionOrNull()?.message)
                .isEqualTo("Service FakeService in $packageName is not exported")

            val pm = mockedContext.packageManager
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verify(mockedContext, never())
                .bindServiceAsUser(
                    any<Intent>(),
                    any<ServiceConnection>(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verifyNoMoreInteractions(mockedContext)

            // Cleanup
            job.cancel()
        }

    @Test
    fun appContentsFor_serviceNotProtectedByPermission_emitsFailure() =
        kosmos.runTest {
            // Arrange
            val packageName = "FakePackage"
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val notProtectedService =
                ResolveInfo().apply {
                    serviceInfo =
                        ServiceInfo().apply {
                            this.packageName = packageName
                            this.name = "FakeService"
                            this.exported = true
                            this.permission = "wrong.permission"
                        }
                }
            stubPackageManager(listOf(notProtectedService))

            // Act
            val job = startCollection(repository, packageName = packageName)

            // Assert
            assertThat(result?.isFailure).isTrue()
            assertThat(result?.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
            assertThat(result?.exceptionOrNull()?.message)
                .isEqualTo(
                    "Service FakeService in $packageName is not protected by MANAGE_MEDIA_PROJECTION"
                )

            val pm = mockedContext.packageManager
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verify(mockedContext, never())
                .bindServiceAsUser(
                    any<Intent>(),
                    any<ServiceConnection>(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verifyNoMoreInteractions(mockedContext)

            // Cleanup
            job.cancel()
        }

    @Test
    fun appContentFor_whenCollectionStops_unbindsFromService() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val pm = mockedContext.packageManager
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(result).isNull()

            // Act
            job.cancel()

            // Assert
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            verify(mockedContext).unbindService(same(serviceConnectionCaptor.lastValue))
            assertThat(result).isNull()
        }

    @Test
    fun appContentFor_failsToBind_doesNotUnbindFromServiceAndEmitsFailure() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context =
                        mockedContext.stub {
                            on {
                                bindServiceAsUser(
                                    any<Intent>(),
                                    any<ServiceConnection>(),
                                    any<Int>(),
                                    any<UserHandle>(),
                                )
                            } doReturn false
                        },
                )
            verifyNoInteractions(mockedContext)
            assertThat(result).isNull()

            // Act
            val job = startCollection(repository)

            // Assert
            val pm = mockedContext.packageManager
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            // If bindService returns false, unbindService SHOULD still be called.
            verify(mockedContext).unbindService(same(serviceConnectionCaptor.lastValue))
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(result?.isFailure).isTrue()

            // Cleanup
            job.cancel()
        }

    @Test
    fun appContentFor_failsToBind_doesNotUnbindWhenCollectionStops() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context =
                        mockedContext.stub {
                            on {
                                bindServiceAsUser(
                                    any<Intent>(),
                                    any<ServiceConnection>(),
                                    any<Int>(),
                                    any<UserHandle>(),
                                )
                            } doReturn false
                        },
                )
            val pm = mockedContext.packageManager
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    any<ServiceConnection>(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verify(mockedContext).unbindService(any())
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(result?.isFailure).isTrue()

            // Act
            job.cancel()

            // Assert
            verify(mockedContext).unbindService(any())
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(result?.isFailure).isTrue()
        }

    @Test
    fun onServiceConnected_requestsContent() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val pm = mockedContext.packageManager
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).isEmpty()
            assertThat(fakeAppContentProjectionCallback.onSessionStoppedCallCount).isEqualTo(0)
            assertThat(result).isNull()

            // Act
            serviceConnectionCaptor.lastValue.onServiceConnected(
                /* name= */ null,
                /* service= */ fakeAppContentProjectionCallback.asBinder(),
            )

            // Assert
            verify(mockedContext, never()).unbindService(any())
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).hasSize(1)
            assertThat(fakeAppContentProjectionCallback.onSessionStoppedCallCount).isEqualTo(0)
            fakeAppContentProjectionCallback.onContentRequestCalls.last().let { call ->
                assertThat(call.newContentConsumer).isNotNull()
                assertThat(call.thumbnailWidth).isEqualTo(200)
                assertThat(call.thumbnailHeight).isEqualTo(100)
                assertThat(call.iconWidth).isEqualTo(50)
                assertThat(call.iconHeight).isEqualTo(50)
            }
            assertThat(result).isNull()

            // Cleanup
            job.cancel()
        }

    @Test
    fun onServiceConnected_invalidBinder_unbindsServiceAndEmitsFailure() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val pm = mockedContext.packageManager
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            val serviceConnection = serviceConnectionCaptor.lastValue
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).isEmpty()
            assertThat(fakeAppContentProjectionCallback.onSessionStoppedCallCount).isEqualTo(0)
            assertThat(result).isNull()

            // Act
            serviceConnection.onServiceConnected(/* name= */ null, /* service= */ null)

            // Assert
            verify(mockedContext).unbindService(same(serviceConnection))
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).isEmpty()
            assertThat(fakeAppContentProjectionCallback.onSessionStoppedCallCount).isEqualTo(0)
            assertThat(result?.isFailure).isTrue()

            // Cleanup
            job.cancel()
        }

    @Test
    fun onBindingDied_unbindsServiceAndEmitsFailure() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val pm = mockedContext.packageManager
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            val serviceConnection = serviceConnectionCaptor.lastValue
            assertThat(result).isNull()

            // Act
            serviceConnection.onBindingDied(/* name= */ null)

            // Assert
            // unbindService is called via awaitClose when flow is closed by onBindingDied
            verify(mockedContext).unbindService(same(serviceConnection))
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(result?.isFailure).isTrue()
            assertThat(result?.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)

            // Cleanup
            job.cancel()
        }

    @Test
    fun onServiceDisconnected_stopSession() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val pm = mockedContext.packageManager
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            val serviceConnection = serviceConnectionCaptor.lastValue
            serviceConnection.onServiceConnected(
                /* name= */ null,
                /* service= */ fakeAppContentProjectionCallback.asBinder(),
            )
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).hasSize(1)
            assertThat(fakeAppContentProjectionCallback.onSessionStoppedCallCount).isEqualTo(0)
            assertThat(result).isNull()

            // Act
            serviceConnection.onServiceDisconnected(null)

            // Assert
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).hasSize(1)
            assertThat(fakeAppContentProjectionCallback.onSessionStoppedCallCount).isEqualTo(1)
            assertThat(result).isNull()

            // Cleanup
            job.cancel()
        }

    @Test
    fun onResult_emitAppContents() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val pm = mockedContext.packageManager
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val fakeAppContent =
                MediaProjectionAppContent.Builder(123)
                    .setTitle("FakeContent")
                    .setThumbnail(createBitmap(200, 100))
                    .build()
            val fakeResultBundle =
                Bundle().apply {
                    putParcelableArray(
                        AppContentProjectionService.EXTRA_APP_CONTENT,
                        arrayOf(fakeAppContent),
                    )
                }
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            serviceConnectionCaptor.lastValue.onServiceConnected(
                /* name= */ null,
                /* service= */ fakeAppContentProjectionCallback.asBinder(),
            )
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).hasSize(1)
            val remoteCallback =
                fakeAppContentProjectionCallback.onContentRequestCalls.last().newContentConsumer
            assertThat(result).isNull()

            // Act
            remoteCallback.sendResult(fakeResultBundle)

            // Assert
            assertThat(result?.isSuccess).isTrue()
            val rawAppContent = result!!.getOrThrow()
            assertThat(rawAppContent.contents).containsExactly(fakeAppContent)
            assertThat(rawAppContent.projectionCallback.get())
                .isEqualTo(fakeAppContentProjectionCallback)

            // Cleanup
            job.cancel()
        }

    @Test
    fun onResult_noAppContent_doesNotEmit() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val pm = mockedContext.packageManager
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val fakeResultBundle = Bundle()
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            serviceConnectionCaptor.lastValue.onServiceConnected(
                /* name= */ null,
                /* service= */ fakeAppContentProjectionCallback.asBinder(),
            )
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).hasSize(1)
            val callback =
                fakeAppContentProjectionCallback.onContentRequestCalls.last().newContentConsumer
            assertThat(result).isNull()

            // Act
            callback.sendResult(fakeResultBundle)

            // Assert
            assertThat(result).isNull()

            // Cleanup
            job.cancel()
        }

    @Test
    fun onResult_noResult_doesNotEmit() =
        kosmos.runTest {
            // Arrange
            val repository =
                ScreenCaptureAppContentRepositoryImpl(
                    scope = backgroundScope,
                    bgContext = testDispatcher,
                    context = mockedContext,
                )
            val pm = mockedContext.packageManager
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verify(mockedContext, times(2)).packageManager
            verify(pm).queryIntentServicesAsUser(any<Intent>(), any<Int>(), any<UserHandle>())
            verifyNoMoreInteractions(mockedContext)
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            serviceConnectionCaptor.lastValue.onServiceConnected(
                /* name= */ null,
                /* service= */ fakeAppContentProjectionCallback.asBinder(),
            )
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).hasSize(1)
            val remoteCallback =
                fakeAppContentProjectionCallback.onContentRequestCalls.last().newContentConsumer
            assertThat(result).isNull()

            // Act
            remoteCallback.sendResult(null)

            // Assert
            assertThat(result).isNull()

            // Cleanup
            job.cancel()
        }

    private fun Kosmos.startCollection(
        repository: ScreenCaptureAppContentRepository,
        packageName: String = "FakePackage",
        user: UserHandle = fakeUserHandle,
        thumbnailWidthPx: Int = 200,
        thumbnailHeightPx: Int = 100,
        iconSizePx: Int = 50,
    ): Job =
        testScope.launch {
            repository
                .appContentsFor(
                    packageName = packageName,
                    user = user,
                    thumbnailWidthPx = thumbnailWidthPx,
                    thumbnailHeightPx = thumbnailHeightPx,
                    iconSizePx = iconSizePx,
                )
                .collect { result = it }
        }
}
