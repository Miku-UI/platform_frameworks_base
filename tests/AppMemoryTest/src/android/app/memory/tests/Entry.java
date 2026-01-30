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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Entries in a heap dump
 */
public class Entry {

    // An entry has one method, which is its size on the heap.  The default is zero,
    // because most entries in the heap dump are administrative.
    long size() {
        return 0;
    }

    // Print self to the output stream.
    void dump(PrintStream o) { }

    // A Context maps IDs to instances.
    static class Context {
        private final HashMap<Long, HeapString> mStr = new HashMap<>();;
        private final HashMap<Long, HeapClass> mClass = new HashMap<>();;

        void addStr(long id, HeapString e) {
            if (mStr.containsKey(id)) {
                throw new RuntimeException("duplicate string id " + id);
            }
            mStr.put(id, e);
        }

        HeapString getStr(long id) {
            return mStr.get(id);
        }

        void addClass(long id, HeapClass e) {
            if (mClass.containsKey(id)) {
                throw new RuntimeException("duplicate class id " + id);
            }
            mClass.put(id, e);
        }

        HeapClass getClass(long id) {
            return mClass.get(id);
        }
    }

    static class HeapString extends Entry {
        final long mId;
        final byte[] mValue;
        HeapString(Scanner s, Context c) {
            mId = s.id();
            mValue = s.jL();
            c.addStr(mId, this);
        }

        @Override public String toString() {
            return new String(mValue);
        }

        @Override void dump(PrintStream o) {
            o.format("String \"%s\"\n", new String(mValue));
        }
    }

    static class HeapClass extends Entry {
        final int mSerial;
        final long mClassId;
        final int mStack;
        final HeapString mName;
        final boolean mIsString;
        HeapClass(Scanner s, Context c) {
            mSerial = s.jI();
            mClassId = s.id();
            mStack = s.jI();
            mName = c.getStr(s.id());
            c.addClass(mClassId, this);

            mIsString = mName.toString().equals("java.lang.String");
        }

        @Override public String toString() {
            return mName.toString();
        }
    }

    static class StackFrame extends Entry {
        final long mId;
        final long mMethodId;
        final long mSignatureId;
        final HeapString mFile;
        final int mSerial;
        final int mLine;
        StackFrame(Scanner s, Context c) {
            mId = s.id();
            mMethodId = s.id();
            mSignatureId = s.id();
            mFile = c.getStr(s.id());
            mSerial = s.jI();
            mLine = s.jI();
        }
        @Override
        void dump(PrintStream o) {
        }
    }

    static class StackTrace extends Entry {
        final int mSerial;
        final int mThread;
        final int mCount;
        final long[] mFrames;
        StackTrace(Scanner s, Context c) {
            mSerial = s.jI();
            mThread = s.jI();
            mCount = s.jI();
            mFrames = s.id(mCount);
        }
    }

    public record AllocSite(byte indicator, int serial, int stack,
                     int live_bytes, int live_instances,
                     int alloc_bytes, int alloc_instances) {
        AllocSite(Scanner s) {
            this(s.jB(), s.jI(), s.jI(), s.jI(), s.jI(), s.jI(), s.jI());
        }
        @Override
        public String toString() {
            return String.format("site %d %d %d %d",
                                 live_bytes, live_instances,
                                 alloc_bytes, alloc_instances);
        }
    }

    static class AllocSites extends Entry {
        final short mFlags;
        final int mCutoffRatio;
        final int mLiveBytes;
        final int mLiveInstances;
        final int mAllocBytes;
        final int mAllocInstances;
        final AllocSite[] mSites;
        AllocSites(Scanner s, Context c) {
            mFlags = s.jS();
            mCutoffRatio = s.jI();
            mLiveBytes = s.jI();
            mLiveInstances = s.jI();
            mAllocBytes = s.jI();
            mAllocInstances = s.jI();
            mSites = new AllocSite[s.jI()];
            for (int i = 0; i < mSites.length; i++) {
                mSites[i] = new AllocSite(s);
            }
        }
        @Override
        void dump(PrintStream o) {
            o.println("ALLOC SITES");
            for (var a : mSites) {
                o.println("  " + a.toString());
            }
        }
    }

