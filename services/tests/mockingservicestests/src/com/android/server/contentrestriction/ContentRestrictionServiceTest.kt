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

package com.android.server.contentrestriction

import android.app.contentrestriction.ClassifiableContent
import android.app.contentrestriction.IContentRestrictionAppService
import android.app.contentrestriction.IContentRestrictionCallback
import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.content.Context
import android.content.LocusId
import android.content.pm.UserInfo
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.UserHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.LocalServices
import com.android.server.appbinding.AppBindingService
import com.android.server.appbinding.AppServiceConnection
import com.android.server.appbinding.finders.ContentRestrictionAppServiceFinder
import com.android.server.pm.UserManagerInternal
import com.android.server.testutils.mock
import com.android.server.testutils.whenever
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

/**
 * Unit tests for [ContentRestrictionService].
 *
 * Build/Install/Run:
 * atest FrameworksMockingServicesTests:com.android.server.contentrestriction.ContentRestrictionServiceTest
 */
 // TODO(b/469111708): Add the test directories to the `PREUPLOAD.cfg`.
@RunWith(AndroidJUnit4::class)
class ContentRestrictionServiceTest {

    private var userId: Int = 0
    private var callingUid: Int = 0

    @Mock private lateinit var mockAppBindingService: AppBindingService
    @Mock private lateinit var mockRoleManager: RoleManager
    @Mock private lateinit var mockUserManagerInternal: UserManagerInternal
    @Mock private lateinit var mockLocusId: LocusId
    @Mock private lateinit var mockData: ParcelFileDescriptor

    @Captor private lateinit var consumerCaptor: ArgumentCaptor<Consumer<AppServiceConnection>>

    private lateinit var context: Context
    private lateinit var service: ContentRestrictionService
    private lateinit var roleListener: OnRoleHoldersChangedListener
    private lateinit var userLifecycleListener: UserManagerInternal.UserLifecycleListener

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        userId = UserHandle.getCallingUserId()
        context = ApplicationProvider.getApplicationContext()
        callingUid = Binder.getCallingUid()

        LocalServices.removeServiceForTest(AppBindingService::class.java)
        LocalServices.removeServiceForTest(UserManagerInternal::class.java)
        LocalServices.removeServiceForTest(ContentRestrictionManagerInternal::class.java)

        LocalServices.addService(AppBindingService::class.java, mockAppBindingService)
        LocalServices.addService(UserManagerInternal::class.java, mockUserManagerInternal)

        service = ContentRestrictionService(context, mockAppBindingService, mockRoleManager, mockUserManagerInternal)

        LocalServices.addService(ContentRestrictionManagerInternal::class.java, service.mInternal)

        val listenerCaptor = ArgumentCaptor.forClass(OnRoleHoldersChangedListener::class.java)
        verify(mockRoleManager).addOnRoleHoldersChangedListenerAsUser(
                any(),
                listenerCaptor.capture(),
                eq(UserHandle.ALL))
        roleListener = listenerCaptor.value

        val userLifecycleListenerCaptor =
            ArgumentCaptor.forClass(UserManagerInternal.UserLifecycleListener::class.java)
        verify(mockUserManagerInternal).addUserLifecycleListener(userLifecycleListenerCaptor.capture())
        userLifecycleListener = userLifecycleListenerCaptor.value

