/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.shared.plugins

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.plugins.OverlayPlugin
import com.android.systemui.plugins.annotations.Requires
import com.android.systemui.plugins.qs.QS
import com.android.systemui.plugins.qs.QS.HeightListener
import com.android.systemui.shared.plugins.VersionInfo.InvalidVersionException
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VersionInfoTest : SysuiTestCase() {
    @Rule @JvmField val mThrown: ExpectedException = ExpectedException.none()

    @Test
    fun testHasInfo() {
        Assert.assertFalse(VersionInfo<VersionInfoTest>().hasVersionInfo)
        Assert.assertTrue(VersionInfo<OverlayPlugin>().hasVersionInfo)
    }

    @Test
    fun testSingleProvides() {
        val overlay = VersionInfo<OverlayPlugin>()
        val impl = VersionInfo<OverlayImpl>()
        overlay.checkVersion(impl)
    }

    @Test
    fun testIncorrectVersion() {
        val overlay = VersionInfo<OverlayPlugin>()
        val impl = VersionInfo<OverlayImplIncorrectVersion>()
        mThrown.expect(InvalidVersionException::class.java)
        overlay.checkVersion(impl)
    }

    @Test
    fun testMissingRequired() {
        mThrown.expect(IllegalArgumentException::class.java)
        VersionInfo(listOf())
    }

    @Test
    fun testMissingDependencies() {
        val overlay = VersionInfo<QS>()
        val impl = VersionInfo<QSImplNoDeps>()
        mThrown.expect(InvalidVersionException::class.java)
        overlay.checkVersion(impl)
    }

    @Test
    fun testHasDependencies() {
        val overlay = VersionInfo<QS>()
        val impl = VersionInfo<QSImpl>()
        overlay.checkVersion(impl)
    }

    @Requires(target = OverlayPlugin::class, version = OverlayPlugin.VERSION) class OverlayImpl

    @Requires(target = OverlayPlugin::class, version = 0) class OverlayImplIncorrectVersion

    @Requires(target = QS::class, version = QS.VERSION) class QSImplNoDeps

    @Requires(target = QS::class, version = QS.VERSION)
    @Requires(target = HeightListener::class, version = HeightListener.VERSION)
    class QSImpl
}
