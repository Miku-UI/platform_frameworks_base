/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.protolog;

import static org.junit.Assert.assertThrows;

import android.platform.test.annotations.Presubmit;

import com.android.internal.protolog.common.IProtoLogGroup;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Test class for {@link ProtoLog}. */
@SuppressWarnings("ConstantConditions")
@Presubmit
@RunWith(JUnit4.class)
public class ProtoLogTest {

    ProtoLogController mController;

    @Before
    public void setUp() {
        mController = ProtoLog.getControllerInstanceForTest();
        ProtoLog.setControllerInstanceForTest(new ProtoLogController());
    }

    @After
    public void tearDown() {
        final var testProtoLogController = ProtoLog.getControllerInstanceForTest();
        testProtoLogController.tearDown();
        ProtoLog.setControllerInstanceForTest(mController);
    }

    @Test
    public void log_whenUninitialized_doesNotThrow() {
        // This test verifies that calling any ProtoLog logging method before the
        // system is initialized does not cause a crash. The change this test covers
        // replaced an IllegalStateException with a Log.wtf() call.

        // The test passes if no exception is thrown for any of the log levels.
        ProtoLog.d(TEST_GROUP_1, "Debug log call before init");
        ProtoLog.v(TEST_GROUP_1, "Verbose log call before init");
        ProtoLog.i(TEST_GROUP_1, "Info log call before init");
        ProtoLog.w(TEST_GROUP_1, "Warning log call before init");
        ProtoLog.e(TEST_GROUP_1, "Error log call before init");
        ProtoLog.wtf(TEST_GROUP_1, "WTF log call before init");
    }

    @Test
    public void canRunProtoLogInitMultipleTimes() {
        ProtoLog.init(TEST_GROUP_1);
        ProtoLog.init(TEST_GROUP_1);
        ProtoLog.init(TEST_GROUP_2);
        ProtoLog.init(TEST_GROUP_1, TEST_GROUP_2);

        final var instance = ProtoLog.getSingleInstance();
        Truth.assertThat(instance.getRegisteredGroups())
                .containsExactly(TEST_GROUP_1, TEST_GROUP_2);
    }

    @Test
    public void deduplicatesRegisteringDuplicateGroup() {
        ProtoLog.init(TEST_GROUP_1, TEST_GROUP_1, TEST_GROUP_2);

        final var instance = ProtoLog.getSingleInstance();
        Truth.assertThat(instance.getRegisteredGroups())
                .containsExactly(TEST_GROUP_1, TEST_GROUP_2);
    }

    @Test
    public void throwOnRegisteringGroupsWithIdCollisions() {
        final var assertion = assertThrows(RuntimeException.class,
                () -> ProtoLog.init(TEST_GROUP_1, TEST_GROUP_WITH_COLLISION, TEST_GROUP_2));

        Truth.assertThat(assertion).hasMessageThat()
            .contains("" + TEST_GROUP_WITH_COLLISION.getId());
        Truth.assertThat(assertion).hasMessageThat().contains("collision");
    }

    @Test
    public void noIdCollisionsBetweenGroups() {
        final var collectionOfKnowGroups = new ArrayList<IProtoLogGroup>();
        collectionOfKnowGroups.addAll(List.of(WmProtoLogGroups.values()));
        collectionOfKnowGroups.addAll(List.of(ShellProtoLogGroup.values()));
        collectionOfKnowGroups.add(new ProtoLogGroup("TEST_GROUP"));
        collectionOfKnowGroups.add(new ProtoLogGroup("OTHER_TEST_GROUP"));

        for (int i = 0; i < collectionOfKnowGroups.size(); i++) {
            for (int j = i + 1; j < collectionOfKnowGroups.size(); j++) {
                final var group1 = collectionOfKnowGroups.get(i);
                final var group2 = collectionOfKnowGroups.get(j);
                Truth.assertWithMessage(
                        "ID collision between " + group1.name() + " and " + group2.name())
                        .that(group1.getId()).isNotEqualTo(group2.getId());
            }
        }
    }

    private static final IProtoLogGroup TEST_GROUP_1 = new TestProtoLogGroup("TEST_TAG_1", 1);
    private static final IProtoLogGroup TEST_GROUP_2 = new TestProtoLogGroup("TEST_TAG_2", 2);
    private static final IProtoLogGroup TEST_GROUP_WITH_COLLISION =
            new TestProtoLogGroup("TEST_TAG_WITH_COLLISION", 1);

    private static class TestProtoLogGroup implements IProtoLogGroup {
        private final boolean mEnabled;
        private volatile boolean mLogToProto;
        private volatile boolean mLogToLogcat;
        private final String mTag;
        private final int mId;

        TestProtoLogGroup(String tag, int id) {
            this(true, true, false, tag, id);
        }

        TestProtoLogGroup(
                boolean enabled, boolean logToProto, boolean logToLogcat, String tag, int id) {
            this.mEnabled = enabled;
            this.mLogToProto = logToProto;
            this.mLogToLogcat = logToLogcat;
            this.mTag = tag;
            this.mId = id;
        }

        @Override
        public String name() {
            return mTag;
        }

        @Override
        public boolean isEnabled() {
            return mEnabled;
        }

        @Override
        public boolean isLogToLogcat() {
            return mLogToLogcat;
        }

        @Override
        public String getTag() {
            return mTag;
        }

        @Override
        public void setLogToLogcat(boolean logToLogcat) {
            this.mLogToLogcat = logToLogcat;
        }

        @Override
        public int getId() {
            return mId;
        }
    }
}
