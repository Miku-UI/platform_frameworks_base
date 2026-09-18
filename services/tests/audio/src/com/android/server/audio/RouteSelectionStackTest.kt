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
import android.media.AudioDeviceAttributes
import android.media.AudioDeviceInfo
import android.os.IBinder
import android.platform.test.annotations.Presubmit
import androidx.test.filters.SmallTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.audio.RouteSelectionStack.RouteClient
import com.android.server.testutils.mock
import com.android.server.utils.EventLogger
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import java.util.function.Consumer
import org.junit.Test
import org.junit.runner.RunWith

@Presubmit
@SmallTest
@RunWith(AndroidJUnit4::class)
class RouteSelectionStackTest {

    private val mDeathCallback = mock<Consumer<RouteClient>>()
    private val mEventLogger = mock<EventLogger>()
    private val mToken1 = mock<IBinder>()
    private val mToken2 = mock<IBinder>()

    private val mAttributionSource1 = AttributionSource(1001, "com.example.app1", null)
    private val mAttributionSource2 = AttributionSource(1002, "com.example.app2", null)
    private val mStack = RouteSelectionStack(mDeathCallback, mEventLogger)
    private val mDevice1 = AudioDeviceAttributes(
        AudioDeviceAttributes.ROLE_OUTPUT,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        "addr1"
    )
    private val mDevice2 = AudioDeviceAttributes(
        AudioDeviceAttributes.ROLE_OUTPUT,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        "addr2"
    )

    private fun createClient(
        token: IBinder,
        attr: AttributionSource,
        device: AudioDeviceAttributes
    ) = RouteClient(token, attr, device, false)

