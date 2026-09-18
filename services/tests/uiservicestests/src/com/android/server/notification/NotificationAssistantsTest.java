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
package com.android.server.notification;

import static android.Manifest.permission.STATUS_BAR_SERVICE;
import static android.app.Flags.nmContextualDisplayLaunch;
import static android.app.NotificationManager.SUPPORTED_NAS_ADJUSTMENT_KEYS_CHANGED;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_BUNDLE;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_HIGHLIGHT;
import static android.app.NotificationRule.Action.PRIMARY_ACTION_LOW;
import static android.app.NotificationRule.RESERVED_ID_IMPORTANT_NOTIFICATIONS;
import static android.app.NotificationRule.RESERVED_ID_PRIORITY_CONVERSATIONS;
import static android.app.NotificationRule.RESERVED_ID_PROMOTED;
import static android.app.NotificationRule.RESERVED_ID_STATIC_BUNDLES;
import static android.os.UserHandle.USER_ALL;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;
import static android.service.notification.Adjustment.KEY_CONTEXTUAL_ACTIONS;
import static android.service.notification.Adjustment.KEY_IMPORTANCE;
import static android.service.notification.Adjustment.KEY_SUMMARIZATION;
import static android.service.notification.Adjustment.KEY_TYPE;
import static android.service.notification.Adjustment.TYPE_CONTENT_RECOMMENDATION;
import static android.service.notification.Adjustment.TYPE_NEWS;
import static android.service.notification.Adjustment.TYPE_PROMOTION;
import static android.service.notification.Adjustment.TYPE_SOCIAL_MEDIA;

import static com.android.server.notification.NotificationManagerService.DEFAULT_ALLOWED_ADJUSTMENTS;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Flags;
import android.app.NotificationRule;
import android.app.backup.BackupRestoreEventLogger;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.UserInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.notification.Adjustment;
import android.service.notification.DynamicBundle;
import android.service.notification.INotificationListener;
import android.testing.TestWithLooperRule;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IntArray;
import android.util.StatsEvent;
import android.util.StatsEventTestUtils;
import android.util.Xml;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.CollectionUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.os.AtomsProto;
import com.android.os.notification.BundleTypes;
import com.android.os.notification.NotificationAdjustmentPreferences;
import com.android.os.notification.NotificationExtensionAtoms;
import com.android.os.notification.NotificationProtoEnums;
import com.android.server.LocalServices;
import com.android.server.UiServiceTestCase;
import com.android.server.notification.NotificationManagerService.NotificationAssistants;
import com.android.server.pm.UserManagerInternal;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.ExtensionRegistryLite;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
public class NotificationAssistantsTest extends UiServiceTestCase {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule(order = Integer.MAX_VALUE)
    public TestWithLooperRule mLooperRule = new TestWithLooperRule();

    @Mock
    private PackageManager mPm;
    @Mock
    private IPackageManager miPm;
    @Mock
    private UserManager mUm;
    @Mock
    private UserManagerInternal mUmInternal;
    TestableNotificationManagerService mNm;
    @Mock
    BackupRestoreEventLogger mLogger;
    NotificationRuleManager mRuleManager;
    private TestableLooper mTestableLooper;

    NotificationAssistants mAssistants;

    private TestableContext mContext = spy(getContext());

    UserInfo mZero = new UserInfo(ActivityManager.getCurrentUser(), "current", UserInfo.FLAG_FULL);
    UserInfo mZeroProfile = new UserInfo(mZero.id + 1, "profile", UserInfo.FLAG_PROFILE);
    UserInfo mZeroManagedProfile = new UserInfo(mZero.id + 2, "managed", null,
            UserInfo.FLAG_PROFILE, USER_TYPE_PROFILE_MANAGED);
    UserInfo mSecondary = new UserInfo(mZero.id + 3, "secondary", UserInfo.FLAG_FULL);
    List<UserInfo> mUsers = List.of(mZero, mZeroProfile, mZeroManagedProfile, mSecondary);

    @Mock
    private INotificationListener mAssistantZero;
    private ManagedServices.ManagedServiceInfo mAssistantZeroInfo;
    @Mock
    private INotificationListener mAssistantSecondary;
    private ManagedServices.ManagedServiceInfo mAssistantSecondaryInfo;

    ComponentName mCn = new ComponentName("a", "b");

    private ExtensionRegistryLite mRegistry;


    // Helper function to hold mApproved lock, avoid GuardedBy lint errors
    private boolean isUserSetServicesEmpty(NotificationAssistants assistant, int userId) {
        synchronized (assistant.mApproved) {
            return assistant.mUserSetServices.get(userId).isEmpty();
        }
    }

