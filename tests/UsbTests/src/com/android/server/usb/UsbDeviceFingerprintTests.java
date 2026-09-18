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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.hardware.usb.IUsbSerialReader;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import com.android.server.usb.UsbDeviceFingerprint.Hashcode;
import com.android.server.usb.descriptors.UsbDescriptorParser;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@EnableFlags({
    android.hardware.usb.flags.Flags.FLAG_ENABLE_PERSISTENT_USB_DEVICE_PERMISSIONS,
    com.android.server.usb.flags.Flags.FLAG_ENABLE_USB_HOST_AUTHORIZATION
})
public class UsbDeviceFingerprintTests {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private File mTempDir;

    // Device descriptor only with no serial number
    private static final byte[] DESCRIPTOR_NOSERIAL =
            new byte[] {
                18, 1, // Device descriptor
                3, 1, // bcdUSB
                4, // class
                5, // subclass
                6, // protocol
                64, // max packet size
                0x12, 0x34, // vid
                0x43, 0x21, // pid
                0, 0, // bcdDevice
                0, // manufacturer
                0, // product
                0, // serial number
                0, // num configurations
            };

    // Device descriptor with serial number only at offset 1
    // Make sure to mock getStringDescriptor
    private static final byte[] DESCRIPTOR_SERIAL_ONLY =
            new byte[] {
                18, 1, // Device descriptor
                3, 1, // bcdUSB
                4, // class
                5, // subclass
                6, // protocol
                64, // max packet size
                0x12, 0x34, // vid
                0x01, 0x02, // pid
                0, 0, // bcdDevice
                0, // manufacturer
                0, // product
                1, // serial number
                0, // num configurations
            };

    private static final boolean NO_PATH_COMPARISON = false;

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

    @Before
    public void setUp() throws Exception {
        mTempDir = folder.newFolder("usb-test");
    }

    private Path createTestFile(Path parent, String name, String content) throws IOException {
        Path filePath = parent.resolve(name);
        Files.write(filePath, content.getBytes());
        return filePath;
    }

    private UsbDevice getDeviceCopy(String serialNumber) {
        return new UsbDevice.Builder(
                        "/dev/bus/usb/001/002", // name
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

    private long now() {
        return System.currentTimeMillis();
    }

    @Test
    public void testHashcode() throws NoSuchAlgorithmException {
        byte[] testData = "test data".getBytes();
        Hashcode.Hasher hasher = Hashcode.getHasher();
        Hashcode hashcode = hasher.putBytes(testData).hash();

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(testData);
        byte[] expectedHash = md.digest();

        assertArrayEquals(expectedHash, hashcode.asBytes());
    }

    @Test
    public void testHashcodeEquals() {
        Hashcode first = Hashcode.getHasher().putBytes(DESCRIPTOR_SERIAL_ONLY).hash();
        Hashcode second = Hashcode.getHasher().putBytes(DESCRIPTOR_SERIAL_ONLY).hash();
        Hashcode third = Hashcode.getHasher().putBytes(DESCRIPTOR_NOSERIAL).hash();

        assertTrue(first.equals(first));
        assertTrue(first.equals(second));
        assertFalse(first.equals(third));
        assertFalse(second.equals(third));
    }

    @Test
    public void testCreateEmptyHashcode() {
        Hashcode hashcode = Hashcode.createEmptyHashcode();
        byte[] expected = new byte[32];
        Arrays.fill(expected, (byte) 0);
        assertArrayEquals(expected, hashcode.asBytes());
    }

    @Test
    public void testFingerprintWithSerialNumber() {
        UsbDevice device = spy(getDeviceCopy("12345"));

        Hashcode descriptorHash = Hashcode.getHasher().putBytes(DESCRIPTOR_SERIAL_ONLY).hash();

        UsbDescriptorParser mockDescriptorParser = mock(UsbDescriptorParser.class);
        when(mockDescriptorParser.getDescriptorHashcode()).thenReturn(descriptorHash);

        UsbDeviceFingerprint fingerprint =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        device,
                        mockDescriptorParser,
                        NO_PATH_COMPARISON,
                        mTempDir.getAbsolutePath(),
                        UsbDescriptorParser::fromDeviceAddress,
                        now());

        assertEquals(
                UsbDeviceFingerprint.HASHCODE_WITH_SERIAL_NUMBER, fingerprint.getHashcodeQuality());
        assertEquals(descriptorHash.asInt(), fingerprint.hashCode());
    }

    @Test
    public void testFingerprintDirectConnection() throws IOException {
        String deviceAddr = "/dev/bus/usb/001/002";
        UsbDevice device = spy(getDeviceCopy(null));
        doReturn(deviceAddr).when(device).getDeviceName();
        doReturn(new Pair<>(1, 2)).when(device).getBusAndDeviceNumber();

        UsbDescriptorParser descriptorParser =
                spy(new UsbDescriptorParser(deviceAddr, DESCRIPTOR_NOSERIAL));
        doReturn("").when(descriptorParser).getDescriptorString(0);

        // Mock sysfs
        String sysfsPrefix = mTempDir.getAbsolutePath();
        Path rootHubPath = Path.of(sysfsPrefix, "usb1");
        Files.createDirectories(rootHubPath);
        Path devicePath = rootHubPath.resolve("1-1");
        Files.createDirectories(devicePath);
        createTestFile(devicePath, "devnum", "2");
        createTestFile(devicePath, "devpath", "1");

        Hashcode descriptorHash = Hashcode.getHasher().putBytes(DESCRIPTOR_NOSERIAL).hash();
        Hashcode devPathHash = Hashcode.getHasher().putBytes("1-1".getBytes()).hash();

        // Mock parser factory
        Function<String, UsbDescriptorParser> mockParserFactory = (addr) -> null;

        UsbDeviceFingerprint fingerprint =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        device,
                        descriptorParser,
                        NO_PATH_COMPARISON,
                        sysfsPrefix,
                        mockParserFactory,
                        now());

        assertEquals(descriptorHash.asInt(), fingerprint.hashCode());
        assertEquals(
                UsbDeviceFingerprint.HASHCODE_WITH_DIRECT_CONNECTION,
                fingerprint.getHashcodeQuality());
        assertEquals(devPathHash, fingerprint.getPathHashcode());
    }

