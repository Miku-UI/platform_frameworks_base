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
 * distributed under the License is distriZenbuted on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.notification;

import static android.app.AutomaticZenRule.TYPE_BEDTIME;
import static android.app.Flags.FLAG_BACKUP_RESTORE_LOGGING;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK;
import static android.app.NotificationManager.Policy.suppressedEffectsToString;
import static android.app.backup.NotificationLoggingConstants.DATA_TYPE_ZEN_RULES;
import static android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
import static android.provider.Settings.Global.ZEN_MODE_OFF;
import static android.service.notification.Condition.SOURCE_USER_ACTION;
import static android.service.notification.Condition.STATE_FALSE;
import static android.service.notification.Condition.STATE_TRUE;
import static android.service.notification.NotificationListenerService.SUPPRESSED_EFFECT_SCREEN_ON;
import static android.service.notification.ZenModeConfig.XML_VERSION_MODES_API;
import static android.service.notification.ZenModeConfig.XML_VERSION_MODES_UI;
import static android.service.notification.ZenModeConfig.ZEN_TAG;
import static android.service.notification.ZenModeConfig.ZenRule.OVERRIDE_DEACTIVATE;
import static android.service.notification.ZenModeConfig.ZenRule.OVERRIDE_NONE;
import static android.service.notification.ZenPolicy.CONVERSATION_SENDERS_ANYONE;
import static android.service.notification.ZenPolicy.CONVERSATION_SENDERS_IMPORTANT;
import static android.service.notification.ZenPolicy.PEOPLE_TYPE_ANYONE;
import static android.service.notification.ZenPolicy.PEOPLE_TYPE_CONTACTS;
import static android.service.notification.ZenPolicy.PEOPLE_TYPE_STARRED;
import static android.service.notification.ZenPolicy.STATE_ALLOW;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.app.AutomaticZenRule;
import android.app.Flags;
import android.app.NotificationManager.Policy;
import android.app.backup.BackupRestoreEventLogger;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Parcel;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.notification.Condition;
import android.service.notification.ZenDeviceEffects;
import android.service.notification.ZenModeConfig;
import android.service.notification.ZenModeConfig.EventInfo;
import android.service.notification.ZenPolicy;
import android.util.Xml;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.UiServiceTestCase;

import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParserException;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;

@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
public class ZenModeConfigTest extends UiServiceTestCase {

    private final String NAME = "name";
    private final ComponentName OWNER = new ComponentName("pkg", "cls");
    private final ComponentName CONFIG_ACTIVITY = new ComponentName("pkg", "act");
    private final ZenPolicy POLICY = new ZenPolicy.Builder().allowAlarms(true).build();
    private final Uri CONDITION_ID = new Uri.Builder().scheme("scheme")
            .authority("authority")
            .appendPath("path")
            .appendPath("test")
            .build();

    private final Condition CONDITION = new Condition(CONDITION_ID, "", Condition.STATE_TRUE);
    private final String TRIGGER_DESC = "Every Night, 10pm to 6am";
    private final int TYPE = TYPE_BEDTIME;
    private final boolean ALLOW_MANUAL = true;
    private final String ICON_RES_NAME = "icon_res";
    private final int INTERRUPTION_FILTER = Settings.Global.ZEN_MODE_ALARMS;
    private final boolean ENABLED = true;
    private final int CREATION_TIME = 123;

    @Mock
    PackageManager mPm;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(
            SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(FLAG_BACKUP_RESTORE_LOGGING);
    }