    @Test
    fun testSelectedDevice_oneClient() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))

        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))
    }

    @Test
    fun testSelectedDevice_stackBehavior() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        mStack.addClient(createClient(mToken2, mAttributionSource2, mDevice2))
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice2))

        mStack.removeClient(mToken2)
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))
    }

    @Test
    fun testSelectedDevice_modeOwnerOverride() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        mStack.addClient(createClient(mToken2, mAttributionSource2, mDevice2))
        // client2 is top

        mStack.setModeOwnerToken(mToken1)
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        mStack.setModeOwnerToken(null)
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice2))
    }

    @Test
    fun testSelectedDevice_modeOwnerNotInStack() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))

        // Set mode owner to a token that is not in the stack
        mStack.setModeOwnerToken(mToken2)

        assertThat(mStack.selectedDevice().isEmpty).isTrue()
    }

    @Test
    fun testSelectedDevice_removeClientPreference_noFallback() {
        mStack.addClient(createClient(mToken2, mAttributionSource2, mDevice2))
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))

        // Make client1 mode owner
        mStack.setModeOwnerToken(mToken1)
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        // remove its preference
        mStack.removeClientPreference { it == mDevice1 }

        assertThat(mStack.selectedDevice().isEmpty).isTrue()
    }

    @Test
    fun testSelectedDevice_inactiveClient() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))

        // Default is active
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        // Make inactive
        mStack.updateActiveForUid(mAttributionSource1.uid, false)

        assertThat(mStack.selectedDevice().isEmpty).isTrue()
    }

    @Test
    fun testSelectedDevice_inactiveClient_preferenceIgnored() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        mStack.addClient(createClient(mToken2, mAttributionSource2, mDevice2))

        // Default is active
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice2))

        // Make top client inactive
        mStack.updateActiveForUid(mAttributionSource2.getUid(), false)

        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))
    }

    @Test
    fun testUpdateActiveForUid_reactivatesRoute() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))

        // Initial state active
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        // Deactivate
        var changed = mStack.updateActiveForUid(mAttributionSource1.getUid(), false)
        assertThat(changed).isTrue()
        assertThat(mStack.selectedDevice().isEmpty).isTrue()

        // Reactivate
        changed = mStack.updateActiveForUid(mAttributionSource1.getUid(), true)
        assertThat(changed).isTrue()
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        // No change
        changed = mStack.updateActiveForUid(mAttributionSource1.getUid(), true)
        assertThat(changed).isFalse()
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))
    }

    @Test
    fun testUpdateClient_updatesDevice() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))

        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        val updated = mStack.updateClient(mToken1, Optional.of(mDevice2))
        assertThat(updated).isTrue()
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice2))
    }

    @Test
    fun testUpdateClient_tokenNotFound() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        val updated = mStack.updateClient(mToken2, Optional.of(mDevice2))
        assertThat(updated).isFalse()
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))
    }

    @Test
    fun testRemoveClientPreference_clearsMatchingDevice() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))

        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        // Remove preference matching mDevice1
        mStack.removeClientPreference { it == mDevice1 }

        assertThat(mStack.selectedDevice().isEmpty).isTrue()
    }

    @Test
    fun testRemoveClient() {
        val client1 = createClient(mToken1, mAttributionSource1, mDevice1)
        mStack.addClient(client1)

        val removed = mStack.removeClient(mToken1)
        assertThat(removed).isEqualTo(client1)
        assertThat(mStack.selectedDevice().isEmpty).isTrue()
    }

    @Test
    fun testRemoveClient_tokenNotFound() {
        val removed = mStack.removeClient(mToken1)
        assertThat(removed).isNull()
    }

    @Test
    fun testAddClient_movesToTopAndUpdates() {
        val device3 = AudioDeviceAttributes(
            AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "addr3"
        )

        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        mStack.addClient(createClient(mToken2, mAttributionSource2, mDevice2))
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice2))

        mStack.addClient(createClient(mToken1, mAttributionSource1, device3))
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(device3))

        // Remove client1, client2 should be next
        mStack.removeClient(mToken1)
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice2))
    }

    @Test
    fun testApplyDeviceRestrictions_disablesSelection() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        mStack.applyDeviceRestrictions { if (it == mDevice1) null else it }

        assertThat(mStack.selectedDevice().isEmpty).isTrue()
    }

    @Test
    fun testApplyDeviceRestrictions_updatesDevice() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        // Restriction: if device is mDevice1, switch to mDevice2
        mStack.applyDeviceRestrictions { if (it == mDevice1) mDevice2 else it }

        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice2))
    }

    @Test
    fun testApplyDeviceRestrictions_onlyAffectsMatchingDevices() {
        // Case 1: Predicate removes device
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        val client2 = createClient(mToken2, mAttributionSource2, mDevice2)
        mStack.addClient(client2)

        // Restriction: if device is mDevice1, disable
        // Ensure this doesn't affect mDevice2.
        mStack.applyDeviceRestrictions { if (it == mDevice1) null else it }

        // client2 is on top and should be unaffected
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice2))

        // Remove client2 to verify client1 was affected
        mStack.removeClient(mToken2)
        assertThat(mStack.selectedDevice().isEmpty).isTrue()

        // Case 2: Predicate updates (disabled) device
        // re-add client2
        mStack.addClient(client2)
        val device3 = AudioDeviceAttributes(
            AudioDeviceAttributes.ROLE_OUTPUT, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "addr3"
        )

        // Restriction: if device is mDevice1 (client 1), switch to device3. Else keep.
        mStack.applyDeviceRestrictions { if (it == mDevice1) device3 else it }

        // client2 (mDevice2) should be unaffected
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice2))

        mStack.removeClient(mToken2)
        // client1 should now be device3
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(device3))
    }

    @Test
    fun testPrivilegedClient_alwaysActive() {
        // Create privileged client
        mStack.addClient(RouteClient(mToken1, mAttributionSource1, mDevice1, true))
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        // Try to deactivate
        mStack.updateActiveForUid(mAttributionSource1.uid, false)

        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))
    }

    @Test
    fun testSelectedDevice_topClientInactive() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        mStack.addClient(createClient(mToken2, mAttributionSource2, mDevice2))

        mStack.updateActiveForUid(mAttributionSource2.getUid(), false)

        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))
    }

    @Test
    fun testSelectedDevice_topClientInactivePrivileged() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        // client2 is privileged
        mStack.addClient(RouteClient(mToken2, mAttributionSource2, mDevice2, true))

        mStack.updateActiveForUid(mAttributionSource2.getUid(), false)

        // Should still be selected because it's privileged
        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice2))
    }

    @Test
    fun testSelectedDevice_topClientDisabled_noFallback() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        mStack.addClient(createClient(mToken2, mAttributionSource2, mDevice2))

        // Disable client2's device via restrictions
        mStack.applyDeviceRestrictions { if (it == mDevice2) null else it }

        // Top client still selected, but device is disabled
        assertThat(mStack.selectedDevice().isEmpty).isTrue()
    }

    @Test
    fun testSelectedDevice_modeOwnerDisabled_noFallback() {
        mStack.addClient(createClient(mToken1, mAttributionSource1, mDevice1))
        mStack.addClient(createClient(mToken2, mAttributionSource2, mDevice2))
        mStack.setModeOwnerToken(mToken1)

        assertThat(mStack.selectedDevice()).isEqualTo(Optional.of(mDevice1))

        // Disable client1's device
        mStack.applyDeviceRestrictions { if (it == mDevice1) null else it }

        // Mode owner still selected but device is disabled
        assertThat(mStack.selectedDevice().isEmpty).isTrue()
    }
}
