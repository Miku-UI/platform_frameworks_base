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

import android.hardware.usb.DeviceFilter;
import android.hardware.usb.UsbDevice;
import android.text.TextUtils;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.usb.descriptors.UsbDescriptorParser;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Uniquely identifiable fingerprint for a connected USB device.
 *
 * <p>This class provides a hashcode implementation for a currently connected UsbDevice via a
 * factory method. This fingerprint is meant to be used for persistently identifying a UsbDevice
 * across reconnections.
 */
public class UsbDeviceFingerprint {
    private static final String TAG = UsbDeviceFingerprint.class.getSimpleName();

    /** Class encapsulating hashcode used for UsbDevice fingerprinting. */
    public static class Hashcode {
        private static final int SHA256_DIGEST_SIZE = 32;

        private final byte[] mHash;

        /** Interface for Hasher implementation. */
        public interface Hasher {
            /**
             * Append bytes to hash.
             *
             * @param bytes - Bytes to hash.
             * @return {@link Hasher}
             */
            Hasher putBytes(byte[] bytes);

            /**
             * Complete hashing bytes that were appended to this hasher.
             *
             * @return {@link HashCode}
             */
            Hashcode hash();
        }

        /** Class encapsulating Sha256 hashing. */
        private static class Sha256Hasher implements Hasher {
            MessageDigest mDigest;

            private Sha256Hasher() {
                try {
                    mDigest = MessageDigest.getInstance("SHA-256");
                } catch (NoSuchAlgorithmException e) {
                    Slog.e(TAG, "SHA-256 algorithm not found; hasher invalid: ", e);
                    throw new IllegalStateException("SHA-256 algorithm not found");
                }
            }

            @Override
            public Hasher putBytes(byte[] bytes) {
                if (mDigest != null) {
                    mDigest.update(bytes);
                }
                return this;
            }

            @Override
            public Hashcode hash() {
                if (mDigest != null) {
                    return new Hashcode(mDigest.digest(), mDigest.getDigestLength());
                }

                return null;
            }
        }

        private Hashcode(byte[] bytes, int length) {
            mHash = new byte[length];
            if (bytes != null) {
                System.arraycopy(bytes, 0, mHash, 0, length);
            } else {
                Arrays.fill(mHash, (byte) 0);
            }
        }

        public Hashcode(Hashcode other) {
            mHash = new byte[other.mHash.length];
            System.arraycopy(other.mHash, 0, mHash, 0, other.mHash.length);
        }

        /** Use sha256 to provide Hashcode. */
        public static Hasher getHasher() {
            return new Sha256Hasher();
        }

        /** Creates an empty (zero) hash code. */
        public static Hashcode createEmptyHashcode() {
            return new Hashcode(null, SHA256_DIGEST_SIZE);
        }

        /** Create a Hashcode from a base64 encoded string. */
        public static Hashcode fromString(String hashString) {
            if (hashString == null) {
                return null;
            }

            try {
                byte[] decodedBytes = Base64.getDecoder().decode(hashString);
                return new Hashcode(decodedBytes, decodedBytes.length);
            } catch (IllegalArgumentException e) {
                Slog.e(TAG, "Invalid input hash string: " + e.getMessage());
                return null;
            }
        }

        /** Return underlying hashed bytes. */
        public byte[] asBytes() {
            return mHash;
        }

        /** Return underlying hash as an int, with little endian byte order. */
        public int asInt() {
            ByteBuffer buffer = ByteBuffer.wrap(mHash, 0, 4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            return buffer.getInt();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            if (!(o instanceof Hashcode)) {
                return false;
            }

            Hashcode other = (Hashcode) o;
            return Arrays.equals(mHash, other.mHash);
        }

        @Override
        public int hashCode() {
            return this.asInt();
        }

        @Override
        public String toString() {
            return Base64.getEncoder().encodeToString(mHash);
        }
    }

    /** Device being fingerprinted has a serial number. Best hashcode. */
    public static final int HASHCODE_WITH_SERIAL_NUMBER = 0;

    /**
     * Device being fingerprinted has one or more hubs in between that have a serial number. Better
     * hashcode.
     */
    public static final int HASHCODE_WITH_UNIQUE_HUB_TOPOLOGY = 1;

