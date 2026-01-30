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

package android.window

import android.content.ComponentName
import android.graphics.Rect
import android.os.test.recreateFromParcel
import android.platform.test.annotations.Presubmit
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_OPEN
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Unit tests for [TransitionInfo].
 *
 * Build/Install/Run:
 * atest FrameworksCoreTests:TransitionInfoTest
 */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4::class)
class TransitionInfoTest {
    private val token = WindowContainerToken(mock<IWindowContainerToken>())
    private val change = TransitionInfo.Change(token, SurfaceControl() /* leash */)
    private val activityTransitionInfo = ActivityTransitionInfo(component, TASK_ID)
    private val appCompatTransitionInfo = AppCompatTransitionInfo(letterboxBounds)
    private val activityTransitionInfoWithAppCompat =
        ActivityTransitionInfo(component, TASK_ID, appCompatTransitionInfo)

    @Test
    fun parcelable_recreateWithActivityTransitionInfoSucceeds() {
        change.activityTransitionInfo = activityTransitionInfo
        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0 /* flags */)
        transitionInfo.addChange(change)

        val createdFromParcel = transitionInfo.recreateFromParcel(TransitionInfo.CREATOR)

        assertThat(createdFromParcel.changes).hasSize(1)
        val chg = createdFromParcel.changes[0]
        assertThat(chg.activityTransitionInfo).isEqualTo(activityTransitionInfo)
    }

    @Test
    fun localRemoteCopy_copiesActivityTransitionInfo() {
        change.activityTransitionInfo = activityTransitionInfo
        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0 /* flags */)
        transitionInfo.addChange(change)

        val copiedTransitionInfo = transitionInfo.localRemoteCopy()

        assertThat(copiedTransitionInfo.changes).hasSize(1)
        val chg = copiedTransitionInfo.changes[0]
        assertThat(chg.activityTransitionInfo).isEqualTo(activityTransitionInfo)
        assertThat(chg.activityTransitionInfo).isNotSameInstanceAs(activityTransitionInfo)
    }

    @Test
    fun parcelable_recreateWithActivityTransitionInfoWithAppCompatSucceeds() {
        change.activityTransitionInfo = activityTransitionInfoWithAppCompat
        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0 /* flags */)
        transitionInfo.addChange(change)

        val createdFromParcel = transitionInfo.recreateFromParcel(TransitionInfo.CREATOR)

        assertThat(createdFromParcel.changes).hasSize(1)
        val chg = createdFromParcel.changes[0]
        assertThat(chg.activityTransitionInfo).isEqualTo(activityTransitionInfoWithAppCompat)
    }

    @Test
    fun localRemoteCopy_copiesActivityTransitionInfoWithAppCompat() {
        change.activityTransitionInfo = activityTransitionInfoWithAppCompat
        val transitionInfo = TransitionInfo(TRANSIT_OPEN, 0 /* flags */)
        transitionInfo.addChange(change)

        val copiedTransitionInfo = transitionInfo.localRemoteCopy()

        assertThat(copiedTransitionInfo.changes).hasSize(1)
        val chg = copiedTransitionInfo.changes[0]
        assertThat(chg.activityTransitionInfo).isEqualTo(activityTransitionInfoWithAppCompat)
        assertThat(chg.activityTransitionInfo).isNotSameInstanceAs(
            activityTransitionInfoWithAppCompat
        )
    }

    companion object {
        private const val TASK_ID = 123
        private const val TEST_PACKAGE_NAME = "com.example.app"
        private const val TEST_CLASS_NAME = "com.example.app.MainActivity"
        private val component = ComponentName(TEST_PACKAGE_NAME, TEST_CLASS_NAME)
        private val letterboxBounds = Rect(1, 2, 3, 4)
    }
}