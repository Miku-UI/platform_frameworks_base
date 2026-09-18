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

package com.android.server.audio

import android.content.AttributionSource
import android.content.Intent
import android.media.AudioManager
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.server.audio.BtHelper.AmScoHelper
import com.android.server.testutils.mock
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

@SmallTest
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class AmScoHelperTest {

    private val mHfp = mock<AmScoHelper.BluetoothHeadsetProxy>()
    private val mBroadcaster = mock<Consumer<Intent?>>()
    private val mHelper = AmScoHelper(mHfp, mBroadcaster)

    @Test
    fun testStartBluetoothSco_forBluetooth() {
        val client = AttributionSource.Builder(Process.BLUETOOTH_UID).build()

        mHelper.startBluetoothSco(client)

        val inOrder = inOrder(mBroadcaster)
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))
        verifyNoMoreInteractions(mHfp)
    }

    @Test
    fun testStartBluetoothSco_telecomCall() {
        val client =
            AttributionSource.Builder(Process.SYSTEM_UID)
                .setPackageName("com.android.server.telecom")
                .build()

        mHelper.startBluetoothSco(client)

        val inOrder = inOrder(mBroadcaster)
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))
        verifyNoMoreInteractions(mHfp)
    }

    @Test
    fun testStartBluetoothSco_virtualCall() {
        val client = AttributionSource.Builder(12345).build()

        mHelper.startBluetoothSco(client)

        verify(mHfp).startScoUsingVirtualVoiceCall()
        val inOrder = inOrder(mBroadcaster)
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))
    }

    @Test
    fun testStartBluetoothSco_managedCall_idempotent() {
        val client1 = AttributionSource.Builder(Process.BLUETOOTH_UID).build()
        val client2 = AttributionSource.Builder(Process.BLUETOOTH_UID).build()

        mHelper.startBluetoothSco(client1)
        mHelper.startBluetoothSco(client2)

        val inOrder = inOrder(mBroadcaster)
        inOrder
            .verify(mBroadcaster, times(1))
            .accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder
            .verify(mBroadcaster, times(1))
            .accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))
        verifyNoMoreInteractions(mHfp)
    }

    @Test
    fun testStartBluetoothSco_virtualCall_idempotent() {
        val client1 = AttributionSource.Builder(12345).build()
        val client2 = AttributionSource.Builder(67890).build()

        mHelper.startBluetoothSco(client1)
        mHelper.startBluetoothSco(client2)

        verify(mHfp, times(1)).startScoUsingVirtualVoiceCall()
        val inOrder = inOrder(mBroadcaster)
        inOrder
            .verify(mBroadcaster, times(1))
            .accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder
            .verify(mBroadcaster, times(1))
            .accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))
    }

    @Test
    fun testStartBluetoothSco_virtualToManagedTransition() {
        val virtualClient = AttributionSource.Builder(12345).build()
        val managedClient = AttributionSource.Builder(Process.BLUETOOTH_UID).build()

        mHelper.startBluetoothSco(virtualClient)
        verify(mHfp, times(1)).startScoUsingVirtualVoiceCall()
        mHelper.startBluetoothSco(managedClient)
        verify(mHfp, times(1)).stopScoUsingVirtualVoiceCall()

        // Broadcaster only called for initial connect from OFF
        val inOrder = inOrder(mBroadcaster)
        inOrder
            .verify(mBroadcaster, times(1))
            .accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder
            .verify(mBroadcaster, times(1))
            .accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))
        verify(mBroadcaster, never()).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_DISCONNECTED))
    }

    @Test
    fun testStartBluetoothSco_managedToVirtualTransition() {
        val managedClient = AttributionSource.Builder(Process.BLUETOOTH_UID).build()
        val virtualClient = AttributionSource.Builder(12345).build()

        mHelper.startBluetoothSco(managedClient)
        mHelper.startBluetoothSco(virtualClient)

        verify(mHfp).startScoUsingVirtualVoiceCall()

        // Broadcaster only called for initial connect from OFF
        val inOrder = inOrder(mBroadcaster)
        inOrder
            .verify(mBroadcaster, times(1))
            .accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder
            .verify(mBroadcaster, times(1))
            .accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))
        verify(mBroadcaster, never()).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_DISCONNECTED))

        // Verify that stopping it properly stops the virtual call
        mHelper.stopBluetoothSco()
        verify(mHfp).stopScoUsingVirtualVoiceCall()
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_DISCONNECTED))
    }

    @Test
    fun testStopBluetoothSco_forTelecom() {
        val client =
            AttributionSource.Builder(Process.SYSTEM_UID)
                .setPackageName("com.android.server.telecom")
                .build()

        mHelper.startBluetoothSco(client)
        val inOrder = inOrder(mBroadcaster)
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))

        mHelper.stopBluetoothSco()

        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_DISCONNECTED))
        verify(mHfp, never()).stopScoUsingVirtualVoiceCall()
    }

    @Test
    fun testStopBluetoothSco_virtualCall() {
        val client = AttributionSource.Builder(12345).build()
        mHelper.startBluetoothSco(client)
        val inOrder = inOrder(mBroadcaster)
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))

        mHelper.stopBluetoothSco()

        verify(mHfp).stopScoUsingVirtualVoiceCall()
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_DISCONNECTED))
    }

    @Test
    fun testStopBluetoothSco_notStarted() {
        mHelper.stopBluetoothSco()

        verifyNoMoreInteractions(mHfp)
        verifyNoMoreInteractions(mBroadcaster)
    }

    @Test
    fun testStopBluetoothSco_idempotent() {
        val client = AttributionSource.Builder(12345).build()
        mHelper.startBluetoothSco(client)
        val inOrder = inOrder(mBroadcaster)
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))

        mHelper.stopBluetoothSco()
        mHelper.stopBluetoothSco()

        verify(mHfp, times(1)).stopScoUsingVirtualVoiceCall()
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_DISCONNECTED))
    }

    @Test
    fun testResetBluetoothSco_fromVirtual() {
        val client = AttributionSource.Builder(12345).build()
        mHelper.startBluetoothSco(client)
        val inOrder = inOrder(mBroadcaster)
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTING))
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_CONNECTED))

        mHelper.resetBluetoothSco()

        verify(mHfp).stopScoUsingVirtualVoiceCall()
        inOrder.verify(mBroadcaster).accept(hasIntent(AudioManager.SCO_AUDIO_STATE_DISCONNECTED))
    }

    private fun hasIntent(state: Int): Intent? {
        val prevState =
            when (state) {
                AudioManager.SCO_AUDIO_STATE_CONNECTING -> AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> AudioManager.SCO_AUDIO_STATE_CONNECTING
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> AudioManager.SCO_AUDIO_STATE_CONNECTED
                else -> throw IllegalArgumentException("Unexpected state: " + state)
            }
        return argThat { intent ->
            intent != null &&
                intent.action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED &&
                intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1) == state &&
                intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE, -1) == prevState
        }
    }
}
