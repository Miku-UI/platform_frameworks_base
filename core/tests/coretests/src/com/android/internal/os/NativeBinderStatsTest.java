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

package com.android.internal.os;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.ContextWrapper;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.util.test.FakeSettingsProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
@DisabledOnRavenwood(blockedBy = MockContentResolver.class)
public class NativeBinderStatsTest {
    private Context mContext;
    private MockContentResolver mResolver;
    private NativeBinderStats.PropertiesWrapper mMockPropertiesWrapper;

    @Before
    public void setup() {
        FakeSettingsProvider.clearSettingsProvider();
        mContext = spy(new ContextWrapper(InstrumentationRegistry.getTargetContext()));
        mResolver = new MockContentResolver(mContext);
        mResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        mMockPropertiesWrapper = mock(NativeBinderStats.PropertiesWrapper.class);
        when(mContext.getContentResolver()).thenReturn(mResolver);
    }

    @After
    public void tearDown() {
        FakeSettingsProvider.clearSettingsProvider();
    }

    @Test
    public void testSettingsObserver_disabledByDefault() {
        NativeBinderStats nativeBinderStats =
                new NativeBinderStats(mContext, mMockPropertiesWrapper);
        nativeBinderStats.systemReady();

        // Verify that the injector was called with default values
        Mockito.verify(mMockPropertiesWrapper).setEnabled(NativeBinderStats.DEFAULT_ENABLED);
        Mockito.verify(mMockPropertiesWrapper)
                .setProcessSharding(NativeBinderStats.DEFAULT_PROCESS_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSpamSharding(NativeBinderStats.DEFAULT_SPAM_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setCallSharding(NativeBinderStats.DEFAULT_CALL_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemProcessSharding(NativeBinderStats.DEFAULT_SYSTEM_PROCESS_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemSpamSharding(NativeBinderStats.DEFAULT_SYSTEM_SPAM_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemCallSharding(NativeBinderStats.DEFAULT_SYSTEM_CALL_SHARDING);

        assertThat(nativeBinderStats.mEnabled).isFalse();
    }

    @Test
    public void testSettingsObserver_enabled() {
        Settings.Global.putString(mResolver, Settings.Global.NATIVE_BINDER_STATS, "enabled=true");
        // Reset mock to clear previous interactions from setup (if any)
        Mockito.reset(mMockPropertiesWrapper);

        NativeBinderStats nativeBinderStats =
                new NativeBinderStats(mContext, mMockPropertiesWrapper);
        nativeBinderStats.systemReady();

        assertThat(nativeBinderStats.mEnabled).isTrue();
        assertThat(nativeBinderStats.mProcessSharding)
                .isEqualTo(NativeBinderStats.DEFAULT_PROCESS_SHARDING);
        assertThat(nativeBinderStats.mSpamSharding)
                .isEqualTo(NativeBinderStats.DEFAULT_SPAM_SHARDING);
        assertThat(nativeBinderStats.mCallSharding)
                .isEqualTo(NativeBinderStats.DEFAULT_CALL_SHARDING);
        assertThat(nativeBinderStats.mSystemProcessSharding)
                .isEqualTo(NativeBinderStats.DEFAULT_SYSTEM_PROCESS_SHARDING);
        assertThat(nativeBinderStats.mSystemSpamSharding)
                .isEqualTo(NativeBinderStats.DEFAULT_SYSTEM_SPAM_SHARDING);
        assertThat(nativeBinderStats.mSystemCallSharding)
                .isEqualTo(NativeBinderStats.DEFAULT_SYSTEM_CALL_SHARDING);
        // Verify that the injector was called correctly
        Mockito.verify(mMockPropertiesWrapper).setEnabled(true);
        Mockito.verify(mMockPropertiesWrapper)
                .setProcessSharding(NativeBinderStats.DEFAULT_PROCESS_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSpamSharding(NativeBinderStats.DEFAULT_SPAM_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setCallSharding(NativeBinderStats.DEFAULT_CALL_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemProcessSharding(NativeBinderStats.DEFAULT_SYSTEM_PROCESS_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemSpamSharding(NativeBinderStats.DEFAULT_SYSTEM_SPAM_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemCallSharding(NativeBinderStats.DEFAULT_SYSTEM_CALL_SHARDING);
    }

    @Test
    public void testSettingsObserver_disabled() {
        Settings.Global.putString(mResolver, Settings.Global.NATIVE_BINDER_STATS, "enabled=false");
        NativeBinderStats nativeBinderStats =
                new NativeBinderStats(mContext, mMockPropertiesWrapper);
        nativeBinderStats.systemReady();

        assertThat(nativeBinderStats.mEnabled).isFalse();
        // Verify that the injector was called correctly
        Mockito.verify(mMockPropertiesWrapper).setEnabled(false);
    }

    @Test
    public void testSettingsObserver_changed() {
        Settings.Global.putString(mResolver, Settings.Global.NATIVE_BINDER_STATS, "enabled=true");
        NativeBinderStats nativeBinderStats =
                new NativeBinderStats(mContext, mMockPropertiesWrapper);
        nativeBinderStats.systemReady();

        assertThat(nativeBinderStats.mEnabled).isTrue();
        Mockito.verify(mMockPropertiesWrapper).setEnabled(true);
        Mockito.verify(mMockPropertiesWrapper)
                .setProcessSharding(NativeBinderStats.DEFAULT_PROCESS_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSpamSharding(NativeBinderStats.DEFAULT_SPAM_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setCallSharding(NativeBinderStats.DEFAULT_CALL_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemProcessSharding(NativeBinderStats.DEFAULT_SYSTEM_PROCESS_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemSpamSharding(NativeBinderStats.DEFAULT_SYSTEM_SPAM_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemCallSharding(NativeBinderStats.DEFAULT_SYSTEM_CALL_SHARDING);

        // Reset mock to clear previous interactions
        Mockito.reset(mMockPropertiesWrapper);

        Settings.Global.putString(mResolver, Settings.Global.NATIVE_BINDER_STATS, "enabled=false");
        // FakeSettingsProvider doesn't support notifications, so we need to notify manually.
        // This assumes that SettingsObserver is registered, which we cannot check because
        // registerContentObserver is final.
        nativeBinderStats.getSettingsObserverForTesting().onChange(
                false, Settings.Global.getUriFor(Settings.Global.NATIVE_BINDER_STATS), 0);

        assertThat(nativeBinderStats.mEnabled).isFalse();
        Mockito.verify(mMockPropertiesWrapper).setEnabled(false);
        Mockito.verify(mMockPropertiesWrapper)
                .setProcessSharding(NativeBinderStats.DEFAULT_PROCESS_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSpamSharding(NativeBinderStats.DEFAULT_SPAM_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setCallSharding(NativeBinderStats.DEFAULT_CALL_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemProcessSharding(NativeBinderStats.DEFAULT_SYSTEM_PROCESS_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemSpamSharding(NativeBinderStats.DEFAULT_SYSTEM_SPAM_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemCallSharding(NativeBinderStats.DEFAULT_SYSTEM_CALL_SHARDING);
    }

    @Test
    public void testSettingsObserver_allValues() {
        Settings.Global.putString(
                mResolver,
                Settings.Global.NATIVE_BINDER_STATS,
                "enabled=true,process_sharding=5,spam_sharding=1,call_sharding=2,"
                    + "system_process_sharding=1,system_spam_sharding=5,system_call_sharding=10");
        NativeBinderStats nativeBinderStats =
                new NativeBinderStats(mContext, mMockPropertiesWrapper);
        nativeBinderStats.systemReady();

        assertThat(nativeBinderStats.mEnabled).isTrue();
        assertThat(nativeBinderStats.mProcessSharding).isEqualTo(5);
        assertThat(nativeBinderStats.mSpamSharding).isEqualTo(1);
        assertThat(nativeBinderStats.mCallSharding).isEqualTo(2);
        assertThat(nativeBinderStats.mSystemProcessSharding).isEqualTo(1);
        assertThat(nativeBinderStats.mSystemSpamSharding).isEqualTo(5);
        assertThat(nativeBinderStats.mSystemCallSharding).isEqualTo(10);
        // Verify that the injector was called with required values
        Mockito.verify(mMockPropertiesWrapper).setEnabled(true);
        Mockito.verify(mMockPropertiesWrapper).setProcessSharding(5);
        Mockito.verify(mMockPropertiesWrapper).setSpamSharding(1);
        Mockito.verify(mMockPropertiesWrapper).setCallSharding(2);
        Mockito.verify(mMockPropertiesWrapper).setSystemProcessSharding(1);
        Mockito.verify(mMockPropertiesWrapper).setSystemSpamSharding(5);
        Mockito.verify(mMockPropertiesWrapper).setSystemCallSharding(10);
    }

    @Test
    public void testSettingsObserver_badInput() {
        Settings.Global.putString(mResolver, Settings.Global.NATIVE_BINDER_STATS,
                "enabled=truepxrocess_sharding=5,spam_sharding=1,call_sharding=2,"
                        + "system_process_sharding=1,system_spam_sharding=5,system_call_sharding="
                        + "10");

        NativeBinderStats nativeBinderStats =
                new NativeBinderStats(mContext, mMockPropertiesWrapper);
        nativeBinderStats.systemReady();

        // Verify that the injector was called with default values
        Mockito.verify(mMockPropertiesWrapper).setEnabled(NativeBinderStats.DEFAULT_ENABLED);
        Mockito.verify(mMockPropertiesWrapper)
                .setProcessSharding(NativeBinderStats.DEFAULT_PROCESS_SHARDING);
        // Verify that correctly got values were set
        Mockito.verify(mMockPropertiesWrapper).setSpamSharding(1);
        Mockito.verify(mMockPropertiesWrapper).setCallSharding(2);
        Mockito.verify(mMockPropertiesWrapper).setSystemProcessSharding(1);
        Mockito.verify(mMockPropertiesWrapper).setSystemSpamSharding(5);
        Mockito.verify(mMockPropertiesWrapper).setSystemCallSharding(10);

        assertThat(nativeBinderStats.mEnabled).isFalse();
    }

    @Test
    public void testSettingsObserver_triggerInputException() {
        Settings.Global.putString(
                mResolver, Settings.Global.NATIVE_BINDER_STATS, "Lorem,ipsum,enable123");

        NativeBinderStats nativeBinderStats =
                new NativeBinderStats(mContext, mMockPropertiesWrapper);
        nativeBinderStats.systemReady();

        // Verify that the injector was called with default values
        Mockito.verify(mMockPropertiesWrapper).setEnabled(NativeBinderStats.DEFAULT_ENABLED);
        Mockito.verify(mMockPropertiesWrapper)
                .setProcessSharding(NativeBinderStats.DEFAULT_PROCESS_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSpamSharding(NativeBinderStats.DEFAULT_SPAM_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setCallSharding(NativeBinderStats.DEFAULT_CALL_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemProcessSharding(NativeBinderStats.DEFAULT_SYSTEM_PROCESS_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemSpamSharding(NativeBinderStats.DEFAULT_SYSTEM_SPAM_SHARDING);
        Mockito.verify(mMockPropertiesWrapper)
                .setSystemCallSharding(NativeBinderStats.DEFAULT_SYSTEM_CALL_SHARDING);
    }
}
