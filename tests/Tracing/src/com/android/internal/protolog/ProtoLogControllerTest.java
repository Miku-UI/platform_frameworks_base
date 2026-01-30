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

package com.android.internal.protolog;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.annotation.NonNull;

import androidx.annotation.Nullable;

import com.android.internal.protolog.IProtoLogConfigurationService.RegisterClientArgs;
import com.android.internal.protolog.common.ILogger;
import com.android.internal.protolog.common.IProtoLog;
import com.android.internal.protolog.common.IProtoLogGroup;
import com.android.internal.protolog.common.LogLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(JUnit4.class)
public class ProtoLogControllerTest {

    private TestableProtoLogController mController;
    private MockProtoLog mMockProtoLogInstance;

    private static final TestProtoLogGroup GROUP_1 =
            new TestProtoLogGroup("GROUP_1", 1, true);
    private static final TestProtoLogGroup GROUP_2 =
            new TestProtoLogGroup("GROUP_2", 2, true);
    private static final TestProtoLogGroup GROUP_1_COLLISION =
            new TestProtoLogGroup("GROUP_1_COLLISION", 1, true);
    private static final TestProtoLogGroup DISABLED_GROUP =
            new TestProtoLogGroup("DISABLED_GROUP", 3, false);

    @Before
    public void setUp() {
        mMockProtoLogInstance = new MockProtoLog();
        mController = new TestableProtoLogController(mMockProtoLogInstance);
    }

    @Test
    public void registerSingleLogGroup() {
        mController.registerLogGroupInProcess(GROUP_1);
        assertThat(mController.getRegisteredGroups()).containsExactly(GROUP_1);
    }

    @Test
    public void registerMultipleLogGroups() {
        mController.registerLogGroupInProcess(GROUP_1, GROUP_2);
        assertThat(mController.getRegisteredGroups()).containsExactly(GROUP_1, GROUP_2);
    }

    @Test
    public void registerDuplicateLogGroupsDeduplicated() {
        mController.registerLogGroupInProcess(GROUP_1);
        mController.registerLogGroupInProcess(GROUP_1);
        assertThat(mController.getRegisteredGroups()).hasSize(1);
        assertThat(mController.getRegisteredGroups()).containsExactly(GROUP_1);
    }

    @Test
    public void registerDifferentLogGroupsTogetherWithCollidingIdsThrows() {
        RuntimeException ex =
                assertThrows(
                        RuntimeException.class,
                        () -> mController.registerLogGroupInProcess(GROUP_1, GROUP_1_COLLISION));
        assertThat(ex).hasMessageThat().contains("ProtoLog group ID collision for ID 1");
    }

    @Test
    public void registerDifferentLogGroupsWithCollidingIdsThrows() {
        mController.registerLogGroupInProcess(GROUP_1);
        RuntimeException ex =
                assertThrows(
                        RuntimeException.class,
                        () -> mController.registerLogGroupInProcess(GROUP_1_COLLISION));
        assertThat(ex).hasMessageThat().contains("ProtoLog group ID collision for ID 1");
        assertThat(ex)
                .hasMessageThat()
                .contains(
                        "Group GROUP_1_COLLISION conflicts with already registered group GROUP_1");
    }

    @Test
    public void registerLogGroupInProcessNullGroupIsIgnored() {
        mController.registerLogGroupInProcess(GROUP_1, null, GROUP_2);
        assertThat(mController.getRegisteredGroups()).containsExactly(GROUP_1, GROUP_2);
    }

    @Test
    public void registerLogGroupInProcessAfterInitCallsRegisterGroupsOnInstance() {
        mController.init(GROUP_1);
        mMockProtoLogInstance.clearRegisteredGroupsHistory();

        mController.registerLogGroupInProcess(GROUP_2);
        assertThat(mMockProtoLogInstance.getLastRegisteredGroups()).containsExactly(GROUP_2);
    }

    @Test
    public void initRegistersInitialGroups() {
        mController.init(GROUP_1, GROUP_2);
        assertThat(mController.getRegisteredGroups()).containsExactly(GROUP_1, GROUP_2);
    }

    @Test
    public void initMultipleCallsAccumulatesGroups() {
        mController.init(GROUP_1);
        IProtoLog firstInstance = mController.getProtoLogInstance();
        assertThat(mController.getRegisteredGroups()).containsExactly(GROUP_1);

        mController.init(GROUP_2);
        assertThat(mController.getProtoLogInstance()).isSameInstanceAs(firstInstance);
        assertThat(mController.getRegisteredGroups()).containsExactly(GROUP_1, GROUP_2);
    }

    @Test
    public void initCollisionInGroupsThrows() {
        RuntimeException ex =
                assertThrows(
                        RuntimeException.class,
                        () -> mController.init(GROUP_1, GROUP_1_COLLISION));
        assertThat(ex).hasMessageThat().contains("ProtoLog group ID collision for ID 1");
    }


    @Test
    public void getProtoLogInstanceBeforeInitReturnsNull() {
        ProtoLogController freshController = new ProtoLogController();
        assertNull(freshController.getProtoLogInstance());
    }

    @Test
    public void getRegisteredGroupsIsInitiallyEmpty() {
        ProtoLogController freshController = new ProtoLogController();
        assertThat(freshController.getRegisteredGroups()).isEmpty();
    }

    @Test
    public void getRegisteredGroupsAfterRegistrationContainsAllGroups() {
        mController.registerLogGroupInProcess(GROUP_1, GROUP_2);
        assertThat(mController.getRegisteredGroups()).containsExactly(GROUP_1, GROUP_2);
    }

    @Test
    public void getRegisteredGroupsReturnsUnmodifiableSet() {
        mController.registerLogGroupInProcess(GROUP_1);
        Set<IProtoLogGroup> groups = mController.getRegisteredGroups();
        assertThrows(UnsupportedOperationException.class, () -> groups.add(GROUP_2));
    }

