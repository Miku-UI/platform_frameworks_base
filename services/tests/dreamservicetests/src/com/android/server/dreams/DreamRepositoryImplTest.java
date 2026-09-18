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

package com.android.server.dreams;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.provider.Settings;
import android.service.dreams.DreamItem;
import android.testing.TestableContext;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DreamRepositoryImplTest {

    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public final TestableContext mContext =
            new TestableContext(InstrumentationRegistry.getInstrumentation().getContext());

    @Mock private DreamMetadataProvider mMetadataProvider;

    private DreamRepositoryImpl mRepository;

    private static final int USER_ID = 10;
    private static final ComponentName COMPONENT_1 =
            ComponentName.unflattenFromString("com.example/.Dream1");
    private static final ComponentName COMPONENT_2 =
            ComponentName.unflattenFromString("com.example/.Dream2");

    @Before
    public void setUp() {
        mRepository = new DreamRepositoryImpl(mContext, mMetadataProvider);
    }

    @Test
    public void testGetDreamComponentsForUser() {
        String componentsString =
                COMPONENT_1.flattenToString() + "," + COMPONENT_2.flattenToString();
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_COMPONENTS,
                componentsString,
                USER_ID);

        ComponentName[] components = mRepository.getDreamComponentsForUser(USER_ID);
        assertThat(components).hasLength(2);
        assertThat(components[0]).isEqualTo(COMPONENT_1);
        assertThat(components[1]).isEqualTo(COMPONENT_2);
    }

    @Test
    public void testGetDreamComponentsForUser_empty() {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(), Settings.Secure.SCREENSAVER_COMPONENTS, "", USER_ID);

        ComponentName[] components = mRepository.getDreamComponentsForUser(USER_ID);
        assertThat(components).isEmpty();
    }

    @Test
    public void testGetDefaultDreamComponentForUser() {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_DEFAULT_COMPONENT,
                COMPONENT_1.flattenToString(),
                USER_ID);

        ComponentName component = mRepository.getDefaultDreamComponentForUser(USER_ID);
        assertThat(component).isEqualTo(COMPONENT_1);
    }

    @Test
    public void testGetDefaultDreamComponentForUser_null() {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_DEFAULT_COMPONENT,
                null,
                USER_ID);

        ComponentName component = mRepository.getDefaultDreamComponentForUser(USER_ID);
        assertThat(component).isNull();
    }

    @Test
    public void testGetActiveDreamComponentForUser() {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVE_COMPONENT,
                COMPONENT_1.flattenToString(),
                USER_ID);

        ComponentName component = mRepository.getActiveDreamComponentForUser(USER_ID);
        assertThat(component).isEqualTo(COMPONENT_1);
    }

    @Test
    public void testGetActiveDreamComponentForUser_null() {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVE_COMPONENT,
                null,
                USER_ID);

        ComponentName component = mRepository.getActiveDreamComponentForUser(USER_ID);
        assertThat(component).isNull();
    }

    @Test
    public void testGetDreamItem() {
        DreamItem item = new DreamItem.Builder(COMPONENT_1).build();
        when(mMetadataProvider.getDreamItem(COMPONENT_1)).thenReturn(item);

        Optional<DreamItem> result = mRepository.getDreamItem(COMPONENT_1);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(item);
    }

    @Test
    public void testGetDreamItem_null() {
        when(mMetadataProvider.getDreamItem(COMPONENT_1)).thenReturn(null);

        Optional<DreamItem> result = mRepository.getDreamItem(COMPONENT_1);
        assertThat(result.isPresent()).isFalse();
    }
}
