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
package android.content.res

import android.os.Build
import android.platform.test.annotations.Presubmit
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.frameworks.coretests.R
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Presubmit
@RunWith(AndroidJUnit4::class)
class MinorVersionedResourcesTest {

    @get:Rule
    val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    private var mResources: Resources = Resources(null)

    @Before
    fun setup() {
        mResources = InstrumentationRegistry.getInstrumentation().targetContext.getResources()
    }

    @After
    fun teardown() {
        mResources.impl.updateResourcesSdkVersion(Build.VERSION.RESOURCES_SDK_INT_FULL)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RESOURCES_MINOR_VERSION_SUPPORT)
    fun getMinorVersionedResources_FlagEnabled() {
        // The resource is in values-v11001.5/strings.xml
        mResources.impl.updateResourcesSdkVersion(Build.parseFullVersion("11000"))
        assertThat(mResources.getString(R.string.minor_version_test))
            .isEqualTo("no minor versions here")
        mResources.impl.updateResourcesSdkVersion(Build.parseFullVersion("11001.4"))
        assertThat(mResources.getString(R.string.minor_version_test))
            .isEqualTo("no minor versions here")

        mResources.impl.updateResourcesSdkVersion(Build.parseFullVersion("11001.5"))
        assertThat(mResources.getString(R.string.minor_version_test))
            .isEqualTo("minor versioned")
        mResources.impl.updateResourcesSdkVersion(Build.parseFullVersion("11001.6"))
        assertThat(mResources.getString(R.string.minor_version_test))
            .isEqualTo("minor versioned")
        mResources.impl.updateResourcesSdkVersion(Build.parseFullVersion("11002"))
        assertThat(mResources.getString(R.string.minor_version_test))
            .isEqualTo("minor versioned")
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_RESOURCES_MINOR_VERSION_SUPPORT)
    fun getMinorVersionedResources_FlagDisabled() {
        // The resource is in values-v11001.5/strings.xml
        // Minor version is ignored (set to 0) when getting resources with the flag disabled
        mResources.impl.updateResourcesSdkVersion(Build.parseFullVersion("11000"))
        assertThat(mResources.getString(R.string.minor_version_test))
            .isEqualTo("no minor versions here")
        mResources.impl.updateResourcesSdkVersion(Build.parseFullVersion("11001.4"))
        assertThat(mResources.getString(R.string.minor_version_test))
            .isEqualTo("no minor versions here")
        mResources.impl.updateResourcesSdkVersion(Build.parseFullVersion("11001.5"))
        assertThat(mResources.getString(R.string.minor_version_test))
            .isEqualTo("no minor versions here")
        mResources.impl.updateResourcesSdkVersion(Build.parseFullVersion("11001.6"))
        assertThat(mResources.getString(R.string.minor_version_test))
            .isEqualTo("no minor versions here")

        mResources.impl.updateResourcesSdkVersion(Build.parseFullVersion("11002"))
        assertThat(mResources.getString(R.string.minor_version_test))
            .isEqualTo("minor versioned")
    }
}