    private void writeXmlAndReload(int userId) throws Exception {
        TypedXmlSerializer serializer = Xml.newFastSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.setOutput(new BufferedOutputStream(baos), "utf-8");
        serializer.startDocument(null, true);
        mAssistants.writeXml(serializer, false, userId, null);
        serializer.endDocument();
        serializer.flush();

        //fail(baos.toString("UTF-8"));

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(baos.toByteArray())), null);

        parser.nextTag();
        mAssistants = spy(mNm.new NotificationAssistants(mContext, miPm));
        mNm.mAssistants = mAssistants;
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL, null);
    }

    private void writePolicyAndRulesAndReload() throws Exception {
        TypedXmlSerializer serializer = Xml.newFastSerializer();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        serializer.setOutput(new BufferedOutputStream(baos), "utf-8");
        serializer.startDocument(null, true);
        mNm.writePolicyXml(serializer, false, USER_ALL, null);
        serializer.endDocument();
        serializer.flush();

        String policyXml = baos.toString("utf-8");

        serializer = Xml.newFastSerializer();
        baos = new ByteArrayOutputStream();
        serializer.setOutput(new BufferedOutputStream(baos), "utf-8");
        serializer.startDocument(null, true);
        mNm.writeRulesXml(serializer, false, USER_ALL, null);
        serializer.endDocument();
        serializer.flush();

        String rulesXml = baos.toString("utf-8");

        mNm = spy(new TestableNotificationManagerService(mContext, mTestableLooper));
        mNm.init(policyXml, rulesXml);
        mAssistants = mNm.mAssistants;
        mRuleManager = mNm.mNotificationRuleManager;
    }

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mUmInternal);
        mContext.setMockPackageManager(mPm);
        mContext.addMockSystemService(Context.USER_SERVICE, mUm);
        mContext.addMockSystemService(UserManager.class, mUm);
        mContext.getOrCreateTestableResources().addOverride(
                com.android.internal.R.string.config_defaultAssistantAccessComponent,
                mCn.flattenToString());

        mTestableLooper = TestableLooper.get(this);

        doNothing().when(mContext).sendBroadcast(any(), anyString());
        doNothing().when(mContext).sendBroadcastAsUser(any(), any());
        doNothing().when(mContext).sendBroadcastAsUser(any(), any(), any());
        doNothing().when(mContext).sendBroadcastMultiplePermissions(any(), any(), any(), any());

        mContext.ensureTestableResources();

        List<ResolveInfo> approved = new ArrayList<>();
        ResolveInfo resolve = new ResolveInfo();
        approved.add(resolve);
        ServiceInfo info = new ServiceInfo();
        info.packageName = mCn.getPackageName();
        info.name = mCn.getClassName();
        info.permission = Manifest.permission.BIND_NOTIFICATION_ASSISTANT_SERVICE;
        resolve.serviceInfo = info;
        when(mPm.queryIntentServicesAsUser(any(), anyInt(), anyInt()))
                .thenReturn(approved);

        IntArray profileIds = new IntArray();
        for (UserInfo user : mUsers) {
            when(mUm.getUserInfo(eq(user.id))).thenReturn(user);
            when(mUmInternal.getUserInfo(eq(user.id))).thenReturn(user);
            if (user.isProfile()) {
                profileIds.add(user.id);
                when(mUmInternal.getProfileParentId(user.id)).thenReturn(mZero.id);
                when(mUm.getProfileParent(user.id)).thenReturn(mZero);
            } else {
                when(mUmInternal.getProfileParentId(user.id)).thenReturn(user.id);
                when(mUm.getProfileParent(user.id)).thenReturn(null);
            }
            when(mUm.getProfileIds(user.id, false)).thenReturn(new int[] {user.id});
            when(mUmInternal.getProfileIds(user.id, false)).thenReturn(new int[] {user.id});
            when(mUm.getEnabledProfileIds(user.id)).thenReturn(new int[] {user.id});
        }
        when(mUm.getUsers()).thenReturn(mUsers);
        when(mUmInternal.getUsers(any())).thenReturn(mUsers);
        when(mUm.getAliveUsers()).thenReturn(mUsers);
        when(mUm.getProfileIds(mZero.id, false)).thenReturn(
                new int[] {mZero.id, mZeroProfile.id, mZeroManagedProfile.id});
        when(mUmInternal.getProfileIds(mZero.id, false)).thenReturn(
                new int[] {mZero.id, mZeroProfile.id, mZeroManagedProfile.id});
        when(mUm.getEnabledProfileIds(mZero.id)).thenReturn(
                new int[] {mZero.id, mZeroProfile.id, mZeroManagedProfile.id});
        when(mUm.getProfiles(mZero.id)).thenReturn(
                List.of(mZero, mZeroProfile, mZeroManagedProfile));

        mNm = spy(new TestableNotificationManagerService(mContext, mTestableLooper));
        mNm.init("<notification-policy></notification-policy>", null);

        mRuleManager = mNm.mNotificationRuleManager;
        mNm.mDefaultUnsupportedAdjustments = new String[] {};

        mNm.setNASMigrationDone(mUserId);
        mRegistry = ExtensionRegistryLite.newInstance();
        NotificationExtensionAtoms.registerAllExtensions(mRegistry);

        mAssistants = mNm.mAssistants;

        // set up managed services (NAS for each of mZero and mSecondary)
        when(mAssistantZero.asBinder()).thenReturn(mock(IBinder.class));
        when(mAssistantSecondary.asBinder()).thenReturn(mock(IBinder.class));
        mAssistants.registerSystemService(mAssistantZero, mCn, mZero.id, 1000);
        mAssistants.registerSystemService(mAssistantSecondary, mCn, mSecondary.id, 1001);
        mAssistantZeroInfo = mAssistants.checkServiceTokenLocked(mAssistantZero);
        mAssistantSecondaryInfo = mAssistants.checkServiceTokenLocked(mAssistantSecondary);

        // a lot of tests verify counts about updating defaults, so ignore counts that
        // happen in init()
        Mockito.clearInvocations(mNm, mAssistants);
    }

    @After
    public void tearDown() {
        LocalServices.removeAllServicesForTest();
    }


    @Test
    public void testXmlUpgrade() {
        mAssistants.resetDefaultAssistantsIfNecessary();

        // 4 users, once each
        verify(mNm, times(4)).setDefaultAssistantForUser(anyInt());
    }

    @Test
    public void testWriteXml_userTurnedOffNAS() throws Exception {
        int userId = ActivityManager.getCurrentUser();

        mAssistants.loadDefaultsFromConfig(true);

        mAssistants.setPackageOrComponentEnabled(mCn.flattenToString(), userId, true,
               true, true);

        ComponentName current = CollectionUtils.firstOrNull(
                mAssistants.getAllowedComponents(userId));
        assertNotNull(current);
        mAssistants.setUserSet(userId, true);
        mAssistants.setPackageOrComponentEnabled(current.flattenToString(), userId, true, false,
                true);

        writeXmlAndReload(USER_ALL);

        ArrayMap<Boolean, ArraySet<String>> approved =
                mAssistants.mApproved.get(ActivityManager.getCurrentUser());
        // approved should not be null
        assertNotNull(approved);
        assertEquals(new ArraySet<>(), approved.get(true));

        // user set is maintained
        assertTrue(mAssistants.mIsUserChanged.get(ActivityManager.getCurrentUser()));
    }

    @Test
    public void testWriteXml_userTurnedOffNAS_backup() throws Exception {
        int userId = 10;

        mAssistants.loadDefaultsFromConfig(true);

        mAssistants.setPackageOrComponentEnabled(mCn.flattenToString(), userId, true,
                true, true);

        ComponentName current = CollectionUtils.firstOrNull(
                mAssistants.getAllowedComponents(userId));
        mAssistants.setUserSet(userId, true);
        mAssistants.setPackageOrComponentEnabled(current.flattenToString(), userId, true, false,
                true);
        assertTrue(mAssistants.mIsUserChanged.get(userId));
        assertThat(mAssistants.getApproved(userId, true)).isEmpty();

        writeXmlAndReload(userId);

        ArrayMap<Boolean, ArraySet<String>> approved = mAssistants.mApproved.get(userId);
        // approved should not be null
        assertNotNull(approved);
        assertEquals(new ArraySet<>(), approved.get(true));

        // user set is maintained
        assertTrue(mAssistants.mIsUserChanged.get(userId));
        assertThat(mAssistants.getApproved(userId, true)).isEmpty();
    }

    @Test
    public void testReadXml_userDisabled() throws Exception {
        String xml = "<enabled_assistants version=\"4\" defaults=\"b/b\">"
                + "<service_listing approved=\"\" user=\"0\" primary=\"true\""
                + "user_changed=\"true\"/>"
                + "</enabled_assistants>";

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);

        parser.nextTag();
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL, null);

        ArrayMap<Boolean, ArraySet<String>> approved = mAssistants.mApproved.get(0);

        // approved should not be null
        assertNotNull(approved);
        assertEquals(new ArraySet<>(), approved.get(true));
    }

    @SuppressWarnings("GuardedBy")
    @Test
    public void testReadXml_userDisabled_restore() throws Exception {
        String xml = "<enabled_assistants version=\"4\" defaults=\"b/b\">"
                + "<service_listing approved=\"\" user=\"0\" primary=\"true\""
                + "user_changed=\"true\"/>"
                + "</enabled_assistants>";

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);

        parser.nextTag();
        mAssistants.readXml(parser, mNm::canUseManagedServices, true,
                ActivityManager.getCurrentUser(), mLogger);

        ArrayMap<Boolean, ArraySet<String>> approved = mAssistants.mApproved.get(
                ActivityManager.getCurrentUser());

        // approved should not be null
        assertNotNull(approved);
        assertEquals(new ArraySet<>(), approved.get(true));

        // user set is maintained
        assertTrue(mAssistants.mIsUserChanged.get(ActivityManager.getCurrentUser()));
    }

    @Test
    public void testReadXml_upgradeUserSet() throws Exception {
        String xml = "<enabled_assistants version=\"3\" defaults=\"b/b\">"
                + "<service_listing approved=\"\" user=\"0\" primary=\"true\""
                + "user_set_services=\"b/b\"/>"
                + "</enabled_assistants>";

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);

        parser.nextTag();
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL, null);

        verify(mAssistants, times(1)).upgradeUserSet();
        assertTrue(mAssistants.mIsUserChanged.get(0));
    }

    @Test
    public void testReadXml_upgradeUserSet_preS_VersionThree() throws Exception {
        String xml = "<enabled_assistants version=\"3\" defaults=\"b/b\">"
                + "<service_listing approved=\"\" user=\"0\" primary=\"true\""
                + "user_set=\"true\"/>"
                + "</enabled_assistants>";

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);

        parser.nextTag();
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL, null);

        verify(mAssistants, times(0)).upgradeUserSet();
        assertTrue(isUserSetServicesEmpty(mAssistants, 0));
        assertTrue(mAssistants.mIsUserChanged.get(0));
    }

    @Test
    public void testReadXml_upgradeUserSet_preS_VersionOne() throws Exception {
        String xml = "<enabled_assistants version=\"1\" defaults=\"b/b\">"
                + "<service_listing approved=\"\" user=\"0\" primary=\"true\""
                + "user_set=\"true\"/>"
                + "</enabled_assistants>";

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);

        parser.nextTag();
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL, null);

        verify(mAssistants, times(0)).upgradeUserSet();
        assertTrue(isUserSetServicesEmpty(mAssistants, 0));
        assertTrue(mAssistants.mIsUserChanged.get(0));
    }

    @Test
    public void testReadXml_upgradeUserSet_preS_noUserSet() throws Exception {
        String xml = "<enabled_assistants version=\"3\" defaults=\"b/b\">"
                + "<service_listing approved=\"b/b\" user=\"0\" primary=\"true\"/>"
                + "</enabled_assistants>";

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);

        parser.nextTag();
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL, null);

        verify(mAssistants, times(1)).upgradeUserSet();
        assertTrue(isUserSetServicesEmpty(mAssistants, 0));
        assertFalse(mAssistants.mIsUserChanged.get(0));
    }

    @Test
    public void testReadXml_upgradeUserSet_preS_noUserSet_diffDefault() throws Exception {
        String xml = "<enabled_assistants version=\"3\" defaults=\"a/b\">"
                + "<service_listing approved=\"b/b\" user=\"" + mZero.id + "\" primary=\"true\"/>"
                + "</enabled_assistants>";

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);

        parser.nextTag();
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL, null);

        verify(mAssistants, times(1)).upgradeUserSet();
        assertTrue(isUserSetServicesEmpty(mAssistants, mZero.id));
        assertFalse(mAssistants.mIsUserChanged.get(0));
        assertEquals(new ArraySet<>(Arrays.asList(new ComponentName("a", "b"))),
                mAssistants.getDefaultComponents());
        assertEquals(Arrays.asList(new ComponentName("b", "b")),
                mAssistants.getAllowedComponents(mZero.id));
    }

    @Test
    public void testReadXml_multiApproved() throws Exception {
        String xml = "<enabled_assistants version=\"4\" defaults=\"b/b\">"
                + "<service_listing approved=\"a/a:b/b\" user=\"0\" primary=\"true\""
                + "user_changed=\"true\"/>"
                + "</enabled_assistants>";

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);

        parser.nextTag();
        mAssistants.readXml(parser, null, false, USER_ALL, null);

        assertEquals(1, mAssistants.getAllowedComponents(0).size());
        assertEquals(new ArrayList(Arrays.asList(new ComponentName("a", "a"))),
                mAssistants.getAllowedComponents(0));
    }

    @Test
    public void testXmlUpgradeExistingApprovedComponents() throws Exception {
        String xml = "<enabled_assistants version=\"2\" defaults=\"b\\b\">"
                + "<service_listing approved=\"b/b\" user=\"" + mZero.id + "\" primary=\"true\" />"
                + "</enabled_assistants>";

        TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);
        parser.nextTag();
        mAssistants.readXml(parser, null, false, USER_ALL, null);

        verify(mNm, never()).setDefaultAssistantForUser(anyInt());

        verify(mAssistants, times(1)).addApprovedList(
                new ComponentName("b", "b").flattenToString(), mZero.id, true, "");
    }

    @Test
    public void testSetPackageOrComponentEnabled_onlyOnePackage() throws Exception {
        ComponentName component1 = ComponentName.unflattenFromString("package/Component1");
        ComponentName component2 = ComponentName.unflattenFromString("package/Component2");
        mAssistants.setPackageOrComponentEnabled(component1.flattenToString(), mZero.id, true,
                true, true);
        verify(mNm, never()).setNotificationAssistantAccessGrantedForUserInternal(
                any(ComponentName.class), eq(mZero.id), anyBoolean(), anyBoolean());

        mAssistants.setPackageOrComponentEnabled(component2.flattenToString(), mZero.id, true,
                true, true);
        verify(mNm, times(1)).setNotificationAssistantAccessGrantedForUserInternal(
                component1, mZero.id, false, true);
    }

    @Test
    public void testSetPackageOrComponentEnabled_samePackage() throws Exception {
        ComponentName component1 = ComponentName.unflattenFromString("package/Component1");
        mAssistants.setPackageOrComponentEnabled(component1.flattenToString(), mZero.id, true,
                true, true);
        mAssistants.setPackageOrComponentEnabled(component1.flattenToString(), mZero.id, true,
                true, true);
        verify(mNm, never()).setNotificationAssistantAccessGrantedForUserInternal(
                any(ComponentName.class), eq(mZero.id), anyBoolean(), anyBoolean());
    }

    @Test
    public void testLoadDefaultsFromConfig() {
        ComponentName oldDefaultComponent = ComponentName.unflattenFromString("a/b");
        ComponentName newDefaultComponent = ComponentName.unflattenFromString("package/Component2");

        doReturn(new ArraySet<>(Arrays.asList(oldDefaultComponent, newDefaultComponent)))
                .when(mAssistants).queryPackageForServices(any(), anyInt(), anyInt());
        // Test loadDefaultsFromConfig() add the config value to mDefaultComponents instead of
        // mDefaultFromConfig
        when(mContext.getResources().getString(
                com.android.internal.R.string.config_defaultAssistantAccessComponent))
                .thenReturn(oldDefaultComponent.flattenToString());
        mAssistants.loadDefaultsFromConfig();
        assertEquals(new ArraySet<>(Arrays.asList(oldDefaultComponent)),
                mAssistants.getDefaultComponents());
        assertNull(mAssistants.mDefaultFromConfig);

        // Test loadDefaultFromConfig(false) only updates the mDefaultFromConfig
        when(mContext.getResources().getString(
                com.android.internal.R.string.config_defaultAssistantAccessComponent))
                .thenReturn(newDefaultComponent.flattenToString());
        mAssistants.loadDefaultsFromConfig(false);
        assertEquals(new ArraySet<>(Arrays.asList(oldDefaultComponent)),
                mAssistants.getDefaultComponents());
        assertEquals(newDefaultComponent, mAssistants.getDefaultFromConfig());

        // Test resetDefaultFromConfig updates the mDefaultComponents to new config value
        mAssistants.resetDefaultFromConfig();
        assertEquals(new ArraySet<>(Arrays.asList(newDefaultComponent)),
                mAssistants.getDefaultComponents());
        assertEquals(newDefaultComponent, mAssistants.getDefaultFromConfig());
    }

    @Test
    public void testNASSettingUpgrade_userNotSet_differentDefaultNAS() {
        ComponentName oldDefaultComponent = ComponentName.unflattenFromString("package/Component1");
        ComponentName newDefaultComponent = ComponentName.unflattenFromString("package/Component2");

        // pretend migration isn't done
        for (UserInfo user : mUsers) {
            Settings.Secure.putIntForUser(getContext().getContentResolver(),
                    Settings.Secure.NAS_SETTINGS_UPDATED, 0, user.id);
        }
        doReturn(new ArraySet<>(Arrays.asList(newDefaultComponent)))
                .when(mAssistants).queryPackageForServices(any(), anyInt(), anyInt());
        when(mContext.getResources().getString(
                com.android.internal.R.string.config_defaultAssistantAccessComponent))
                .thenReturn(newDefaultComponent.flattenToString());

        // User hasn't set the default NAS, set the oldNAS as the default with userSet=false here.
        mAssistants.setPackageOrComponentEnabled(oldDefaultComponent.flattenToString(),
                mZero.id, true, true /*enabled*/, false /*userSet*/);
        // The migration for userSet==false happens in resetDefaultAssistantsIfNecessary
        mAssistants.resetDefaultAssistantsIfNecessary();

        // Verify the migration happened: setDefaultAssistantForUser should be called to
        // update defaults

        verify(mNm, times(2)).setNASMigrationDone(anyInt());
        verify(mNm, times(1)).setDefaultAssistantForUser(eq(mZero.id));
        assertEquals(new ArraySet<>(Arrays.asList(newDefaultComponent)),
                mAssistants.getDefaultComponents());

        // Test resetDefaultAssistantsIfNecessary again since it will be called on every reboot
        mAssistants.resetDefaultAssistantsIfNecessary();

        // The migration should not happen again, the invoke time for migration should not increase
        verify(mNm, times(2)).setNASMigrationDone(anyInt());
        // The invoke time outside migration part should increase by 1
        verify(mNm, times(2)).setDefaultAssistantForUser(eq(mZero.id));
    }

    @Test
    public void testNASSettingUpgrade_userNotSet_sameDefaultNAS() {
        ComponentName defaultComponent = ComponentName.unflattenFromString("package/Component1");

        for (UserInfo user : mUsers) {
            Settings.Secure.putIntForUser(getContext().getContentResolver(),
                    Settings.Secure.NAS_SETTINGS_UPDATED, 0, user.id);
        }
        doReturn(new ArraySet<>(Arrays.asList(defaultComponent)))
                .when(mAssistants).queryPackageForServices(any(), anyInt(), anyInt());
        when(mContext.getResources().getString(
                com.android.internal.R.string.config_defaultAssistantAccessComponent))
                .thenReturn(defaultComponent.flattenToString());

        // User hasn't set the default NAS, set the oldNAS as the default with userSet=false here.
        mAssistants.setPackageOrComponentEnabled(defaultComponent.flattenToString(),
                mZero.id, true, true /*enabled*/, false /*userSet*/);

        // The migration for userSet==false happens in resetDefaultAssistantsIfNecessary
        mAssistants.resetDefaultAssistantsIfNecessary();

        verify(mNm, times(1)).setNASMigrationDone(eq(mZero.id));
        verify(mNm, times(1)).setDefaultAssistantForUser(eq(mZero.id));
        assertEquals(new ArraySet<>(Arrays.asList(defaultComponent)),
                mAssistants.getDefaultComponents());
    }

    @Test
    public void testNASSettingUpgrade_userNotSet_defaultNASNone() {
        ComponentName oldDefaultComponent = ComponentName.unflattenFromString("package/Component1");
        for (UserInfo user : mUsers) {
            Settings.Secure.putIntForUser(getContext().getContentResolver(),
                    Settings.Secure.NAS_SETTINGS_UPDATED, 0, user.id);
        }
        doReturn(new ArraySet<>(Arrays.asList(oldDefaultComponent)))
                .when(mAssistants).queryPackageForServices(any(), anyInt(), anyInt());
        // New default is none
        when(mContext.getResources().getString(
                com.android.internal.R.string.config_defaultAssistantAccessComponent))
                .thenReturn("");

        // User hasn't set the default NAS, set the oldNAS as the default with userSet=false here.
        mAssistants.setPackageOrComponentEnabled(oldDefaultComponent.flattenToString(),
                mZero.id, true, true /*enabled*/, false /*userSet*/);

        // The migration for userSet==false happens in resetDefaultAssistantsIfNecessary
        mAssistants.resetDefaultAssistantsIfNecessary();

        verify(mNm, times(1)).setNASMigrationDone(eq(mZero.id));
        verify(mNm, times(1)).setDefaultAssistantForUser(eq(mZero.id));
        assertEquals(new ArraySet<>(), mAssistants.getDefaultComponents());
    }

    @Test
    public void testSetAdjustmentTypeSupportedState() throws Exception {
        int userId = ActivityManager.getCurrentUser();

        assertThat(mAssistants.getUnsupportedAdjustments(userId).size()).isEqualTo(0);

        mNm.getBinderService().setAdjustmentTypeSupportedState(
                mNm.mNas, Adjustment.KEY_NOT_CONVERSATION, false);

        assertThat(mAssistants.getUnsupportedAdjustments(userId)).contains(
                Adjustment.KEY_NOT_CONVERSATION);
        assertThat(mAssistants.getUnsupportedAdjustments(userId).size()).isEqualTo(1);
        verify(mContext).sendBroadcastAsUser(eqIntent(
                new Intent(SUPPORTED_NAS_ADJUSTMENT_KEYS_CHANGED)
                        .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT)),
                eq(UserHandle.SYSTEM), eq(STATUS_BAR_SERVICE));
    }

    @Test
    public void testSetAdjustmentTypeSupportedState_readWriteXml_entries() throws Exception {
        int userId = ActivityManager.getCurrentUser();

        mNm.getBinderService().setAdjustmentTypeSupportedState(
                mNm.mNas, Adjustment.KEY_NOT_CONVERSATION, false);

        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.getUnsupportedAdjustments(userId)).contains(
                Adjustment.KEY_NOT_CONVERSATION);
        assertThat(mAssistants.getUnsupportedAdjustments(userId).size()).isEqualTo(1);
    }

    @Test
    public void testSetAdjustmentTypeSupportedState_readWriteXml_empty() throws Exception {
        int userId = ActivityManager.getCurrentUser();

        mAssistants.loadDefaultsFromConfig(true);
        mAssistants.setPackageOrComponentEnabled(mCn.flattenToString(), userId, true,
                true, true);
        ComponentName current = CollectionUtils.firstOrNull(
                mAssistants.getAllowedComponents(userId));
        assertNotNull(current);

        writeXmlAndReload(USER_ALL);
        assertThat(mAssistants.getUnsupportedAdjustments(userId).size()).isEqualTo(0);
    }

    @Test
    public void testDisallowAdjustmentKey() {
        mAssistants.disallowAdjustmentKey(mZero.id, Adjustment.KEY_RANKING_SCORE);
        assertThat(mAssistants.getAllowedAssistantAdjustments(mZero.id))
                .doesNotContain(Adjustment.KEY_RANKING_SCORE);
        assertThat(mAssistants.getAllowedAssistantAdjustments(mZero.id)).contains(KEY_TYPE);

        // should not affect other (full) users
        assertThat(mAssistants.getAllowedAssistantAdjustments(13)).contains(
                Adjustment.KEY_RANKING_SCORE);
    }

    @Test
    public void testAllowAdjustmentKey() {
        mAssistants.disallowAdjustmentKey(mZero.id, Adjustment.KEY_RANKING_SCORE);
        assertThat(mAssistants.getAllowedAssistantAdjustments(mZero.id))
                .doesNotContain(Adjustment.KEY_RANKING_SCORE);
        mAssistants.allowAdjustmentKey(mZero.id, Adjustment.KEY_RANKING_SCORE);
        assertThat(mAssistants.getAllowedAssistantAdjustments(mZero.id))
                .contains(Adjustment.KEY_RANKING_SCORE);
    }

    @Test
    public void testClassificationAdjustments_readWriteXml_userSetStateMaintained()
            throws Exception {
        // Turn on KEY_TYPE classification for (parent) but not (managed profile)
        mNm.getBinderService().allowAssistantAdjustment(mZero.id, Adjustment.KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(Adjustment.KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).doesNotContain(Adjustment.KEY_TYPE);

        // reload from XML; default state should persist
        if (nmContextualDisplayLaunch()) {
            writePolicyAndRulesAndReload();
        } else {
            writeXmlAndReload(USER_ALL);
        }

        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(Adjustment.KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).doesNotContain(Adjustment.KEY_TYPE);

        mNm.getBinderService().allowAssistantAdjustment(
                mZeroManagedProfile.id, Adjustment.KEY_TYPE);
        assertThat(mAssistants.isAdjustmentAllowed(mZeroManagedProfile.id, Adjustment.KEY_TYPE))
                .isTrue();

        // now that it's been set, we should still retain this information after XML reload
        writeXmlAndReload(USER_ALL);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).contains(Adjustment.KEY_TYPE);
    }

    @Test
    public void testClassificationAdjustment_managedProfileDefaultsOff() throws Exception {
        // Turn on KEY_TYPE classification for mUserId (parent) but not the profile
        mNm.getBinderService().allowAssistantAdjustment(mZero.id, KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).doesNotContain(KEY_TYPE);

        // Check this doesn't apply to other adjustments if they default to allowed
        mNm.getBinderService().allowAssistantAdjustment(mZero.id, KEY_IMPORTANCE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).contains(KEY_IMPORTANCE);

        // now turn on classification for the profile user directly
        mNm.getBinderService().allowAssistantAdjustment(mZeroManagedProfile.id, KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).contains(KEY_TYPE);
    }

    @Test
    public void testIsAdjustmentAllowed_profileUser_offIfParentOff() throws Exception {
        // Even if an adjustment is allowed for a profile user, it should not be considered allowed
        // if the profile's parent has that adjustment disabled.
        mNm.getBinderService().allowAssistantAdjustment(
                mZeroManagedProfile.id, Adjustment.KEY_TYPE);
        mNm.getBinderService().allowAssistantAdjustment(mSecondary.id, Adjustment.KEY_TYPE);
        mNm.getBinderService().disallowAssistantAdjustment(mZero.id, Adjustment.KEY_TYPE);

        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .doesNotContain(Adjustment.KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).doesNotContain(Adjustment.KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mSecondary.id))
                .contains(Adjustment.KEY_TYPE);

        // Now turn it back on for the parent; it should be considered allowed for the profile
        // (for which it was already on).
        mNm.getBinderService().allowAssistantAdjustment(mZero.id, Adjustment.KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(Adjustment.KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).contains(Adjustment.KEY_TYPE);
    }


    @Test
    public void testDisallowAdjustmentKey_readWriteXml_entries() throws Exception {
        int userId = ActivityManager.getCurrentUser();

        mAssistants.loadDefaultsFromConfig(true);
        mAssistants.disallowAdjustmentKey(userId, KEY_IMPORTANCE);

        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.getAllowedAssistantAdjustments(userId)).contains(
                Adjustment.KEY_NOT_CONVERSATION);
        assertThat(mAssistants.getAllowedAssistantAdjustments(userId)).doesNotContain(
                KEY_IMPORTANCE);
    }

    @Test
    public void testDefaultAllowedAdjustments_readWriteXml_entries() throws Exception {
        mAssistants.loadDefaultsFromConfig(true);

        writeXmlAndReload(USER_ALL);

        ArrayList<String> expected = new ArrayList<>(List.of(DEFAULT_ALLOWED_ADJUSTMENTS));
        expected.remove(KEY_SUMMARIZATION);

        assertThat(mAssistants.getAllowedAssistantAdjustments(mZero.id))
                .containsExactlyElementsIn(expected);
    }

    @Test
    public void testSetAdjustmentSupportedForPackage_allowsAndDenies() {
        // Given that a package (for user 0) is allowed to have summarization adjustments
        String key = KEY_SUMMARIZATION;
        String allowedPackage = "allowed.package";

        assertThat(
                mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, allowedPackage)).isTrue();
        assertThat(mAssistants.getAdjustmentDeniedPackages(mZero.id, key)).isEmpty();

        // and also for other users: one unrelated, one profile of the full user
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZeroProfile.id, key, allowedPackage))
                .isTrue();
        assertThat(mAssistants.getAdjustmentDeniedPackages(mZeroProfile.id, key)).isEmpty();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(11, key, allowedPackage)).isTrue();
        assertThat(mAssistants.getAdjustmentDeniedPackages(11, key)).isEmpty();

        // Set type adjustment disallowed for this package
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, key, allowedPackage, false);

        // Then the package is marked as denied
        assertThat(
                mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, allowedPackage)).isFalse();
        assertThat(mAssistants.getAdjustmentDeniedPackages(mZero.id, key)).asList()
                .containsExactly(allowedPackage);

        // but not for a different user, not even the profile
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZeroProfile.id, key, allowedPackage))
                .isTrue();
        assertThat(mAssistants.getAdjustmentDeniedPackages(mZeroProfile.id, key)).isEmpty();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(11, key, allowedPackage)).isTrue();
        assertThat(mAssistants.getAdjustmentDeniedPackages(11, key)).isEmpty();

        // Set type adjustment allowed again
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, key, allowedPackage, true);

        // Then the package is marked as allowed again
        assertThat(
                mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, allowedPackage)).isTrue();
        assertThat(mAssistants.getAdjustmentDeniedPackages(mZero.id, key)).isEmpty();
    }

    @Test
    public void testSetAdjustmentSupportedForPackage_deniesMultiple() {
        // Given packages not allowed to have summarizations applied
        String key = KEY_SUMMARIZATION;
        String deniedPkg1 = "denied.Pkg1";
        String deniedPkg2 = "denied.Pkg2";
        String deniedPkg3 = "denied.Pkg3";
        // Set type adjustment disallowed for these packages
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, key, deniedPkg1, false);
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, key, deniedPkg2, false);
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, key, deniedPkg3, false);

        // Then the packages are marked as denied
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, deniedPkg1)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, deniedPkg2)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, deniedPkg3)).isFalse();
        assertThat(mAssistants.getAdjustmentDeniedPackages(mZero.id, key)).asList()
                .containsExactlyElementsIn(List.of(deniedPkg1, deniedPkg2, deniedPkg3));

        // And when we re-allow one of them,
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, key, deniedPkg2, true);

        // Then the rest of the original packages are still marked as denied.
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, deniedPkg1)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, deniedPkg2)).isTrue();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, deniedPkg3)).isFalse();
        assertThat(mAssistants.getAdjustmentDeniedPackages(mZero.id, key)).asList()
                .containsExactlyElementsIn(List.of(deniedPkg1, deniedPkg3));
    }

    @Test
    public void testSetAdjustmentSupportedForPackage_readWriteXml_singleAdjustment()
            throws Exception {
        mAssistants.loadDefaultsFromConfig(true);
        String key = KEY_SUMMARIZATION;
        String deniedPkg1 = "denied.Pkg1";
        String allowedPkg2 = "allowed.Pkg2";
        String deniedPkg3 = "denied.Pkg3";
        // Set summarization adjustment disallowed or allowed for these packages
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, key, deniedPkg1, false);
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, key, allowedPkg2, true);
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, key, deniedPkg3, false);

        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, deniedPkg1)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, allowedPkg2)).isTrue();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, deniedPkg3)).isFalse();
    }

    @Test
    public void testSetAdjustmentSupportedForPackage_readWriteXml_multipleAdjustments()
            throws Exception {
        mAssistants.loadDefaultsFromConfig(true);
        String deniedPkg1 = "denied.Pkg1";
        String deniedPkg2 = "denied.Pkg2";
        String deniedPkg3 = "denied.Pkg3";
        // Set summarization adjustment disallowed these packages
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, KEY_SUMMARIZATION, deniedPkg1,
                false);
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, KEY_SUMMARIZATION, deniedPkg3,
                false);
        // Set smart actions adjustment disallowed for these packages
        mAssistants.setAdjustmentSupportedForPackage(
                mZero.id, KEY_CONTEXTUAL_ACTIONS, deniedPkg2, false);

        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, KEY_SUMMARIZATION,
                deniedPkg1)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, KEY_CONTEXTUAL_ACTIONS,
                deniedPkg2)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, KEY_SUMMARIZATION,
                deniedPkg3)).isFalse();
    }

    @Test
    public void testSetAdjustmentSupportedForPackage_readWriteXml_multipleUsers()
            throws Exception {
        mAssistants.loadDefaultsFromConfig(true);
        String deniedPkg0 = "denied.Pkg1";
        String deniedPkg10 = "denied.Pkg10";
        String deniedPkg11 = "denied.Pkg11";
        // Set summarization adjustment disallowed for each of these packages for different users
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, KEY_SUMMARIZATION, deniedPkg0,
                false);
        mAssistants.setAdjustmentSupportedForPackage(
                mZeroManagedProfile.id, KEY_SUMMARIZATION, deniedPkg11, false);
        // Set classification adjustment disallowed for this package/user
        mAssistants.setAdjustmentSupportedForPackage(
                mZeroProfile.id, KEY_CONTEXTUAL_ACTIONS, deniedPkg10, false);

        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, KEY_SUMMARIZATION,
                deniedPkg0)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(
                mZeroProfile.id, KEY_CONTEXTUAL_ACTIONS, deniedPkg10)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(
                mZeroManagedProfile.id, KEY_SUMMARIZATION, deniedPkg11)).isFalse();

        // but they don't affect other users
        assertThat(mAssistants.isAdjustmentAllowedForPackage(
                mZeroManagedProfile.id, KEY_SUMMARIZATION, deniedPkg0)).isTrue();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, KEY_CONTEXTUAL_ACTIONS,
                deniedPkg10)).isTrue();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZeroProfile.id, KEY_SUMMARIZATION,
                deniedPkg11)).isTrue();
    }

    @Test
    @SuppressWarnings("GuardedBy")
    public void testPullAdjustmentPreferencesStats_fillsOutStatsEvent()
            throws Exception {
        // Scenario setup: the total list of users is system user, profile of system user, managed
        // profile of system user, full secondary user
        //   * user system has both bundles (KEY_TYPE) + summaries (KEY_SUMMARIZATION) supported
        //      * KEY_TYPE is disallowed; KEY_SUMMARIZATION allowed
        //   * user full secondary has only KEY_TYPE, which is allowed
        //   * user managed profile has only KEY_SUMMARIZATION, disallowed
        //   * other users are non-managed profiles; should not be logged even if both types are
        //     supported
        for (int user : List.of(mZero.id, mZeroProfile.id, mZeroManagedProfile.id, mSecondary.id)) {
            // users with KEY_TYPE supported: all but mZeroManagedProfile
            mAssistants.setAdjustmentKeySupportedState(user, KEY_TYPE, true);
        }
        mAssistants.setAdjustmentKeySupportedState(mZeroManagedProfile.id, KEY_TYPE, false);

        for (int user : List.of(mZero.id, mZeroProfile.id, mZeroManagedProfile.id)) {
            // users with KEY_SUMMARIZATION supported: all but mSecondary.id
            mAssistants.setAdjustmentKeySupportedState(user, KEY_SUMMARIZATION, true);
        }
        mAssistants.setAdjustmentKeySupportedState(mSecondary.id, KEY_SUMMARIZATION, false);

        // permissions for adjustments as described above
        mNm.getBinderService().disallowAssistantAdjustment(mZero.id, KEY_TYPE);
        mNm.getBinderService().allowAssistantAdjustment(mZero.id, KEY_SUMMARIZATION);
        mNm.getBinderService().disallowAssistantAdjustment(
                mZeroManagedProfile.id, KEY_SUMMARIZATION);
        mNm.getBinderService().allowAssistantAdjustment(mSecondary.id, KEY_TYPE);

        // Enable specific bundle types for user mZero
        mNm.getBinderService().setAssistantClassificationTypeStateForUser(
                mZero.id, TYPE_SOCIAL_MEDIA, false);
        mNm.getBinderService().setAssistantClassificationTypeStateForUser(
                mZero.id, TYPE_PROMOTION, false);
        mNm.getBinderService().setAssistantClassificationTypeStateForUser(
                mZero.id, TYPE_NEWS, true);
        mNm.getBinderService().setAssistantClassificationTypeStateForUser(
                mZero.id, TYPE_CONTENT_RECOMMENDATION, true);

        // and different ones for user mSecondary.id
        mNm.getBinderService().setAssistantClassificationTypeStateForUser(
                mSecondary.id, TYPE_SOCIAL_MEDIA, true);
        mNm.getBinderService().setAssistantClassificationTypeStateForUser(
                mSecondary.id, TYPE_PROMOTION, false);
        mNm.getBinderService().setAssistantClassificationTypeStateForUser(
                mSecondary.id, TYPE_NEWS, false);
        mNm.getBinderService().setAssistantClassificationTypeStateForUser(
                mSecondary.id, TYPE_CONTENT_RECOMMENDATION, false);

        // When pullBundlePreferencesStats is run with the given preferences
        ArrayList<StatsEvent> events = new ArrayList<>();
        mAssistants.pullAdjustmentPreferencesStats(events);

        // The StatsEvent is filled out with the expected NotificationAdjustmentPreferences values.
        // We expect 2 atoms for user 0, 1 atom for user 11, and 1 for user 13.
        assertThat(events.size()).isEqualTo(4);

        // Collect all the resulting atoms in a map of user ID -> adjustment type (enum) -> atom to
        // confirm that we have the correct set of atoms without enforcing anything about ordering.
        Map<Integer, Map<Integer, NotificationAdjustmentPreferences>> atoms =
                new ArrayMap<>();
        for (StatsEvent event : events) {
            AtomsProto.Atom atom = StatsEventTestUtils.convertToAtom(event);
            NotificationAdjustmentPreferences p = parsePulledAtom(atom);
            int userId = p.getUserId();
            atoms.putIfAbsent(userId, new ArrayMap<>());
            atoms.get(userId).put(p.getKey().getNumber(), p);
        }

        // user mZero, KEY_TYPE (bundles)
        assertThat(atoms).containsKey(mZero.id);
        Map<Integer, NotificationAdjustmentPreferences> userZeroAtoms = atoms.get(mZero.id);
        assertThat(userZeroAtoms).containsKey(NotificationProtoEnums.KEY_TYPE);
        NotificationAdjustmentPreferences p0b = userZeroAtoms.get(NotificationProtoEnums.KEY_TYPE);
        assertThat(p0b.getAdjustmentAllowed()).isFalse();
        assertThat(p0b.getAllowedBundleTypesList()).containsExactly(
                BundleTypes.forNumber(NotificationProtoEnums.TYPE_NEWS),
                BundleTypes.forNumber(NotificationProtoEnums.TYPE_CONTENT_RECOMMENDATION));

        // user mZero, KEY_SUMMARIZATION
        assertThat(userZeroAtoms).containsKey(NotificationProtoEnums.KEY_SUMMARIZATION);
        NotificationAdjustmentPreferences p0s = userZeroAtoms.get(
                NotificationProtoEnums.KEY_SUMMARIZATION);
        assertThat(p0s.getAdjustmentAllowed()).isTrue();

        // user mZeroManagedProfile, KEY_SUMMARIZATION
        assertThat(atoms).containsKey(mZeroManagedProfile.id);
        assertThat(atoms.get(mZeroManagedProfile.id)).containsKey(
                NotificationProtoEnums.KEY_SUMMARIZATION);
        NotificationAdjustmentPreferences p11s = atoms.get(mZeroManagedProfile.id).get(
                NotificationProtoEnums.KEY_SUMMARIZATION);
        assertThat(p11s.getAdjustmentAllowed()).isFalse();

        // user mSecondary, KEY_TYPE (bundles)
        assertThat(atoms).containsKey(mSecondary.id);
        assertThat(atoms.get(mSecondary.id)).containsKey(NotificationProtoEnums.KEY_TYPE);
        NotificationAdjustmentPreferences p13b = atoms.get(mSecondary.id).get(
                NotificationProtoEnums.KEY_TYPE);
        assertThat(p13b.getAdjustmentAllowed()).isTrue();
        assertThat(p13b.getAllowedBundleTypesList())
                .containsExactly(BundleTypes.forNumber(NotificationProtoEnums.TYPE_SOCIAL_MEDIA));
    }

    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY)
    @Test
    public void testDynamicBundles_createReadDelete() {
        DynamicBundle expected = new DynamicBundle(111, "sports spoilers");
        mAssistants.createDynamicBundle(
                0, expected.getDynamicBundleType(), expected.getBundleName());

        assertThat(mAssistants.getDynamicBundles(0)).containsExactly(expected);
    }

    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY)
    @Test
    public void testDynamicBundles_primarySharesWithProfiles() {
        DynamicBundle expected = new DynamicBundle(111, "sports spoilers");
        mAssistants.createDynamicBundle(
                mZeroProfile.id, expected.getDynamicBundleType(), expected.getBundleName());

        assertThat(mAssistants.getDynamicBundles(mZero.id)).containsExactly(expected);
    }

    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY)
    @Test
    public void testDynamicBundles_xml() throws Exception {
        DynamicBundle expected = new DynamicBundle(111, "sports spoilers");
        DynamicBundle expected2 = new DynamicBundle(112, "shipping emails");
        mAssistants.createDynamicBundle(
                mZeroProfile.id, expected.getDynamicBundleType(), expected.getBundleName());
        mAssistants.createDynamicBundle(
                mZero.id, expected2.getDynamicBundleType(), expected2.getBundleName());

        if (nmContextualDisplayLaunch()) {
            writePolicyAndRulesAndReload();
        } else {
            writeXmlAndReload(USER_ALL);
        }

        assertThat(mAssistants.getDynamicBundles(mZero.id)).containsExactly(expected, expected2);
    }

    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY)
    @Test
    public void testDynamicBundles_getNameFromId() {
        DynamicBundle expected = new DynamicBundle(111, "sports spoilers");
        mAssistants.createDynamicBundle(
                mZero.id, expected.getDynamicBundleType(), expected.getBundleName());

        assertThat(mAssistants.getDynamicBundleName(mZero.id, 0)).isNull();
        assertThat(mAssistants.getDynamicBundleName(mZero.id, 111))
                .isEqualTo(expected.getBundleName());
    }

    @Test
    public void testDisallowClassificationType_readWriteXml() throws Exception {
        mRuleManager.setAssistantClassificationTypeState(mZero.id, TYPE_SOCIAL_MEDIA, false);
        mRuleManager.setAssistantClassificationTypeState(mZero.id, TYPE_PROMOTION, false);
        mRuleManager.setAssistantClassificationTypeState(mZero.id, TYPE_NEWS, true);
        mRuleManager.setAssistantClassificationTypeState(mZero.id, TYPE_CONTENT_RECOMMENDATION,
                true);

        if (nmContextualDisplayLaunch()) {
            writePolicyAndRulesAndReload();
        } else {
            writeXmlAndReload(USER_ALL);
        }

        assertThat(mRuleManager.getAllowedClassificationTypes(mZero.id))
                .containsExactlyElementsIn(List.of(TYPE_NEWS, TYPE_CONTENT_RECOMMENDATION));
    }

    @Test
    public void testSetClassificationSupportedForPackage_readWriteXml()
            throws Exception {
        mRuleManager.setClassificationSupportedForPackage(mZero.id, PKG_O, false);
        mRuleManager.setClassificationSupportedForPackage(mZero.id, PKG_P, false);

        if (nmContextualDisplayLaunch()) {
            writePolicyAndRulesAndReload();
        } else {
            writeXmlAndReload(USER_ALL);
        }

        assertThat(mRuleManager.isClassificationAllowedForPackage(mZero.id, PKG_O)).isFalse();
        assertThat(mRuleManager.isClassificationAllowedForPackage(mZero.id, PKG_P)).isFalse();
        assertThat(mRuleManager.isClassificationAllowedForPackage(mZero.id, PKG_R)).isTrue();
    }

    // Helper function for getting the NotificationAdjustmentPreferences pulled atom data from a
    // given Atom object that's expected to have this extension.
    private NotificationAdjustmentPreferences parsePulledAtom(AtomsProto.Atom atom)
            throws IOException {
        // The returned atom does not have external extensions registered.
        // So we serialize and then deserialize with extensions registered.
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CodedOutputStream codedos = CodedOutputStream.newInstance(outputStream);
        atom.writeTo(codedos);
        codedos.flush();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        CodedInputStream codedis = CodedInputStream.newInstance(inputStream);
        AtomsProto.Atom parsedAtom = AtomsProto.Atom.parseFrom(codedis, mRegistry);
        assertTrue(parsedAtom.hasExtension(
                NotificationExtensionAtoms.notificationAdjustmentPreferences));
        return parsedAtom.getExtension(
                NotificationExtensionAtoms.notificationAdjustmentPreferences);
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testStaticBundleRule_freshInstall() throws Exception {
        initFromPolicyAndRuleXml(null, null);
        NotificationRule actual = mNm.mService.getNotificationRule(
                mUserId, RESERVED_ID_STATIC_BUNDLES);
        assertThat(actual.getConditions()).isEmpty();
        assertThat(actual.getFilters().size()).isEqualTo(1);
        assertThat(actual.getFilters().getFirst().getUsers())
                .containsExactly(UserHandle.of(mUserId));
        assertThat(actual.getFilters().getFirst().getStaticBundleTypes())
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION);
        assertThat(actual.getAction().getPrimaryAction()).isEqualTo(PRIMARY_ACTION_BUNDLE);
        assertThat(actual.getAction().getModeBreakthroughIds()).isEmpty();
        assertThat(actual.getAction().getDynamicBundleEmojiIcon()).isNull();
        assertThat(actual.getAction().getDynamicBundleName()).isNull();
        assertThat(actual.getAction().getLightColorOverride()).isEqualTo(0);
        assertThat(actual.getAction().getSoundHapticOverride()).isNull();
        assertThat(actual.canBeDisabled()).isTrue();
        assertThat(actual.isEnabled()).isTrue();
        assertThat(actual.getEditIntentAction()).contains("BUNDLE");
    }

    private void initFromPolicyXml(String policyXml) throws Exception {
        mNm = spy(new TestableNotificationManagerService(mContext, mTestableLooper));
        mNm.init(policyXml, null);
        mAssistants = mNm.mAssistants;
        mRuleManager = mNm.mNotificationRuleManager;
    }

    private void initFromPolicyAndRuleXml(String policyXml, String rulesXml) throws Exception {
        mNm = spy(new TestableNotificationManagerService(mContext, mTestableLooper));
        mNm.init(policyXml, rulesXml);
        mAssistants = mNm.mAssistants;
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testStaticBundleRule_upgradeToRule_classificationOff()
            throws Exception {
        final String preupgradeXml = "<notification-policy>"
                + "<enabled_assistants version=\"4\" defaults=\"a/b\">\n"
                + "<denied_adjustment_keys user=\"" + mZero.id + "\" types=\"key_type\" />\n"
                + "<adjustment user=\"" + mZero.id + "\" key=\"key_type\" "
                + "denied_apps=\""+ PKG_O +"\" />\n"
                + "<enabled_bundle_types user=\"" + mZero.id + "\" types=\"1,3\" />\n"
                + "<adjustment_pref_set_by_users users=\"" + mZero.id + "\" />"
                + "</enabled_assistants>"
                + "</notification-policy>";
        initFromPolicyXml(preupgradeXml);

        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_IMPORTANT_NOTIFICATIONS))
                .isNotNull();
        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_PRIORITY_CONVERSATIONS))
                .isNotNull();
        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_PROMOTED))
                .isNotNull();

        NotificationRule actual = mNm.mService.getNotificationRule(
                mUserId, RESERVED_ID_STATIC_BUNDLES);
        assertThat(actual.isEnabled()).isFalse();
        assertThat(actual.getConditions()).isEmpty();
        assertThat(actual.getFilters().size()).isEqualTo(1);
        assertThat(actual.getFilters().getFirst().getUsers()).isEmpty();
        assertThat(actual.getFilters().getFirst().getStaticBundleTypes())
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION);
        assertThat(actual.getFilters().getFirst().getExcludedPackageUids()).containsExactly(UID_O);
        assertThat(actual.getAction().getPrimaryAction()).isEqualTo(PRIMARY_ACTION_BUNDLE);
        assertThat(actual.getAction().getModeBreakthroughIds()).isEmpty();
        assertThat(actual.getAction().getDynamicBundleEmojiIcon()).isNull();
        assertThat(actual.getAction().getDynamicBundleName()).isNull();
        assertThat(actual.getAction().getLightColorOverride()).isEqualTo(0);
        assertThat(actual.getAction().getSoundHapticOverride()).isNull();
        assertThat(actual.canBeDisabled()).isTrue();
        assertThat(actual.getEditIntentAction()).contains("BUNDLE");

        assertThat(mNm.getBinderService().getAllowedClassificationTypes()).asList()
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .doesNotContain(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustments(""))
                .doesNotContain(KEY_TYPE);
        assertThat(mNm.getBinderService().getAdjustmentDeniedPackages(mZero.id, KEY_TYPE)).asList()
                .containsExactly(PKG_O);
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testStaticBundleRule_upgradeToRule_enabled_primaryOnly() throws Exception {
        final String preupgradeXml = "<notification-policy>"
                + "<enabled_assistants version=\"4\" defaults=\"a/b\">\n"
                + "<denied_adjustment_keys user=\"" + mZero.id + "\" types=\"\" />\n"
                + "<enabled_bundle_types user=\"" + mZero.id + "\" types=\"1,2,3\" />\n"
                + "<adjustment_pref_set_by_users users=\"" + mZero.id + "\" />"
                + "</enabled_assistants>"
                + "</notification-policy>";
        initFromPolicyXml(preupgradeXml);

        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_IMPORTANT_NOTIFICATIONS))
                .isNotNull();
        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_PRIORITY_CONVERSATIONS))
                .isNotNull();
        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_PROMOTED))
                .isNotNull();

        NotificationRule actual = mNm.mService.getNotificationRule(
                mUserId, RESERVED_ID_STATIC_BUNDLES);
        assertThat(actual.getFilters().getFirst().getUsers())
                .containsExactly(UserHandle.of(mZero.id));
        assertThat(actual.getFilters().getFirst().getStaticBundleTypes())
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION, TYPE_SOCIAL_MEDIA);
        assertThat(actual.isEnabled()).isTrue();

        assertThat(mNm.getBinderService().getAllowedClassificationTypes()).asList()
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION, TYPE_SOCIAL_MEDIA);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).doesNotContain(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustments(""))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAdjustmentDeniedPackages(mZero.id, KEY_TYPE))
                .asList().isEmpty();
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testStaticBundleRule_upgradeToRule_enabled_primaryOnly_userSet() throws Exception {
        final String preupgradeXml = "<notification-policy>"
                + "<enabled_assistants version=\"4\" defaults=\"a/b\">\n"
                + "<denied_adjustment_keys user=\"" + mZero.id + "\" types=\"\" />\n"
                + "<denied_adjustment_keys user=\"" + mZeroManagedProfile.id + "\""
                + " types=\"key_type\" />\n"
                + "<enabled_bundle_types user=\"" + mZero.id + "\" types=\"1,2,3\" />\n"
                + "<adjustment_pref_set_by_users users=\"" + mZero.id + ","
                + mZeroManagedProfile.id + "\" />"
                + "</enabled_assistants>"
                + "</notification-policy>";
        initFromPolicyXml(preupgradeXml);

        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_IMPORTANT_NOTIFICATIONS))
                .isNotNull();
        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_PRIORITY_CONVERSATIONS))
                .isNotNull();
        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_PROMOTED))
                .isNotNull();

        NotificationRule actual = mNm.mService.getNotificationRule(
                mUserId, RESERVED_ID_STATIC_BUNDLES);
        assertThat(actual.getFilters().getFirst().getUsers())
                .containsExactly(UserHandle.of(mZero.id));
        assertThat(actual.getFilters().getFirst().getStaticBundleTypes())
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION, TYPE_SOCIAL_MEDIA);
        assertThat(actual.isEnabled()).isTrue();

        assertThat(mNm.getBinderService().getAllowedClassificationTypes()).asList()
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION, TYPE_SOCIAL_MEDIA);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).doesNotContain(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustments(""))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAdjustmentDeniedPackages(mZero.id, KEY_TYPE))
                .asList().isEmpty();
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testStaticBundleRule_upgradeToRule_enabled_primaryAndProfile() throws Exception {
        final String preupgradeXml = "<notification-policy>"
                + "<enabled_assistants version=\"4\" defaults=\"a/b\">\n"
                + "<denied_adjustment_keys user=\"" + mZero.id + "\" types=\"\" />\n"
                + "<enabled_bundle_types user=\"" + mZero.id + "\" types=\"1,2,3\" />\n"
                + "<adjustment_pref_set_by_users users=\"" + mZero.id + "," + mZeroManagedProfile.id
                + "\" />"
                + "</enabled_assistants>"
                + "</notification-policy>";
        initFromPolicyXml(preupgradeXml);

        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_IMPORTANT_NOTIFICATIONS))
                .isNotNull();
        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_PRIORITY_CONVERSATIONS))
                .isNotNull();
        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_PROMOTED))
                .isNotNull();

        NotificationRule actual = mNm.mService.getNotificationRule(
                mUserId, RESERVED_ID_STATIC_BUNDLES);
        assertThat(actual.getFilters().getFirst().getUsers())
                .containsExactly(UserHandle.of(mZero.id), UserHandle.of(mZeroManagedProfile.id));
        assertThat(actual.getFilters().getFirst().getStaticBundleTypes())
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION, TYPE_SOCIAL_MEDIA);
        assertThat(actual.isEnabled()).isTrue();

        assertThat(mNm.getBinderService().getAllowedClassificationTypes()).asList()
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION, TYPE_SOCIAL_MEDIA);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustments(""))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAdjustmentDeniedPackages(mZero.id, KEY_TYPE))
                .asList().isEmpty();
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testStaticBundleRule_freshInstall_withRestore() throws Exception {
        // fresh install - should have default settings for static bundle rule
        NotificationRule actual = mNm.mService.getNotificationRule(
                mZero.id, RESERVED_ID_STATIC_BUNDLES);
        assertThat(actual.getConditions()).isEmpty();
        assertThat(actual.getFilters().size()).isEqualTo(1);
        assertThat(actual.getFilters().getFirst().getUsers())
                .containsExactly(UserHandle.of(mZero.id));
        assertThat(actual.getFilters().getFirst().getStaticBundleTypes())
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION);
        assertThat(actual.getAction().getPrimaryAction()).isEqualTo(PRIMARY_ACTION_BUNDLE);
        assertThat(actual.getAction().getModeBreakthroughIds()).isEmpty();
        assertThat(actual.getAction().getDynamicBundleEmojiIcon()).isNull();
        assertThat(actual.getAction().getDynamicBundleName()).isNull();
        assertThat(actual.getAction().getLightColorOverride()).isEqualTo(0);
        assertThat(actual.getAction().getSoundHapticOverride()).isNull();
        assertThat(actual.canBeDisabled()).isTrue();
        assertThat(actual.isEnabled()).isTrue();
        assertThat(actual.getEditIntentAction()).contains("BUNDLE");

        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_IMPORTANT_NOTIFICATIONS))
                .isNotNull();
        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_PRIORITY_CONVERSATIONS))
                .isNotNull();
        assertThat(mNm.mService.getNotificationRule(mZero.id, RESERVED_ID_PROMOTED))
                .isNotNull();

        final String restoreXml = "<notification-policy>"
                + "<enabled_assistants version=\"4\" defaults=\"a/b\">\n"
                + "<denied_adjustment_keys user=\"" + mZero.id + "\" types=\"\" />\n"
                + "<enabled_bundle_types user=\"" + mZero.id + "\" types=\"1,2,3\" />\n"
                + "<adjustment user=\"" + mZero.id + "\" key=\"key_type\" "
                + "denied_apps=\""+ PKG_O +"\" />\n"
                + "<adjustment_pref_set_by_users users=\"" + mZero.id + "," + mZeroManagedProfile.id
                + "\" />"
                + "</enabled_assistants>"
                + "</notification-policy>";
        mNm.getInternalService().applyRestore(
                restoreXml.getBytes(StandardCharsets.UTF_8), mZero.id, null);

         actual = mNm.mService.getNotificationRule(
                mUserId, RESERVED_ID_STATIC_BUNDLES);
        assertThat(actual.getFilters().getFirst().getUsers())
                .containsExactly(UserHandle.of(mZero.id), UserHandle.of(mZeroManagedProfile.id));
        assertThat(actual.getFilters().getFirst().getStaticBundleTypes())
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION, TYPE_SOCIAL_MEDIA);
        assertThat(actual.isEnabled()).isTrue();
        assertThat(mNm.getBinderService().getAllowedClassificationTypes()).asList()
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION, TYPE_SOCIAL_MEDIA);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustments(""))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAdjustmentDeniedPackages(mZero.id, KEY_TYPE))
                .asList().containsExactly(PKG_O);
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testStaticBundleRule_subsequentBootWithFlagEnabled() throws Exception {
        final String policyXml = "<notification-policy>"
                + "<enabled_assistants version=\"4\" defaults=\"a/b\">\n"
                + "<denied_adjustment_keys user=\"" + mZero.id + "\" types=\"\" />\n"
                + "<enabled_bundle_types user=\"" + mZero.id + "\" types=\"1,2,3\" />\n"
                + "<adjustment user=\"" + mZero.id + "\" key=\"key_type\" "
                + "denied_apps=\""+ PKG_O +"\" />\n"
                + "<adjustment_pref_set_by_users users=\"\" />"
                + "</enabled_assistants>"
                + "</notification-policy>";
        final String rulesXml = "<notification-rules version=\"1\">"
                + "<rules>\n"
                + "<rule user=\"" + mZero.id + "\" id=\"203\" enabled=\"true\" name=\"Bundle\""
                + " editIntentAction=\"android.settings.NOTIFICATION_BUNDLES\""
                + " canBeDisabled=\"true\">"
                + "<action primaryAction=\"4\" lightColor=\"0\" modeBreakthroughs=\"\" />"
                + "<filters>"
                + "<filter>"
                + "<contacts contactLevel=\"0\" conversationLevel=\"0\" />"
                + "<staticBundleType value=\"" + TYPE_CONTENT_RECOMMENDATION + "\" />\n"
                + "<staticBundleType value=\"" + TYPE_PROMOTION + "\" />\n"
                + "<excludedPackages value=\"" + UID_P + "\" />"
                + "<excludedPackages value=\"" + UID_N_MR1 + "\" />"
                + "<flags value=\"0\" />\n"
                + "<user value=\"" + mZero.id + "\" />\n"
                + "<user value=\"" + mZeroManagedProfile.id + "\" />\n"
                + "</filter>\n"
                + "</filters>\n"
                + "</rule>\n"
                + "<rule user=\"" + mZero.id + "\" id=\"204\" enabled=\"true\" name=\"Important\""
                + " canBeDisabled=\"true\">\n"
                + "<action primaryAction=\"1\" lightColor=\"0\" modeBreakthroughs=\"\" />\n"
                + " </rule>"
                + "<rule user=\"" + mZero.id + "\" id=\"201\" enabled=\"true\" name=\"Promoted\""
                + " editIntentAction=\"android.settings.MANAGE_APP_POST_PROMOTED_NOTIFICATIONS\""
                + " canBeDisabled=\"false\">\n"
                + "<action primaryAction=\"1\" lightColor=\"0\" modeBreakthroughs=\"\" />\n"
                + "<filters>\n"
                + "<filter>\n"
                + "<contacts contactLevel=\"0\" conversationLevel=\"0\" />\n"
                + "<flags value=\"262144\" />\n"
                + "</filter>\n"
                + "</filters>\n"
                + "</rule>\n"
                + "<rule user=\"" + mZero.id + "\" id=\"202\" enabled=\"true\""
                + " name=\"Conversations\""
                + " editIntentAction=\"android.settings.CONVERSATION_SETTINGS\""
                + " canBeDisabled=\"false\">\n"
                + "<action primaryAction=\"1\" lightColor=\"0\" modeBreakthroughs=\"\" />\n"
                + "<filters>\n"
                + "<filter>\n"
                + "<contacts contactLevel=\"0\" conversationLevel=\"1\" />\n"
                + "<flags value=\"0\" />\n"
                + "</filter>\n"
                + "</filters>\n"
                + "</rule>"
                + "</rules>";

        initFromPolicyAndRuleXml(policyXml, rulesXml);

        NotificationRule actual = mNm.mService.getNotificationRule(
                mUserId, RESERVED_ID_STATIC_BUNDLES);
        assertThat(actual.getFilters().getFirst().getUsers())
                .containsExactly(UserHandle.of(mZero.id), UserHandle.of(mZeroManagedProfile.id));
        assertThat(actual.getFilters().getFirst().getStaticBundleTypes())
                .containsExactly(TYPE_CONTENT_RECOMMENDATION, TYPE_PROMOTION);
        assertThat(actual.getFilters().getFirst().getExcludedPackageUids())
                .containsExactly(UID_P, UID_N_MR1);
        assertThat(actual.isEnabled()).isTrue();
        assertThat(mNm.getBinderService().getAllowedClassificationTypes()).asList()
                .containsExactly(TYPE_CONTENT_RECOMMENDATION, TYPE_PROMOTION);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(
                mZeroManagedProfile.id)).contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustments(""))
                .contains(KEY_TYPE);
        assertThat(mNm.getBinderService().getAdjustmentDeniedPackages(mZero.id, KEY_TYPE))
                .asList().containsExactly(PKG_P, PKG_N_MR1);
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testDenyMultipleAdjustmentKeys_readWriteXml()
            throws Exception {
        mNm.getBinderService().disallowAssistantAdjustment(mZero.id, KEY_SUMMARIZATION);
        mNm.getBinderService().disallowAssistantAdjustment(mZero.id, KEY_TYPE);

        writePolicyAndRulesAndReload();

        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .containsNoneIn(List.of(KEY_SUMMARIZATION, KEY_TYPE));
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testDenyClassification_readWriteXml()
            throws Exception {
        mNm.getBinderService().disallowAssistantAdjustment(mZero.id, KEY_TYPE);

        writePolicyAndRulesAndReload();

        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .doesNotContain(KEY_TYPE);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(KEY_IMPORTANCE);
    }

    @Test
    @EnableFlags(Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testDenySummarization_readWriteXml()
            throws Exception {
        mNm.getBinderService().disallowAssistantAdjustment(mZero.id, KEY_SUMMARIZATION);
        writePolicyAndRulesAndReload();

        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .doesNotContain(KEY_SUMMARIZATION);
        assertThat(mNm.getBinderService().getAllowedAssistantAdjustmentsForUser(mZero.id))
                .contains(KEY_IMPORTANCE);
    }

    @Test
    public void testAddDefaultClassificationTypesIfEmpty() throws Exception {
        mNm.getBinderService().setAssistantClassificationTypeState(TYPE_NEWS, false);
        mNm.getBinderService().setAssistantClassificationTypeState(
                TYPE_CONTENT_RECOMMENDATION, false);
        mNm.getBinderService().setAssistantClassificationTypeState(TYPE_PROMOTION, false);
        mNm.getBinderService().setAssistantClassificationTypeState(TYPE_SOCIAL_MEDIA, false);

        mNm.getBinderService().disallowAssistantAdjustment(mZero.id, KEY_TYPE);

        assertThat(mNm.getBinderService().getAllowedClassificationTypes()).asList()
                .containsExactly(TYPE_NEWS, TYPE_PROMOTION);
    }

    @Test
    public void testNoAddDefaultClassificationTypesIfNotEmpty() throws Exception {
        mNm.getBinderService().setAssistantClassificationTypeState(TYPE_NEWS, false);
        mNm.getBinderService().setAssistantClassificationTypeState(
                TYPE_CONTENT_RECOMMENDATION, false);
        mNm.getBinderService().setAssistantClassificationTypeState(TYPE_PROMOTION, false);
        mNm.getBinderService().setAssistantClassificationTypeState(TYPE_SOCIAL_MEDIA, true);

        mNm.getBinderService().disallowAssistantAdjustment(mZero.id, KEY_TYPE);

        assertThat(mNm.getBinderService().getAllowedClassificationTypes()).asList()
                .containsExactly(TYPE_SOCIAL_MEDIA);
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testNotifyNotificationRuleAdded_notifiesAssistantForUser()
            throws RemoteException {
        // when: rule added
        NotificationRule rule = new NotificationRule.Builder(123,
                new NotificationRule.Action(PRIMARY_ACTION_HIGHLIGHT)).build();
        mAssistants.notifyNotificationRuleAdded(mZero.id, rule);

        // make sure the runnables actually run
        mTestableLooper.processAllMessages();

        // then: assistant for mZero is notified, but not mSecondary
        verify(mAssistantZero, times(1)).onNotificationRuleAdded(eq(rule));
        verify(mAssistantSecondary, never()).onNotificationRuleAdded(any());

    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testNotifyNotificationRuleModified_notifiesAssistantForUser()
            throws RemoteException {
        // when: notification rule modified
        NotificationRule updated = new NotificationRule.Builder(123,
                new NotificationRule.Action(PRIMARY_ACTION_LOW)).build();
        mAssistants.notifyNotificationRuleModified(mZero.id, updated);
        mTestableLooper.processAllMessages();

        // then: assistant for mZero is notified but not mSecondary
        verify(mAssistantZero, times(1)).onNotificationRuleModified(eq(updated));
        verify(mAssistantSecondary, never()).onNotificationRuleModified(any());
    }

    @Test
    @EnableFlags(android.app.Flags.FLAG_NM_CONTEXTUAL_DISPLAY_LAUNCH)
    public void testNotifyNotificationRuleRemoved_notifiesAssistantForUser()
            throws RemoteException {
        // when: notification rule removed for mSecondary
        mAssistants.notifyNotificationRuleRemoved(mSecondary.id, 123);
        mTestableLooper.processAllMessages();

        // then: assistant for mSecondary is notified but not mZero
        verify(mAssistantZero, never()).onNotificationRuleRemoved(anyInt());
        verify(mAssistantSecondary, times(1)).onNotificationRuleRemoved(123);
    }
}