    @Test
    public void testFingerprintWithHubTopology() throws IOException {
        String deviceAddr = "/dev/bus/usb/001/003";
        UsbDevice device = spy(getDeviceCopy(null));
        doReturn(deviceAddr).when(device).getDeviceName();
        doReturn(new Pair<>(1, 3)).when(device).getBusAndDeviceNumber();

        UsbDescriptorParser descriptorParser =
                spy(new UsbDescriptorParser(deviceAddr, DESCRIPTOR_NOSERIAL));
        doReturn("").when(descriptorParser).getDescriptorString(0);

        // Mock sysfs
        String sysfsPrefix = mTempDir.getAbsolutePath();
        Path rootHubPath = Path.of(sysfsPrefix, "usb1");
        Files.createDirectories(rootHubPath);

        Path hubPath = rootHubPath.resolve("1-1");
        Files.createDirectories(hubPath);
        createTestFile(hubPath, "devnum", "2");

        Path devicePath = hubPath.resolve("1-1.2");
        Files.createDirectories(devicePath);
        createTestFile(devicePath, "devnum", "3");

        // Mock parser factory
        Map<String, UsbDescriptorParser> mockParsers = new HashMap<>();

        UsbDescriptorParser hubParser =
                spy(new UsbDescriptorParser("/dev/bus/usb/001/002", DESCRIPTOR_NOSERIAL));
        doReturn("").when(hubParser).getDescriptorString(0);
        mockParsers.put("/dev/bus/usb/001/002", hubParser);

        UsbDescriptorParser devParser =
                spy(new UsbDescriptorParser("/dev/bus/usb/001/003", DESCRIPTOR_NOSERIAL));
        doReturn("").when(devParser).getDescriptorString(0);
        mockParsers.put("/dev/bus/usb/001/003", devParser);

        Function<String, UsbDescriptorParser> mockParserFactory = mockParsers::get;

        UsbDeviceFingerprint fingerprint =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        device,
                        descriptorParser,
                        NO_PATH_COMPARISON,
                        sysfsPrefix,
                        mockParserFactory,
                        now());

        Hashcode descriptorHash = Hashcode.getHasher().putBytes(DESCRIPTOR_NOSERIAL).hash();
        Hashcode pathHash =
                Hashcode.getHasher().putBytes(hubParser.getDescriptorHashcode().asBytes()).hash();

        assertEquals(descriptorHash.asInt(), fingerprint.hashCode());
        assertEquals(
                UsbDeviceFingerprint.HASHCODE_WITH_HUB_TOPOLOGY, fingerprint.getHashcodeQuality());
        assertEquals(pathHash, fingerprint.getPathHashcode());

        // Test for unique hub topology by putting a serial # on the hub
        Map<String, UsbDescriptorParser> mockParsersWithSerialHub = new HashMap<>();
        mockParserFactory = mockParsersWithSerialHub::get;

        hubParser = spy(new UsbDescriptorParser("/dev/bus/usb/001/002", DESCRIPTOR_SERIAL_ONLY));
        doReturn("SERIAL_HUB").when(hubParser).getDescriptorString(1);
        mockParsersWithSerialHub.put("/dev/bus/usb/001/002", hubParser);
        mockParsersWithSerialHub.put("/dev/bus/usb/001/003", devParser);

