/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.security.authenticationpolicy.agent;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.test.mock.MockContentResolver;
import android.testing.AndroidTestingRunner;

import androidx.test.core.app.ApplicationProvider;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.server.security.authenticationpolicy.agent.AgentSessionMap.Key;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class AgentSessionMapTest {

    private static final int USER_ID = 10;
    private static final String SETTINGS_KEY = AgentSessionMap.SETTINGS_KEY;

    private MockContentResolver mContentResolver;
    private Context mContext;
    private AgentSessionMap mAgentSessionMap;

    @Before
    public void setUp() {
        FakeSettingsProvider.clearSettingsProvider();
        mContentResolver = new MockContentResolver();
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        mContext = new TestContext(ApplicationProvider.getApplicationContext(), mContentResolver);
    }

    @After
    public void tearDown() {
        FakeSettingsProvider.clearSettingsProvider();
    }

    @Test
    public void testConstructor_clearsSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        assertThat(getSetting()).isEmpty();
    }

    @Test
    public void testPut_allowedSession_doesNotUpdateSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.authorized(USER_ID));

        assertThat(getSetting()).isEmpty();
    }

    @Test
    public void testPut_notAllowedSession_updatesSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID));

        assertThat(getSetting()).containsExactly("remote_1");
    }

    @Test
    public void testPut_multipleSessions_updatesSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID));
        mAgentSessionMap.put(Key.ofRemote(2), AgentSession.authorized(USER_ID));
        mAgentSessionMap.put(Key.ofRemote(3), AgentSession.notAuthorized(USER_ID));

        assertThat(getSetting()).containsExactly("remote_1", "remote_3");
    }

    @Test
    public void testRemove_updatesSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID));
        mAgentSessionMap.put(Key.ofRemote(2), AgentSession.notAuthorized(USER_ID));

        mAgentSessionMap.remove(Key.ofRemote(1));
        assertThat(getSetting()).containsExactly("remote_2");
    }

    @Test
    public void testClear_updatesSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID));

        mAgentSessionMap.clear();
        assertThat(getSetting()).isEmpty();
    }

    @Test
    public void testGet_returnsCorrectSession() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        AgentSession session = AgentSession.authorized(USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), session);
        mAgentSessionMap.put(Key.ofRemote(2), AgentSession.authorized(USER_ID));

        assertThat(mAgentSessionMap.get(Key.ofRemote(1))).isEqualTo(session);
    }

    @Test
    public void testPut_overwriteExistingSession_updatesSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID));
        assertThat(getSetting()).containsExactly("remote_1");

        // Overwrite with authorized
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.authorized(USER_ID));
        assertThat(getSetting()).isEmpty();

        // Overwrite back to not authorized
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID));
        assertThat(getSetting()).containsExactly("remote_1");
    }

    @Test
    public void testMultipleUsers_separateSettings() {
        int otherUser = USER_ID + 1;
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        AgentSessionMap otherMap = new AgentSessionMap(mContext, otherUser);

        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID));
        otherMap.put(Key.ofRemote(1), AgentSession.notAuthorized(otherUser));

        assertThat(getSetting()).containsExactly("remote_1");
        assertThat(getSetting(otherUser)).containsExactly("remote_1");
    }

    @Test
    public void testPut_wrongUserId_doesNotUpdateSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID + 1));

        assertThat(getSetting()).isEmpty();
    }

    @Test
    public void testRemove_nonExistentKey_updatesSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID));
        assertThat(getSetting()).containsExactly("remote_1");

        mAgentSessionMap.remove(Key.ofRemote(2)); // non-existent
        assertThat(getSetting()).containsExactly("remote_1");
    }

    @Test
    public void testAuthorizeAll_updatesSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID));
        mAgentSessionMap.put(Key.ofRemote(2), AgentSession.notAuthorized(USER_ID));
        assertThat(getSetting()).containsExactly("remote_1", "remote_2");

        mAgentSessionMap.authorizeAll(USER_ID);

        assertThat(mAgentSessionMap.get(Key.ofRemote(1)).isAllowed()).isTrue();
        assertThat(mAgentSessionMap.get(Key.ofRemote(2)).isAllowed()).isTrue();
        assertThat(getSetting()).isEmpty();
    }

    @Test
    public void testAuthorizeIfPresent_updatesSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.notAuthorized(USER_ID));
        mAgentSessionMap.put(Key.ofRemote(2), AgentSession.notAuthorized(USER_ID));
        assertThat(getSetting()).containsExactly("remote_1", "remote_2");

        mAgentSessionMap.authorizeIfPresent(USER_ID, Key.ofRemote(1));

        assertThat(mAgentSessionMap.get(Key.ofRemote(1)).isAllowed()).isTrue();
        assertThat(mAgentSessionMap.get(Key.ofRemote(2)).isAllowed()).isFalse();
        assertThat(getSetting()).containsExactly("remote_2");
    }

    @Test
    public void testRevokeIfPresent_updatesSetting() {
        mAgentSessionMap = new AgentSessionMap(mContext, USER_ID);
        mAgentSessionMap.put(Key.ofRemote(1), AgentSession.authorized(USER_ID));
        mAgentSessionMap.put(Key.ofRemote(2), AgentSession.authorized(USER_ID));
        assertThat(getSetting()).isEmpty();

        mAgentSessionMap.revokeIfPresent(USER_ID, Key.ofRemote(1));

        assertThat(mAgentSessionMap.get(Key.ofRemote(1)).isAllowed()).isFalse();
        assertThat(mAgentSessionMap.get(Key.ofRemote(2)).isAllowed()).isTrue();
        assertThat(getSetting()).containsExactly("remote_1");
    }

    private List<String> getSetting() {
        return getSetting(USER_ID);
    }

    private List<String> getSetting(int userId) {
        String setting = Settings.Secure.getStringForUser(mContentResolver, SETTINGS_KEY, userId);
        if (setting == null || setting.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(setting.split(","));
    }

    private static class TestContext extends ContextWrapper {
        private final MockContentResolver mContentResolver;

        TestContext(Context base, MockContentResolver contentResolver) {
            super(base);
            mContentResolver = contentResolver;
        }

        @Override
        public ContentResolver getContentResolver() {
            return mContentResolver;
        }
    }
}