    static class StartThread extends Entry {
        final int mThread;
        final long mObjectId;
        final int mStack;
        final long mNameId;
        final long mGroupNameId;
        final long mPgroupNameId;
        StartThread(Scanner s, Context c) {
            mThread = s.jI();
            mObjectId = s.id();
            mStack = s.jI();
            mNameId = s.id();
            mGroupNameId = s.id();
            mPgroupNameId = s.id();
        }
    }

    static class EndThread extends Entry {
        final int mThread;
        EndThread(Scanner s, Context c) {
            mThread = s.jI();
        }
    }

    static class ControlSettings extends Entry {
        final int mFlags;
        final short mDepth;
        ControlSettings(Scanner s, Context c) {
            mFlags = s.jI();
            mDepth = s.jS();
        }
    }

    enum Root {
        Unknown,
        JniGlobal,
        JniLocal,
        JavaFrame,
        NativeStack,
        StickyClass,
        ThreadBlock,
        MonitorUsed,
        ThreadObject,
        ClassDump,
        Instance,
        ObjectArray,
        PrimitiveArray,
        InternedString,
        Debugger,
        VmInternal,
        DumpInfo
    }

    // A single heap allocation.  The key attribute is its size in bytes.  It may be
    // reachable.  It has another attribute that defines where the object was allocated.
    static class Alloc {
        final Root mRoot;
        final long mId;

        Alloc(Root root, long id) {
            this.mRoot = root;
            this.mId = id;
        }

        void dump(PrintStream o) { }

        long size() {
            return 0;
        }
    }

    static class UnknownAlloc extends Alloc {
        UnknownAlloc(long id, Scanner s, Context c) {
            super(Root.Unknown, id);
        }

        @Override void dump(PrintStream o) {
            o.println("  unknown");
        }
    }

    static class JniGlobal extends Alloc {
        final long mReferentId;
        JniGlobal(long id, Scanner s, Context c) {
            super(Root.JniGlobal, id);
            mReferentId = s.id();
        }

        @Override void dump(PrintStream o) {
            o.println("  jni global");
        }
    }

    static class JniLocal extends Alloc {
        final int mThread;
        final int mFrame;
        JniLocal(long id, Scanner s, Context c) {
            super(Root.JniLocal, id);
            mThread = s.jI();
            mFrame = s.jI();
        }

        @Override void dump(PrintStream o) {
            o.println("  jni local");
        }
    }

    static class JavaFrame extends Alloc {
        final int mSerial;
        final int mFrame;
        JavaFrame(long id, Scanner s, Context c) {
            super(Root.JavaFrame, id);
            mSerial = s.jI();
            mFrame = s.jI();
        }

        @Override void dump(PrintStream o) {
            o.println("  java frame");
        }
    }

    static class NativeStack extends Alloc {
        final int mSerial;
        NativeStack(long id, Scanner s, Context c) {
            super(Root.NativeStack, id);
            mSerial = s.jI();
        }

        @Override void dump(PrintStream o) {
            o.println("  native stack");
        }
    }

    static class StickyClass extends Alloc {
        StickyClass(long id, Scanner s, Context c) {
            super(Root.StickyClass, id);
        }

        @Override void dump(PrintStream o) {
            o.println("  sticky class");
        }
    }

    static class ThreadBlock extends Alloc {
        final int mThread;
        ThreadBlock(long id, Scanner s, Context c) {
            super(Root.ThreadBlock, id);
            mThread = s.jI();
        }

        @Override void dump(PrintStream o) {
            o.println("  thread block");
        }
    }

    static class MonitorUsed extends Alloc {
        MonitorUsed(long id, Scanner s, Context c) {
            super(Root.MonitorUsed, id);
        }

        @Override void dump(PrintStream o) {
            o.println("  monitor used");
        }
    }

    static class ThreadObject extends Alloc {
        final int mSerial;
        final int mStack;
        ThreadObject(long id, Scanner s, Context c) {
            super(Root.ThreadObject, id);
            mSerial = s.jI();
            mStack = s.jI();
        }