        fingerprint =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        device,
                        descriptorParser,
                        NO_PATH_COMPARISON,
                        sysfsPrefix,
                        mockParserFactory,
                        now());

        descriptorHash = Hashcode.getHasher().putBytes(DESCRIPTOR_NOSERIAL).hash();
        pathHash =
                Hashcode.getHasher().putBytes(hubParser.getDescriptorHashcode().asBytes()).hash();

        assertEquals(descriptorHash.asInt(), fingerprint.hashCode());
        assertEquals(
                UsbDeviceFingerprint.HASHCODE_WITH_UNIQUE_HUB_TOPOLOGY,
                fingerprint.getHashcodeQuality());
        assertEquals(pathHash, fingerprint.getPathHashcode());
    }

    @Test
    public void testEqualsAndHashCode() throws IOException {
        UsbDevice device = spy(getDeviceCopy("SERIAL1"));
        doReturn(new Pair<>(1, 2)).when(device).getBusAndDeviceNumber();

        byte[] descriptorBytes1 = new byte[] {8, 0x2A, 6, 7, 8, 9, 10, 11};
        Hashcode descriptorHash1 = Hashcode.getHasher().putBytes(descriptorBytes1).hash();

        UsbDescriptorParser mockDescriptorParser1 = mock(UsbDescriptorParser.class);
        when(mockDescriptorParser1.getDescriptorHashcode()).thenReturn(descriptorHash1);

        UsbDeviceFingerprint fingerprint1 =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        device,
                        mockDescriptorParser1,
                        NO_PATH_COMPARISON,
                        mTempDir.getAbsolutePath(),
                        UsbDescriptorParser::fromDeviceAddress,
                        now());

        UsbDevice mockUsbDevice2 = spy(getDeviceCopy("SERIAL1"));
        doReturn(new Pair<>(1, 2)).when(mockUsbDevice2).getBusAndDeviceNumber();

        byte[] descriptorBytes2 = new byte[] {8, 0x2A, 6, 7, 8, 9, 10, 11};
        Hashcode descriptorHash2 = Hashcode.getHasher().putBytes(descriptorBytes2).hash();

        UsbDescriptorParser mockDescriptorParser2 = mock(UsbDescriptorParser.class);
        when(mockDescriptorParser2.getDescriptorHashcode()).thenReturn(descriptorHash2);

        UsbDeviceFingerprint fingerprint2 =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        mockUsbDevice2,
                        mockDescriptorParser2,
                        NO_PATH_COMPARISON,
                        mTempDir.getAbsolutePath(),
                        UsbDescriptorParser::fromDeviceAddress,
                        now());

        // Test equality when descriptors are the same.
        assertEquals(fingerprint1.getHashcodeQuality(), fingerprint2.getHashcodeQuality());
        assertEquals(fingerprint1.hashCode(), fingerprint2.hashCode());
        assertEquals(fingerprint1, fingerprint2);

        // Create a different fingerprint (different serial) - results in same hashcode
        UsbDevice mockUsbDevice3 = spy(getDeviceCopy("SERIAL2"));
        doReturn(new Pair<>(1, 3)).when(mockUsbDevice3).getBusAndDeviceNumber();

        byte[] descriptorBytes3 = new byte[] {8, 0x2A, 6, 7, 8, 9, 10, 11};
        Hashcode descriptorHash3 = Hashcode.getHasher().putBytes(descriptorBytes3).hash();

        UsbDescriptorParser mockDescriptorParser3 = mock(UsbDescriptorParser.class);
        when(mockDescriptorParser3.getDescriptorHashcode()).thenReturn(descriptorHash3);

        UsbDeviceFingerprint fingerprint3 =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        mockUsbDevice3,
                        mockDescriptorParser3,
                        NO_PATH_COMPARISON,
                        mTempDir.getAbsolutePath(),
                        UsbDescriptorParser::fromDeviceAddress,
                        now());

        // Test inequality - hashcode will be same (based off descriptors) but unequal (due to
        // serial number).
        assertEquals(fingerprint1.hashCode(), fingerprint3.hashCode());
        assertNotEquals(fingerprint1, fingerprint3);

        // Create a different fingerprint (different descriptor hash)
        UsbDevice mockUsbDevice4 = spy(getDeviceCopy("SERIAL1"));
        doReturn(new Pair<>(1, 4)).when(mockUsbDevice4).getBusAndDeviceNumber();

        byte[] descriptorBytes4 = new byte[] {8, 0x2A, 7, 8, 9, 10, 11, 12};
        Hashcode descriptorHash4 = Hashcode.getHasher().putBytes(descriptorBytes4).hash();

        UsbDescriptorParser mockDescriptorParser4 = mock(UsbDescriptorParser.class);
        when(mockDescriptorParser4.getDescriptorHashcode()).thenReturn(descriptorHash4);

        UsbDeviceFingerprint fingerprint4 =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        mockUsbDevice4,
                        mockDescriptorParser4,
                        NO_PATH_COMPARISON,
                        mTempDir.getAbsolutePath(),
                        UsbDescriptorParser::fromDeviceAddress,
                        now());

        // Test inequality
        assertNotEquals(fingerprint1.hashCode(), fingerprint4.hashCode());
        assertNotEquals(fingerprint1, fingerprint4);
    }

    @Test
    public void testLastSeen_updateToNow() {
        long testStartedAt = now();

        UsbDevice device = spy(getDeviceCopy("12345"));
        Hashcode descriptorHash = Hashcode.getHasher().putBytes(DESCRIPTOR_SERIAL_ONLY).hash();
        UsbDescriptorParser mockDescriptorParser = mock(UsbDescriptorParser.class);
        when(mockDescriptorParser.getDescriptorHashcode()).thenReturn(descriptorHash);

        UsbDeviceFingerprint fingerprint =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        device,
                        mockDescriptorParser,
                        NO_PATH_COMPARISON,
                        mTempDir.getAbsolutePath(),
                        UsbDescriptorParser::fromDeviceAddress,
                        testStartedAt - 100);

        assertTrue(testStartedAt > fingerprint.getLastSeenMs());
        assertFalse(fingerprint.isStale());
        fingerprint.updateLastSeenToNow();
        assertTrue(fingerprint.getLastSeenMs() >= testStartedAt);
    }

    @Test
    public void testLastSeen_checkStaleDates() {
        long startTime = now() - 1;
        long serial_stale = startTime - UsbDeviceFingerprint.PERSIST_DURATION_WITH_SERIAL_MS;
        long noserial_stale = startTime - UsbDeviceFingerprint.PERSIST_DURATION_WITHOUT_SERIAL_MS;

        // Test assumption is that we keep devices with serial numbers longer.
        assertTrue(
                UsbDeviceFingerprint.PERSIST_DURATION_WITH_SERIAL_MS
                        > UsbDeviceFingerprint.PERSIST_DURATION_WITHOUT_SERIAL_MS);

        UsbDevice device = spy(getDeviceCopy("12345"));
        Hashcode descriptorHash = Hashcode.getHasher().putBytes(DESCRIPTOR_SERIAL_ONLY).hash();
        UsbDescriptorParser mockDescriptorParser = mock(UsbDescriptorParser.class);
        when(mockDescriptorParser.getDescriptorHashcode()).thenReturn(descriptorHash);

        UsbDeviceFingerprint fingerprint =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        device,
                        mockDescriptorParser,
                        NO_PATH_COMPARISON,
                        mTempDir.getAbsolutePath(),
                        UsbDescriptorParser::fromDeviceAddress,
                        serial_stale);

        assertEquals(
                UsbDeviceFingerprint.HASHCODE_WITH_SERIAL_NUMBER, fingerprint.getHashcodeQuality());
        assertTrue(fingerprint.isStale());

        fingerprint =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        device,
                        mockDescriptorParser,
                        NO_PATH_COMPARISON,
                        mTempDir.getAbsolutePath(),
                        UsbDescriptorParser::fromDeviceAddress,
                        noserial_stale);

        assertEquals(
                UsbDeviceFingerprint.HASHCODE_WITH_SERIAL_NUMBER, fingerprint.getHashcodeQuality());
        assertFalse(fingerprint.isStale());

        // Now create a device with no serial number.
        device = spy(getDeviceCopy(null));
        descriptorHash = Hashcode.getHasher().putBytes(DESCRIPTOR_NOSERIAL).hash();
        mockDescriptorParser = mock(UsbDescriptorParser.class);
        when(mockDescriptorParser.getDescriptorHashcode()).thenReturn(descriptorHash);

        fingerprint =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        device,
                        mockDescriptorParser,
                        NO_PATH_COMPARISON,
                        mTempDir.getAbsolutePath(),
                        UsbDescriptorParser::fromDeviceAddress,
                        startTime);

        // If we're close to the start time, the fingerprint shouldn't be stale.
        assertFalse(fingerprint.isStale());

        fingerprint =
                UsbDeviceFingerprint.createLiveFingerprintInternal(
                        device,
                        mockDescriptorParser,
                        NO_PATH_COMPARISON,
                        mTempDir.getAbsolutePath(),
                        UsbDescriptorParser::fromDeviceAddress,
                        noserial_stale);

        assertTrue(fingerprint.isStale());
    }
}
