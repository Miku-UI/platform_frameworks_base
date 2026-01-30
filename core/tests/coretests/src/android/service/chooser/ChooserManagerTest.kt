/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.chooser

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
import android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
import android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
import android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
import android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
import android.content.Intent.FLAG_ACTIVITY_TASK_ON_HOME
import android.os.Bundle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ChooserManagerTest {
    @get:Rule
    val mSetFlagsRule: SetFlagsRule = SetFlagsRule()

    @Test(expected = IllegalArgumentException::class)
    fun test_startSession_nonChooserActionIntent_exceptionThrown() {
        ChooserManager().startSession(mock<Context>(), Intent(ACTION_SEND))
    }

    @EnableFlags(Flags.FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_startSession_intentWithFlags_intentProperlyConfigured() {
        val context = mock<Context>()
        val flags =
            FLAG_ACTIVITY_SINGLE_TOP or
                FLAG_ACTIVITY_NEW_TASK or
                FLAG_ACTIVITY_CLEAR_TASK or
                FLAG_ACTIVITY_CLEAR_TOP or
                FLAG_ACTIVITY_MULTIPLE_TASK or
                FLAG_ACTIVITY_REORDER_TO_FRONT or
                FLAG_ACTIVITY_TASK_ON_HOME or
                FLAG_ACTIVITY_LAUNCH_ADJACENT
        val chooserIntent =
            Intent.createChooser(Intent(ACTION_SEND), null).apply { setFlags(flags) }

        ChooserManager().startSession(context, chooserIntent)

        val intentCaptor = argumentCaptor<Intent>()
        val optionsCaptor = argumentCaptor<Bundle>()
        verify(context) {
            1 * { context.startActivity(intentCaptor.capture(), optionsCaptor.capture()) }
        }

        assertThat(intentCaptor.firstValue.flags and flags).isEqualTo(0)
        assertThat(intentCaptor.firstValue.flags and FLAG_ACTIVITY_NO_ANIMATION)
            .isEqualTo(FLAG_ACTIVITY_NO_ANIMATION)
        val options = ActivityOptions.fromBundle(optionsCaptor.firstValue)
        assertThat(options.isAllowPassThroughOnTouchOutside).isTrue()
    }

    @EnableFlags(Flags.FLAG_INTERACTIVE_CHOOSER)
    @Test
    fun test_getSession_nullForClosedSession() {
        val context = mock<Context>()
        val chooserIntent = Intent.createChooser(Intent(ACTION_SEND), null)
        val testSubject = ChooserManager()

        val session = testSubject.startSession(context, chooserIntent)

        assertThat(testSubject.getSession(session.token)).isEqualTo(session)

        session.endSession()

        assertThat(testSubject.getSession(session.token)).isNull()
    }
}