    /**
     * Device being fingerprinted has one or more hubs without serial numbers. Ok hashcode but
     * starting to become shakier quality.
     */
    public static final int HASHCODE_WITH_HUB_TOPOLOGY = 2;

    /** Device being fingerprinted is directly connected to a physical port. Worst hashcode. */
    public static final int HASHCODE_WITH_DIRECT_CONNECTION = 3;

    // Sysfs path for USB devices.
    private static final String SYSFS_USB_DEVICES_PATH = "/sys/bus/usb/devices";

    // By default, we do not compare device paths for equivalence.
    // We still calculate this and keep in case we want it for the future.
    private static final boolean COMPARE_PATHS_DEFAULT = false;

    /** Name of XML tag used for serialization */
    public static final String XML_ROOT_NAME = "usb-device-fingerprint";

    // Constant strings for XML serialization and deserialization
    private static final String DESCRIPTOR_HASH_ATTR_TAG = "descriptor-hash";
    private static final String DEVICEPATH_HASH_ATTR_TAG = "devicepath-hash";
    private static final String HASHCODE_QUALITY_ATTR_TAG = "hashcode-quality";
    private static final String COMPARE_DEVPATHS_ATTR_TAG = "compare-device-paths";
    private static final String LAST_SEEN_ATTR_TAG = "last-seen";

    // Devices with serial numbers will retain data for 30 days since they were last seen.
    static final long PERSIST_DURATION_WITH_SERIAL_MS = TimeUnit.DAYS.toMillis(30);

    // Devices without serial numbers will retain data for 7 days since they were last seen.
    static final long PERSIST_DURATION_WITHOUT_SERIAL_MS = TimeUnit.DAYS.toMillis(7);

    // Information about the device being fingerprinted.
    private DeviceFilter mDeviceFilter;

    // Hash of the descriptors of the tracked USB Device.
    private Hashcode mDescriptorHashcode;

    // Hash describing the path from the root hub to the tracked USB device.
    // - If there are external hubs in between, this provides a hash of all the
    //   hub descriptors between the root hub and the target device.
    // - If the USB device is directly connected to an external port, this provides
    //   a hash of the bus number and the device path (i.e. 4-1) of this device.
    private Hashcode mDevicePathHashcode;

    // Quality of the hashcode generated.
    int mHashcodeQuality;

    // Truncated hashcode for Java override.
    int mTruncatedHashcode;

    // Whether to check device paths for equivalence.
    boolean mCompareDevicePaths;

    // When was this device/fingerprint last seen on the system? Used for trimming away
    // devices that have not been used/seen in a while.
    long mLastSeenMs;

    // Private constructor requiring a DeviceFilter and descriptor hash code.
    //
    // @param deviceFilter - Serializable representation of a Usb Device
    // @param descriptorHashcode - Hashcode of the descriptor representing the device
    // @param devicePathHashcode - Hashcode of device path from root hub to this device
    @VisibleForTesting
    UsbDeviceFingerprint(
            DeviceFilter deviceFilter,
            Hashcode descriptorHashcode,
            Hashcode devicePathHashcode,
            int hashcodeQuality,
            boolean compareDevicePaths,
            long lastSeenTimestamp) {
        mDeviceFilter = deviceFilter;
        mDescriptorHashcode = descriptorHashcode;
        mDevicePathHashcode = devicePathHashcode;
        mHashcodeQuality = hashcodeQuality;
        mCompareDevicePaths = compareDevicePaths;
        mLastSeenMs = lastSeenTimestamp;

        // Java hashcode is simply the first 4 bytes of descriptor hashcode
        mTruncatedHashcode = mDescriptorHashcode.asInt();
    }

    public UsbDeviceFingerprint(UsbDeviceFingerprint other) {
        this.mDeviceFilter = new DeviceFilter(other.mDeviceFilter);
        this.mDescriptorHashcode = new Hashcode(other.mDescriptorHashcode);
        this.mDevicePathHashcode = new Hashcode(other.mDevicePathHashcode);
        this.mHashcodeQuality = other.mHashcodeQuality;
        this.mCompareDevicePaths = other.mCompareDevicePaths;
        this.mLastSeenMs = other.mLastSeenMs;
    }

