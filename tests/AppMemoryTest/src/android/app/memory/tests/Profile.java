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

package android.app.memory.tests;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

/**
 * Read a heap profile in enough detail to support the memory test.
 */
public class Profile {

    // The profile version.
    String mVersion;

    // The size of identifiers.
    int mIdSize;

    // The timestamp of the heap dump, in seconds since the epoch.
    long mTimestamp;

    Profile(File src) throws FileNotFoundException, IOException {
        FileInputStream file = new FileInputStream(src);
        Scanner s = new Scanner(file);
        try {
            parse(s);
        } finally {
            file.close();
        }
    }

    static class Tlv {
        // The tag, time, and value of the TLV.
        final byte mTag;
        final int mTime;
        final byte[] mValue;

        Tlv(byte tag, int time, byte[] value) {
            mTag = tag;
            mTime = time;
            mValue = value;
        }

        Scanner scanner(int idSize) {
            return new Scanner(new ByteArrayInputStream(mValue), idSize);
        }
    }


    // The TLVs extracted from the heap dump.
    final ArrayList<Tlv> mEntryList = new ArrayList<>();

    private boolean readEntry(Scanner s) {
        try {
            byte tag = s.jB();
            int time = s.jI();
            int len = s.jI();
            byte[] data = s.jL(len);
            mEntryList.add(new Tlv(tag, time, data));
            return true;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    // Pick off the version.  The file offset is assumed to be zero.
    private String readVersion(Scanner s) throws IOException {
        byte[] version = new byte[64];
        for (int i = 0; i < version.length; i++) {
            int b = s.jB();
            if (b == 0) {
                return new String(version, 0, i);
            }
            version[i] = (byte) b;
        }
        throw new RuntimeException("version overflow: " + new String(version));
    }

    private void parse(Scanner s) throws IOException {
        mVersion = readVersion(s);
        mIdSize = s.jI();
        // The timestamp is in milliseconds since the epoch.  Just keep the seconds.
        mTimestamp = s.jJ() / 1000;
        while (readEntry(s)) {
            // The number of entries is not known a priori.  Read until there are none left.
        }
        expand();
    }

    final ArrayList<Entry> mEntries = new ArrayList<>();

    private void expand() {
        Entry.Context c = new Entry.Context();
        for (Tlv t : mEntryList) {
            mEntries.add(Entry.inflate(t.mTag, t.scanner(mIdSize), c));
        }
    }


    // Dump the content of the profile
    void dump(PrintStream o) {
        for (var e : mEntries) {
            e.dump(o);
        }
    }

    // Return the size of the heap.
    long size() {
        long total = 0;
        for (var e : mEntries) {
            total += e.size();
        }
        return total;
    }

    /**
     * Main method for stand-alone tests.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("missing required heap profile");
            System.exit(1);
        } else if (args.length > 1) {
            System.err.println("extra arguments on command line");
            System.exit(1);
        }
        File dump = new File(args[0]);

        try {
            Profile profile = new Profile(dump);
            System.out.format("version:   %s\n", profile.mVersion);
            System.out.format("idsize:    %d\n", profile.mIdSize);
            System.out.format("timestamp: %d\n", profile.mTimestamp);

            // We firmly believe that the number of TLV types is less than 32 (0x20).
            int[] count = new int[32];
            for (Tlv t : profile.mEntryList) {
                if (t.mTag != 0x2c) count[t.mTag]++;
            }
            System.out.format("%-20s %8d\n", "Entries", profile.mEntryList.size());
            for (int i = 0; i < count.length; i++) {
                if (count[i] != 0) {
                    System.out.format("%-20s %8d\n", Entry.name(i), count[i]);
                }
            }

            long allocated = profile.size();
            System.out.format("%-20s %8d\n", "size", allocated);

            // profile.dump(System.out);
        } catch (FileNotFoundException e) {
            System.err.format("file %s not found: %s\n", dump.getPath(), e.toString());
            System.exit(2);
        } catch (IOException e) {
            System.err.format("unable to read file %s: %s\n", dump.getPath(), e.toString());
            System.exit(2);
        }
    }
}
