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
package com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC
import android.companion.CompanionDeviceManager.FLAG_AIRPLANE_MODE
import android.os.PersistableBundle
import android.os.UserHandle
import android.os.UserHandle.USER_ALL
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings
import android.util.IndentingPrintWriter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.companion.datatransfer.crossdevicesync.data.DocumentSchemaInfo
import com.android.server.companion.datatransfer.crossdevicesync.data.SchemaProvider
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.MutableDocument
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreTestBase
import com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager.APM_ENABLED_PATH
import com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager.APM_SYNC_SUPPORTED
import com.android.server.companion.datatransfer.crossdevicesync.network.fake.FakeNetworkManager.FakeRemoteDevice
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationReason.AIRPLANE_MODE_SYNCED
import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase
import com.android.internal.R
import com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_AIRPLANE_MODE_SYNC
import com.android.server.connectivity.Flags
import com.google.common.truth.Truth.assertThat
import java.io.StringWriter
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AirplaneModeSyncManagerTest : SyncServiceTestBase() {
    @get:Rule val setFlagsRule: SetFlagsRule = SetFlagsRule()

    private lateinit var mRemoteDeviceContext: RemoteDeviceContext

    // Use real shared data store in this test.
    override fun useRealSharedDataStoreImpl() = true

    @Before
    fun setUp() {
        mFakeResources.overrideResource(R.bool.config_supportAirplaneModeSync, true)
        initializeAirplaneModeSyncManager()
        mFakeNetworkManager.init()
        mFakeMetadataPublisher.init()
        mRemoteDeviceContext = RemoteDeviceContext()
    }

    @After
    fun tearDown() {
        mAirplaneModeSyncManager.destroy()
        mRemoteDeviceContext.close()
        mFakeNetworkManager.destroy()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_syncEnabled_publishesMetadata() {
        setAirplaneModeSyncEnabled(true)
        mAirplaneModeSyncManager.init()

        assertThat(mFakeMetadataPublisher.getMetadata(USER_ALL).getBoolean(APM_SYNC_SUPPORTED))
            .isTrue()
        assertThat(
                mFakeMetadataPublisher
                    .getMetadata(USER_ALL)
                    .getBoolean(Settings.Global.AIRPLANE_MODE_SYNC)
            )
            .isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_syncDisabled_publishesMetadata() {
        setAirplaneModeSyncEnabled(false)
        mAirplaneModeSyncManager.init()

        assertThat(mFakeMetadataPublisher.getMetadata(USER_ALL).getBoolean(APM_SYNC_SUPPORTED))
            .isTrue()
        assertThat(
                mFakeMetadataPublisher
                    .getMetadata(USER_ALL)
                    .getBoolean(Settings.Global.AIRPLANE_MODE_SYNC)
            )
            .isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_initializesNetworkWithFeature() {
        mAirplaneModeSyncManager.init()

        val network = mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL)

        assertThat(network.feature)
            .isEqualTo(CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_AIRPLANE_MODE_SYNC)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_isWatch_allRemoteDevicesEligibleForAirplaneModeSync() {
        mFakeDeviceUtil.isWatch = true
        mAirplaneModeSyncManager.init()

        val network = mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL)

        val remoteDevice =
            FakeRemoteDevice(1, USER_ALL).setAssociationInfoCache(createAssociation())
        assertThat(network.isDeviceEligibleForNetwork(remoteDevice)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_isNotWatch_onlyAllowsRemoteWatchesWithSyncEnabledForAirplaneModeSyncNetwork() {
        mFakeDeviceUtil.isWatch = false
        mAirplaneModeSyncManager.init()

        val network = mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL)

        val remoteDevice =
            FakeRemoteDevice(1, USER_ALL).setAssociationInfoCache(createAssociation())
        val watchWithSyncNotSupported =
            FakeRemoteDevice(1, USER_ALL)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .setAssociationInfoCache(createAssociation(apmSyncSupportedMetadata = false))
        val watchWithSyncDisabled =
            FakeRemoteDevice(1, USER_ALL)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .setAssociationInfoCache(createAssociation(apmSyncEnabledMetadata = false))
        val watchWithSystemDataSyncDisabled =
            FakeRemoteDevice(1, USER_ALL)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .setAssociationInfoCache(createAssociation(apmSystemDataSyncFlagEnabled = false))
        val watchDevice =
            FakeRemoteDevice(1, USER_ALL)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .setAssociationInfoCache(createAssociation())

        assertThat(network.isDeviceEligibleForNetwork(remoteDevice)).isFalse()
        assertThat(network.isDeviceEligibleForNetwork(watchWithSyncNotSupported)).isFalse()
        assertThat(network.isDeviceEligibleForNetwork(watchWithSyncDisabled)).isFalse()
        assertThat(network.isDeviceEligibleForNetwork(watchWithSystemDataSyncDisabled)).isFalse()
        assertThat(network.isDeviceEligibleForNetwork(watchDevice)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_syncsCurrentState() {
        // Enable airplane mode locally before initialization.
        setAirplaneMode(true)

        // Initialize the manager.
        connectDevicesToNetwork()
        mAirplaneModeSyncManager.init()
        flushNetworks()

        // Verify remote device has the initial state.
        verifyRemoteAirplaneModeEnabled(true)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testLocalChange_syncToRemote() {
        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode locally after init, which should trigger the remote device sync.
        setAirplaneMode(true)
        flushNetworks()

        // Verify remote device receives the update.
        verifyRemoteAirplaneModeEnabled(true)

        // Disable airplane mode locally.
        setAirplaneMode(false)
        flushNetworks()

        // Verify remote device receives the update.
        verifyRemoteAirplaneModeEnabled(false)

        // Enable airplane mode locally after destroy, which should not trigger the remote sync.
        mAirplaneModeSyncManager.destroy()
        setAirplaneMode(true)
        flushNetworks()

        // Verify remote device stays off.
        verifyRemoteAirplaneModeEnabled(false)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testRemoteChange_syncToLocal() {
        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode on remote device.
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()

        // Verify local airplane mode is enabled.
        assertThat(isAirplaneModeOn()).isTrue()

        // Disable airplane mode on remote device.
        setRemoteAirplaneModeEnabled(false)
        flushNetworks()

        // Verify local airplane mode is disabled.
        assertThat(isAirplaneModeOn()).isFalse()

        // Enable airplane mode on remote device after destroy.
        mAirplaneModeSyncManager.destroy()
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()

        // Verify local airplane mode stays disabled.
        assertThat(isAirplaneModeOn()).isFalse()
    }

    @Test
    @DisableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_flagDisabled_doesNothing() {
        initializeAirplaneModeSyncManager()
        mAirplaneModeSyncManager.init()
        assertThat(mFakeMetadataPublisher.getMetadata(USER_ALL).isEmpty).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_configDisabled_doesNothing() {
        mFakeResources.overrideResource(R.bool.config_supportAirplaneModeSync, false)
        initializeAirplaneModeSyncManager()
        mAirplaneModeSyncManager.init()
        assertThat(mFakeMetadataPublisher.getMetadata(USER_ALL).isEmpty).isTrue()
    }

    @Test
    @DisableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDestroy_flagDisabled_doesNothing() {
        initializeAirplaneModeSyncManager()
        // No exception should be thrown.
        mAirplaneModeSyncManager.destroy()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDestroy_configDisabled_doesNothing() {
        mFakeResources.overrideResource(R.bool.config_supportAirplaneModeSync, false)
        initializeAirplaneModeSyncManager()
        // No exception should be thrown.
        mAirplaneModeSyncManager.destroy()
    }

    @Test
    @DisableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDump_flagDisabled_doesNothing() {
        initializeAirplaneModeSyncManager()
        val stringWriter = StringWriter()
        val pw = IndentingPrintWriter(stringWriter, "  ")

        mAirplaneModeSyncManager.dump(pw)

        assertThat(stringWriter.toString()).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDump_configDisabled_doesNothing() {
        mFakeResources.overrideResource(R.bool.config_supportAirplaneModeSync, false)
        initializeAirplaneModeSyncManager()
        val stringWriter = StringWriter()
        val pw = IndentingPrintWriter(stringWriter, "  ")

        mAirplaneModeSyncManager.dump(pw)

        assertThat(stringWriter.toString()).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testLocalChange_syncDisabled_doesNothing() {
        // Disable airplane mode sync.
        setAirplaneModeSyncEnabled(false)

        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode locally after init, which should not trigger the remote device sync.
        setAirplaneMode(true)
        flushNetworks()

        // Verify remote device does not receive any update.
        verifyRemoteAirplaneModeEnabled(null)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testRemoteChange_syncDisabled_doesNothing() {
        // Disable airplane mode sync.
        setAirplaneModeSyncEnabled(false)

        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode on remote device.
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()

        // Verify local airplane mode is not enabled.
        assertThat(isAirplaneModeOn()).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testLocalChange_syncDisabled_thenEnabled_syncsToRemote() {
        // Disable airplane mode sync initially.
        setAirplaneModeSyncEnabled(false)

        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode locally.
        setAirplaneMode(true)
        flushNetworks()

        // Verify remote device has NOT received the update.
        verifyRemoteAirplaneModeEnabled(null)

        // Enable airplane mode sync.
        setAirplaneModeSyncEnabled(true)
        flushNetworks()

        // Verify remote device receives the update.
        verifyRemoteAirplaneModeEnabled(true)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testRemoteChange_syncEnabled_showsNotification() {
        // Initialize the manager.
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        // Enable airplane mode on remote device.
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()

        // Verify notification is shown.
        assertThat(mFakeNotificationHelper.shownUserNotifications)
            .contains(AIRPLANE_MODE_SYNCED to UserHandle.ALL)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testInit_isKidsWatch_publishesNotSupported() {
        mFakeDeviceUtil.setKidsWatch(true)
        mAirplaneModeSyncManager.init()
        assertThat(mFakeMetadataPublisher.getMetadata(USER_ALL).getBoolean(APM_SYNC_SUPPORTED))
            .isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testIsKidsWatchChangedToTrue_updatesMetadataAndClosesNetworkAndDataStore() {
        mFakeDeviceUtil.setKidsWatch(false)
        setAirplaneModeSyncEnabled(true)
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        setAirplaneMode(true)
        flushNetworks()

        // Initially supported
        assertThat(mFakeMetadataPublisher.getMetadata(USER_ALL).getBoolean(APM_SYNC_SUPPORTED))
            .isTrue()
        // Verify network was initialized.
        assertThat(mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL))
            .isNotNull()
        // Verify remote device is set.
        verifyRemoteAirplaneModeEnabled(true)

        // Change to kids watch and disable airplane mode
        mFakeDeviceUtil.setKidsWatch(true)
        setAirplaneMode(false)
        flushNetworks()

        // Should be unsupported
        assertThat(mFakeMetadataPublisher.getMetadata(USER_ALL).getBoolean(APM_SYNC_SUPPORTED))
            .isFalse()
        // Verify network was not initialized.
        assertThat(mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL))
            .isNull()
        // Verify remote device stays enabled.
        verifyRemoteAirplaneModeEnabled(true)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testIsKidsWatchChangedToFalse_updatesMetadataAndReopensNetworkAndDataStore() {
        mFakeDeviceUtil.setKidsWatch(false)
        setAirplaneModeSyncEnabled(true)
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()
        // Change to kids watch
        mFakeDeviceUtil.setKidsWatch(true)

        // Change back to regular watch
        mFakeDeviceUtil.setKidsWatch(false)
        setAirplaneMode(true)
        flushNetworks()

        // Should be supported again and syncs current state
        assertThat(mFakeMetadataPublisher.getMetadata(USER_ALL).getBoolean(APM_SYNC_SUPPORTED))
            .isTrue()
        // Verify network was initialized.
        assertThat(mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL))
            .isNotNull()
        // Verify remote device is set.
        verifyRemoteAirplaneModeEnabled(true)
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testOnAirplaneModeSyncEnabledStateChanged_enabled_remoteChangeUpdatesLocal() {
        // Start with sync disabled.
        setAirplaneModeSyncEnabled(false)
        mAirplaneModeSyncManager.init()

        // Enable sync. This will trigger the listener.
        setAirplaneModeSyncEnabled(true)
        connectDevicesToNetwork()

        // Enable airplane mode on remote device.
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()

        // Verify local airplane mode is enabled.
        assertThat(isAirplaneModeOn()).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testOnAirplaneModeSyncEnabledStateChanged_disabled_remoteChangeDoesNotUpdateLocal() {
        // Start with sync enabled.
        setAirplaneModeSyncEnabled(true)
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()
        flushNetworks()

        // Verify sync works
        setRemoteAirplaneModeEnabled(true)
        flushNetworks()
        assertThat(isAirplaneModeOn()).isTrue()

        // Disable sync.
        setAirplaneModeSyncEnabled(false)

        // Try to sync a change from remote
        setRemoteAirplaneModeEnabled(false)
        flushNetworks()

        // Verify local state does not change
        assertThat(isAirplaneModeOn()).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDump_printsCorrectState() {
        val stringWriter = StringWriter()
        val pw = IndentingPrintWriter(stringWriter, "  ")

        // 1. Initial state with sync disabled
        setAirplaneModeSyncEnabled(false)
        setAirplaneMode(true)
        mAirplaneModeSyncManager.init()
        mAirplaneModeSyncManager.dump(pw)
        var dumpString = stringWriter.toString()
        assertThat(dumpString).contains("AirplaneModeSyncManager:")
        assertThat(dumpString).contains("Airplane Mode State = true")
        // Network and Data store are now open even if sync is disabled
        assertThat(dumpString).contains("Network :")
        assertThat(dumpString).contains("Data Store :")

        // 2. Enable sync
        stringWriter.buffer.setLength(0)
        setAirplaneModeSyncEnabled(true)
        connectDevicesToNetwork()
        flushNetworks()
        mAirplaneModeSyncManager.dump(pw)
        dumpString = stringWriter.toString()
        assertThat(dumpString).contains("AirplaneModeSyncManager:")
        assertThat(dumpString).contains("Airplane Mode State = true")
        assertThat(dumpString).contains("Network :")
        assertThat(dumpString).contains("Data Store :")
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDestroy_cleansUpNetworkAndState() {
        setAirplaneModeSyncEnabled(true)
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        assertThat(mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL))
            .isNotNull()

        mAirplaneModeSyncManager.destroy()

        assertThat(mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL))
            .isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDisableSync_doesNotDestroyNetwork() {
        setAirplaneModeSyncEnabled(true)
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        assertThat(mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL))
            .isNotNull()

        setAirplaneModeSyncEnabled(false)

        // Network and Data store are NOT cleaned up anymore when sync is disabled
        assertThat(mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL))
            .isNotNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_SYNC_AIRPLANE_MODE_WITH_WATCHES)
    fun testDisableSync_removesDevicesFromNetwork() {
        setAirplaneModeSyncEnabled(true)
        mAirplaneModeSyncManager.init()
        connectDevicesToNetwork()

        val network = mFakeNetworkManager.getNetwork(AirplaneModeSyncManager.NETWORK_ID, USER_ALL)
        assertThat(network.remoteDevices).isNotEmpty()

        setAirplaneModeSyncEnabled(false)

        assertThat(network.remoteDevices).isEmpty()
    }

    private fun createAssociation(
        apmSyncSupportedMetadata: Boolean = true,
        apmSyncEnabledMetadata: Boolean = true,
        apmSystemDataSyncFlagEnabled: Boolean = true,
    ) =
        AssociationInfo.Builder(1, USER_ALL, "packageName")
            .setDisplayName("test_device")
            .setMetadata(
                PersistableBundle().apply {
                    putPersistableBundle(
                        FEATURE_CROSS_DEVICE_SYNC,
                        PersistableBundle().apply {
                            putBoolean(APM_SYNC_SUPPORTED, apmSyncSupportedMetadata)
                            putBoolean(Settings.Global.AIRPLANE_MODE_SYNC, apmSyncEnabledMetadata)
                        },
                    )
                }
            )
            .setSystemDataSyncFlags(if (apmSystemDataSyncFlagEnabled) FLAG_AIRPLANE_MODE else 0)
            .build()

    private fun setAirplaneMode(enabled: Boolean) {
        mFakeAirplaneModeController.updateAirplaneModeState(enabled)
    }

    private fun isAirplaneModeOn(): Boolean {
        return mFakeAirplaneModeController.isAirplaneModeEnabled
    }

    private fun setAirplaneModeSyncEnabled(enabled: Boolean) {
        mFakeAirplaneModeController.setAirplaneModeSyncEnabledState(enabled)
    }

    private fun setRemoteAirplaneModeEnabled(enabled: Boolean) {
        transactRemoteDataStore { doc ->
            doc.putData(APM_ENABLED_PATH, AirplaneModeSyncManager.toAirplaneModeState(enabled))
        }
    }

    private fun verifyRemoteAirplaneModeEnabled(enabled: Boolean?) {
        transactRemoteDataStore { doc ->
            val record = doc.getRecord(APM_ENABLED_PATH)!!
            assertThat(record.get())
                .isEqualTo(enabled?.let { AirplaneModeSyncManager.toAirplaneModeState(it) })
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun transactRemoteDataStore(consumer: (MutableDocument<String>) -> Unit) {
        mRemoteDeviceContext.sharedDataStore
            .transact(AirplaneModeSyncManager.DOC_ID) { doc ->
                consumer(doc as MutableDocument<String>)
                true
            }
            .get()
    }

    private fun connectDevicesToNetwork() {
        val remoteDevice =
            FakeRemoteDevice(1, USER_ALL)
                .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                .setRemoteNetworkManager(mRemoteDeviceContext.networkManager)
                .setAssociationInfoCache(createAssociation())
        this.mFakeNetworkManager.addRemoteDevice(remoteDevice)

        val localDevice =
            FakeRemoteDevice(1, USER_ALL).setRemoteNetworkManager(this.mFakeNetworkManager)
        mRemoteDeviceContext.networkManager.addRemoteDevice(localDevice)
    }

    private fun flushNetworks() {
        var shouldFlushMessages = true
        while (shouldFlushMessages) {
            shouldFlushMessages =
                this.mFakeNetworkManager.flushAllMessages() or
                    mRemoteDeviceContext.networkManager.flushAllMessages()
        }
    }

    companion object {
        private class RemoteDeviceContext : SharedDataStoreTestBase() {
            init {
                mSharedDataStore.init(
                    networkManager.createNetwork(AirplaneModeSyncManager.NETWORK_ID) { true }
                )
            }

            override fun getSchemaProvider() =
                object : SchemaProvider<String> {
                    override fun getAllDocumentSchema(): List<DocumentSchemaInfo> =
                        AirplaneModeSyncManager.DOCUMENT_SCHEMA_INFO_LIST

                    override fun migrateDocument(document: MutableDocument<String>) {}
                }

            override fun useRealSharedDataStoreImpl(): Boolean = true

            fun close() {
                mSharedDataStore.close().get()
            }
        }
    }
}
