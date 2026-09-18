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

import android.Manifest
import android.app.appfunctions.AppFunctionService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.argumentCaptor

@RunWith(AndroidJUnit4::class)
class ServiceHelperImplTest {
    private val packageManager = mock<PackageManager>()
    private val context = mock<Context>()
    private lateinit var serviceHelper: ServiceHelperImpl

    @Before
    fun setup() {
        whenever(context.createContextAsUser(any(), anyInt())).doReturn(context)
        whenever(context.packageManager).doReturn(packageManager)
        serviceHelper = ServiceHelperImpl(context)
    }

    @Test
    fun testResolveAppFunctionService_found() {
        val packageName = "com.example.app"
        val user = UserHandle.of(10)
        val resolveInfo =
            ResolveInfo().apply {
                serviceInfo =
                    ServiceInfo().apply {
                        this.packageName = packageName
                        this.name = "MyService"
                        this.permission = Manifest.permission.BIND_APP_FUNCTION_SERVICE
                    }
            }
        whenever(packageManager.resolveService(any(), anyInt())).thenReturn(resolveInfo)

        val intent = serviceHelper.resolveAppFunctionService(packageName, user)

        assertThat(intent).isNotNull()
        assertThat(intent!!.component!!.packageName).isEqualTo(packageName)
        assertThat(intent.component!!.className).isEqualTo("MyService")
        assertThat(intent.action).isEqualTo(AppFunctionService.SERVICE_INTERFACE)
    }

    @Test
    fun testResolveAppFunctionService_withClassName_found() {
        val packageName = "com.example.app"
        val className = "com.example.app.SpecificService"
        val user = UserHandle.of(10)
        val resolveInfo =
            ResolveInfo().apply {
                serviceInfo =
                    ServiceInfo().apply {
                        this.packageName = packageName
                        this.name = className
                        this.permission = Manifest.permission.BIND_APP_FUNCTION_SERVICE
                    }
            }
        val intentCaptor = argumentCaptor<Intent>()
        whenever(packageManager.resolveService(intentCaptor.capture(), anyInt()))
            .thenReturn(resolveInfo)

        val intent = serviceHelper.resolveAppFunctionService(packageName, className, user)

        val capturedIntent = intentCaptor.firstValue
        assertThat(capturedIntent).isNotNull()
        assertThat(capturedIntent.component?.className).isEqualTo(className)
        assertThat(capturedIntent.component?.packageName).isEqualTo(packageName)
        assertThat(intent).isNotNull()
        assertThat(intent!!.component!!.className).isEqualTo(className)
        assertThat(intent.component!!.packageName).isEqualTo(packageName)
        assertThat(intent.action).isEqualTo(AppFunctionService.SERVICE_INTERFACE)
    }

    @Test
    fun testResolveAppFunctionService_notFound() {
        val packageName = "com.example.app"
        val user = UserHandle.of(10)
        whenever(packageManager.resolveService(any(), anyInt())).thenReturn(null)

        val intent = serviceHelper.resolveAppFunctionService(packageName, user)

        assertThat(intent).isNull()
    }

    @Test
    fun testResolveAppFunctionService_wrongPermission() {
        val packageName = "com.example.app"
        val user = UserHandle.of(10)
        val resolveInfo =
            ResolveInfo().apply {
                serviceInfo =
                    ServiceInfo().apply {
                        this.packageName = packageName
                        this.name = "MyService"
                        this.permission = "wrong.permission"
                    }
            }
        whenever(packageManager.resolveService(any(), anyInt())).thenReturn(resolveInfo)

        val intent = serviceHelper.resolveAppFunctionService(packageName, user)

        assertThat(intent).isNull()
    }
}
