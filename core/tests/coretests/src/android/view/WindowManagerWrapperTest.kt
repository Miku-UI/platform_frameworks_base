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
package android.view

import android.platform.test.annotations.Presubmit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the [WindowManagerWrapper].
 *
 * Build/Install/Run:
 * atest FrameworksCoreTests:WindowManagerWrapperTest
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
@Presubmit
class WindowManagerWrapperTest {

    /**
     * Tests that all default methods from [WindowManager] are implemented.
     */
    @Test
    fun testWindowManagerWrapperImplementation() {
        val windowManagerInterface = WindowManager::class.java
        val wmInterfaceMethods = windowManagerInterface.methods
        val windowManagerWrapperClass = WindowManagerWrapper::class.java
        val wrapperMethodsFromWm = windowManagerWrapperClass.declaredMethods
            .filter { m -> windowManagerInterface.isAssignableFrom(m.declaringClass) }
            .map { m -> m.name }
            .toSet()

        // Only checks the default methods in WM interface. Missing implementation of non-default
        // methods should be caught at compile time.
        val wmDefaultMethods = wmInterfaceMethods
            .filter { m -> m.isDefault }
            .map { m -> m.name }
            .toList()

        for (defaultMethod in wmDefaultMethods) {
            assertThat(wrapperMethodsFromWm).contains(defaultMethod)
        }
    }
}
