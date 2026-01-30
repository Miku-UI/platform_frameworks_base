/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.pdb;

import static com.android.server.pdb.PersistentDataBlockService.DIGEST_SIZE_BYTES;
import static com.android.server.pdb.PersistentDataBlockService.FRP_CREDENTIAL_RESERVED_SIZE;
import static com.android.server.pdb.PersistentDataBlockService.FRP_SECRET_MAGIC;
import static com.android.server.pdb.PersistentDataBlockService.FRP_SECRET_SIZE;
import static com.android.server.pdb.PersistentDataBlockService.HEADER_SIZE;
import static com.android.server.pdb.PersistentDataBlockService.MAX_DATA_BLOCK_SIZE;
import static com.android.server.pdb.PersistentDataBlockService.MAX_FRP_CREDENTIAL_HANDLE_SIZE;
import static com.android.server.pdb.PersistentDataBlockService.MAX_TEST_MODE_DATA_SIZE;
import static com.android.server.pdb.PersistentDataBlockService.TEST_MODE_RESERVED_SIZE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.Manifest;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.RemoteException;
import android.os.UserManager;
import android.service.persistentdata.IPersistentDataBlockService;

import androidx.test.core.app.ApplicationProvider;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

@RunWith(JUnitParamsRunner.class)
public class PersistentDataBlockServiceTest {
    private static final String TAG = "PersistentDataBlockServiceTest";

    private static final byte[] SMALL_DATA = "data to write".getBytes();
    private static final byte[] ANOTHER_SMALL_DATA = "something else".getBytes();
    public static final int DEFAULT_BLOCK_DEVICE_SIZE = -1;

    private Context mContext;
    private PersistentDataBlockService mPdbService;
    private IPersistentDataBlockService mInterface;
    private PersistentDataBlockManagerInternal mInternalInterface;
    private File mDataBlockFile;
    private File mFrpSecretFile;
    private File mFrpSecretTmpFile;
    private boolean mIsUpgradingFromPreV = false;

    @Mock private UserManager mUserManager;

    private class FakePersistentDataBlockService extends PersistentDataBlockService {

        FakePersistentDataBlockService(Context context, String dataBlockFile,
                long blockDeviceSize, String frpSecretFile, String frpSecretTmpFile) {
            super(context, /* isFileBacked */ true, dataBlockFile, blockDeviceSize, frpSecretFile,
                    frpSecretTmpFile);
            // In the real service, this is done by onStart(), which we don't want to call because
            // it registers the service, etc.  But we need to signal init done to prevent
            // `isFrpActive` from blocking.
            signalInitDone();
        }

        @Override
        boolean isUpgradingFromPreVRelease() {
            return mIsUpgradingFromPreV;
        }
    }

    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mDataBlockFile = mTemporaryFolder.newFile();
        mFrpSecretFile = mTemporaryFolder.newFile();
        mFrpSecretTmpFile = mTemporaryFolder.newFile();
        mContext = spy(ApplicationProvider.getApplicationContext());
        mPdbService = new FakePersistentDataBlockService(mContext, mDataBlockFile.getPath(),
                DEFAULT_BLOCK_DEVICE_SIZE, mFrpSecretFile.getPath(), mFrpSecretTmpFile.getPath());
        mPdbService.setAllowedUid(Binder.getCallingUid());
        mPdbService.formatPartitionLocked(/* setOemUnlockEnabled */ false);
        mInterface = mPdbService.getInterfaceForTesting();
        mInternalInterface = mPdbService.getInternalInterfaceForTesting();

        when(mContext.getSystemService(eq(Context.USER_SERVICE))).thenReturn(mUserManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
    }

    abstract static class Block {
        public PersistentDataBlockService service;

        abstract int write(byte[] data) throws RemoteException;
        abstract byte[] read() throws RemoteException;
    }

