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
package com.android.server.usb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.DeviceFilter;
import android.hardware.usb.IUsbSerialReader;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Xml;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.usb.UsbUserPermissionManager.PackageAndUid;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Unit tests for {@link com.android.server.usb.UsbUserPermissionManager}. */
@RunWith(AndroidJUnit4.class)
@EnableFlags({android.hardware.usb.flags.Flags.FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS})
public class UsbUserPermissionManagerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Context mContext;
    @Mock private UsbUserSettingsManager mUsbUserSettingsManager;
    @Mock private PackageManager mPackageManager;

    private UsbUserPermissionManager mPermissionManager;
    private UsbAccessory mUsbAccessory;

    private UsbDevice mUsbDevice;
    private UsbDeviceFingerprint mUsbDeviceFingerprint;

    private AutoCloseable mMocks;

    private static final String TEST_DEVICE_NAME = "/dev/bus/usb/001/002";

    private record TestData(String packageName, int pid, int uid) {}

    private ArrayMap<Integer, String> mValidPackageUidPairs = new ArrayMap<>();
    private ArrayMap<String, TestData> mTestData = new ArrayMap<>();

    private static final String PERSISTED_PACKAGE = "persisted";
    private static final String TEMPORARY_PACKAGE = "temporary";
    private static final String VALID_NEW_PACKAGE = "valid-new";
    private static final String INVALID_PACKAGE = "invalid";

    static class LocalSerialReader extends IUsbSerialReader.Stub {
        String mSerialNumber;

        LocalSerialReader(String serialNumber) {
            mSerialNumber = serialNumber;
        }

        @Override
        public String getSerial(String packageName) {
            return mSerialNumber;
        }
    }

    void createEmptyFingerprint(UsbDevice device) {
        DeviceFilter df = new DeviceFilter(device);
        UsbDeviceFingerprint.Hashcode emptyHashcode =
                UsbDeviceFingerprint.Hashcode.createEmptyHashcode();
        mUsbDeviceFingerprint =
                new UsbDeviceFingerprint(df, emptyHashcode, emptyHashcode, 0, false, 0);
    }

    private UsbDevice getDeviceCopy(String serialNumber) {
        return new UsbDevice.Builder(
                        TEST_DEVICE_NAME, // name
                        0x1234, // vendorId
                        0x5678, // productId
                        0,
                        0,
                        0,
                        "Google",
                        "Fake Product",
                        "version",
                        new UsbConfiguration[0], // configurations
                        "", // empty serial number -- need to spy + return later
                        false,
                        false,
                        false,
                        false,
                        false // unused fields
                        )
                .build(new LocalSerialReader(serialNumber));
    }

    @Before
    public void setUp() throws Exception {
        mMocks = MockitoAnnotations.openMocks(this);
        mContext = spy(InstrumentationRegistry.getInstrumentation().getContext());

        doReturn(mPackageManager).when(mContext).getPackageManager();
        // Intercept broadcast for permission changed.
        // Unit tests don't have the necessary permission to send this.
        doNothing().when(mContext).sendBroadcastAsUser(any(), any(), anyString());

        // Wire up the valid packages list
        when(mPackageManager.getPackagesForUid(anyInt()))
                .thenAnswer(
                        (invocation) -> {
                            int uid = invocation.getArgument(0);
                            if (mValidPackageUidPairs.containsKey(uid)) {
                                return new String[] {mValidPackageUidPairs.get(uid)};
                            }

                            return null;
                        });

        mUsbDevice = spy(getDeviceCopy(null));
        createEmptyFingerprint(mUsbDevice);
        mUsbAccessory = new UsbAccessory("man", "model", "desc", "1.0", "uri", "serial");

        // Create various packages in lists.
        mTestData.put(PERSISTED_PACKAGE, new TestData("com.foo.persisted", 100, 11000));
        mTestData.put(TEMPORARY_PACKAGE, new TestData("com.foo.temporary", 200, 22000));
        mTestData.put(VALID_NEW_PACKAGE, new TestData("com.foo.valid.new", 300, 33000));
        mTestData.put(INVALID_PACKAGE, new TestData("com.foo.invalid", 400, 44000));

        // Set up valid packages
        mValidPackageUidPairs.put(11000, "com.foo.persisted");
        mValidPackageUidPairs.put(22000, "com.foo.temporary");
        mValidPackageUidPairs.put(33000, "com.foo.valid.new");

        mPermissionManager =
                new UsbUserPermissionManager(
                        mContext,
                        mUsbUserSettingsManager,
                        folder.newFolder("test").getAbsolutePath());
    }

    void grantDevicePersistedAndTemporaryInitial() {
        TestData persisted = mTestData.get(PERSISTED_PACKAGE);
        mPermissionManager.grantDevicePermissionInternal(
                mUsbDevice, mUsbDeviceFingerprint, persisted.packageName, persisted.uid, true);
        TestData temporary = mTestData.get(TEMPORARY_PACKAGE);
        mPermissionManager.grantDevicePermissionInternal(
                mUsbDevice, mUsbDeviceFingerprint, temporary.packageName, temporary.uid, false);
    }

    void grantAccessoryTemporaryInitial() {
        TestData temporary = mTestData.get(TEMPORARY_PACKAGE);
        mPermissionManager.grantAccessoryPermissionInternal(
                mUsbAccessory, temporary.packageName, temporary.uid);
    }

    @After
    public void tearDown() throws Exception {
        mMocks.close();
    }

    @Test
    public void testAccessory_hasAndGrantPermission() {
        grantAccessoryTemporaryInitial();

        // Non-granted permission fails.
        TestData validNew = mTestData.get(VALID_NEW_PACKAGE);
        assertFalse(
                mPermissionManager.hasPermissionInternal(
                        mUsbAccessory, validNew.packageName, validNew.pid, validNew.uid));

        // Granted permission succeeds.
        TestData temporary = mTestData.get(TEMPORARY_PACKAGE);
        assertTrue(
                mPermissionManager.hasPermissionInternal(
                        mUsbAccessory, temporary.packageName, temporary.pid, temporary.uid));
    }

    @Test
    public void testAccessory_grantPermissionThrowsOnInvalid() {
        TestData invalidPackage = mTestData.get(INVALID_PACKAGE);

        // Invalid package + uid pair should throw
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mPermissionManager.grantAccessoryPermissionInternal(
                            mUsbAccessory, invalidPackage.packageName, invalidPackage.uid);
                });
    }

    @Test
    public void testDevice_hasAndGrantPermission() {
        grantDevicePersistedAndTemporaryInitial();

        // Non-granted permission fails.
        TestData validNew = mTestData.get(VALID_NEW_PACKAGE);
        assertFalse(
                mPermissionManager.hasPermissionInternal(
                        mUsbDevice, mUsbDeviceFingerprint, validNew.packageName, validNew.uid));

        // Granted permission succeeds.
        TestData temporary = mTestData.get(TEMPORARY_PACKAGE);
        assertTrue(
                mPermissionManager.hasPermissionInternal(
                        mUsbDevice, mUsbDeviceFingerprint, temporary.packageName, temporary.uid));

        TestData persisted = mTestData.get(PERSISTED_PACKAGE);
        assertTrue(
                mPermissionManager.hasPermissionInternal(
                        mUsbDevice, mUsbDeviceFingerprint, persisted.packageName, persisted.uid));
    }

    @Test
    public void testDevice_grantPermissionThrowsOnInvalid() {
        TestData invalidPackage = mTestData.get(INVALID_PACKAGE);

        // Invalid package + uid pair should throw
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mPermissionManager.grantAccessoryPermissionInternal(
                            mUsbAccessory, invalidPackage.packageName, invalidPackage.uid);
                });
    }

    @Test
    public void testDevice_revokePermission() {
        grantDevicePersistedAndTemporaryInitial();

        TestData persisted = mTestData.get(PERSISTED_PACKAGE);
        TestData temporary = mTestData.get(TEMPORARY_PACKAGE);

        assertTrue(
                mPermissionManager.hasPermissionInternal(
                        mUsbDevice, mUsbDeviceFingerprint, temporary.packageName, temporary.uid));
        assertTrue(
                mPermissionManager.hasPermissionInternal(
                        mUsbDevice, mUsbDeviceFingerprint, persisted.packageName, persisted.uid));

        mPermissionManager.revokeDevicePermission(
                mUsbDevice, mUsbDeviceFingerprint, temporary.packageName, temporary.uid);
        assertFalse(
                mPermissionManager.hasPermissionInternal(
                        mUsbDevice, mUsbDeviceFingerprint, temporary.packageName, temporary.uid));

        mPermissionManager.revokeDevicePermission(
                mUsbDevice, mUsbDeviceFingerprint, persisted.packageName, persisted.uid);
        assertFalse(
                mPermissionManager.hasPermissionInternal(
                        mUsbDevice, mUsbDeviceFingerprint, persisted.packageName, persisted.uid));
    }

    @Test
    public void testRemovePackage() {
        grantAccessoryTemporaryInitial();
        grantDevicePersistedAndTemporaryInitial();

        TestData temporary = mTestData.get(TEMPORARY_PACKAGE);
        mPermissionManager.removePackagePermissions(temporary.packageName, temporary.uid);
        TestData persisted = mTestData.get(PERSISTED_PACKAGE);
        mPermissionManager.removePackagePermissions(persisted.packageName, persisted.uid);

        assertFalse(
                mPermissionManager.hasPermissionInternal(
                        mUsbAccessory, temporary.packageName, temporary.pid, temporary.uid));
        assertFalse(
                mPermissionManager.hasPermissionInternal(
                        mUsbDevice, mUsbDeviceFingerprint, temporary.packageName, temporary.uid));
        assertFalse(
                mPermissionManager.hasPermissionInternal(
                        mUsbDevice, mUsbDeviceFingerprint, persisted.packageName, persisted.uid));
    }

    @Test
    public void testDeviceSerialization_writeAndRead() throws Exception {
        TestData validNew = mTestData.get(VALID_NEW_PACKAGE);
        TestData invalid = mTestData.get(INVALID_PACKAGE);

        // Construct initial data to send to serializer.
        ArrayMap<UsbDeviceFingerprint, ArraySet<PackageAndUid>> permissionsMap = new ArrayMap<>();
        ArrayMap<UsbDeviceFingerprint, ArraySet<PackageAndUid>> outputPermissions =
                new ArrayMap<>();
        ArraySet<PackageAndUid> packageUidSet = new ArraySet<>();
        permissionsMap.put(mUsbDeviceFingerprint, packageUidSet);

        // Insert both valid and invalid data (should get checked on read).
        packageUidSet.add(new PackageAndUid(validNew.packageName, validNew.uid));
        packageUidSet.add(new PackageAndUid(invalid.packageName, invalid.uid));

        UsbDeviceFingerprint[] devicesArray =
                permissionsMap.keySet().toArray(new UsbDeviceFingerprint[permissionsMap.size()]);
        PackageAndUid[][] packageAndUidsForDevices = new PackageAndUid[permissionsMap.size()][];
        packageAndUidsForDevices[0] =
                packageUidSet.toArray(new PackageAndUid[packageUidSet.size()]);

        // Do the write.
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        TypedXmlSerializer serializer = Xml.resolveSerializer(stream);
        mPermissionManager.writeToSerializer(serializer, devicesArray, packageAndUidsForDevices);

        // Read back the stream.
        ByteArrayInputStream inputStream = new ByteArrayInputStream(stream.toByteArray());
        TypedXmlPullParser parser = Xml.resolvePullParser(inputStream);

        mPermissionManager.readPermissions(parser, outputPermissions);

        // Assert output has a single entry with the right fingerprint and only the valid package is
        // included.
        assertEquals(permissionsMap.size(), outputPermissions.size());
        assertTrue(outputPermissions.containsKey(mUsbDeviceFingerprint));
        assertEquals(1, outputPermissions.get(mUsbDeviceFingerprint).size());
        assertTrue(
                outputPermissions
                        .get(mUsbDeviceFingerprint)
                        .contains(new PackageAndUid(validNew.packageName, validNew.uid)));
    }

    @Test
    public void testGetPackagesWithDevicePermission() {
        grantDevicePersistedAndTemporaryInitial();
        TestData temporary = mTestData.get(TEMPORARY_PACKAGE);
        TestData persisted = mTestData.get(PERSISTED_PACKAGE);

        ArraySet<String> expectedPackages = new ArraySet<>();
        expectedPackages.add(persisted.packageName);
        expectedPackages.add(temporary.packageName);

        List<String> actualPackagesList =
                mPermissionManager.getPackagesWithDevicePermission(
                        mUsbDevice, mUsbDeviceFingerprint);
        ArraySet<String> actualPackages = new ArraySet<>(actualPackagesList);

        // Expect there not to be any duplicates and to match expected packages
        assertEquals(actualPackages.size(), actualPackagesList.size());
        assertEquals(actualPackages, expectedPackages);
    }
}