    // Read the file from given directory or return empty string on error.
    static String readFileFromPath(Path directoryPath, String file) {
        try {
            return Files.readString(directoryPath.resolve(file));
        } catch (IOException e) {
            Slog.e(TAG, "Error reading" + file + " : " + e.getMessage());
            return "";
        }
    }

    // Starting from the given startPath, which should be the root hub path,
    // find the path that matches the target device number (which is unique per
    // root hub) and return the list of paths leading up to it.
    //
    // If the returned List is empty, the target device number wasn't found.
    // If the returned List has a single entry, the device is connected directly
    //     to the root hub.
    // Otherwise, all entries after the first in the returned list are hubs
    //     between the target device and the root hub.
    static List<Path> findPathToDevNumMatch(Path startPath, int targetDevNum) {
        try (Stream<Path> pathStream = Files.walk(startPath)) {
            Optional<Path> foundDirectory =
                    pathStream
                            // Filter to device directories only. Interfaces dirs have `:`.
                            .filter(Files::isDirectory)
                            .filter(path -> !path.getFileName().toString().contains(":"))
                            .filter(
                                    dirPath -> {
                                        Path devNumFile = dirPath.resolve("devnum");
                                        if (Files.exists(devNumFile)
                                                && Files.isRegularFile(devNumFile)) {
                                            try (Stream<String> stream = Files.lines(devNumFile)) {
                                                // Read the first line of the file and check for
                                                // content match
                                                String content =
                                                        stream.findFirst().orElse("").trim();
                                                return content.equals(
                                                        Integer.toString(targetDevNum));
                                            } catch (IOException e) {
                                                // Ignore files that cannot be read
                                                return false;
                                            }
                                        }
                                        return false;
                                    })
                            // DevNum should be unique so we only need to find the first match.
                            .findFirst();

            return foundDirectory
                    .map(
                            matchPath -> {
                                // Get all directory paths from the matching path back up to the
                                // startPath
                                return Stream.iterate(
                                                matchPath,
                                                p -> (p != null) && !p.equals(startPath),
                                                Path::getParent)
                                        // Collect the paths in order from matchPath back to
                                        // startPath (or its parents).
                                        .collect(Collectors.toList());
                            })
                    .orElseGet(List::of); // Return empty list if no match is found
            //
        } catch (IOException e) {
            // Handle initial walk error (e.g., startPath doesn't exist or permission denied)
            Slog.e(TAG, "Error walking the file tree: " + e.getMessage());
            return List.of();
        }
    }

    // Generate Hashcode by hashing the descriptors of the ordered set of hubs
    // between the given UsbDevice and the root hub it is connected to.
    //
    // If the device is directly connected to an external port, the hash will be of
    // the bus number and the device path (i.e. 4-1) of this device.
    //
    // @param device - USB device to generate Hashcode for.
    // @param sysfsPrefix - Sysfs directory for the root hub.
    //
    // Returns a pair of hashcode and hashcode quality.
    private static Pair<Hashcode, Integer> generateRootHubToDevicePathHashcode(
            UsbDevice device,
            String sysfsPrefix,
            Function<String, UsbDescriptorParser> parserFactory) {
        // A USB device provides a bus number and device number but this doesn't
        // map neatly into topology. In order to determine the ordering, we need
        // to start at the root hub and make our way to the leaf device with the
        // right device number.
        Pair<Integer, Integer> busAndDeviceNumber = device.getBusAndDeviceNumber();
        int busNumber = busAndDeviceNumber.first;
        int deviceNumber = busAndDeviceNumber.second;

        Path rootHubPath = Paths.get(sysfsPrefix, TextUtils.formatSimple("usb%d", busNumber));
        List<Path> pathsToDevice = findPathToDevNumMatch(rootHubPath, deviceNumber);

        if (pathsToDevice == null || pathsToDevice.size() == 0) {
            return null;
        } else if (pathsToDevice.size() == 1) {
            String devpath = readFileFromPath(pathsToDevice.get(0), "devpath").trim();
            // Create hash of busnum-devpath. i.e. 4-1
            String fullPath = TextUtils.formatSimple("%d-%s", busNumber, devpath);
            Hashcode hash = Hashcode.getHasher().putBytes(fullPath.getBytes()).hash();

            return Pair.create(hash, HASHCODE_WITH_DIRECT_CONNECTION);
        } else {
            Hashcode.Hasher hasher = Hashcode.getHasher();
            boolean hasSerialNumber = false;
            boolean skipFirst = true;
            for (Path path : pathsToDevice) {
                // Skip the first entry (which is the device)
                if (skipFirst) {
                    skipFirst = false;
                    continue;
                }

                try {
                    int devNum = Integer.parseInt(readFileFromPath(path, "devnum").trim());
                    String deviceAddr =
                            TextUtils.formatSimple("/dev/bus/usb/%03d/%03d", busNumber, devNum);
                    UsbDescriptorParser parser = parserFactory.apply(deviceAddr);
                    if (parser != null) {
                        hasher.putBytes(parser.getDescriptorHashcode().asBytes());
                        hasSerialNumber |=
                                !parser.getDeviceDescriptor().getSerialString(parser).isEmpty();
                    }
                } catch (NumberFormatException e) {
                    Slog.e(TAG, "Invalid devnum", e);
                }
            }

            Hashcode hashCode = hasher.hash();
            int hashQuality =
                    hasSerialNumber
                            ? HASHCODE_WITH_UNIQUE_HUB_TOPOLOGY
                            : HASHCODE_WITH_HUB_TOPOLOGY;
            return Pair.create(hashCode, hashQuality);
        }
    }