    /**
     * Configuration for parameterizing tests, including the block name, maximum block size, and
     * a block implementation for the read/write operations.
     */
    public Object[][] getTestParametersForBlocks() {
        Block simpleReadWrite = new Block() {
                    @Override public int write(byte[] data) throws RemoteException {
                        return service.getInterfaceForTesting().write(data);
                    }

                    @Override public byte[] read() throws RemoteException {
                        return service.getInterfaceForTesting().read();
                    }
                };
        Block credHandle =  new Block() {
                    @Override public int write(byte[] data) {
                        service.getInternalInterfaceForTesting().setFrpCredentialHandle(data);
                        // The written size isn't returned. Pretend it's fully written in the
                        // test for now.
                        return data.length;
                    }

                    @Override public byte[] read() {
                        return service.getInternalInterfaceForTesting().getFrpCredentialHandle();
                    }
                };
        Block testHarness = new Block() {
                    @Override public int write(byte[] data) {
                        service.getInternalInterfaceForTesting().setTestHarnessModeData(data);
                        // The written size isn't returned. Pretend it's fully written in the
                        // test for now.
                        return data.length;
                    }

                    @Override public byte[] read() {
                        return service.getInternalInterfaceForTesting().getTestHarnessModeData();
                    }
                };
        return new Object[][] {
                { simpleReadWrite },
                { simpleReadWrite },
                { credHandle },
                { credHandle },
                { testHarness },
                { testHarness },
        };
    }

    @Test
    @Parameters(method = "getTestParametersForBlocks")
    public void writeThenRead(Block block) throws Exception {
        block.service = mPdbService;
        assertThat(block.write(SMALL_DATA)).isEqualTo(SMALL_DATA.length);
        assertThat(block.read()).isEqualTo(SMALL_DATA);
    }

    @Test
    @Parameters(method = "getTestParametersForBlocks")
    public void writeWhileAlreadyCorrupted(Block block) throws Exception {
        block.service = mPdbService;
        assertThat(block.write(SMALL_DATA)).isEqualTo(SMALL_DATA.length);
        assertThat(block.read()).isEqualTo(SMALL_DATA);

        tamperWithDigest();

        // In the currently implementation, expect the write to not trigger formatting.
        assertThat(block.write(ANOTHER_SMALL_DATA)).isEqualTo(ANOTHER_SMALL_DATA.length);
    }

    @Test
    public void frpWriteOutOfBound() throws Exception {
        byte[] maxData = new byte[mPdbService.getMaximumFrpDataSize()];
        assertThat(mInterface.write(maxData)).isEqualTo(maxData.length);

        byte[] overflowData = new byte[mPdbService.getMaximumFrpDataSize() + 1];
        assertThat(mInterface.write(overflowData)).isLessThan(0);
    }

    @Test
    public void frpCredentialWriteOutOfBound() throws Exception {
        byte[] maxData = new byte[MAX_FRP_CREDENTIAL_HANDLE_SIZE];
        mInternalInterface.setFrpCredentialHandle(maxData);

        byte[] overflowData = new byte[MAX_FRP_CREDENTIAL_HANDLE_SIZE + 1];
        assertThrows(IllegalArgumentException.class, () ->
                mInternalInterface.setFrpCredentialHandle(overflowData));
    }

    @Test
    public void testHardnessWriteOutOfBound() throws Exception {
        byte[] maxData = new byte[MAX_TEST_MODE_DATA_SIZE];
        mInternalInterface.setTestHarnessModeData(maxData);

        byte[] overflowData = new byte[MAX_TEST_MODE_DATA_SIZE + 1];
        assertThrows(IllegalArgumentException.class, () ->
                mInternalInterface.setTestHarnessModeData(overflowData));
    }

    @Test
    public void readCorruptedFrpData() throws Exception {
        assertThat(mInterface.write(SMALL_DATA)).isEqualTo(SMALL_DATA.length);
        assertThat(mInterface.read()).isEqualTo(SMALL_DATA);

        tamperWithDigest();

        // Expect the read to trigger formatting, resulting in reading empty data.
        assertThat(mInterface.read()).hasLength(0);
    }

    @Test
    public void readCorruptedFrpCredentialData() throws Exception {
        mInternalInterface.setFrpCredentialHandle(SMALL_DATA);
        assertThat(mInternalInterface.getFrpCredentialHandle()).isEqualTo(SMALL_DATA);

        tamperWithDigest();

        assertThrows(IllegalStateException.class, () ->
                mInternalInterface.getFrpCredentialHandle());
    }

    @Test
    public void readCorruptedTestHarnessData() throws Exception {
        mInternalInterface.setTestHarnessModeData(SMALL_DATA);
        assertThat(mInternalInterface.getTestHarnessModeData()).isEqualTo(SMALL_DATA);

        tamperWithDigest();

        assertThrows(IllegalStateException.class, () ->
                mInternalInterface.getTestHarnessModeData());
    }

