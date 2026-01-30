package com.android.systemui.keyguard.ui.preview

import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.FlagsParameterization.allCombinationsOf
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_DO_NOT_USE_RUN_BLOCKING
import com.android.systemui.SysuiTestCase
import com.android.systemui.util.mockito.mock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class KeyguardRemotePreviewManagerTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return allCombinationsOf(FLAG_DO_NOT_USE_RUN_BLOCKING)
        }
    }

    @Test
    fun onDestroy_clearsReferencesToRenderer() =
        testScope.runTest {
            val preview = KeyguardPreview(mock(), mock(), mock(), mock(), mock(), mock())
            val onDestroy: (PreviewLifecycleObserver) -> Unit = {}

            val observer = PreviewLifecycleObserver(this, testDispatcher, preview, onDestroy)

            // Precondition check.
            assertThat(observer.renderer).isNotNull()
            assertThat(observer.onDestroy).isNotNull()

            observer.onDestroy()

            // The verification checks renderer/requestDestruction lambda because they-re
            // non-singletons which can't leak KeyguardPreviewRenderer.
            assertThat(observer.renderer).isNull()
            assertThat(observer.onDestroy).isNull()
        }
}