    /**
     * Create fingerprint from a live, connected USB device.
     *
     * <p>Note: This will traverse sysfs and may read up to 5 additional USB descriptors in order to
     * calculate all hashes. Avoid calling this from System Server or UI threads.
     *
     * @param device - Connected USB device
     * @param descriptorParser - Descriptor parser for connected device
     * @return UsbDeviceFingerprint instance for this device
     */
    public static UsbDeviceFingerprint createLiveFingerprint(
            UsbDevice device, UsbDescriptorParser descriptorParser) {
        long lastSeenMs = System.currentTimeMillis();
        return createLiveFingerprintInternal(
                device,
                descriptorParser,
                COMPARE_PATHS_DEFAULT,
                SYSFS_USB_DEVICES_PATH,
                UsbDescriptorParser::fromDeviceAddress,
                lastSeenMs);
    }

    /** Internal factory method that allows injecting a UsbDescriptorParser factory. */
    static UsbDeviceFingerprint createLiveFingerprintInternal(
            UsbDevice device,
            UsbDescriptorParser descriptorParser,
            boolean compareDevicePaths,
            String sysfsPrefix,
            Function<String, UsbDescriptorParser> parserFactory,
            long lastSeenMs) {
        DeviceFilter df = new DeviceFilter(device);

        // Start with worst hash quality and empty hashcode.
        Hashcode devicePathHashcode = Hashcode.createEmptyHashcode();
        int hashQuality = HASHCODE_WITH_DIRECT_CONNECTION;

        Pair<Hashcode, Integer> pathHash =
                generateRootHubToDevicePathHashcode(device, sysfsPrefix, parserFactory);
        if (pathHash != null) {
            devicePathHashcode = pathHash.first;
            hashQuality = pathHash.second;
        }

        // Serial number of device descriptor automatically makes this high quality.
        boolean hasSerialNumber = df.mSerialNumber != null && !df.mSerialNumber.isEmpty();
        if (hasSerialNumber) {
            hashQuality = HASHCODE_WITH_SERIAL_NUMBER;
        }

        return new UsbDeviceFingerprint(
                df,
                descriptorParser.getDescriptorHashcode(),
                devicePathHashcode,
                hashQuality,
                compareDevicePaths,
                lastSeenMs);
    }

    /**
     * Read the UsbDeviceFingerprint from XML.
     *
     * @param parser - Live parser instance.
     * @return {@link UsbDeviceFingerprint}
     */
    public static UsbDeviceFingerprint read(TypedXmlPullParser parser)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();