    @Test
    public void nullWrite() throws Exception {
        assertThrows(NullPointerException.class, () -> mInterface.write(null));
        mInternalInterface.setFrpCredentialHandle(null);  // no exception
        mInternalInterface.setTestHarnessModeData(null);  // no exception
    }

    @Test
    public void emptyDataWrite() throws Exception {
        var empty = new byte[0];
        assertThat(mInterface.write(empty)).isEqualTo(0);

        assertThrows(IllegalArgumentException.class, () ->
                mInternalInterface.setFrpCredentialHandle(empty));
        assertThrows(IllegalArgumentException.class, () ->
                mInternalInterface.setTestHarnessModeData(empty));
    }

    @Test
    public void frpWriteMoreThan100K() throws Exception {
        File dataBlockFile = mTemporaryFolder.newFile();
        PersistentDataBlockService pdbService = new FakePersistentDataBlockService(mContext,
                dataBlockFile.getPath(), /* blockDeviceSize */ 128 * 1000,
                /* frpSecretFile */ null, /* frpSecretTmpFile */ null);
        pdbService.setAllowedUid(Binder.getCallingUid());
        pdbService.formatPartitionLocked(/* setOemUnlockEnabled */ false);

        IPersistentDataBlockService service = pdbService.getInterfaceForTesting();
        int maxDataSize = (int) service.getMaximumDataBlockSize();
        assertThat(service.write(new byte[maxDataSize])).isEqualTo(maxDataSize);
        assertThat(service.write(new byte[maxDataSize + 1])).isEqualTo(-MAX_DATA_BLOCK_SIZE);
    }

    @Test
    public void frpBlockReadWriteWithoutPermission() throws Exception {
        mPdbService.setAllowedUid(Binder.getCallingUid() + 1);  // unexpected uid
        assertThrows(SecurityException.class, () -> mInterface.write(SMALL_DATA));
        assertThrows(SecurityException.class, () -> mInterface.read());
    }

    @Test
    public void getMaximumDataBlockSizeDenied() throws Exception {
        mPdbService.setAllowedUid(Binder.getCallingUid() + 1);  // unexpected uid
        assertThrows(SecurityException.class, () -> mInterface.getMaximumDataBlockSize());
    }

    @Test
    public void getMaximumDataBlockSize() throws Exception {
        mPdbService.setAllowedUid(Binder.getCallingUid());
        assertThat(mInterface.getMaximumDataBlockSize())
                .isEqualTo(mPdbService.getMaximumFrpDataSize());
    }

    @Test
    public void getMaximumDataBlockSizeOfLargerPartition() throws Exception {
        File dataBlockFile = mTemporaryFolder.newFile();
        PersistentDataBlockService pdbService = new FakePersistentDataBlockService(mContext,
                dataBlockFile.getPath(), /* blockDeviceSize */ 128 * 1000,
                /* frpSecretFile */null, /* mFrpSecretTmpFile */ null);
        pdbService.setAllowedUid(Binder.getCallingUid());
        pdbService.formatPartitionLocked(/* setOemUnlockEnabled */ false);

        IPersistentDataBlockService service = pdbService.getInterfaceForTesting();
        assertThat(service.getMaximumDataBlockSize()).isEqualTo(MAX_DATA_BLOCK_SIZE);
    }

    @Test
    public void getFrpDataBlockSizeGrantedByUid() throws Exception {
        assertThat(mInterface.write(SMALL_DATA)).isEqualTo(SMALL_DATA.length);

        mPdbService.setAllowedUid(Binder.getCallingUid());
        assertThat(mInterface.getDataBlockSize()).isEqualTo(SMALL_DATA.length);

        // Modify the magic / type marker. In the current implementation, getting the FRP data block
        // size does not check digest.
        tamperWithMagic();
        assertThat(mInterface.getDataBlockSize()).isEqualTo(0);
    }