    public ZenModeConfigTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public final void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext.setMockPackageManager(mPm);
    }

    @Test
    public void testPriorityOnlyMutingAllNotifications() {
        ZenModeConfig config = getMutedRingerConfig();
        assertTrue(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));

        config.manualRule.zenPolicy = new ZenPolicy.Builder(config.manualRule.zenPolicy)
                .allowReminders(true)
                .build();
        assertFalse(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));
        config.manualRule.zenPolicy = new ZenPolicy.Builder(config.manualRule.zenPolicy)
                .allowReminders(false)
                .build();
        assertTrue(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));

        config.hasPriorityChannels = true;
        assertTrue(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));

        config.manualRule.zenPolicy = new ZenPolicy.Builder(config.manualRule.zenPolicy)
                .allowPriorityChannels(true)
                .build();

        assertFalse(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));

        config.hasPriorityChannels = false;
        config.manualRule.zenPolicy = new ZenPolicy.Builder(config.manualRule.zenPolicy)
                .allowPriorityChannels(false)
                .build();

        assertTrue(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));
    }

    @Test
    public void testZenPolicyNothingSetToNotificationPolicy() {
        ZenModeConfig config = getCustomConfig();
        ZenPolicy zenPolicy = new ZenPolicy.Builder().build();
        assertEquals(config.toNotificationPolicy(), config.toNotificationPolicy(zenPolicy));
    }

    @Test
    public void testZenPolicyToNotificationPolicy_classic() {
        ZenModeConfig config = getMutedAllConfig();

        // Explicitly allow conversations from priority senders to make sure that goes through
        ZenPolicy zenPolicy = new ZenPolicy.Builder()
                .allowAlarms(true)
                .allowReminders(true)
                .allowEvents(true)
                .allowConversations(CONVERSATION_SENDERS_IMPORTANT)
                .showLights(false)
                .showInAmbientDisplay(false)
                .build();

        Policy originalPolicy = config.toNotificationPolicy();
        int priorityCategories = originalPolicy.priorityCategories;
        int priorityCallSenders = originalPolicy.priorityCallSenders;
        int priorityMessageSenders = originalPolicy.priorityMessageSenders;
        int priorityConversationsSenders = CONVERSATION_SENDERS_IMPORTANT;
        int suppressedVisualEffects = originalPolicy.suppressedVisualEffects;
        priorityCategories |= Policy.PRIORITY_CATEGORY_ALARMS;
        priorityCategories |= Policy.PRIORITY_CATEGORY_REMINDERS;
        priorityCategories |= Policy.PRIORITY_CATEGORY_EVENTS;
        priorityCategories |= Policy.PRIORITY_CATEGORY_CONVERSATIONS;
        suppressedVisualEffects |= SUPPRESSED_EFFECT_LIGHTS;
        suppressedVisualEffects |= SUPPRESSED_EFFECT_AMBIENT;

        Policy expectedPolicy = new Policy(priorityCategories, priorityCallSenders,
                priorityMessageSenders, suppressedVisualEffects, 0, priorityConversationsSenders);
        assertEquals(expectedPolicy, config.toNotificationPolicy(zenPolicy));
    }

    @Test
    public void testZenPolicyToNotificationPolicy() {
        ZenModeConfig config = getMutedAllConfig();

        // Explicitly allow conversations from priority senders to make sure that goes through
        // Explicitly disallow channels to make sure that goes through, too
        ZenPolicy zenPolicy = new ZenPolicy.Builder()
                .allowAlarms(true)
                .allowReminders(true)
                .allowEvents(true)
                .allowConversations(CONVERSATION_SENDERS_IMPORTANT)
                .showLights(false)
                .showInAmbientDisplay(false)
                .allowPriorityChannels(false)
                .build();

        Policy originalPolicy = config.toNotificationPolicy();
        int priorityCategories = originalPolicy.priorityCategories;
        int priorityCallSenders = originalPolicy.priorityCallSenders;
        int priorityMessageSenders = originalPolicy.priorityMessageSenders;
        int priorityConversationsSenders = CONVERSATION_SENDERS_IMPORTANT;
        int suppressedVisualEffects = originalPolicy.suppressedVisualEffects;
        priorityCategories |= Policy.PRIORITY_CATEGORY_ALARMS;
        priorityCategories |= Policy.PRIORITY_CATEGORY_REMINDERS;
        priorityCategories |= Policy.PRIORITY_CATEGORY_EVENTS;
        priorityCategories |= Policy.PRIORITY_CATEGORY_CONVERSATIONS;
        suppressedVisualEffects |= SUPPRESSED_EFFECT_LIGHTS;
        suppressedVisualEffects |= SUPPRESSED_EFFECT_AMBIENT;

        Policy expectedPolicy = new Policy(priorityCategories, priorityCallSenders,
                priorityMessageSenders, suppressedVisualEffects,
                Policy.STATE_PRIORITY_CHANNELS_BLOCKED, priorityConversationsSenders);
        assertEquals(expectedPolicy, config.toNotificationPolicy(zenPolicy));

        // make sure allowChannels=false has gotten through correctly (also covered above)
        assertFalse(expectedPolicy.allowPriorityChannels());
    }

    @Test
    public void testZenPolicyToNotificationPolicy_unsetChannelsTakesDefault() {
        ZenModeConfig config = new ZenModeConfig();
        ZenPolicy zenPolicy = new ZenPolicy.Builder().build();

        // When allowChannels is not set to anything in the ZenPolicy builder, make sure it takes
        // the default value from the zen mode config.
        Policy policy = config.toNotificationPolicy(zenPolicy);
        assertEquals(config.manualRule.zenPolicy.getPriorityChannelsAllowed() == STATE_ALLOW,
                policy.allowPriorityChannels());
    }

    @Test
    public void testGetZenPolicy() {
        ZenPolicy expected = new ZenPolicy.Builder()
                .allowAlarms(true)
                .allowReminders(true)
                .allowEvents(true)
                .showLights(false)
                .showBadges(false)
                .showInAmbientDisplay(false)
                .allowCalls(ZenPolicy.PEOPLE_TYPE_CONTACTS)
                .allowMessages(PEOPLE_TYPE_STARRED)
                .allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
                .allowPriorityChannels(false)
                .build();

        ZenModeConfig config = getMutedAllConfig();
        config.manualRule.zenPolicy = expected.copy();
        ZenPolicy actual = config.getZenPolicy();

        assertEquals(expected.getVisualEffectBadge(), actual.getVisualEffectBadge());
        assertEquals(expected.getPriorityCategoryAlarms(), actual.getPriorityCategoryAlarms());
        assertEquals(expected.getPriorityCategoryReminders(),
                actual.getPriorityCategoryReminders());
        assertEquals(expected.getPriorityCategoryEvents(), actual.getPriorityCategoryEvents());
        assertEquals(expected.getVisualEffectLights(), actual.getVisualEffectLights());
        assertEquals(expected.getVisualEffectAmbient(), actual.getVisualEffectAmbient());
        assertEquals(expected.getPriorityConversationSenders(),
                actual.getPriorityConversationSenders());
        assertEquals(expected.getPriorityCallSenders(), actual.getPriorityCallSenders());
        assertEquals(expected.getPriorityMessageSenders(), actual.getPriorityMessageSenders());
        assertEquals(expected.getPriorityChannelsAllowed(), actual.getPriorityChannelsAllowed());
    }

    @Test
    public void testPriorityOnlyMutingAll() {
        ZenModeConfig config = getMutedAllConfig();
        assertTrue(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));

        config.manualRule.zenPolicy = new ZenPolicy.Builder(config.manualRule.zenPolicy)
                .allowReminders(true)
                .build();

        assertFalse(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));

        config.manualRule.zenPolicy = new ZenPolicy.Builder(config.manualRule.zenPolicy)
                .allowReminders(false)
                .build();

        assertTrue(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));

        config.hasPriorityChannels = true;
        config.manualRule.zenPolicy = new ZenPolicy.Builder(config.manualRule.zenPolicy)
                .allowPriorityChannels(true)
                .build();

        assertFalse(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));

        config.hasPriorityChannels = false;
        config.manualRule.zenPolicy = new ZenPolicy.Builder(config.manualRule.zenPolicy)
                .allowPriorityChannels(false)
                .build();
        config.manualRule.zenPolicy = new ZenPolicy.Builder(config.manualRule.zenPolicy)
                .allowAlarms(true)
                .build();

        assertTrue(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));

        config.manualRule.zenPolicy = new ZenPolicy.Builder(config.manualRule.zenPolicy)
                .allowAlarms(false)
                .build();

        assertTrue(ZenModeConfig.areAllPriorityOnlyRingerSoundsMuted(config));
    }

    @Test
    public void testParseOldEvent() {
        EventInfo oldEvent = new EventInfo();
        oldEvent.userId = 1;
        oldEvent.calName = "calName";
        oldEvent.calendarId = null; // old events will have null ids

        Uri conditionId = ZenModeConfig.toEventConditionId(oldEvent);
        EventInfo eventParsed = ZenModeConfig.tryParseEventConditionId(conditionId);
        assertEquals(oldEvent, eventParsed);
    }

    @Test
    public void testParseNewEvent() {
        EventInfo event = new EventInfo();
        event.userId = 1;
        event.calName = "calName";
        event.calendarId = 12345L;

        Uri conditionId = ZenModeConfig.toEventConditionId(event);
        EventInfo eventParsed = ZenModeConfig.tryParseEventConditionId(conditionId);
        assertEquals(event, eventParsed);
    }

    @Test
    public void testCanBeUpdatedByApp_nullPolicyAndDeviceEffects() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.zenPolicy = null;
        rule.zenDeviceEffects = null;
        assertThat(rule.isUserModified()).isFalse();

        rule.userModifiedFields = 1;

        assertThat(rule.isUserModified()).isTrue();
    }

    @Test
    public void testCanBeUpdatedByApp_policyModified() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.zenPolicy = new ZenPolicy();
        assertThat(rule.isUserModified()).isFalse();

        rule.zenPolicyUserModifiedFields = 1;

        assertThat(rule.isUserModified()).isTrue();
    }

    @Test
    public void testCanBeUpdatedByApp_deviceEffectsModified() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.zenDeviceEffects = new ZenDeviceEffects.Builder().build();
        assertThat(rule.isUserModified()).isFalse();

        rule.zenDeviceEffectsUserModifiedFields = 1;

        assertThat(rule.isUserModified()).isTrue();
    }

    @Test
    public void testBackupRestore_fromPreModesUi() throws IOException, XmlPullParserException {
        // Very non-default XML, to check that DND policy is transferred from the old values.
        String xml =
                """
                <zen version="12">
                <allow calls="true" repeatCallers="false" messages="true"
                  reminders="false" events="true" callsFrom="0" messagesFrom="1"
                  alarms="true" media="false" system="true" convos="true"
                  convosFrom="1" priorityChannelsAllowed="false" />
                <disallow visualEffects="24" />
                <manual enabled="true" zen="1" creationTime="0" modified="false" />
                <state areChannelsBypassingDnd="true" />
                </zen>\
                """;

        ZenModeConfig config = readConfigXml(new ByteArrayInputStream(xml.getBytes()),
                mock(BackupRestoreEventLogger.class));

        assertThat(config.manualRule.zenPolicy).isEqualTo(
                new ZenPolicy.Builder()
                        .allowCalls(PEOPLE_TYPE_ANYONE)
                        .allowMessages(PEOPLE_TYPE_CONTACTS)
                        .allowConversations(CONVERSATION_SENDERS_ANYONE)
                        .allowRepeatCallers(false)
                        .allowReminders(false)
                        .allowEvents(true)
                        .allowAlarms(true)
                        .allowMedia(false)
                        .allowSystem(true)
                        .allowPriorityChannels(false)
                        .showAllVisualEffects()
                        .showLights(false)
                        .showPeeking(false)
                        .build());
    }

    @Test
    @EnableFlags(FLAG_BACKUP_RESTORE_LOGGING)
    public void testBackupRestore_fromPreModesUi_logs() throws IOException, XmlPullParserException {
        String xml = "<zen version=\"12\">\n"
                + "<allow calls=\"true\" repeatCallers=\"true\" messages=\"true\""
                + " reminders=\"false\" events=\"false\" callsFrom=\"2\" messagesFrom=\"2\""
                + " alarms=\"true\" media=\"true\" system=\"false\" convos=\"true\""
                + " convosFrom=\"2\" priorityChannelsAllowed=\"true\" />\n"
                + "<disallow visualEffects=\"157\" />\n"
                + "<manual enabled=\"true\" zen=\"1\" creationTime=\"0\" modified=\"false\" />\n"
                + "<state areChannelsBypassingDnd=\"true\" />\n"
                + "</zen>";

        BackupRestoreEventLogger logger = mock(BackupRestoreEventLogger.class);
        readConfigXml(new ByteArrayInputStream(xml.getBytes()), logger);

        verify(logger).logItemsRestored(DATA_TYPE_ZEN_RULES, 1);
    }

    @Test
    public void testBackupRestore() throws IOException, XmlPullParserException {
        ZenModeConfig config = new ZenModeConfig();
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.configurationActivity = CONFIG_ACTIVITY;
        rule.component = OWNER;
        rule.conditionId = CONDITION_ID;
        rule.condition = CONDITION;
        rule.enabled = ENABLED;
        rule.creationTime = 123;
        rule.id = "id";
        rule.zenMode = INTERRUPTION_FILTER;
        rule.name = NAME;
        rule.setConditionOverride(OVERRIDE_DEACTIVATE);
        rule.pkg = OWNER.getPackageName();
        rule.zenPolicy = POLICY;

        rule.allowManualInvocation = ALLOW_MANUAL;
        rule.type = TYPE;
        rule.userModifiedFields = 16;
        rule.zenPolicyUserModifiedFields = 5;
        rule.zenDeviceEffectsUserModifiedFields = 2;
        rule.iconResName = ICON_RES_NAME;
        rule.triggerDescription = TRIGGER_DESC;
        rule.deletionInstant = Instant.ofEpochMilli(1701790147000L);
        rule.disabledOrigin = ZenModeConfig.ORIGIN_USER_IN_SYSTEMUI;
        rule.lastActivation = Instant.ofEpochMilli(456);
        if (Flags.modesUiTileReactivatesLast()) {
            rule.lastManualActivation = Instant.ofEpochMilli(789);
            rule.lastDeactivation = Instant.ofEpochMilli(876);
            rule.lastManualDeactivation = Instant.ofEpochMilli(543);
        }
        config.automaticRules.put(rule.id, rule);

        BackupRestoreEventLogger logger = null;
        if (Flags.backupRestoreLogging()) {
            logger = mock(BackupRestoreEventLogger.class);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeConfigXml(config, XML_VERSION_MODES_API, true, baos, logger);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig fromXml = readConfigXml(bais, logger);

        ZenModeConfig.ZenRule ruleActual = fromXml.automaticRules.get(rule.id);
        assertEquals(rule.pkg, ruleActual.pkg);
        assertEquals(OVERRIDE_NONE, ruleActual.getConditionOverride());
        assertEquals(rule.enabler, ruleActual.enabler);
        assertEquals(rule.component, ruleActual.component);
        assertEquals(rule.configurationActivity, ruleActual.configurationActivity);
        assertEquals(rule.condition, ruleActual.condition);
        assertEquals(rule.enabled, ruleActual.enabled);
        assertEquals(rule.creationTime, ruleActual.creationTime);
        assertEquals(rule.conditionId, ruleActual.conditionId);
        assertEquals(rule.name, ruleActual.name);
        assertEquals(rule.zenMode, ruleActual.zenMode);

        assertEquals(rule.allowManualInvocation, ruleActual.allowManualInvocation);
        assertEquals(rule.iconResName, ruleActual.iconResName);
        assertEquals(rule.type, ruleActual.type);
        assertEquals(rule.userModifiedFields, ruleActual.userModifiedFields);
        assertEquals(rule.zenPolicyUserModifiedFields, ruleActual.zenPolicyUserModifiedFields);
        assertEquals(rule.zenDeviceEffectsUserModifiedFields,
                ruleActual.zenDeviceEffectsUserModifiedFields);
        assertEquals(rule.triggerDescription, ruleActual.triggerDescription);
        assertEquals(rule.zenPolicy, ruleActual.zenPolicy);
        assertEquals(rule.deletionInstant, ruleActual.deletionInstant);
        assertEquals(rule.disabledOrigin, ruleActual.disabledOrigin);
        assertEquals(rule.lastActivation, ruleActual.lastActivation);
        if (Flags.modesUiTileReactivatesLast()) {
            assertEquals(rule.lastManualActivation, ruleActual.lastManualActivation);
            assertEquals(rule.lastDeactivation, ruleActual.lastDeactivation);
            assertEquals(rule.lastManualDeactivation, ruleActual.lastManualDeactivation);
        }
        if (Flags.backupRestoreLogging()) {
            verify(logger).logItemsBackedUp(DATA_TYPE_ZEN_RULES, 2);
            verify(logger).logItemsRestored(DATA_TYPE_ZEN_RULES, 2);
        }
    }

    @Test
    public void testWriteToParcel() {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.configurationActivity = CONFIG_ACTIVITY;
        rule.component = OWNER;
        rule.conditionId = CONDITION_ID;
        rule.condition = CONDITION;
        rule.enabled = ENABLED;
        rule.creationTime = 123;
        rule.id = "id";
        rule.zenMode = INTERRUPTION_FILTER;
        rule.name = NAME;
        rule.setConditionOverride(OVERRIDE_DEACTIVATE);
        rule.pkg = OWNER.getPackageName();
        rule.zenPolicy = POLICY;

        rule.allowManualInvocation = ALLOW_MANUAL;
        rule.type = TYPE;
        rule.userModifiedFields = 16;
        rule.zenPolicyUserModifiedFields = 5;
        rule.zenDeviceEffectsUserModifiedFields = 2;
        rule.iconResName = ICON_RES_NAME;
        rule.triggerDescription = TRIGGER_DESC;
        rule.deletionInstant = Instant.ofEpochMilli(1701790147000L);
        rule.disabledOrigin = ZenModeConfig.ORIGIN_USER_IN_SYSTEMUI;
        rule.lastActivation = Instant.ofEpochMilli(789);
        if (Flags.modesUiTileReactivatesLast()) {
            rule.lastManualActivation = Instant.ofEpochMilli(123);
            rule.lastDeactivation = Instant.ofEpochMilli(296);
            rule.lastManualDeactivation = Instant.ofEpochMilli(481);
        }

        Parcel parcel = Parcel.obtain();
        rule.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ZenModeConfig.ZenRule parceled = new ZenModeConfig.ZenRule(parcel);

        assertEquals(rule.pkg, parceled.pkg);
        assertEquals(rule.getConditionOverride(), parceled.getConditionOverride());
        assertEquals(rule.enabler, parceled.enabler);
        assertEquals(rule.component, parceled.component);
        assertEquals(rule.configurationActivity, parceled.configurationActivity);
        assertEquals(rule.condition, parceled.condition);
        assertEquals(rule.enabled, parceled.enabled);
        assertEquals(rule.creationTime, parceled.creationTime);
        assertEquals(rule.conditionId, parceled.conditionId);
        assertEquals(rule.name, parceled.name);
        assertEquals(rule.zenMode, parceled.zenMode);

        assertEquals(rule.allowManualInvocation, parceled.allowManualInvocation);
        assertEquals(rule.iconResName, parceled.iconResName);
        assertEquals(rule.type, parceled.type);
        assertEquals(rule.userModifiedFields, parceled.userModifiedFields);
        assertEquals(rule.zenPolicyUserModifiedFields, parceled.zenPolicyUserModifiedFields);
        assertEquals(rule.zenDeviceEffectsUserModifiedFields,
                parceled.zenDeviceEffectsUserModifiedFields);
        assertEquals(rule.triggerDescription, parceled.triggerDescription);
        assertEquals(rule.zenPolicy, parceled.zenPolicy);
        assertEquals(rule.deletionInstant, parceled.deletionInstant);
        assertEquals(rule.disabledOrigin, parceled.disabledOrigin);
        assertEquals(rule.lastActivation, parceled.lastActivation);
        if (Flags.modesUiTileReactivatesLast()) {
            assertEquals(rule.lastManualActivation, parceled.lastManualActivation);
            assertEquals(rule.lastDeactivation, parceled.lastDeactivation);
            assertEquals(rule.lastManualDeactivation, parceled.lastManualDeactivation);
        }

        assertEquals(rule, parceled);
        assertEquals(rule.hashCode(), parceled.hashCode());
    }

    @Test
    public void testRuleXml_classic() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.configurationActivity = new ComponentName("a", "a");
        rule.component = new ComponentName("b", "b");
        rule.conditionId = new Uri.Builder().scheme("hello").build();
        rule.condition = new Condition(rule.conditionId, "", Condition.STATE_TRUE);
        rule.enabled = true;
        rule.creationTime = 123;
        rule.id = "id";
        rule.zenMode = Settings.Global.ZEN_MODE_ALARMS;
        rule.name = "name";
        rule.pkg = "b";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(rule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);

        assertEquals("b", fromXml.pkg);
        //should all match original
        assertEquals(rule.component, fromXml.component);
        assertEquals(rule.configurationActivity, fromXml.configurationActivity);
        assertNull(fromXml.enabler);
        assertEquals(rule.condition, fromXml.condition);
        assertEquals(rule.enabled, fromXml.enabled);
        assertEquals(rule.creationTime, fromXml.creationTime);
        assertEquals(rule.conditionId, fromXml.conditionId);
        assertEquals(rule.name, fromXml.name);
        assertEquals(rule.zenMode, fromXml.zenMode);
    }

    @Test
    public void testRuleXml() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.configurationActivity = CONFIG_ACTIVITY;
        rule.component = OWNER;
        rule.conditionId = CONDITION_ID;
        rule.condition = CONDITION;
        rule.enabled = ENABLED;
        rule.id = "id";
        rule.zenMode = INTERRUPTION_FILTER;
        rule.name = NAME;
        rule.setConditionOverride(OVERRIDE_DEACTIVATE);
        rule.pkg = OWNER.getPackageName();
        rule.zenPolicy = POLICY;
        rule.zenDeviceEffects =
                new ZenDeviceEffects.Builder()
                        .setShouldDisplayGrayscale(false)
                        .setShouldSuppressAmbientDisplay(true)
                        .setShouldDimWallpaper(false)
                        .setShouldUseNightMode(true)
                        .setShouldDisableAutoBrightness(false)
                        .setShouldDisableTapToWake(true)
                        .setShouldDisableTiltToWake(false)
                        .setShouldDisableTouch(true)
                        .setShouldMinimizeRadioUsage(false)
                        .setShouldMaximizeDoze(true)
                        .setShouldUseNightLight(true)
                        .setBrightnessCap(0.5f)
                        .setExtraEffects(ImmutableSet.of("one", "two"))
                        .build();
        rule.creationTime = CREATION_TIME;

        rule.allowManualInvocation = ALLOW_MANUAL;
        rule.type = TYPE;
        rule.userModifiedFields = 4;
        rule.zenPolicyUserModifiedFields = 5;
        rule.zenDeviceEffectsUserModifiedFields = 2;
        rule.iconResName = ICON_RES_NAME;
        rule.triggerDescription = TRIGGER_DESC;
        rule.deletionInstant = Instant.ofEpochMilli(1701790147000L);
        rule.disabledOrigin = ZenModeConfig.ORIGIN_APP;
        rule.lastActivation = Instant.ofEpochMilli(123);
        if (Flags.modesUiTileReactivatesLast()) {
            rule.lastManualActivation = Instant.ofEpochMilli(456);
            rule.lastDeactivation = Instant.ofEpochMilli(285);
            rule.lastManualDeactivation = Instant.ofEpochMilli(301);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(rule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);

        assertEquals(rule.pkg, fromXml.pkg);
        // always resets on reboot
        assertEquals(OVERRIDE_NONE, fromXml.getConditionOverride());
        //should all match original
        assertEquals(rule.component, fromXml.component);
        assertEquals(rule.configurationActivity, fromXml.configurationActivity);
        assertNull(fromXml.enabler);
        assertEquals(rule.condition, fromXml.condition);
        assertEquals(rule.enabled, fromXml.enabled);
        assertEquals(rule.creationTime, fromXml.creationTime);
        assertEquals(rule.conditionId, fromXml.conditionId);
        assertEquals(rule.name, fromXml.name);
        assertEquals(rule.zenMode, fromXml.zenMode);
        assertEquals(rule.creationTime, fromXml.creationTime);
        assertEquals(rule.zenPolicy, fromXml.zenPolicy);
        assertEquals(rule.zenDeviceEffects, fromXml.zenDeviceEffects);

        assertEquals(rule.allowManualInvocation, fromXml.allowManualInvocation);
        assertEquals(rule.type, fromXml.type);
        assertEquals(rule.userModifiedFields, fromXml.userModifiedFields);
        assertEquals(rule.zenPolicyUserModifiedFields, fromXml.zenPolicyUserModifiedFields);
        assertEquals(rule.zenDeviceEffectsUserModifiedFields,
                fromXml.zenDeviceEffectsUserModifiedFields);
        assertEquals(rule.triggerDescription, fromXml.triggerDescription);
        assertEquals(rule.iconResName, fromXml.iconResName);
        assertEquals(rule.deletionInstant, fromXml.deletionInstant);
        assertEquals(rule.disabledOrigin, fromXml.disabledOrigin);
        assertEquals(rule.lastActivation, fromXml.lastActivation);
        if (Flags.modesUiTileReactivatesLast()) {
            assertEquals(rule.lastManualActivation, fromXml.lastManualActivation);
            assertEquals(rule.lastDeactivation, fromXml.lastDeactivation);
            assertEquals(rule.lastManualDeactivation, fromXml.lastManualDeactivation);
        }
    }

    @Test
    public void testRuleXml_weirdEffects() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.zenDeviceEffects = new ZenDeviceEffects.Builder()
                .setShouldMaximizeDoze(true)
                .addExtraEffect("one,stillOne,,andStillOne,,,andYetStill")
                .addExtraEffect(",two,stillTwo,")
                .addExtraEffect("three\\andThree")
                .addExtraEffect("four\\,andFour")
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(rule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);

        assertThat(fromXml.zenDeviceEffects.getExtraEffects()).isNotNull();
        assertThat(fromXml.zenDeviceEffects.getExtraEffects())
                .containsExactly("one,stillOne,,andStillOne,,,andYetStill", ",two,stillTwo,",
                        "three\\andThree", "four\\,andFour");
    }

    @Test
    public void testRuleXml_pkg_component() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.configurationActivity = new ComponentName("a", "a");
        rule.component = new ComponentName("b", "b");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(rule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);

        assertEquals("b", fromXml.pkg);
    }

    @Test
    public void testRuleXml_pkg_configActivity() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.configurationActivity = new ComponentName("a", "a");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(rule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);

        assertNull(fromXml.pkg);
    }

    @Test
    public void testRuleXml_getPkg_nullPkg() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.enabled = true;
        rule.configurationActivity = new ComponentName("a", "a");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(rule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);
        assertEquals("a", fromXml.getPkg());

        fromXml.condition = new Condition(Uri.EMPTY, "", Condition.STATE_TRUE);
        assertTrue(fromXml.isActive());
    }

    @Test
    public void testRuleXml_emptyConditionId() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.conditionId = Uri.EMPTY;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(rule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);

        assertEquals(rule.condition, fromXml.condition);
    }

    @Test
    public void testRuleXml_customInterruptionFilter() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.zenMode = Settings.Global.ZEN_MODE_ALARMS;
        rule.conditionId = Uri.parse("condition://android/blah");
        assertThat(Condition.isValidId(rule.conditionId, ZenModeConfig.SYSTEM_AUTHORITY)).isTrue();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(rule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);

        assertEquals(rule.zenMode, fromXml.zenMode);
    }

    @Test
    public void testRuleXml_invalidInterruptionFilter_readsDefault() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.zenMode = 1979;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(rule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);

        assertThat(fromXml.zenMode).isEqualTo(ZEN_MODE_IMPORTANT_INTERRUPTIONS);
    }

    @Test
    public void testZenPolicyXml_allUnset() throws Exception {
        ZenPolicy policy = new ZenPolicy.Builder().build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePolicyXml(policy, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenPolicy fromXml = readPolicyXml(bais);

        // nothing was set, so we should have nothing from the parser
        assertNull(fromXml);
    }

    @Test
    public void testRuleXml_userModifiedField() throws Exception {
        ZenModeConfig.ZenRule rule = new ZenModeConfig.ZenRule();
        rule.userModifiedFields |= AutomaticZenRule.FIELD_NAME;
        assertThat(rule.userModifiedFields).isEqualTo(1);
        assertThat(rule.isUserModified()).isTrue();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(rule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);

        assertThat(fromXml.userModifiedFields).isEqualTo(rule.userModifiedFields);
        assertThat(fromXml.isUserModified()).isTrue();
    }

    @Test
    public void testZenPolicyXml_classic() throws Exception {
        ZenPolicy policy = new ZenPolicy.Builder()
                .allowCalls(ZenPolicy.PEOPLE_TYPE_CONTACTS)
                .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
                .allowConversations(ZenPolicy.CONVERSATION_SENDERS_IMPORTANT)
                .allowRepeatCallers(true)
                .allowAlarms(true)
                .allowMedia(false)
                .allowSystem(true)
                .allowReminders(false)
                .allowEvents(true)
                .hideAllVisualEffects()
                .showVisualEffect(ZenPolicy.VISUAL_EFFECT_AMBIENT, true)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePolicyXml(policy, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenPolicy fromXml = readPolicyXml(bais);

        assertNotNull(fromXml);
        assertEquals(policy.getPriorityCategoryCalls(), fromXml.getPriorityCategoryCalls());
        assertEquals(policy.getPriorityCallSenders(), fromXml.getPriorityCallSenders());
        assertEquals(policy.getPriorityCategoryMessages(), fromXml.getPriorityCategoryMessages());
        assertEquals(policy.getPriorityMessageSenders(), fromXml.getPriorityMessageSenders());
        assertEquals(policy.getPriorityCategoryConversations(),
                fromXml.getPriorityCategoryConversations());
        assertEquals(policy.getPriorityConversationSenders(),
                fromXml.getPriorityConversationSenders());
        assertEquals(policy.getPriorityCategoryRepeatCallers(),
                fromXml.getPriorityCategoryRepeatCallers());
        assertEquals(policy.getPriorityCategoryAlarms(), fromXml.getPriorityCategoryAlarms());
        assertEquals(policy.getPriorityCategoryMedia(), fromXml.getPriorityCategoryMedia());
        assertEquals(policy.getPriorityCategorySystem(), fromXml.getPriorityCategorySystem());
        assertEquals(policy.getPriorityCategoryReminders(), fromXml.getPriorityCategoryReminders());
        assertEquals(policy.getPriorityCategoryEvents(), fromXml.getPriorityCategoryEvents());

        assertEquals(policy.getVisualEffectFullScreenIntent(),
                fromXml.getVisualEffectFullScreenIntent());
        assertEquals(policy.getVisualEffectLights(), fromXml.getVisualEffectLights());
        assertEquals(policy.getVisualEffectPeek(), fromXml.getVisualEffectPeek());
        assertEquals(policy.getVisualEffectStatusBar(), fromXml.getVisualEffectStatusBar());
        assertEquals(policy.getVisualEffectBadge(), fromXml.getVisualEffectBadge());
        assertEquals(policy.getVisualEffectAmbient(), fromXml.getVisualEffectAmbient());
        assertEquals(policy.getVisualEffectNotificationList(),
                fromXml.getVisualEffectNotificationList());
    }

    @Test
    public void testZenPolicyXml() throws Exception {
        ZenPolicy policy = new ZenPolicy.Builder()
                .allowCalls(ZenPolicy.PEOPLE_TYPE_CONTACTS)
                .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
                .allowConversations(ZenPolicy.CONVERSATION_SENDERS_IMPORTANT)
                .allowRepeatCallers(true)
                .allowAlarms(true)
                .allowMedia(false)
                .allowSystem(true)
                .allowReminders(false)
                .allowEvents(true)
                .allowPriorityChannels(false)
                .hideAllVisualEffects()
                .showVisualEffect(ZenPolicy.VISUAL_EFFECT_AMBIENT, true)
                .build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writePolicyXml(policy, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenPolicy fromXml = readPolicyXml(bais);

        assertNotNull(fromXml);
        assertEquals(policy.getPriorityCategoryCalls(), fromXml.getPriorityCategoryCalls());
        assertEquals(policy.getPriorityCallSenders(), fromXml.getPriorityCallSenders());
        assertEquals(policy.getPriorityCategoryMessages(), fromXml.getPriorityCategoryMessages());
        assertEquals(policy.getPriorityMessageSenders(), fromXml.getPriorityMessageSenders());
        assertEquals(policy.getPriorityCategoryConversations(),
                fromXml.getPriorityCategoryConversations());
        assertEquals(policy.getPriorityConversationSenders(),
                fromXml.getPriorityConversationSenders());
        assertEquals(policy.getPriorityCategoryRepeatCallers(),
                fromXml.getPriorityCategoryRepeatCallers());
        assertEquals(policy.getPriorityCategoryAlarms(), fromXml.getPriorityCategoryAlarms());
        assertEquals(policy.getPriorityCategoryMedia(), fromXml.getPriorityCategoryMedia());
        assertEquals(policy.getPriorityCategorySystem(), fromXml.getPriorityCategorySystem());
        assertEquals(policy.getPriorityCategoryReminders(), fromXml.getPriorityCategoryReminders());
        assertEquals(policy.getPriorityCategoryEvents(), fromXml.getPriorityCategoryEvents());
        assertEquals(policy.getPriorityChannelsAllowed(), fromXml.getPriorityChannelsAllowed());

        assertEquals(policy.getVisualEffectFullScreenIntent(),
                fromXml.getVisualEffectFullScreenIntent());
        assertEquals(policy.getVisualEffectLights(), fromXml.getVisualEffectLights());
        assertEquals(policy.getVisualEffectPeek(), fromXml.getVisualEffectPeek());
        assertEquals(policy.getVisualEffectStatusBar(), fromXml.getVisualEffectStatusBar());
        assertEquals(policy.getVisualEffectBadge(), fromXml.getVisualEffectBadge());
        assertEquals(policy.getVisualEffectAmbient(), fromXml.getVisualEffectAmbient());
        assertEquals(policy.getVisualEffectNotificationList(),
                fromXml.getVisualEffectNotificationList());
    }

    @Test
    public void testisManualActive_stateTrue() {
        ZenModeConfig config = getMutedAllConfig();
        final ZenModeConfig.ZenRule newRule = new ZenModeConfig.ZenRule();
        newRule.type = AutomaticZenRule.TYPE_OTHER;
        newRule.enabled = true;
        newRule.conditionId = Uri.EMPTY;
        newRule.allowManualInvocation = true;
        config.manualRule = newRule;
        config.manualRule.pkg = "android";
        config.manualRule.zenMode = ZEN_MODE_IMPORTANT_INTERRUPTIONS;
        config.manualRule.condition = new Condition(Uri.EMPTY, "", STATE_TRUE, SOURCE_USER_ACTION);

        assertThat(config.isManualActive()).isTrue();
    }

    @Test
    public void testisManualActive_stateFalse() {
        ZenModeConfig config = getMutedAllConfig();
        final ZenModeConfig.ZenRule newRule = new ZenModeConfig.ZenRule();
        newRule.type = AutomaticZenRule.TYPE_OTHER;
        newRule.enabled = true;
        newRule.conditionId = Uri.EMPTY;
        newRule.allowManualInvocation = true;
        config.manualRule = newRule;
        config.manualRule.pkg = "android";
        config.manualRule.zenMode = ZEN_MODE_OFF;
        config.manualRule.condition = new Condition(Uri.EMPTY, "", STATE_FALSE, SOURCE_USER_ACTION);

        assertThat(config.isManualActive()).isFalse();
    }

    @Test
    public void testisManualActive_noRule() {
        ZenModeConfig config = getMutedAllConfig();

        assertThat(config.isManualActive()).isFalse();
    }

    @Test
    public void testRuleXml_manual_upgrade() throws Exception {
        ZenModeConfig config = getMutedAllConfig();
        final ZenModeConfig.ZenRule newRule = new ZenModeConfig.ZenRule();
        newRule.type = AutomaticZenRule.TYPE_OTHER;
        newRule.enabled = true;
        newRule.conditionId = Uri.EMPTY;
        newRule.allowManualInvocation = true;
        newRule.pkg = "android";
        newRule.zenMode = ZEN_MODE_OFF;
        config.manualRule = newRule;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeRuleXml(newRule, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig.ZenRule fromXml = readRuleXml(bais);

        assertThat(fromXml.zenPolicy).isEqualTo(config.getZenPolicy());
    }

    @Test
    public void testConfigXml_manualRuleWithoutCondition_upgradeWhenExisting() throws Exception {
        // prior to modes_ui, it's possible to have a non-null manual rule that doesn't have much
        // data on it because it's meant to indicate that the manual rule is on by merely existing.
        ZenModeConfig config = new ZenModeConfig();
        config.manualRule = new ZenModeConfig.ZenRule();
        config.manualRule.enabled = true;
        config.manualRule.pkg = "android";
        config.manualRule.zenMode = ZEN_MODE_IMPORTANT_INTERRUPTIONS;
        config.manualRule.conditionId = null;
        config.manualRule.enabler = "test";

        // write out entire config xml
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeConfigXml(config, XML_VERSION_MODES_API, /* forBackup= */ false, baos, null);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig fromXml = readConfigXml(bais, null);


        // The result should be valid and contain a manual rule; the rule should have a non-null
        // ZenPolicy and a condition whose state is true. The conditionId should be default.
        assertThat(fromXml.isValid()).isTrue();
        assertThat(fromXml.manualRule).isNotNull();
        assertThat(fromXml.manualRule.zenPolicy).isNotNull();
        assertThat(fromXml.manualRule.condition).isNotNull();
        assertThat(fromXml.manualRule.condition.state).isEqualTo(STATE_TRUE);
        assertThat(fromXml.manualRule.conditionId).isEqualTo(Uri.EMPTY);
        assertThat(fromXml.manualRule.enabler).isEqualTo("test");
        assertThat(fromXml.isManualActive()).isTrue();
    }

    @Test
    public void testConfigXml_manualRuleWithCondition_upgradeWhenExisting() throws Exception {
        // prior to modes_ui, it's possible to have a non-null manual rule that doesn't have much
        // data on it because it's meant to indicate that the manual rule is on by merely existing.
        ZenModeConfig config = new ZenModeConfig();
        config.manualRule = new ZenModeConfig.ZenRule();
        config.manualRule.enabled = true;
        config.manualRule.pkg = "android";
        config.manualRule.zenMode = ZEN_MODE_IMPORTANT_INTERRUPTIONS;
        config.manualRule.conditionId = ZenModeConfig.toTimeCondition(mContext, 200, mUserId).id;
        config.manualRule.enabler = "test";

        // write out entire config xml
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeConfigXml(config, XML_VERSION_MODES_API, /* forBackup= */ false, baos, null);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig fromXml = readConfigXml(bais, null);

        // The result should have a manual rule; it should have a non-null ZenPolicy and a condition
        // whose state is true. The conditionId and enabler data should also be preserved.
        assertThat(fromXml.isValid()).isTrue();
        assertThat(fromXml.manualRule).isNotNull();
        assertThat(fromXml.manualRule.zenPolicy).isNotNull();
        assertThat(fromXml.manualRule.condition).isNotNull();
        assertThat(fromXml.manualRule.condition.state).isEqualTo(STATE_TRUE);
        assertThat(fromXml.manualRule.conditionId).isEqualTo(config.manualRule.conditionId);
        assertThat(fromXml.manualRule.enabler).isEqualTo("test");
        assertThat(fromXml.isManualActive()).isTrue();
    }

    @Test
    public void testConfigXml_manualRule_doesNotTurnOnIfNotUpgrade() throws Exception {
        // confirm that if the manual rule is already properly set up for modes_ui, it does not get
        // turned on (set to condition with STATE_TRUE) when reading xml.

        // getMutedAllConfig sets up the manual rule with a policy muting everything
        ZenModeConfig config = getMutedAllConfig();
        config.manualRule.condition = new Condition(Uri.EMPTY, "", STATE_FALSE, SOURCE_USER_ACTION);
        assertThat(config.isManualActive()).isFalse();

        // write out entire config xml
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeConfigXml(config, XML_VERSION_MODES_API, /* forBackup= */ false, baos, null);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig fromXml = readConfigXml(bais, null);

        // The result should have a manual rule; it should not be changed from the previous rule.
        assertThat(fromXml.manualRule).isEqualTo(config.manualRule);
        assertThat(fromXml.isManualActive()).isFalse();
    }

    @Test
    public void toNotificationPolicy_withNewSuppressedEffects_returnsSuppressedEffects() {
        ZenModeConfig config = getCustomConfig();
        // From LegacyNotificationManagerTest.testSetNotificationPolicy_preP_setNewFields
        // When a pre-P app sets SUPPRESSED_EFFECT_NOTIFICATION_LIST, it's converted by NMS into:
        Policy policy = new Policy(0, 0, 0,
                SUPPRESSED_EFFECT_FULL_SCREEN_INTENT | SUPPRESSED_EFFECT_LIGHTS
                        | SUPPRESSED_EFFECT_PEEK | SUPPRESSED_EFFECT_AMBIENT);

        config.applyNotificationPolicy(policy);
        Policy result = config.toNotificationPolicy();

        assertThat(suppressedEffectsOf(result)).isEqualTo(suppressedEffectsOf(policy));
    }

    @Test
    public void toNotificationPolicy_withOldAndNewSuppressedEffects_returnsSuppressedEffects() {
        ZenModeConfig config = getCustomConfig();
        // From LegacyNotificationManagerTest.testSetNotificationPolicy_preP_setOldNewFields.
        // When a pre-P app sets SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_STATUS_BAR, it's
        // converted by NMS into:
        Policy policy = new Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT
                        | SUPPRESSED_EFFECT_LIGHTS | SUPPRESSED_EFFECT_PEEK
                        | SUPPRESSED_EFFECT_AMBIENT);

        config.applyNotificationPolicy(policy);
        Policy result = config.toNotificationPolicy();

        assertThat(suppressedEffectsOf(result)).isEqualTo(suppressedEffectsOf(policy));
    }

    @Test
    public void readXml_fixesWronglyDisabledManualRule() throws Exception {
        ZenModeConfig config = getCustomConfig();
        config.manualRule.enabled = false;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeConfigXml(config, XML_VERSION_MODES_UI, /* forBackup= */ false, baos, null);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ZenModeConfig fromXml = readConfigXml(bais, null);

        assertThat(fromXml.manualRule.enabled).isTrue();
    }

    private static String suppressedEffectsOf(Policy policy) {
        return suppressedEffectsToString(policy.suppressedVisualEffects) + "("
                + policy.suppressedVisualEffects + ")";
    }

    private ZenModeConfig getMutedRingerConfig() {
        ZenModeConfig config = new ZenModeConfig();

        config.manualRule.zenPolicy = new ZenPolicy.Builder()
                .disallowAllSounds()
                .allowAlarms(true)
                .allowMedia(true)
                .allowPriorityChannels(false)
                .showAllVisualEffects()
                .build();
        config.hasPriorityChannels = false;

        return config;
    }

    private ZenModeConfig getCustomConfig() {
        ZenModeConfig config = new ZenModeConfig();

        config.manualRule.zenPolicy = new ZenPolicy.Builder()
                .disallowAllSounds()
                .allowAlarms(true)
                .allowCalls(PEOPLE_TYPE_ANYONE)
                .allowRepeatCallers(true)
                .allowConversations(CONVERSATION_SENDERS_IMPORTANT)
                .allowPriorityChannels(true)
                .showAllVisualEffects()
                .build();
        config.hasPriorityChannels = false;
        return config;
    }

    private ZenModeConfig getMutedAllConfig() {
        ZenModeConfig config = new ZenModeConfig();

        config.manualRule.zenPolicy = new ZenPolicy.Builder()
                .disallowAllSounds()
                .showAllVisualEffects()
                .allowPriorityChannels(false)
                .build();
        config.hasPriorityChannels = false;
        return config;
    }

    private void writeRuleXml(ZenModeConfig.ZenRule rule, ByteArrayOutputStream os)
            throws IOException {
        String tag = "tag";

        TypedXmlSerializer out = Xml.newFastSerializer();
        out.setOutput(new BufferedOutputStream(os), "utf-8");
        out.startDocument(null, true);
        out.startTag(null, tag);
        ZenModeConfig.writeRuleXml(rule, out, /* forBackup= */ false);
        out.endTag(null, tag);
        out.endDocument();
    }

    private ZenModeConfig.ZenRule readRuleXml(ByteArrayInputStream is)
            throws XmlPullParserException, IOException {
        TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(is), null);
        parser.nextTag();
        return ZenModeConfig.readRuleXml(parser);
    }

    private void writePolicyXml(ZenPolicy policy, ByteArrayOutputStream os) throws IOException {
        String tag = "tag";

        TypedXmlSerializer out = Xml.newFastSerializer();
        out.setOutput(new BufferedOutputStream(os), "utf-8");
        out.startDocument(null, true);
        out.startTag(null, tag);
        ZenModeConfig.writeZenPolicyXml(policy, out);
        out.endTag(null, tag);
        out.endDocument();
    }

    private ZenPolicy readPolicyXml(ByteArrayInputStream is)
            throws XmlPullParserException, IOException {
        TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(is), null);
        parser.nextTag();
        return ZenModeConfig.readZenPolicyXml(parser);
    }

    private void writeConfigXml(ZenModeConfig config, Integer version, boolean forBackup,
            ByteArrayOutputStream os, BackupRestoreEventLogger logger) throws IOException {
        String tag = ZEN_TAG;

        TypedXmlSerializer out = Xml.newFastSerializer();
        out.setOutput(new BufferedOutputStream(os), "utf-8");
        out.startDocument(null, true);
        out.startTag(null, tag);
        config.writeXml(out, version, forBackup, logger);
        out.endTag(null, tag);
        out.endDocument();
    }

    private ZenModeConfig readConfigXml(ByteArrayInputStream is, BackupRestoreEventLogger logger)
            throws XmlPullParserException, IOException {
        TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(is), null);
        parser.nextTag();
        return ZenModeConfig.readXml(parser, logger);
    }
}
