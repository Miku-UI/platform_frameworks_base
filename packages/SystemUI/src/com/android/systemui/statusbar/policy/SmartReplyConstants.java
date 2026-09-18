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

import android.app.RemoteInput;
import android.content.Context;
import android.content.res.Resources;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.res.R;

import javax.inject.Inject;

@SysUISingleton
public final class SmartReplyConstants {

    private final boolean mEnabled = true;
    private final boolean mRequiresTargetingP = false;
    private final int mMaxSqueezeRemeasureAttempts;
    private final boolean mEditChoicesBeforeSending;
    private final boolean mShowInHeadsUp = true;
    private final int mMinNumSystemGeneratedReplies;
    private final int mMaxNumActions;
    private final long mOnClickInitDelay;

    private final Context mContext;

    @Inject
    public SmartReplyConstants(Context context) {
        mContext = context;
        final Resources resources = mContext.getResources();
        mMaxSqueezeRemeasureAttempts = resources.getInteger(
                R.integer.config_smart_replies_in_notifications_max_squeeze_remeasure_attempts);
        mEditChoicesBeforeSending = resources.getBoolean(
                R.bool.config_smart_replies_in_notifications_edit_choices_before_sending);
        mMinNumSystemGeneratedReplies = resources.getInteger(
                R.integer.config_smart_replies_in_notifications_min_num_system_generated_replies);
        mMaxNumActions = resources.getInteger(
                R.integer.config_smart_replies_in_notifications_max_num_actions);
        mOnClickInitDelay = resources.getInteger(
                R.integer.config_smart_replies_in_notifications_onclick_init_delay);
    }

    /** Returns whether smart replies in notifications are enabled. */
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Returns whether smart replies in notifications should be disabled when the app targets a
     * version of Android older than P.
     */
    public boolean requiresTargetingP() {
        return mRequiresTargetingP;
    }

    /**
     * Returns the maximum number of times {@link SmartReplyView#onMeasure(int, int)} will try to
     * find a better (narrower) line-break for a double-line smart reply button.
     */
    public int getMaxSqueezeRemeasureAttempts() {
        return mMaxSqueezeRemeasureAttempts;
    }

    /**
     * Returns whether by tapping on a choice should let the user edit the input before it
     * is sent to the app.
     *
     * @param remoteInputEditChoicesBeforeSending The value from
     *         {@link RemoteInput#getEditChoicesBeforeSending()}
     */
    public boolean getEffectiveEditChoicesBeforeSending(
            @RemoteInput.EditChoicesBeforeSending int remoteInputEditChoicesBeforeSending) {
        switch (remoteInputEditChoicesBeforeSending) {
            case RemoteInput.EDIT_CHOICES_BEFORE_SENDING_DISABLED:
                return false;
            case RemoteInput.EDIT_CHOICES_BEFORE_SENDING_ENABLED:
                return true;
            case RemoteInput.EDIT_CHOICES_BEFORE_SENDING_AUTO:
            default:
                return mEditChoicesBeforeSending;
        }
    }

    /**
     * Returns whether smart suggestions should be enabled in heads-up notifications.
     */
    public boolean getShowInHeadsUp() {
        return mShowInHeadsUp;
    }

    /**
     * Returns the minimum number of system generated replies to show in a notification.
     * If we cannot show at least this many system generated replies we should show none.
     */
    public int getMinNumSystemGeneratedReplies() {
        return mMinNumSystemGeneratedReplies;
    }

    /**
     * Returns the maximum number smart actions to show in a notification, or -1 if there shouldn't
     * be a limit.
     */
    public int getMaxNumActions() {
        return mMaxNumActions;
    }

    /**
     * Returns the amount of time (ms) before smart suggestions are clickable, since the suggestions
     * were added.
     */
    public long getOnClickInitDelay() {
        return mOnClickInitDelay;
    }
}