    @Test
    public void getFrpDataBlockSizeGrantedByPermission() throws Exception {
        assertThat(mInterface.write(SMALL_DATA)).isEqualTo(SMALL_DATA.length);

        mPdbService.setAllowedUid(Binder.getCallingUid() + 1);  // unexpected uid
        grantAccessPdbStatePermission();

        assertThat(mInterface.getDataBlockSize()).isEqualTo(SMALL_DATA.length);

        // Modify the magic / type marker. In the current implementation, getting the FRP data block
        // size does not check digest.
        tamperWithMagic();
        assertThat(mInterface.getDataBlockSize()).isEqualTo(0);
    }

    @Test
    public void testPartitionFormat() throws Exception {
        /*
         * 1. Fill the PDB with a specific value, so we can check regions that weren't touched
         *    by formatting
         */
        FileChannel channel = FileChannel.open(mDataBlockFile.toPath(), StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
        byte[] bufArray = new byte[(int) mPdbService.getBlockDeviceSize()];
        Arrays.fill(bufArray, (byte) 0x7f);
        ByteBuffer buf = ByteBuffer.wrap(bufArray);
        channel.write(buf);
        channel.close();

        /*
         * 2. Format it.
         */
        mPdbService.formatPartitionLocked(true);

        /*
         * 3. Check it.
         */
        channel = FileChannel.open(mDataBlockFile.toPath(), StandardOpenOption.READ);

        // 3a. Skip the digest and header
        channel.position(channel.position() + DIGEST_SIZE_BYTES + HEADER_SIZE);

        // 3b. Check the FRP data segment
        assertContains("FRP data", readData(channel, mPdbService.getMaximumFrpDataSize()).array(),
                (byte) 0);

        // 3c. The FRP secret magic & value
        assertThat(mPdbService.getFrpSecretMagicOffset()).isEqualTo(channel.position());
        assertThat(readData(channel, FRP_SECRET_MAGIC.length).array()).isEqualTo(
                FRP_SECRET_MAGIC);
        assertThat(mPdbService.getFrpSecretDataOffset()).isEqualTo(channel.position());
        assertContains("FRP secret", readData(channel, FRP_SECRET_SIZE).array(), (byte) 0);

        // 3d. The test mode data (unmodified by formatPartitionLocked()).
        assertThat(mPdbService.getTestHarnessModeDataOffset()).isEqualTo(channel.position());
        assertContains("Test data", readData(channel, TEST_MODE_RESERVED_SIZE).array(),
                (byte) 0x7f);

        // 3e. The FRP credential segment
        assertThat(mPdbService.getFrpCredentialDataOffset()).isEqualTo(channel.position());
        assertContains("FRP credential", readData(channel, FRP_CREDENTIAL_RESERVED_SIZE).array(),
                (byte) 0);

        // 3f. OEM unlock byte.
        assertThat(mPdbService.getOemUnlockDataOffset()).isEqualTo(channel.position());
        assertThat(new byte[]{1}).isEqualTo(readData(channel, 1).array());

        // 3g. EOF
        assertThat(channel.position()).isEqualTo(channel.size());
    }

    @Test
    public void wipePermissionCheck() throws Exception {
        denyOemUnlockPermission();
        assertThrows(SecurityException.class, () -> mInterface.wipe());
    }

    @Test
    public void wipeMakesItNotWritable() throws Exception {
        grantOemUnlockPermission(); // This permission check is still relevant
        mInterface.wipe();

        // Verify that nothing is written.
        final int headerAndDataBytes = 4 + SMALL_DATA.length;
        assertThat(mInterface.write(SMALL_DATA)).isLessThan(0);
        assertThat(readBackingFile(DIGEST_SIZE_BYTES + 4, headerAndDataBytes).array())
                .isEqualTo(new byte[headerAndDataBytes]);

        mInternalInterface.setFrpCredentialHandle(SMALL_DATA);
        assertThat(readBackingFile(mPdbService.getFrpCredentialDataOffset() + 4,
                    headerAndDataBytes)
                .array())
                .isEqualTo(new byte[headerAndDataBytes]);

        mInternalInterface.setTestHarnessModeData(SMALL_DATA);
        assertThat(readBackingFile(mPdbService.getTestHarnessModeDataOffset() + 4,
                    headerAndDataBytes)
                .array())
                .isEqualTo(new byte[headerAndDataBytes]);
    }

    @Test
    public void hasFrpCredentialHandle_GrantedByUid() throws Exception {
        mPdbService.setAllowedUid(Binder.getCallingUid());

        assertThat(mInterface.hasFrpCredentialHandle()).isFalse();
        mInternalInterface.setFrpCredentialHandle(SMALL_DATA);
        assertThat(mInterface.hasFrpCredentialHandle()).isTrue();
    }

    @Test
    public void hasFrpCredentialHandle_GrantedByConfigureFrpPermission()
            throws Exception {
        grantConfigureFrpPermission();

        mPdbService.setAllowedUid(Binder.getCallingUid() + 1);  // unexpected uid

        assertThat(mInterface.hasFrpCredentialHandle()).isFalse();
        mInternalInterface.setFrpCredentialHandle(SMALL_DATA);
        assertThat(mInterface.hasFrpCredentialHandle()).isTrue();
    }

    @Test
    public void hasFrpCredentialHandle_GrantedByAccessPdbStatePermission() throws Exception {
        grantAccessPdbStatePermission();

        mPdbService.setAllowedUid(Binder.getCallingUid() + 1);  // unexpected uid

        assertThat(mInterface.hasFrpCredentialHandle()).isFalse();
        mInternalInterface.setFrpCredentialHandle(SMALL_DATA);
        assertThat(mInterface.hasFrpCredentialHandle()).isTrue();
    }

    @Test
    public void hasFrpCredentialHandle_Unauthorized() throws Exception {
        mPdbService.setAllowedUid(Binder.getCallingUid() + 1);  // unexpected uid

        assertThrows(SecurityException.class, () -> mInterface.hasFrpCredentialHandle());
    }

    @Test
    public void clearTestHarnessModeData() throws Exception {
        mInternalInterface.setTestHarnessModeData(SMALL_DATA);
        mInternalInterface.clearTestHarnessModeData();

        assertThat(readBackingFile(mPdbService.getTestHarnessModeDataOffset(),
                    MAX_TEST_MODE_DATA_SIZE).array())
                .isEqualTo(new byte[MAX_TEST_MODE_DATA_SIZE]);
    }

    @Test
    public void getAllowedUid() throws Exception {
        assertThat(mInternalInterface.getAllowedUid()).isEqualTo(Binder.getCallingUid());
    }

    @Test
    public void oemUnlockWithoutPermission() throws Exception {
        denyOemUnlockPermission();

        assertThrows(SecurityException.class, () -> mInterface.setOemUnlockEnabled(true));
    }

    @Test
    public void oemUnlockNotAdmin() throws Exception {
        grantOemUnlockPermission();
        makeUserAdmin(false);

        assertThrows(SecurityException.class, () -> mInterface.setOemUnlockEnabled(true));
    }

    @Test
    public void oemUnlock() throws Exception {
        grantOemUnlockPermission();
        makeUserAdmin(true);

        mInterface.setOemUnlockEnabled(true);
        assertThat(mInterface.getOemUnlockEnabled()).isTrue();
    }

    @Test
    public void oemUnlockUserRestriction_OemUnlock() throws Exception {
        grantOemUnlockPermission();
        makeUserAdmin(true);
        when(mUserManager.hasUserRestriction(eq(UserManager.DISALLOW_OEM_UNLOCK)))
                .thenReturn(true);

        assertThrows(SecurityException.class, () -> mInterface.setOemUnlockEnabled(true));
    }

    @Test
    public void oemUnlockUserRestriction_FactoryReset() throws Exception {
        grantOemUnlockPermission();
        makeUserAdmin(true);
        when(mUserManager.hasUserRestriction(eq(UserManager.DISALLOW_FACTORY_RESET)))
                .thenReturn(true);

        assertThrows(SecurityException.class, () -> mInterface.setOemUnlockEnabled(true));
    }

    @Test
    public void oemUnlockIgnoreTampering() throws Exception {
        grantOemUnlockPermission();
        makeUserAdmin(true);

        // The current implementation does not check digest before set or get the oem unlock bit.
        tamperWithDigest();
        mInterface.setOemUnlockEnabled(true);
        tamperWithDigest();
        assertThat(mInterface.getOemUnlockEnabled()).isTrue();
    }

    @Test
    public void getOemUnlockEnabledPermissionCheck_NoPermission() throws Exception {
        assertThrows(SecurityException.class, () -> mInterface.getOemUnlockEnabled());
    }

    @Test
    public void getOemUnlockEnabledPermissionCheck_OemUnlockState() throws Exception {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(eq(Manifest.permission.OEM_UNLOCK_STATE));
        assertThat(mInterface.getOemUnlockEnabled()).isFalse();
    }

    @Test
    public void getOemUnlockEnabledPermissionCheck_ReadOemUnlockState()
            throws Exception {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(eq(Manifest.permission.READ_OEM_UNLOCK_STATE));
        assertThat(mInterface.getOemUnlockEnabled()).isFalse();
    }

    @Test
    public void forceOemUnlock_RequiresNoPermission() throws Exception {
        denyOemUnlockPermission();

        mInternalInterface.forceOemUnlockEnabled(true);

        assertThat(readBackingFile(mPdbService.getOemUnlockDataOffset(), 1).array())
                .isEqualTo(new byte[] { 1 });
    }

    @Test
    public void getFlashLockStatePermissionCheck_NoPermission() throws Exception {
        assertThrows(SecurityException.class, () -> mInterface.getFlashLockState());
    }

    @Test
    public void getFlashLockStatePermissionCheck_OemUnlockState()
            throws Exception {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(eq(Manifest.permission.OEM_UNLOCK_STATE));
        mInterface.getFlashLockState();  // Do not throw
    }

    @Test
    public void getFlashLockStatePermissionCheck_ReadOemUnlockState() throws Exception {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(eq(Manifest.permission.READ_OEM_UNLOCK_STATE));
        mInterface.getFlashLockState();  // Do not throw
    }

    @Test
    public void frpMagicTest() throws Exception {
        byte[] magicField = mPdbService.readDataBlock(mPdbService.getFrpSecretMagicOffset(),
                PersistentDataBlockService.FRP_SECRET_MAGIC.length);
        assertThat(magicField).isEqualTo(PersistentDataBlockService.FRP_SECRET_MAGIC);
    }

    @Test
    public void frpSecret_StartsAsDefault() throws Exception {
        byte[] secretField = mPdbService.readDataBlock(
                mPdbService.getFrpSecretDataOffset(), PersistentDataBlockService.FRP_SECRET_SIZE);
        assertThat(secretField).isEqualTo(new byte[PersistentDataBlockService.FRP_SECRET_SIZE]);
    }

    @Test
    public void frpSecret_SetSecret() throws Exception {
        grantConfigureFrpPermission();

        byte[] hashedSecret = hashStringto32Bytes("secret");
        assertThat(mInterface.setFactoryResetProtectionSecret(hashedSecret)).isTrue();

        byte[] secretField = mPdbService.readDataBlock(
                mPdbService.getFrpSecretDataOffset(), PersistentDataBlockService.FRP_SECRET_SIZE);
        assertThat(secretField).isEqualTo(hashedSecret);

        assertThat(mFrpSecretFile.exists()).isTrue();
        byte[] secretFileData = Files.readAllBytes(mFrpSecretFile.toPath());
        assertThat(secretFileData).isEqualTo(hashedSecret);

        assertThat(mFrpSecretTmpFile.exists()).isFalse();
    }

    @Test
    public void frpSecret_SetSecretByUnauthorizedCaller() throws Exception {
        mPdbService.setAllowedUid(Binder.getCallingUid() + 1);  // unexpected uid
        assertThrows(SecurityException.class,
                () -> mInterface.setFactoryResetProtectionSecret(hashStringto32Bytes("secret")));
    }

    /**
     * Verify that FRP always starts in active state (if flag-enabled), until something is done to
     * deactivate it.
     */
    @Test
    public void frpState_StartsActive() throws Exception {
        // Create a service without calling formatPartition, which deactivates FRP.
        PersistentDataBlockService pdbService = new FakePersistentDataBlockService(mContext,
                mDataBlockFile.getPath(), DEFAULT_BLOCK_DEVICE_SIZE, mFrpSecretFile.getPath(),
                mFrpSecretTmpFile.getPath());
        assertThat(pdbService.isFrpActive()).isTrue();
    }

    @Test
    public void frpState_AutomaticallyDeactivateWithDefault() throws Exception {
        mPdbService.activateFrp();
        assertThat(mPdbService.isFrpActive()).isTrue();

        assertThat(mPdbService.automaticallyDeactivateFrpIfPossible()).isTrue();
        assertThat(mPdbService.isFrpActive()).isFalse();
    }

    @Test
    public void frpState_AutomaticallyDeactivateWithPrimaryDataFile() throws Exception {
        grantConfigureFrpPermission();

        mInterface.setFactoryResetProtectionSecret(hashStringto32Bytes("secret"));

        mPdbService.activateFrp();
        assertThat(mPdbService.isFrpActive()).isTrue();
        assertThat(mPdbService.automaticallyDeactivateFrpIfPossible()).isTrue();
        assertThat(mPdbService.isFrpActive()).isFalse();
    }

    @Test
    public void frpState_AutomaticallyDeactivateWithBackupDataFile() throws Exception {
        grantConfigureFrpPermission();

        mInterface.setFactoryResetProtectionSecret(hashStringto32Bytes("secret"));
        Files.move(mFrpSecretFile.toPath(), mFrpSecretTmpFile.toPath(), REPLACE_EXISTING);

        mPdbService.activateFrp();
        assertThat(mPdbService.isFrpActive()).isTrue();
        assertThat(mPdbService.automaticallyDeactivateFrpIfPossible()).isTrue();
        assertThat(mPdbService.isFrpActive()).isFalse();
    }

    @Test
    public void frpState_DeactivateWithSecret() throws Exception {
        grantConfigureFrpPermission();

        mInterface.setFactoryResetProtectionSecret(hashStringto32Bytes("secret"));
        simulateDataWipe();

        assertThat(mPdbService.isFrpActive()).isFalse();
        mPdbService.activateFrp();
        assertThat(mPdbService.isFrpActive()).isTrue();

        assertThat(mPdbService.automaticallyDeactivateFrpIfPossible()).isFalse();
        assertThat(mPdbService.isFrpActive()).isTrue();

        assertThat(mInterface.deactivateFactoryResetProtection(hashStringto32Bytes("wrongSecret")))
                .isFalse();
        assertThat(mPdbService.isFrpActive()).isTrue();

        assertThat(mInterface.deactivateFactoryResetProtection(hashStringto32Bytes("secret")))
                .isTrue();
        assertThat(mPdbService.isFrpActive()).isFalse();

        assertThat(mInterface.setFactoryResetProtectionSecret(new byte[FRP_SECRET_SIZE])).isTrue();
        assertThat(mPdbService.isFrpActive()).isFalse();

        mPdbService.activateFrp();
        assertThat(mPdbService.isFrpActive()).isTrue();
        assertThat(mPdbService.automaticallyDeactivateFrpIfPossible()).isTrue();
        assertThat(mPdbService.isFrpActive()).isFalse();
    }

    @Test
    public void frpState_DeactivateOnUpgradeFromPreV() throws Exception {
        grantConfigureFrpPermission();

        mInterface.setFactoryResetProtectionSecret(hashStringto32Bytes("secret"));
        // If the /data files are still present, deactivation will use them.  We want to verify
        // that deactivation will succeed even if they are not present, so remove them.
        simulateDataWipe();

        // Verify that automatic deactivation fails without the /data files when we're not
        // upgrading from pre-V.
        mPdbService.activateFrp();
        assertThat(mPdbService.automaticallyDeactivateFrpIfPossible()).isFalse();
        assertThat(mPdbService.isFrpActive()).isTrue();

        // Verify that automatic deactivation succeeds when upgrading from pre-V.
        mIsUpgradingFromPreV = true;
        assertThat(mPdbService.automaticallyDeactivateFrpIfPossible()).isTrue();
        assertThat(mPdbService.isFrpActive()).isFalse();
    }

    @Test
    public void frpState_PrivilegedDeactivationByAuthorizedCaller() throws Exception {
        grantConfigureFrpPermission();

        assertThat(mPdbService.isFrpActive()).isFalse();
        assertThat(mInterface.setFactoryResetProtectionSecret(hashStringto32Bytes("secret")))
                .isTrue();

        simulateDataWipe();
        mPdbService.activateFrp();
        assertThat(mPdbService.isFrpActive()).isTrue();

        assertThat(mPdbService.automaticallyDeactivateFrpIfPossible()).isFalse();
        assertThat(mPdbService.isFrpActive()).isTrue();

        assertThat(mInternalInterface.deactivateFactoryResetProtectionWithoutSecret()).isTrue();
        assertThat(mPdbService.isFrpActive()).isFalse();
    }

    @Test
    public void frpActive_WipeFails() throws Exception {
        grantOemUnlockPermission();
        mPdbService.activateFrp();
        SecurityException e = assertThrows(SecurityException.class, () -> mInterface.wipe());
        assertThat(e).hasMessageThat().contains("FRP is active");
    }

    @Test
    public void frpActive_WriteFails() throws Exception {
        mPdbService.activateFrp();
        SecurityException e =
                assertThrows(SecurityException.class, () -> mInterface.write("data".getBytes()));
        assertThat(e).hasMessageThat().contains("FRP is active");
    }

    @Test
    public void frpActive_SetSecretFails() throws Exception {
        grantConfigureFrpPermission();

        mPdbService.activateFrp();

        byte[] hashedSecret = hashStringto32Bytes("secret");
        SecurityException e = assertThrows(SecurityException.class, ()
                -> mInterface.setFactoryResetProtectionSecret(hashedSecret));
        assertThat(e).hasMessageThat().contains("FRP is active");
        assertThat(mPdbService.isFrpActive()).isTrue();

        // Verify that secret we failed to set isn't accepted.
        assertThat(mInterface.deactivateFactoryResetProtection(hashedSecret)).isFalse();
        assertThat(mPdbService.isFrpActive()).isTrue();

        // Default should work, since it should never have been changed.
        assertThat(mPdbService.automaticallyDeactivateFrpIfPossible()).isTrue();
        assertThat(mPdbService.isFrpActive()).isFalse();
    }

    private void simulateDataWipe() throws IOException {
        Files.deleteIfExists(mFrpSecretFile.toPath());
        Files.deleteIfExists(mFrpSecretTmpFile.toPath());
    }

    private static byte[] hashStringto32Bytes(String secret) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(secret.getBytes());
    }