        assertFalse(service.isContentRestrictionEnabledForUser(userId))
    }

    @After
    fun tearDown() {
        LocalServices.removeServiceForTest(ContentRestrictionManagerInternal::class.java)
    }

    @Test
    fun testIsContentRestrictionEnabled_whenEnabled_returnsTrue() {
        setServiceEnabled(true)
        assertTrue(service.isContentRestrictionEnabledForUser(userId))
    }

    @Test
    @Throws(RemoteException::class)
    fun testRequestClassification_serviceDisabled_returnsTrue() {
        setServiceEnabled(false)

        val callback = mock<IContentRestrictionCallback>()
        val content = ClassifiableContent.Builder(mockLocusId, "text/plain").build()

        service.requestClassification(userId, content, callback)

        verify(callback).onResult(true)
        verify(mockAppBindingService, never())
                .dispatchAppServiceEvent(any(), anyInt(), any())
    }

    @Test
    @Throws(RemoteException::class)
    // TODO(b/469111708): Create a helper method `createConnection` for clarity.
    fun testRequestClassification_serviceEnabled_bindsAndCallsApp() {
        setServiceEnabled(true)

        val callback = mock<IContentRestrictionCallback>()
        val content = ClassifiableContent.Builder(mockLocusId, "text/plain").setData(mockData).build()
        val connection = mock<AppServiceConnection>()
        val receivedContent = AtomicReference<ClassifiableContent>()
        val appServiceStub = object : IContentRestrictionAppService.Stub() {
            override fun onClassifyContent(
                    c: ClassifiableContent, cb: IContentRestrictionCallback) {
                receivedContent.set(c)
                cb.onResult(true)
            }

            override fun onContentRestrictionEnabled(enabled: Boolean) {}
        }
        whenever(connection.getServiceBinder()).thenReturn(appServiceStub)

        service.requestClassification(userId, content, callback)
        verify(mockAppBindingService)
                .dispatchAppServiceEvent(
                        eq(ContentRestrictionAppServiceFinder::class.java),
                        eq(userId),
                        consumerCaptor.capture())

        consumerCaptor.value.accept(connection)

        val capturedContent = receivedContent.get()
        assertNotNull(capturedContent)
        assertSame(content, capturedContent)
    }

    @Test
    @Throws(RemoteException::class)
    // TODO(b/469111708): Create a helper method `createConnection` for clarity.
    fun testRequestClassification_remoteException_failsClosed() {
        setServiceEnabled(true)

        val callback = mock<IContentRestrictionCallback>()
        val content = ClassifiableContent.Builder(mockLocusId, "text/plain").build()
        val connection = mock<AppServiceConnection>()
        val appServiceStub = object : IContentRestrictionAppService.Stub() {
            @Throws(RemoteException::class)
            override fun onClassifyContent(
                    c: ClassifiableContent, cb: IContentRestrictionCallback) {
                throw RemoteException("Test Exception")
            }

            override fun onContentRestrictionEnabled(enabled: Boolean) {}
        }
        whenever(connection.getServiceBinder()).thenReturn(appServiceStub)

        service.requestClassification(userId, content, callback)
        verify(mockAppBindingService)
                .dispatchAppServiceEvent(
                        eq(ContentRestrictionAppServiceFinder::class.java),
                        eq(userId),
                        consumerCaptor.capture())

        consumerCaptor.value.accept(connection)

        verify(callback, never()).onResult(any(Boolean::class.java))
    }

    @Test
    @Throws(RemoteException::class)
    fun testRequestClassification_serviceEnabled_nullBinder_failsClosed() {
        setServiceEnabled(true)

        val callback = mock<IContentRestrictionCallback>()
        val content = ClassifiableContent.Builder(mockLocusId, "text/plain").build()
        val connection = mock<AppServiceConnection>()

        whenever(connection.getServiceBinder()).thenReturn(null)
        service.requestClassification(userId, content, callback)
        verify(mockAppBindingService)
                .dispatchAppServiceEvent(
                        eq(ContentRestrictionAppServiceFinder::class.java),
                        eq(userId),
                        consumerCaptor.capture())

        consumerCaptor.value.accept(connection)

        verify(callback).onResult(false)
    }

    @Test
    fun onUserRemoved_removesSettings() {
        setServiceEnabled(true)
        val userInfo = UserInfo(userId, "test_user", 0)
        userLifecycleListener.onUserRemoved(userInfo)
        assertFalse(service.isContentRestrictionEnabledForUser(userId))
    }

    private fun setServiceEnabled(enabled: Boolean) {
        val packages = if (enabled) listOf("com.example.filterapp") else null
        service.mInternal.setContentRestrictionPackages(userId, packages, "test_source")
    }
}
