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
package android.hardware.hid

import android.app.AppOpsManager
import android.content.Context
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.hardware.input.Flags.FLAG_HID_API
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "HidManagerPermissionsTest"

/**
 * Tests for [HidManager] permissions.
 *
 * Build/Install/Run: atest HidTests:HidManagerPermissionsTest
 */
@RunWith(AndroidJUnit4::class)
class HidManagerPermissionsTest {
    @get:Rule val setFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var context: Context
    private lateinit var hidManager: HidManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getContext()!!
        hidManager = HidManager(context)
    }

    @After
    fun resetToDefault() {
        modifyHidAccessAppOp(AppOpsManager.MODE_DEFAULT)
    }

    private fun modifyHidAccessAppOp(mode: Int) {
        val uid = context.getPackageManager().getPackageUid(context.getPackageName(), 0)
        context
            .getSystemService(AppOpsManager::class.java)
            .setUidMode(AppOpsManager.OPSTR_ACCESS_HID, uid, mode)
    }

    @Test
    @RequiresFlagsEnabled(FLAG_HID_API)
    fun canEnumerateDevices_trueWhenAppOpAllowed() {
        modifyHidAccessAppOp(AppOpsManager.MODE_ALLOWED)
        assertTrue(hidManager.canEnumerateDevices())
    }

    @Test
    @RequiresFlagsEnabled(FLAG_HID_API)
    fun canEnumerateDevices_falseWhenAppOpIgnored() {
        modifyHidAccessAppOp(AppOpsManager.MODE_IGNORED)
        assertFalse(hidManager.canEnumerateDevices())
    }

    @Test
    @RequiresFlagsEnabled(FLAG_HID_API)
    fun canEnumerateDevices_falseWhenAppOpErrored() {
        modifyHidAccessAppOp(AppOpsManager.MODE_ERRORED)
        assertFalse(hidManager.canEnumerateDevices())
    }
}
