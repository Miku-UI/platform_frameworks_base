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
import android.annotation.EnforcePermission
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.mockedContext
import android.media.projection.AppContentProjectionService
import android.media.projection.IAppContentProjectionCallback
import android.media.projection.IAppContentProjectionSession
import android.media.projection.MediaProjectionAppContent
import android.os.Bundle
import android.os.PermissionEnforcer
import android.os.RemoteCallback
import android.os.RemoteException
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
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions

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
            }
        }

    private val fakeUserHandle = UserHandle.of(123)
    private val serviceConnectionCaptor = argumentCaptor<ServiceConnection>()

    private var result: Result<List<MediaProjectionAppContent>>? = null

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
            verify(mockedContext)
                .bindServiceAsUser(
                    intentCaptor.capture(),
                    any(),
                    eq(Context.BIND_AUTO_CREATE),
                    eq(fakeUserHandle),
                )
            verifyNoMoreInteractions(mockedContext)
            assertThat(intentCaptor.allValues).hasSize(1)
            with(intentCaptor.lastValue) {
                assertThat(action).isEqualTo(AppContentProjectionService.SERVICE_INTERFACE)
                assertThat(`package`).isEqualTo("FakePackage")
            }
            assertThat(result).isNull()

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
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
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
    fun appContentFor_failsToBind_unbindsFromServiceAndEmitsFailure() =
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
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            verify(mockedContext).unbindService(same(serviceConnectionCaptor.lastValue))
            verifyNoMoreInteractions(mockedContext)
            assertThat(result?.isFailure).isTrue()

            // Cleanup
            job.cancel()
        }

    @Test
    fun appContentFor_failsToBind_doesNotUnbindAgainWhenCollectionStops() =
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
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    any<ServiceConnection>(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            verify(mockedContext).unbindService(any())
            verifyNoMoreInteractions(mockedContext)
            assertThat(result?.isFailure).isTrue()

            // Act
            job.cancel()

            // Assert
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
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
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
            fakeAppContentProjectionCallback.onContentRequestCalls.last().let {
                (listener, width, height) ->
                assertThat(listener).isNotNull()
                assertThat(width).isEqualTo(200)
                assertThat(height).isEqualTo(100)
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
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
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
            verifyNoMoreInteractions(mockedContext)
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).isEmpty()
            assertThat(fakeAppContentProjectionCallback.onSessionStoppedCallCount).isEqualTo(0)
            assertThat(result?.isFailure).isTrue()

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
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
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
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val fakeAppContent =
                MediaProjectionAppContent(
                    /* thumbnail= */ createBitmap(200, 100),
                    /* title= */ "FakeContent",
                    /* id= */ 123,
                )
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
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            serviceConnectionCaptor.lastValue.onServiceConnected(
                /* name= */ null,
                /* service= */ fakeAppContentProjectionCallback.asBinder(),
            )
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).hasSize(1)
            val callback = fakeAppContentProjectionCallback.onContentRequestCalls.last().first
            assertThat(result).isNull()

            // Act
            callback.sendResult(fakeResultBundle)

            // Assert
            assertThat(result?.isSuccess).isTrue()
            assertThat(result?.getOrNull()).containsExactly(fakeAppContent)

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
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            serviceConnectionCaptor.lastValue.onServiceConnected(
                /* name= */ null,
                /* service= */ fakeAppContentProjectionCallback.asBinder(),
            )
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).hasSize(1)
            val callback = fakeAppContentProjectionCallback.onContentRequestCalls.last().first
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
            val fakeAppContentProjectionCallback = FakeAppContentProjectionCallback(mockedContext)
            val job = startCollection(repository)
            verify(mockedContext)
                .bindServiceAsUser(
                    any<Intent>(),
                    serviceConnectionCaptor.capture(),
                    any<Int>(),
                    any<UserHandle>(),
                )
            assertThat(serviceConnectionCaptor.allValues).hasSize(1)
            serviceConnectionCaptor.lastValue.onServiceConnected(
                /* name= */ null,
                /* service= */ fakeAppContentProjectionCallback.asBinder(),
            )
            assertThat(fakeAppContentProjectionCallback.onContentRequestCalls).hasSize(1)
            val callback = fakeAppContentProjectionCallback.onContentRequestCalls.last().first
            assertThat(result).isNull()

            // Act
            callback.sendResult(null)

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
    ): Job =
        testScope.launch {
            repository
                .appContentsFor(
                    packageName = packageName,
                    user = user,
                    thumbnailWidthPx = thumbnailWidthPx,
                    thumbnailHeightPx = thumbnailHeightPx,
                )
                .collect { result = it }
        }
}

private class FakeAppContentProjectionCallback(context: Context) :
    IAppContentProjectionCallback.Stub(PermissionEnforcer(context)) {
    val onContentRequestCalls = mutableListOf<Triple<RemoteCallback, Int, Int>>()

    @EnforcePermission(allOf = [Manifest.permission.MANAGE_MEDIA_PROJECTION])
    @Throws(RemoteException::class)
    override fun onContentRequest(
        newContentConsumer: RemoteCallback,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
    ) {
        onContentRequest_enforcePermission()
        onContentRequestCalls.add(Triple(newContentConsumer, thumbnailWidth, thumbnailHeight))
    }

    @EnforcePermission(allOf = [Manifest.permission.MANAGE_MEDIA_PROJECTION])
    @Throws(RemoteException::class)
    override fun onLoopbackProjectionStarted(
        session: IAppContentProjectionSession?,
        contentId: Int,
    ) {
        onLoopbackProjectionStarted_enforcePermission()
    }

    var onSessionStoppedCallCount: Int = 0

    @EnforcePermission(allOf = [Manifest.permission.MANAGE_MEDIA_PROJECTION])
    @Throws(RemoteException::class)
    override fun onSessionStopped() {
        onSessionStopped_enforcePermission()
        onSessionStoppedCallCount++
    }

    @EnforcePermission(allOf = [Manifest.permission.MANAGE_MEDIA_PROJECTION])
    @Throws(RemoteException::class)
    override fun onContentRequestCanceled() {
        onContentRequestCanceled_enforcePermission()
    }
}