        try {
            Hashcode descriptorHash =
                    Hashcode.fromString(parser.getAttributeValue(null, DESCRIPTOR_HASH_ATTR_TAG));
            Hashcode devicePathHash =
                    Hashcode.fromString(parser.getAttributeValue(null, DEVICEPATH_HASH_ATTR_TAG));
            int hashQuality = parser.getAttributeInt(null, HASHCODE_QUALITY_ATTR_TAG);
            boolean compareDevicePaths =
                    parser.getAttributeBoolean(null, COMPARE_DEVPATHS_ATTR_TAG);
            long lastSeen = parser.getAttributeLong(
                    null, LAST_SEEN_ATTR_TAG, System.currentTimeMillis());

            XmlUtils.nextElementWithin(parser, outerDepth);

            DeviceFilter deviceFilter = null;
            if (DeviceFilter.XML_ROOT_NAME.equals(parser.getName())) {
                deviceFilter = DeviceFilter.read(parser);
            }

            if (deviceFilter != null && descriptorHash != null && devicePathHash != null) {
                return new UsbDeviceFingerprint(
                        deviceFilter,
                        descriptorHash,
                        devicePathHash,
                        hashQuality,
                        compareDevicePaths,
                        lastSeen);
            } else {
                Slog.e(TAG, "error reading fingerprint data");
            }

        } catch (NumberFormatException e) {
            Slog.e(TAG, "error reading hash quality", e);
            XmlUtils.skipCurrentTag(parser);
        }

        return null;
    }

    /**
     * Write this UsbDeviceFingerprint to XML.
     *
     * @param serializer - Live serializer instance.
     */
    public void write(TypedXmlSerializer serializer) throws IOException {
        serializer.startTag(null, XML_ROOT_NAME);
        serializer.attribute(null, DESCRIPTOR_HASH_ATTR_TAG, mDescriptorHashcode.toString());
        serializer.attribute(null, DEVICEPATH_HASH_ATTR_TAG, mDevicePathHashcode.toString());
        serializer.attributeInt(null, HASHCODE_QUALITY_ATTR_TAG, mHashcodeQuality);
        serializer.attributeBoolean(null, COMPARE_DEVPATHS_ATTR_TAG, mCompareDevicePaths);
        serializer.attributeLong(null, LAST_SEEN_ATTR_TAG, mLastSeenMs);
        mDeviceFilter.write(serializer);
        serializer.endTag(null, XML_ROOT_NAME);
    }

    // Compare hashcodes and then the DeviceFilter for equivalence.
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof UsbDeviceFingerprint)) {
            return false;
        }

        UsbDeviceFingerprint other = (UsbDeviceFingerprint) o;

        // We only compare paths if configured to.
        boolean pathComparison =
                !mCompareDevicePaths || (mDevicePathHashcode.equals(other.mDevicePathHashcode));
        return mDescriptorHashcode.equals(other.mDescriptorHashcode)
                && pathComparison
                && mDeviceFilter.equals(other.mDeviceFilter);
    }

    // Truncated hashcode implementation for Java containers.
    @Override
    public int hashCode() {
        return mTruncatedHashcode;
    }

    public Hashcode getPathHashcode() {
        return mDevicePathHashcode;
    }

    // Get the quality of the hashcode generated. A lower quality hash is more
    // susceptible to spoofing or collisions.
    public int getHashcodeQuality() {
        return mHashcodeQuality;
    }

    /** Get the time this device as last seen (timestamp in milliseconds). */
    public long getLastSeenMs() {
        return mLastSeenMs;
    }

    /** Update when this device was last seen to now. */
    public void updateLastSeenToNow() {
        mLastSeenMs = System.currentTimeMillis();
    }

    /**
     * Check if this fingerprint is for a device that hasn't been seen recently.
     *
     * <p>Depending on the quality of the fingerprint (with serial number or without), the duration
     * used for this check is different.
     *
     * @return True if the fingerprint is stale, False otherwise
     */
    public boolean isStale() {
        long now = System.currentTimeMillis();

        // Fingerprint is from the future so it's not stale.
        if (now < mLastSeenMs) {
            return false;
        }

        long delta = now - mLastSeenMs;

        if (getHashcodeQuality() == HASHCODE_WITH_SERIAL_NUMBER) {
            return delta > PERSIST_DURATION_WITH_SERIAL_MS;
        } else {
            return delta > PERSIST_DURATION_WITHOUT_SERIAL_MS;
        }
    }
}
