/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.app.RemoteInput;
import android.testing.TestableResources;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.res.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SmartReplyConstantsTest extends SysuiTestCase {
    private TestableResources mResources;

    @Before
    public void setUp() {
        mResources = mContext.getOrCreateTestableResources();
        mResources.addOverride(
                R.integer.config_smart_replies_in_notifications_max_squeeze_remeasure_attempts, 7);
        mResources.addOverride(
                R.bool.config_smart_replies_in_notifications_edit_choices_before_sending, false);
        mResources.addOverride(
                R.integer.config_smart_replies_in_notifications_min_num_system_generated_replies,
                2);
        mResources.addOverride(
                R.integer.config_smart_replies_in_notifications_max_num_actions, -1);
    }

    private SmartReplyConstants getSmartReplyConstants() {
        return new SmartReplyConstants(mContext);
    }

    @Test
    public void testIsEnabled() {
        assertTrue(getSmartReplyConstants().isEnabled());
    }

    @Test
    public void testGetEffectiveEditChoicesBeforeSendingWithNoConfig() {
        SmartReplyConstants constants = getSmartReplyConstants();
        assertFalse(
                constants.getEffectiveEditChoicesBeforeSending(
                        RemoteInput.EDIT_CHOICES_BEFORE_SENDING_AUTO));
        assertTrue(
                constants.getEffectiveEditChoicesBeforeSending(
                        RemoteInput.EDIT_CHOICES_BEFORE_SENDING_ENABLED));
        assertFalse(
                constants.getEffectiveEditChoicesBeforeSending(
                        RemoteInput.EDIT_CHOICES_BEFORE_SENDING_DISABLED));
    }

    @Test
    public void testGetEffectiveEditChoicesBeforeSendingWithEnabledConfig() {
        mResources.addOverride(
                R.bool.config_smart_replies_in_notifications_edit_choices_before_sending, true);
        SmartReplyConstants constants = getSmartReplyConstants();
        assertTrue(
                constants.getEffectiveEditChoicesBeforeSending(
                        RemoteInput.EDIT_CHOICES_BEFORE_SENDING_AUTO));
        assertTrue(
                constants.getEffectiveEditChoicesBeforeSending(
                        RemoteInput.EDIT_CHOICES_BEFORE_SENDING_ENABLED));
        assertFalse(
                constants.getEffectiveEditChoicesBeforeSending(
                        RemoteInput.EDIT_CHOICES_BEFORE_SENDING_DISABLED));
    }
}