        @Override void dump(PrintStream o) {
            o.println("  thread object");
        }
    }

    static class ClassDump extends Alloc {
        final int mSerial;
        final long mObjectId;
        final long mLoaderId;
        final long mSignersId;
        final long mDomainId;
        final long mReserved1;
        final long mReserved2;
        final int mInstanceSize;
        final short mPoolSize;
        final short mNumStaticFields;
        final short mNumInstFields;

        record Pool(short index, Type type, long val) {}
        record Static(long name, Type type, long val) {}
        record Instance(long name, Type type) {}

        final Pool[] mPool;
        final Static[] mStaticFields;
        final Instance[] mInstanceFields;


        ClassDump(long id, Scanner s, Context c) {
            super(Root.ClassDump, id);
            mSerial = s.jI();
            mObjectId = s.id();
            mLoaderId = s.id();
            mSignersId = s.id();
            mDomainId = s.id();
            mReserved1 = s.id();
            mReserved2 = s.id();
            mInstanceSize = s.jI();

            mPoolSize = s.jS();
            mPool = new Pool[mPoolSize];
            for (int i = 0; i < mPoolSize; i++) {
                mPool[i] = new Pool(s.jS(), s.type(), s.typedValue());
            }

            mNumStaticFields = s.jS();
            mStaticFields = new Static[mNumStaticFields];
            for (int i = 0; i < mNumStaticFields; i++) {
                mStaticFields[i] = new Static(s.id(), s.type(), s.typedValue());
            }

            mNumInstFields = s.jS();
            mInstanceFields = new Instance[mNumInstFields];
            for (int i = 0; i < mNumInstFields; i++) {
                mInstanceFields[i] = new Instance(s.id(), s.type());
            }
        }

        @Override void dump(PrintStream o) {
            o.println("  class dump");
        }
    }

    static class Instance extends Alloc {
        final int mStack;
        final HeapClass mClass;
        final int mDataLen;
        final byte[] mData;
        Instance(long id, Scanner s, Context c) {
            super(Root.Instance, id);
            mStack = s.jI();
            mClass = c.getClass(s.id());
            mDataLen = s.jI();
            mData = s.jL(mDataLen);
        }

        @Override void dump(PrintStream o) {
            if (mClass.mIsString) {
                o.format("  instance %s %s\n", mClass, new String(mData));
            } else {
                o.format("  instance %s %d %d\n", mClass, mDataLen, mData.length);
            }
        }

        @Override long size() {
            return mDataLen;
        }
    }

    static class ObjectArray extends Alloc {
        final int mStack;
        final int mCount;
        final long mTypeId;
        final long[] mData;
        ObjectArray(long id, Scanner s, Context c) {
            super(Root.ObjectArray, id);
            mStack = s.jI();
            mCount = s.jI();
            mTypeId = s.id();
            mData = s.id(mCount);
        }

        @Override void dump(PrintStream o) {
            o.println("  object array");
        }

        @Override long size() {
            return (long) mCount * 8;
        }
    }

    static class PrimitiveArray extends Alloc {
        final int mStack;
        final int mCount;
        final Type mTypeId;
        final byte[] mData;
        PrimitiveArray(long id, Scanner s, Context c) {
            super(Root.PrimitiveArray, id);
            mStack = s.jI();
            mCount = s.jI();
            mTypeId = s.type();
            mData = s.jL(mCount * mTypeId.size());
        }

        @Override long size() {
            return mData.length;
        }

        @Override void dump(PrintStream o) {
            o.println("  primitive array");
        }
    }

    static class InternedString extends Alloc {
        InternedString(long id, Scanner s, Context c) {
            super(Root.InternedString, id);
        }

        @Override void dump(PrintStream o) {
            o.println("  interned string");
        }
    }

    static class Debugger extends Alloc {
        Debugger(long id, Scanner s, Context c) {
            super(Root.Debugger, id);
        }

        @Override void dump(PrintStream o) {
            o.println("  debugger");
        }
    }

