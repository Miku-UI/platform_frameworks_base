/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.notification;

import static android.app.NotificationChannel.SOCIAL_MEDIA_ID;
import static android.app.NotificationManager.IMPORTANCE_HIGH;
import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.NotificationManager.IMPORTANCE_MAX;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_LOW;
import static android.media.AudioAttributes.USAGE_NOTIFICATION;
import static android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI;
import static android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
import static android.provider.Settings.System.DEFAULT_RINGTONE_URI;
import static android.service.notification.Adjustment.KEY_NOTIFICATION_RULES;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.app.Flags;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationRule;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.notification.Adjustment;
import android.service.notification.SnoozeCriterion;
import android.service.notification.StatusBarNotification;
import android.testing.TestableContext;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.UiServiceTestCase;
import com.android.server.uri.UriGrantsManagerInternal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RunWith(AndroidJUnit4.class)
public class NotificationAdjustmentExtractorTest extends UiServiceTestCase {
    @Mock
    GroupHelper mGroupHelper;
    NotificationRuleManager mRuleManager;
    @Mock
    NotificationManagerPrivate mNmPrivate;
    TestableContext mContext = spy(getContext());
    RankingConfig mRankingConfig;
    @Mock
    PackageManager mPackageManagerClient;

    NotificationAdjustmentExtractor underTest;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void before() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);

        mRuleManager = new NotificationRuleManager(mContext, mNmPrivate);
        mRuleManager.onUserAdded(mUserId);

        mContext.setMockPackageManager(mPackageManagerClient);
        when(mPackageManagerClient.getApplicationInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenAnswer((Answer<ApplicationInfo>) invocation -> {
                    Object[] args = invocation.getArguments();
                    return getApplicationInfo((String) args[0], mUid);
                });


        mRankingConfig = new TestPreferencesHelper(mContext, mPackageManagerClient,
                mock(RankingHandler.class), mock(ZenModeHelper.class), mock(PermissionHelper.class),
                mock(PermissionManager.class), mock(NotificationChannelLogger.class),
                mock(AppOpsManager.class), mock(ManagedServices.UserProfiles.class),
                mock(UriGrantsManagerInternal.class), false, mock(Clock.class), mNmPrivate);

        underTest = new NotificationAdjustmentExtractor();
        underTest.setGroupHelper(mGroupHelper);
        underTest.setConfig(mRankingConfig);
        underTest.setRuleManager(mRuleManager);
    }

    @Test
    public void testExtractsAdjustment() {
        NotificationRecord r = generateRecord();

        Bundle signals = new Bundle();
        signals.putString(Adjustment.KEY_GROUP_KEY, GroupHelper.AUTOGROUP_KEY);
        ArrayList<SnoozeCriterion> snoozeCriteria = new ArrayList<>();
        snoozeCriteria.add(new SnoozeCriterion("n", "n", "n"));
        signals.putParcelableArrayList(Adjustment.KEY_SNOOZE_CRITERIA, snoozeCriteria);
        ArrayList<String> people = new ArrayList<>();
        people.add("you");
        signals.putStringArrayList(Adjustment.KEY_PEOPLE, people);
        ArrayList<Notification.Action> smartActions = new ArrayList<>();
        smartActions.add(createAction());
        signals.putParcelableArrayList(Adjustment.KEY_CONTEXTUAL_ACTIONS, smartActions);
        Adjustment adjustment = new Adjustment("pkg", r.getKey(), signals, "", 0);
        r.addAdjustment(adjustment);

        assertFalse(r.getGroupKey().contains(GroupHelper.AUTOGROUP_KEY));
        assertFalse(Objects.equals(people, r.getPeopleOverride()));
        assertFalse(Objects.equals(snoozeCriteria, r.getSnoozeCriteria()));

        assertNull(underTest.process(r));

        assertTrue(r.getGroupKey().contains(GroupHelper.AUTOGROUP_KEY));
        assertEquals(people, r.getPeopleOverride());
        assertEquals(snoozeCriteria, r.getSnoozeCriteria());
        assertEquals(smartActions, r.getSystemGeneratedSmartActions());
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testApplySingleHighlightRuleAdjustment_noActionOverrides() {
        NotificationRule rule = createHighlightRule(100, false);
        mRuleManager.addNotificationRule(mUserId, 0, rule);

        NotificationRecord r = generateRecord();
        NotificationChannel originalChannel = r.getChannel();
        Uri originalSound = r.getSound();
        NotificationRecord.Light originalLight = r.getLight();

        Bundle signals = new Bundle();
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(rule.getId());
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, ids);
        Adjustment adjustment = new Adjustment(
                r.getSbn().getOpPkg(), r.getKey(), signals, "", mUserId);
        r.addAdjustment(adjustment);

        underTest.process(r);

        assertThat(r.getLight()).isEqualTo(originalLight);
        assertThat(r.getSound()).isEqualTo(originalSound);
        assertThat(r.getChannel()).isEqualTo(originalChannel);
        assertThat(r.getImportance()).isEqualTo(IMPORTANCE_MAX);
        assertThat(r.getMatchingRulesAdjustment().getSignals().getIntegerArrayList(
                KEY_NOTIFICATION_RULES)).containsExactly(rule.getId());
        // TODO(b/479575690): validate rule breakthrough
        verify(mNmPrivate, never()).triggerPolicyFileWrite();
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testApplySingleHighlightRuleAdjustment_actionOverrides() {
        NotificationRule rule = createHighlightRule(100, true);
        mRuleManager.addNotificationRule(mUserId, 0, rule);

        NotificationRecord r = generateRecord();
        NotificationChannel originalChannel = r.getChannel();

        Bundle signals = new Bundle();
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(rule.getId());
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, ids);
        Adjustment adjustment = new Adjustment(
                r.getSbn().getOpPkg(), r.getKey(), signals, "", mUserId);
        r.addAdjustment(adjustment);

        underTest.process(r);

        assertThat(r.getLight().color).isEqualTo(rule.getAction().getLightColorOverride());
        assertThat(r.getSound()).isEqualTo(rule.getAction().getSoundHapticOverride());
        assertThat(r.getChannel()).isEqualTo(originalChannel);
        assertThat(r.getImportance()).isEqualTo(IMPORTANCE_MAX);
        assertThat(r.getMatchingRulesAdjustment().getSignals().getIntegerArrayList(
                KEY_NOTIFICATION_RULES)).containsExactly(rule.getId());
        // TODO(b/479575690): validate rule breakthrough
        verify(mNmPrivate, never()).triggerPolicyFileWrite();
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testApplySingleLowRuleAdjustment() {
        NotificationRule rule = createLowRule(false);
        mRuleManager.addNotificationRule(mUserId, 0, rule);

        NotificationRecord r = generateRecord();
        NotificationChannel originalChannel = r.getChannel();
        Uri originalSound = r.getSound();
        NotificationRecord.Light originalLight = r.getLight();

        Bundle signals = new Bundle();
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(rule.getId());
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, ids);
        Adjustment adjustment = new Adjustment(
                r.getSbn().getOpPkg(), r.getKey(), signals, "", mUserId);
        r.addAdjustment(adjustment);

        underTest.process(r);

        assertThat(r.getLight()).isEqualTo(originalLight);
        assertThat(r.getSound()).isEqualTo(originalSound);
        assertThat(r.getChannel()).isEqualTo(originalChannel);
        assertThat(r.getImportance()).isEqualTo(IMPORTANCE_LOW);
        assertThat(r.getMatchingRulesAdjustment().getSignals().getIntegerArrayList(
                KEY_NOTIFICATION_RULES)).containsExactly(rule.getId());
        // TODO(b/479575690): validate rule breakthrough
        verify(mNmPrivate, never()).triggerPolicyFileWrite();
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testApplySingleBundleRuleAdjustment() {
        NotificationRule rule = createBundleRule();
        mRuleManager.addNotificationRule(mUserId, 0, rule);

        NotificationRecord r = generateRecord();
        Uri originalSound = r.getSound();
        NotificationRecord.Light originalLight = r.getLight();

        Bundle signals = new Bundle();
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(rule.getId());
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, ids);
        Adjustment adjustment = new Adjustment(
                r.getSbn().getOpPkg(), r.getKey(), signals, "", mUserId);
        r.addAdjustment(adjustment);

        underTest.process(r);

        assertThat(r.getLight()).isEqualTo(originalLight);
        assertThat(r.getSound()).isEqualTo(originalSound);
        assertThat(r.getChannel().getId()).isEqualTo(
                NotificationChannel.getChannelIdForBundleType(rule.getId()));
        assertThat(r.getChannel().getName()).isEqualTo(
                rule.getAction().getDynamicBundleName());
        assertThat(r.getChannel().getEmoji()).isEqualTo(
                rule.getAction().getDynamicBundleEmojiIcon());
        assertThat(r.getImportance()).isEqualTo(IMPORTANCE_LOW);
        assertThat(r.getMatchingRulesAdjustment().getSignals().getIntegerArrayList(
                KEY_NOTIFICATION_RULES)).containsExactly(rule.getId());
        // TODO(b/479575690): validate rule breakthrough
        verify(mNmPrivate, times(1)).triggerPolicyFileWrite();
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testApplyMultipleHighlightRuleAdjustments() {
        int defaultLightOn = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_defaultNotificationLedOn);
        int defaultLightOff = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_defaultNotificationLedOff);

        NotificationRule rule = createHighlightRule(100, false);
        NotificationRule rule2 = createHighlightRule(101, true);
        NotificationRule rule3 = createHighlightRule(102, false);
        mRuleManager.addNotificationRule(mUserId, 0, rule);
        mRuleManager.addNotificationRule(mUserId, 1, rule2);
        mRuleManager.addNotificationRule(mUserId, 2, rule3);

        NotificationRecord r = generateRecord();
        NotificationChannel originalChannel = r.getChannel();

        Bundle signals = new Bundle();
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(rule.getId());
        ids.add(rule2.getId());
        ids.add(rule3.getId());
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, ids);
        Adjustment adjustment = new Adjustment(
                r.getSbn().getOpPkg(), r.getKey(), signals, "", mUserId);
        r.addAdjustment(adjustment);

        underTest.process(r);

        assertThat(r.getLight()).isEqualTo(new NotificationRecord.Light(
                rule2.getAction().getLightColorOverride(),
                defaultLightOn, defaultLightOff));
        assertThat(r.getSound()).isEqualTo(rule2.getAction().getSoundHapticOverride());
        assertThat(r.getChannel()).isEqualTo(originalChannel);
        assertThat(r.getImportance()).isEqualTo(IMPORTANCE_MAX);
        assertThat(r.getMatchingRulesAdjustment().getSignals().getIntegerArrayList(
                KEY_NOTIFICATION_RULES)).containsExactly(
                        rule.getId(), rule2.getId(), rule3.getId());
        // TODO(b/479575690): validate rule breakthrough
        verify(mNmPrivate, never()).triggerPolicyFileWrite();
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testApplyMultipleHighlightRuleAdjustments_conflictingOverrides() {
        int defaultLightOn = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_defaultNotificationLedOn);
        int defaultLightOff = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_defaultNotificationLedOff);

        NotificationRule rule = createHighlightRule(100, true);
        NotificationRule.Action a = new NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT)
                .setLightColorOverride(Color.parseColor("blue"))
                .setModeBreakthroughIds(List.of("manual"))
                .setSoundHapticOverride(DEFAULT_ALARM_ALERT_URI)
                .build();
        NotificationRule rule2 = new NotificationRule.Builder(101, a)
                .build();
        mRuleManager.addNotificationRule(mUserId, 0, rule);
        mRuleManager.addNotificationRule(mUserId, 1, rule2);

        NotificationRecord r = generateRecord();
        NotificationChannel originalChannel = r.getChannel();

        Bundle signals = new Bundle();
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(rule.getId());
        ids.add(rule2.getId());
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, ids);
        Adjustment adjustment = new Adjustment(
                r.getSbn().getOpPkg(), r.getKey(), signals, "", mUserId);
        r.addAdjustment(adjustment);

        underTest.process(r);

        assertThat(r.getLight()).isEqualTo(new NotificationRecord.Light(
                rule.getAction().getLightColorOverride(),
                defaultLightOn, defaultLightOff));
        assertThat(r.getSound()).isEqualTo(rule.getAction().getSoundHapticOverride());
        assertThat(r.getChannel()).isEqualTo(originalChannel);
        assertThat(r.getImportance()).isEqualTo(IMPORTANCE_MAX);
        assertThat(r.getMatchingRulesAdjustment().getSignals().getIntegerArrayList(
                KEY_NOTIFICATION_RULES)).containsExactly(rule.getId(), rule2.getId());
        // TODO(b/479575690): validate rule breakthrough
        verify(mNmPrivate, never()).triggerPolicyFileWrite();
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testApplyMultipleMixedRuleAdjustments() {
        NotificationRule rule = createHighlightRule(100, false);
        NotificationRule rule2 = createLowRule(true);
        NotificationRule rule3 = createBundleRule();
        mRuleManager.addNotificationRule(mUserId, 0, rule);
        mRuleManager.addNotificationRule(mUserId, 1, rule2);
        mRuleManager.addNotificationRule(mUserId, 2, rule3);

        NotificationRecord r = generateRecord();
        Uri originalSound = r.getSound();
        NotificationRecord.Light originalLight = r.getLight();
        NotificationChannel originalChannel = r.getChannel();

        Bundle signals = new Bundle();
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(rule.getId());
        ids.add(rule2.getId());
        ids.add(rule3.getId());
        signals.putIntegerArrayList(KEY_NOTIFICATION_RULES, ids);
        Adjustment adjustment = new Adjustment(
                r.getSbn().getOpPkg(), r.getKey(), signals, "", mUserId);
        r.addAdjustment(adjustment);

        underTest.process(r);

        // Highlight rules trump low/bundle rules, so the entire low/bundle rules should be ignored
        assertThat(r.getLight()).isEqualTo(originalLight);
        assertThat(r.getSound()).isEqualTo(originalSound);
        assertThat(r.getChannel()).isEqualTo(originalChannel);
        assertThat(r.getImportance()).isEqualTo(IMPORTANCE_MAX);
        assertThat(r.getMatchingRulesAdjustment().getSignals().getIntegerArrayList(
                KEY_NOTIFICATION_RULES)).containsExactly(
                        rule.getId(), rule2.getId(), rule3.getId());
        // TODO(b/479575690): validate rule breakthrough
        verify(mNmPrivate, never()).triggerPolicyFileWrite();
    }

    @Test
    public void testExtractsAdjustments() {
        NotificationRecord r = generateRecord();

        Bundle pSignals = new Bundle();
        ArrayList<String> people = new ArrayList<>();
        people.add("you");
        pSignals.putStringArrayList(Adjustment.KEY_PEOPLE, people);
        Adjustment pAdjustment = new Adjustment("pkg", r.getKey(), pSignals, "", 0);
        r.addAdjustment(pAdjustment);

        Bundle sSignals = new Bundle();
        ArrayList<SnoozeCriterion> snoozeCriteria = new ArrayList<>();
        snoozeCriteria.add(new SnoozeCriterion("n", "n", "n"));
        sSignals.putParcelableArrayList(Adjustment.KEY_SNOOZE_CRITERIA, snoozeCriteria);
        Adjustment sAdjustment = new Adjustment("pkg", r.getKey(), sSignals, "", 0);
        r.addAdjustment(sAdjustment);

        Bundle gSignals = new Bundle();
        gSignals.putString(Adjustment.KEY_GROUP_KEY, GroupHelper.AUTOGROUP_KEY);
        Adjustment gAdjustment = new Adjustment("pkg", r.getKey(), gSignals, "", 0);
        r.addAdjustment(gAdjustment);

        assertFalse(r.getGroupKey().contains(GroupHelper.AUTOGROUP_KEY));
        assertFalse(Objects.equals(people, r.getPeopleOverride()));
        assertFalse(Objects.equals(snoozeCriteria, r.getSnoozeCriteria()));

        assertNull(underTest.process(r));

        assertTrue(r.getGroupKey().contains(GroupHelper.AUTOGROUP_KEY));
        assertEquals(people, r.getPeopleOverride());
        assertEquals(snoozeCriteria, r.getSnoozeCriteria());
    }

    @Test
    public void testClassificationAdjustments_noisy_notImmediately() {
        NotificationChannel social = new NotificationChannel(
                SOCIAL_MEDIA_ID, "social", IMPORTANCE_LOW);

        NotificationRecord r = generateRecord();
        r.setAudiblyAlerted(true);

        Bundle classificationAdj = new Bundle();
        classificationAdj.putParcelable(Adjustment.KEY_TYPE, social);
        Adjustment adjustment = new Adjustment("pkg", r.getKey(), classificationAdj, "", 0);
        r.addAdjustment(adjustment);

        RankingReconsideration regroupingTask = underTest.process(r);
        assertThat(regroupingTask).isNotNull();
        regroupingTask.applyChangesLocked(r);
        assertThat(r.getChannel()).isNotEqualTo(social);
        verify(mGroupHelper, times(0)).onChannelUpdated(r);
    }

    @Test
    public void testClassificationAdjustments_noisy_okAfterDelay() {
        NotificationChannel social = new NotificationChannel(
                SOCIAL_MEDIA_ID, "social", IMPORTANCE_LOW);
        NotificationRecord r = generateRecord();
        r.setAudiblyAlerted(true);

        Bundle classificationAdj = new Bundle();
        classificationAdj.putParcelable(Adjustment.KEY_TYPE, social);
        Adjustment adjustment = new Adjustment("pkg", r.getKey(), classificationAdj, "", 0);
        r.addAdjustment(adjustment);

        RankingReconsideration regroupingTask = underTest.process(r);
        assertThat(regroupingTask).isNotNull();

        underTest.mInjectedTimeMs = new NotificationAdjustmentExtractor.InjectedTime(
                System.currentTimeMillis() + NotificationAdjustmentExtractor.HANG_TIME_MS);

        regroupingTask.applyChangesLocked(r);
        assertThat(r.getChannel()).isEqualTo(social);
        verify(mGroupHelper, times(1)).onChannelUpdated(r);
    }

    @Test
    public void testClassificationAdjustments_triggerRegrouping_whenSilent() {
        NotificationChannel social = new NotificationChannel(
                SOCIAL_MEDIA_ID, "social", IMPORTANCE_LOW);

        NotificationRecord r = generateRecord();

        Bundle classificationAdj = new Bundle();
        classificationAdj.putParcelable(Adjustment.KEY_TYPE, social);
        Adjustment adjustment = new Adjustment("pkg", r.getKey(), classificationAdj, "", 0);
        r.addAdjustment(adjustment);

        RankingReconsideration regroupingTask = underTest.process(r);
        assertThat(regroupingTask).isNotNull();
        regroupingTask.applyChangesLocked(r);
        assertThat(r.getChannel()).isEqualTo(social);
        verify(mGroupHelper, times(1)).onChannelUpdated(r);
    }

    @Test
    public void testClassificationAdjustments_unclassifyTriggersUnbundling() {
        NotificationRecord r = generateRecord();
        r.setHadGroupSummaryWhenUnclassified(true);

        Bundle classificationAdj = new Bundle();
        classificationAdj.putParcelable(Adjustment.KEY_UNCLASSIFY, mock(NotificationChannel.class));
        Adjustment adjustment = new Adjustment("pkg", r.getKey(), classificationAdj, "", 0);
        r.addAdjustment(adjustment);

        RankingReconsideration regroupingTask = underTest.process(r);
        assertThat(regroupingTask).isNotNull();
        regroupingTask.applyChangesLocked(r);
        verify(mGroupHelper, times(1)).onNotificationUnbundled(r, true);

        // make sure that the group summary boolean is passed through correctly
        r.setHadGroupSummaryWhenUnclassified(false);
        classificationAdj.putParcelable(Adjustment.KEY_UNCLASSIFY, mock(NotificationChannel.class));
        adjustment = new Adjustment("pkg", r.getKey(), classificationAdj, "", 0);
        r.addAdjustment(adjustment);
        RankingReconsideration regroupingTask2 = underTest.process(r);
        assertThat(regroupingTask2).isNotNull();
        regroupingTask2.applyChangesLocked(r);
        verify(mGroupHelper, times(1)).onNotificationUnbundled(r, false);
    }

    private NotificationRecord generateRecord() {
        NotificationChannel channel = new NotificationChannel("a", "a", IMPORTANCE_HIGH);
        channel.setSound(DEFAULT_RINGTONE_URI,
                new AudioAttributes.Builder().setUsage(USAGE_NOTIFICATION).build());
        final Notification.Builder builder = new Notification.Builder(getContext())
                .setContentTitle("foo")
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Notification n = builder.build();
        StatusBarNotification sbn = new StatusBarNotification(mPkg, mPkg, 0, "", mUid,
                0, n, UserHandle.ALL, null, System.currentTimeMillis());
       return new NotificationRecord(getContext(), sbn, channel);
    }

    private Notification.Action createAction() {
        return new Notification.Action.Builder(
                Icon.createWithResource(getContext(), android.R.drawable.sym_def_app_icon),
                "action",
                PendingIntent.getBroadcast(getContext(), 0, new Intent("Action"),
                    PendingIntent.FLAG_IMMUTABLE)).build();
    }

    private NotificationRule createHighlightRule(int id, boolean withOverrides) {
        NotificationRule.Action a = new NotificationRule.Action.Builder(PRIMARY_ACTION_HIGHLIGHT)
                .setLightColorOverride(withOverrides ? Color.parseColor("green") : 0)
                .setModeBreakthroughIds(withOverrides ? List.of("sleep") : new ArrayList<>())
                .setSoundHapticOverride(withOverrides ? DEFAULT_NOTIFICATION_URI : null)
                .build();
        return new NotificationRule.Builder(id, a)
                .build();
    }

    private NotificationRule createLowRule(boolean withOverrides) {
        NotificationRule.Action a = new NotificationRule.Action.Builder(PRIMARY_ACTION_LOW)
                .setModeBreakthroughIds(withOverrides ? List.of("sleep") : new ArrayList<>())
                .build();
        return new NotificationRule.Builder(withOverrides ? 111 : 151, a)
                .build();
    }

    private NotificationRule createBundleRule() {
        NotificationRule.Action a = new NotificationRule.Action.Builder(PRIMARY_ACTION_BUNDLE)
                .setDynamicBundleName("box!")
                .setDynamicBundleEmojiIcon("\uD83D\uDCE6")
                .build();
        return new NotificationRule.Builder(130, a)
                .build();
    }
}
