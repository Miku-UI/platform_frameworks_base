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

package com.android.server.companion.datatransfer.crossdevicesync.services

import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncServiceShellCommandTest : SyncServiceTestBase() {

    @Test
    fun testOnCommand_resetNotifications_succeeds() {
        val (outRead, outWrite) = ParcelFileDescriptor.createPipe()

        val args = arrayOf("reset", "notifications")
        val result =
            mSyncServiceShellCommand.exec(
                Binder(),
                null, // in
                outWrite.fileDescriptor, // out
                null, // err
                args,
                null, // shellCallback
                ResultReceiver(null),
            )

        outWrite.close() // Close write end so read end can finish reading

        assertThat(result).isEqualTo(0)
        assertThat(mFakeNotificationHelper.resetCalled).isTrue()

        val output =
            FileInputStream(outRead.fileDescriptor).bufferedReader(StandardCharsets.UTF_8).use {
                it.readText()
            }
        assertThat(output).contains("Reset all Notifications for crossdevicesync")
    }

    @Test
    fun testOnCommand_resetUnknown_returnsError() {
        val (outRead, outWrite) = ParcelFileDescriptor.createPipe()

        val args = arrayOf("reset", "unknown_type")
        val result =
            mSyncServiceShellCommand.exec(
                Binder(),
                null,
                outWrite.fileDescriptor,
                null,
                args,
                null,
                ResultReceiver(null),
            )

        outWrite.close()

        assertThat(result).isEqualTo(-1)
        assertThat(mFakeNotificationHelper.resetCalled).isFalse()

        val output =
            FileInputStream(outRead.fileDescriptor).bufferedReader(StandardCharsets.UTF_8).use {
                it.readText()
            }
        assertThat(output).contains("Unknown command : reset unknown_type")
    }

    @Test
    fun testOnCommand_help_printsHelp() {
        val (outRead, outWrite) = ParcelFileDescriptor.createPipe()

        val args = arrayOf("help")
        val result =
            mSyncServiceShellCommand.exec(
                Binder(),
                null,
                outWrite.fileDescriptor,
                null,
                args,
                null,
                ResultReceiver(null),
            )

        outWrite.close()

        assertThat(result).isEqualTo(0)

        val output =
            FileInputStream(outRead.fileDescriptor).bufferedReader(StandardCharsets.UTF_8).use {
                it.readText()
            }
        assertThat(output).contains("SyncService commands:")
    }
}