    private void tamperWithDigest() throws Exception {
        try (var ch = FileChannel.open(mDataBlockFile.toPath(), StandardOpenOption.WRITE)) {
            ch.write(ByteBuffer.wrap("tampered-digest".getBytes()));
        }
    }

    private void tamperWithMagic() throws Exception {
        try (var ch = FileChannel.open(mDataBlockFile.toPath(), StandardOpenOption.WRITE)) {
            ch.write(ByteBuffer.wrap("mark".getBytes()), DIGEST_SIZE_BYTES);
        }
    }

    private void makeUserAdmin(boolean isAdmin) {
        when(mUserManager.isUserAdmin(anyInt())).thenReturn(isAdmin);
    }

    private void grantOemUnlockPermission() {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(eq(Manifest.permission.OEM_UNLOCK_STATE));
        doNothing().when(mContext)
                .enforceCallingOrSelfPermission(eq(Manifest.permission.OEM_UNLOCK_STATE),
                        anyString());
    }

    private void denyOemUnlockPermission() {
        doReturn(PackageManager.PERMISSION_DENIED).when(mContext)
                .checkCallingOrSelfPermission(eq(Manifest.permission.OEM_UNLOCK_STATE));
    }

    private void grantAccessPdbStatePermission() {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingPermission(eq(Manifest.permission.ACCESS_PDB_STATE));
    }

    private void grantConfigureFrpPermission() {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                eq(Manifest.permission.CONFIGURE_FACTORY_RESET_PROTECTION));
        doNothing().when(mContext).enforceCallingOrSelfPermission(
                eq(Manifest.permission.CONFIGURE_FACTORY_RESET_PROTECTION),
                anyString());
    }

    private ByteBuffer readBackingFile(long position, int size) throws Exception {
        try (var ch = FileChannel.open(mDataBlockFile.toPath(), StandardOpenOption.READ)) {
            var buffer = ByteBuffer.allocate(size);
            assertThat(ch.read(buffer, position)).isGreaterThan(0);
            return buffer;
        }
    }

    @NonNull
    private static ByteBuffer readData(FileChannel channel, int length) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(length);
        assertThat(channel.read(buf)).isEqualTo(length);
        buf.flip();
        assertThat(buf.limit()).isEqualTo(length);
        return buf;
    }

    private static void assertContains(String sectionName, byte[] buf, byte expected) {
        for (int i = 0; i < buf.length; i++) {
            assertWithMessage(sectionName + " is incorrect at offset " + i)
                    .that(buf[i]).isEqualTo(expected);
        }
    }
}