    static class VmInternal extends Alloc {
        VmInternal(long id, Scanner s, Context c) {
            super(Root.VmInternal, id);
        }

        @Override void dump(PrintStream o) {
            o.println("  vm internal");
        }
    }

    static class DumpInfo extends Alloc {
        final long mName;
        DumpInfo(long id, Scanner s, Context c) {
            super(Root.DumpInfo, id);
            mName = s.id();
        }

        @Override void dump(PrintStream o) {
            o.println("  dump info");
        }
    }

    static class HeapDump extends Entry {
        final ArrayList<Alloc> mAlloc = new ArrayList<>();

        HeapDump(Scanner s, Context c) {
            while (!s.empty()) {
                mAlloc.add(alloc(s, c));
            }
        }

        @Override
        void dump(PrintStream o) {
            o.println("heap dump " + mAlloc.size());
            for (Alloc a : mAlloc) {
                a.dump(o);
            }
        }

        @Override
        long size() {
            long s = 0;
            for (Alloc a : mAlloc) {
                s += a.size();
            }
            return s;
        }

        private Alloc alloc(Scanner s, Context c) {
            byte subtag = s.jB();
            long id = s.id();

            // Set the root and burn off any unused fields.
            return switch (subtag) {
                case (byte) 0x01 -> new JniGlobal(id, s, c);
                case (byte) 0x02 -> new JniLocal(id, s, c);
                case (byte) 0x03 -> new JavaFrame(id, s, c);
                case (byte) 0x04 -> new NativeStack(id, s, c);
                case (byte) 0x05 -> new StickyClass(id, s, c);
                case (byte) 0x06 -> new ThreadBlock(id, s, c);
                case (byte) 0x07 -> new MonitorUsed(id, s, c);
                case (byte) 0x08 -> new ThreadObject(id, s, c);
                case (byte) 0x20 -> new ClassDump(id, s, c);
                case (byte) 0x21 -> new Instance(id, s, c);
                case (byte) 0x22 -> new ObjectArray(id, s, c);
                case (byte) 0x23 -> new PrimitiveArray(id, s, c);
                case (byte) 0x89 -> new InternedString(id, s, c);
                case (byte) 0x8b -> new Debugger(id, s, c);
                case (byte) 0x8d -> new VmInternal(id, s, c);
                case (byte) 0xfe -> new DumpInfo(id, s, c);

                case (byte) 0xff -> new UnknownAlloc(id, s, c);

                default ->
                    throw new RuntimeException(
                        String.format("unknown subtag: 0x%x\n", (byte) subtag));
            };
        }
    }

    static class EndOfHeap extends Entry {
        // A sentinel for the end-of-heap
    }

    static Entry inflate(int tag, Scanner s, Context c) {
        return switch (tag) {
            case 0x01 -> new HeapString(s, c);
            case 0x02 -> new HeapClass(s, c);
            case 0x04 -> new StackFrame(s, c);
            case 0x05 -> new StackTrace(s, c);
            case 0x06 -> new AllocSites(s, c);
            case 0x0a -> new StartThread(s, c);
            case 0x0b -> new EndThread(s, c);
            case 0x0e -> new ControlSettings(s, c);
            case 0x0c -> new HeapDump(s, c);
            case 0x1c -> new HeapDump(s, c);
            case 0x2c -> new EndOfHeap();
            default -> throw new RuntimeException(String.format("unknown tag: 0x%x\n", tag));
        };
    }

    static String name(int tag) {
        return switch (tag) {
            case 0x01 -> "HeapString";
            case 0x02 -> "HeapClass";
            case 0x04 -> "StackFrame";
            case 0x05 -> "StackTrace";
            case 0x06 -> "AllocSites";
            case 0x0a -> "StartThread";
            case 0x0b -> "EndThread";
            case 0x0e -> "ControlSettings";
            case 0x0c -> "HeapDump";
            case 0x1c -> "HeapDump";
            case 0x2c -> "End";
            default -> throw new RuntimeException(String.format("unknown tag: 0x%x\n", tag));
        };
    }
}