    // Test Helpers

    static class TestableProtoLogController extends ProtoLogController {
        private final MockProtoLog mMockInjectedProtoLogInstance;

        TestableProtoLogController(MockProtoLog mockInjectedInstance) {
            super();
            this.mMockInjectedProtoLogInstance = mockInjectedInstance;
        }

        @Override
        @NonNull
        protected IProtoLog createLogcatOnlyInstance() {
            mMockInjectedProtoLogInstance.setInitialGroups(new HashSet<>(getRegisteredGroups()));
            return mMockInjectedProtoLogInstance;
        }

        @Override
        @NonNull
        protected PerfettoProtoLogImpl createAndEnableNewPerfettoProtoLogImpl(
                @NonNull ProtoLogDataSource datasource, @NonNull IProtoLogGroup[] currentGroups) {
            mMockInjectedProtoLogInstance.setInitialGroups(
                    new HashSet<>(Arrays.asList(currentGroups)));
            return new DummyPerfettoProtoLogImpl(datasource, currentGroups,
                    mMockInjectedProtoLogInstance);
        }
    }

    static class MockProtoLog implements IProtoLog {
        private final List<IProtoLogGroup> mLastRegisteredGroups = new ArrayList<>();
        private final Set<IProtoLogGroup> mInitialGroups = new HashSet<>();

        public void setInitialGroups(Set<IProtoLogGroup> groups) {
            this.mInitialGroups.clear();
            this.mInitialGroups.addAll(groups);
        }

        @Override
        public void log(@NonNull LogLevel logLevel, @NonNull IProtoLogGroup group, long messageHash,
                int paramsMask, @Nullable Object[] args) {
            // No-op for testing
        }

        @Override
        public void log(@NonNull LogLevel level, @NonNull IProtoLogGroup group,
                @NonNull String messageString, @NonNull Object[] args) {
            // No-op for testing
        }

        @Override
        public boolean isProtoEnabled() {
            return false;
        }

        @Override
        public int startLoggingToLogcat(@NonNull String[] groups, @NonNull ILogger logger) {
            return 0;
        }

        @Override
        public int stopLoggingToLogcat(@NonNull String[] groups, @NonNull ILogger logger) {
            return 0;
        }

        @Override
        public boolean isEnabled(@NonNull IProtoLogGroup group, @NonNull LogLevel level) {
            return group.isEnabled();
        }

        @Override
        public void registerGroups(@NonNull IProtoLogGroup[] groups) {
            mLastRegisteredGroups.clear();
            Collections.addAll(mLastRegisteredGroups, groups);
        }

        @Override
        @NonNull
        public List<IProtoLogGroup> getRegisteredGroups() {
            return List.copyOf(mInitialGroups);
        }

        public List<IProtoLogGroup> getLastRegisteredGroups() {
            return List.copyOf(mLastRegisteredGroups);
        }

        public void clearRegisteredGroupsHistory() {
            mLastRegisteredGroups.clear();
        }
    }

    static class DummyPerfettoProtoLogImpl extends PerfettoProtoLogImpl {
        private final MockProtoLog mWrappedInstance;

        DummyPerfettoProtoLogImpl(ProtoLogDataSource dataSource, IProtoLogGroup[] groups,
                MockProtoLog wrappedInstance) {
            super(dataSource, protoLogInstance -> {}, groups);
            this.mWrappedInstance = wrappedInstance;
            this.mWrappedInstance.registerGroups(groups);
        }

        public MockProtoLog getWrappedInstance() {
            return mWrappedInstance;
        }

        @Override
        public void enable() {
            // No-op for tests
        }

        @Override
        public void log(@NonNull LogLevel level, @NonNull IProtoLogGroup group,
                @NonNull String messageString, @NonNull Object[] args) {
            mWrappedInstance.log(level, group, messageString, args);
        }

        @Override
        public boolean isEnabled(@NonNull IProtoLogGroup group, @NonNull LogLevel level) {
            return mWrappedInstance.isEnabled(group, level);
        }

        @Override
        public void registerGroups(@NonNull IProtoLogGroup[] groups) {
            mWrappedInstance.registerGroups(groups);
        }

        @NonNull
        @Override
        protected RegisterClientArgs createConfigurationServiceRegisterClientArgs() {
            return new RegisterClientArgs();
        }

        @Override
        @NonNull
        public List<IProtoLogGroup> getRegisteredGroups() {
            return mWrappedInstance.getRegisteredGroups();
        }

        @Override
        void dumpViewerConfig() {
            // No-op for testing
        }

        @NonNull
        @Override
        String getLogcatMessageString(@NonNull Message message) {
            return "";
        }
    }

    static class TestProtoLogGroup implements IProtoLogGroup {
        private final String mName;
        private final int mId;
        private final boolean mEnabled;
        private boolean mLogToLogcat = true;

        TestProtoLogGroup(String name, int id, boolean enabled) {
            this.mName = name;
            this.mId = id;
            this.mEnabled = enabled;
        }

        @Override public String name() {
            return mName;
        }

        @Override public int getId() {
            return mId;
        }

        @Override public boolean isEnabled() {
            return mEnabled;
        }

        @Override public String getTag() {
            return mName;
        }

        @Override public boolean isLogToLogcat() {
            return mLogToLogcat;
        }

        @Override public void setLogToLogcat(boolean val) {
            this.mLogToLogcat = val;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestProtoLogGroup that = (TestProtoLogGroup) o;
            return mId == that.mId && mName.equals(that.mName);
        }

        @Override
        public int hashCode() {
            return 31 * mName.hashCode() + mId;
        }
    }
}
