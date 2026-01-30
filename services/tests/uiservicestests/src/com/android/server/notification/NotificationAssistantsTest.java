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

import static android.os.UserHandle.USER_ALL;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Flags;
import android.app.INotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.notification.Adjustment;
import android.testing.TestableContext;
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

@RunWith(AndroidJUnit4.class)
public class NotificationAssistantsTest extends UiServiceTestCase {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private PackageManager mPm;
    @Mock
    private IPackageManager miPm;
    @Mock
    private UserManager mUm;
    @Mock
    private UserManagerInternal mUmInternal;
    @Mock
    NotificationManagerService mNm;
    @Mock
    private INotificationManager mINm;

    NotificationAssistants mAssistants;

    @Mock
    private ManagedServices.UserProfiles mUserProfiles;

    private TestableContext mContext = spy(getContext());
    Object mLock = new Object();

    UserInfo mZero = new UserInfo(0, "zero", UserInfo.FLAG_FULL);
    UserInfo mTen = new UserInfo(10, "ten", UserInfo.FLAG_PROFILE);

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
        mAssistants.writeXml(serializer, false, userId);
        serializer.endDocument();
        serializer.flush();

        //fail(baos.toString("UTF-8"));

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(baos.toByteArray())), null);

        parser.nextTag();
        mAssistants = spy(mNm.new NotificationAssistants(mContext, mLock, mUserProfiles, miPm));
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL);
    }


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext.setMockPackageManager(mPm);
        mContext.addMockSystemService(Context.USER_SERVICE, mUm);
        mContext.getOrCreateTestableResources().addOverride(
                com.android.internal.R.string.config_defaultAssistantAccessComponent,
                mCn.flattenToString());
        mNm.mDefaultUnsupportedAdjustments = new String[] {};

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mUmInternal);

        mAssistants = spy(mNm.new NotificationAssistants(mContext, mLock, mUserProfiles, miPm));
        when(mNm.getBinderService()).thenReturn(mINm);
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

        List<UserInfo> users = new ArrayList<>();
        users.add(mZero);
        users.add(mTen);
        users.add(new UserInfo(11, "11", null, UserInfo.FLAG_PROFILE, USER_TYPE_PROFILE_MANAGED));
        users.add(new UserInfo(12, "12", UserInfo.FLAG_PROFILE));
        users.add(new UserInfo(13, "13", UserInfo.FLAG_FULL));
        for (UserInfo user : users) {
            when(mUm.getUserInfo(eq(user.id))).thenReturn(user);
            if (user.isProfile()) {
                when(mNm.hasParent(user)).thenReturn(true);
                when(mNm.isProfileUser(user)).thenReturn(true);
                when(mUserProfiles.isProfileUser(eq(user.id), any())).thenReturn(true);
                if (user.isManagedProfile()) {
                    when(mUserProfiles.isManagedProfileUser(user.id)).thenReturn(true);
                }
            }
        }
        when(mUm.getUsers()).thenReturn(users);
        when(mUm.getAliveUsers()).thenReturn(users);
        IntArray profileIds = new IntArray();
        profileIds.add(0);
        profileIds.add(11);
        profileIds.add(10);
        profileIds.add(12);
        when(mUmInternal.getProfileParentId(13)).thenReturn(13);  // 13 is a full user
        when(mUserProfiles.getProfileParentId(eq(13), any())).thenReturn(13);
        when(mUserProfiles.getCurrentProfileIds()).thenReturn(profileIds);
        when(mUmInternal.getProfileParentId(11)).thenReturn(mZero.id);
        when(mUserProfiles.getProfileParentId(eq(11), any())).thenReturn(mZero.id);
        when(mNm.isNASMigrationDone(anyInt())).thenReturn(true);
        when(mNm.canUseManagedServices(any(), anyInt(), any())).thenReturn(true);
        mRegistry = ExtensionRegistryLite.newInstance();
        NotificationExtensionAtoms.registerAllExtensions(mRegistry);
    }

    @Test
    public void testXmlUpgrade() {
        mAssistants.resetDefaultAssistantsIfNecessary();

        //once per user
        verify(mNm, times(mUm.getUsers().size())).setDefaultAssistantForUser(anyInt());
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
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL);

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
                ActivityManager.getCurrentUser());

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
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL);

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
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL);

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
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL);

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
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL);

        verify(mAssistants, times(1)).upgradeUserSet();
        assertTrue(isUserSetServicesEmpty(mAssistants, 0));
        assertFalse(mAssistants.mIsUserChanged.get(0));
    }

    @Test
    public void testReadXml_upgradeUserSet_preS_noUserSet_diffDefault() throws Exception {
        String xml = "<enabled_assistants version=\"3\" defaults=\"a/a\">"
                + "<service_listing approved=\"b/b\" user=\"0\" primary=\"true\"/>"
                + "</enabled_assistants>";

        final TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);

        parser.nextTag();
        mAssistants.readXml(parser, mNm::canUseManagedServices, false, USER_ALL);

        verify(mAssistants, times(1)).upgradeUserSet();
        assertTrue(isUserSetServicesEmpty(mAssistants, 0));
        assertFalse(mAssistants.mIsUserChanged.get(0));
        assertEquals(new ArraySet<>(Arrays.asList(new ComponentName("a", "a"))),
                mAssistants.getDefaultComponents());
        assertEquals(Arrays.asList(new ComponentName("b", "b")),
                mAssistants.getAllowedComponents(0));
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
        mAssistants.readXml(parser, null, false, USER_ALL);

        assertEquals(1, mAssistants.getAllowedComponents(0).size());
        assertEquals(new ArrayList(Arrays.asList(new ComponentName("a", "a"))),
                mAssistants.getAllowedComponents(0));
    }

    @Test
    public void testXmlUpgradeExistingApprovedComponents() throws Exception {
        String xml = "<enabled_assistants version=\"2\" defaults=\"b\\b\">"
                + "<service_listing approved=\"b/b\" user=\"10\" primary=\"true\" />"
                + "</enabled_assistants>";

        TypedXmlPullParser parser = Xml.newFastPullParser();
        parser.setInput(new BufferedInputStream(
                new ByteArrayInputStream(xml.toString().getBytes())), null);
        parser.nextTag();
        mAssistants.readXml(parser, null, false, USER_ALL);

        verify(mNm, never()).setDefaultAssistantForUser(anyInt());
        verify(mAssistants, times(1)).addApprovedList(
                new ComponentName("b", "b").flattenToString(), 10, true, "");
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
        ComponentName oldDefaultComponent = ComponentName.unflattenFromString("package/Component1");
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

        when(mNm.isNASMigrationDone(anyInt())).thenReturn(false);
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
        verify(mNm, times(1)).setNASMigrationDone(eq(mZero.id));
        verify(mNm, times(1)).setDefaultAssistantForUser(eq(mZero.id));
        assertEquals(new ArraySet<>(Arrays.asList(newDefaultComponent)),
                mAssistants.getDefaultComponents());

        when(mNm.isNASMigrationDone(anyInt())).thenReturn(true);

        // Test resetDefaultAssistantsIfNecessary again since it will be called on every reboot
        mAssistants.resetDefaultAssistantsIfNecessary();

        // The migration should not happen again, the invoke time for migration should not increase
        verify(mNm, times(1)).setNASMigrationDone(eq(mZero.id));
        // The invoke time outside migration part should increase by 1
        verify(mNm, times(2)).setDefaultAssistantForUser(eq(mZero.id));
    }

    @Test
    public void testNASSettingUpgrade_userNotSet_sameDefaultNAS() {
        ComponentName defaultComponent = ComponentName.unflattenFromString("package/Component1");

        when(mNm.isNASMigrationDone(anyInt())).thenReturn(false);
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
        when(mNm.isNASMigrationDone(anyInt())).thenReturn(false);
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
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testSetAdjustmentTypeSupportedState() throws Exception {
        int userId = ActivityManager.getCurrentUser();

        mAssistants.loadDefaultsFromConfig(true);
        mAssistants.setPackageOrComponentEnabled(mCn.flattenToString(), userId, true,
                true, true);
        ComponentName current = CollectionUtils.firstOrNull(
                mAssistants.getAllowedComponents(userId));
        assertNotNull(current);

        assertThat(mAssistants.getUnsupportedAdjustments(userId).size()).isEqualTo(0);

        ManagedServices.ManagedServiceInfo info =
                mAssistants.new ManagedServiceInfo(null, mCn, userId, false, null, 35, 2345256);
        mAssistants.setAdjustmentTypeSupportedState(
                info.userid, Adjustment.KEY_NOT_CONVERSATION, false);

        assertThat(mAssistants.getUnsupportedAdjustments(userId)).contains(
                Adjustment.KEY_NOT_CONVERSATION);
        assertThat(mAssistants.getUnsupportedAdjustments(userId).size()).isEqualTo(1);
    }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testSetAdjustmentTypeSupportedState_readWriteXml_entries() throws Exception {
        int userId = ActivityManager.getCurrentUser();

        mAssistants.loadDefaultsFromConfig(true);
        mAssistants.setPackageOrComponentEnabled(mCn.flattenToString(), userId, true,
                true, true);
        ComponentName current = CollectionUtils.firstOrNull(
                mAssistants.getAllowedComponents(userId));
        assertNotNull(current);

        ManagedServices.ManagedServiceInfo info =
                mAssistants.new ManagedServiceInfo(null, mCn, userId, false, null, 35, 2345256);
        mAssistants.setAdjustmentTypeSupportedState(
                info.userid, Adjustment.KEY_NOT_CONVERSATION, false);

        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.getUnsupportedAdjustments(userId)).contains(
                Adjustment.KEY_NOT_CONVERSATION);
        assertThat(mAssistants.getUnsupportedAdjustments(userId).size()).isEqualTo(1);
    }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
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
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testDisallowAdjustmentType() {
        mAssistants.disallowAdjustmentType(mZero.id, Adjustment.KEY_RANKING_SCORE);
        assertThat(mAssistants.getAllowedAssistantAdjustments(mZero.id))
                .doesNotContain(Adjustment.KEY_RANKING_SCORE);
        assertThat(mAssistants.getAllowedAssistantAdjustments(mZero.id)).contains(KEY_TYPE);

        // should not affect other (full) users
        assertThat(mAssistants.getAllowedAssistantAdjustments(13)).contains(
                Adjustment.KEY_RANKING_SCORE);
    }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testAllowAdjustmentType() {
        mAssistants.disallowAdjustmentType(mZero.id, Adjustment.KEY_RANKING_SCORE);
        assertThat(mAssistants.getAllowedAssistantAdjustments(mZero.id))
                .doesNotContain(Adjustment.KEY_RANKING_SCORE);
        mAssistants.allowAdjustmentType(mZero.id, Adjustment.KEY_RANKING_SCORE);
        assertThat(mAssistants.getAllowedAssistantAdjustments(mZero.id))
                .contains(Adjustment.KEY_RANKING_SCORE);
    }

    @Test
    public void testIsAdjustmentAllowed_profileUser_offIfParentOff() {
        // Even if an adjustment is allowed for a profile user, it should not be considered allowed
        // if the profile's parent has that adjustment disabled.
        // User 11 is set up as a profile user of mZero in setup; user 13 is not
        mAssistants.allowAdjustmentType(11, Adjustment.KEY_TYPE);
        mAssistants.allowAdjustmentType(13, Adjustment.KEY_TYPE);
        mAssistants.disallowAdjustmentType(mZero.id, Adjustment.KEY_TYPE);

        assertThat(mAssistants.getAllowedAssistantAdjustments(11)).doesNotContain(
                Adjustment.KEY_TYPE);
        assertThat(mAssistants.isAdjustmentAllowed(11, Adjustment.KEY_TYPE)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowed(13, Adjustment.KEY_TYPE)).isTrue();

        // Now turn it back on for the parent; it should be considered allowed for the profile
        // (for which it was already on).
        mAssistants.allowAdjustmentType(mZero.id, Adjustment.KEY_TYPE);
        assertThat(mAssistants.getAllowedAssistantAdjustments(11)).contains(Adjustment.KEY_TYPE);
        assertThat(mAssistants.isAdjustmentAllowed(11, Adjustment.KEY_TYPE)).isTrue();
    }

    @Test
    public void testClassificationAdjustment_managedProfileDefaultsOff() {
        // Turn on KEY_TYPE classification for mZero (parent) but not 11 (managed profile)
        mAssistants.allowAdjustmentType(mZero.id, Adjustment.KEY_TYPE);
        assertThat(mAssistants.isAdjustmentAllowed(11, Adjustment.KEY_TYPE)).isFalse();

        // Check this doesn't apply to other adjustments if they default to allowed
        mAssistants.allowAdjustmentType(mZero.id, KEY_IMPORTANCE);
        assertThat(mAssistants.isAdjustmentAllowed(11, Adjustment.KEY_IMPORTANCE)).isTrue();

        // now turn on classification for the profile user directly
        mAssistants.allowAdjustmentType(11, Adjustment.KEY_TYPE);
        assertThat(mAssistants.isAdjustmentAllowed(11, Adjustment.KEY_TYPE)).isTrue();
    }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testAllowAdjustmentType_classifListEmpty_resetDefaultClassificationTypes() {
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_PROMOTION, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_NEWS, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_SOCIAL_MEDIA, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_CONTENT_RECOMMENDATION,
                false);
        assertThat(mAssistants.getAllowedClassificationTypes(mZero.id)).isEmpty();
        mAssistants.disallowAdjustmentType(mZero.id, Adjustment.KEY_TYPE);
        mAssistants.allowAdjustmentType(mZero.id, Adjustment.KEY_TYPE);
        assertThat(mAssistants.getAllowedClassificationTypes(mZero.id)).asList()
                .contains(TYPE_PROMOTION);
    }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testAllowAdjustmentType_classifListNotEmpty_doNotResetDefaultClassificationTypes() {
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_PROMOTION, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_SOCIAL_MEDIA, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_CONTENT_RECOMMENDATION,
                false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_NEWS, true);
        assertThat(mAssistants.getAllowedClassificationTypes(mZero.id)).isNotEmpty();
        mAssistants.disallowAdjustmentType(mZero.id, Adjustment.KEY_TYPE);
        mAssistants.allowAdjustmentType(mZero.id, Adjustment.KEY_TYPE);
        assertThat(mAssistants.getAllowedClassificationTypes(mZero.id)).asList()
            .containsExactly(TYPE_NEWS);
    }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testClassificationAdjustments_readWriteXml_userSetStateMaintained()
            throws Exception {
        // Turn on KEY_TYPE classification for mZero (parent) but not 11 (managed profile)
        mAssistants.allowAdjustmentType(mZero.id, Adjustment.KEY_TYPE);
        assertThat(mAssistants.isAdjustmentAllowed(11, Adjustment.KEY_TYPE)).isFalse();

        // reload from XML; default state should persist
        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.isAdjustmentAllowed(mZero.id, Adjustment.KEY_TYPE)).isTrue();
        assertThat(mAssistants.isAdjustmentAllowed(11, Adjustment.KEY_TYPE)).isFalse();

        mAssistants.allowAdjustmentType(11, Adjustment.KEY_TYPE);
        assertThat(mAssistants.isAdjustmentAllowed(11, Adjustment.KEY_TYPE)).isTrue();

        // now that it's been set, we should still retain this information after XML reload
        writeXmlAndReload(USER_ALL);
        assertThat(mAssistants.isAdjustmentAllowed(11, Adjustment.KEY_TYPE)).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI)
    public void testDisallowAdjustmentType_readWriteXml_entries() throws Exception {
        int userId = ActivityManager.getCurrentUser();

        mAssistants.loadDefaultsFromConfig(true);
        mAssistants.disallowAdjustmentType(userId, KEY_IMPORTANCE);

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

        if (android.app.Flags.nmSummarizationOnboardingUi()) {
            expected.remove(KEY_SUMMARIZATION);
        }

        assertThat(mAssistants.getAllowedAssistantAdjustments(mZero.id))
                .containsExactlyElementsIn(expected);
    }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testSetAssistantClassificationTypeState_allow() {
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_CONTENT_RECOMMENDATION,
                false);
        assertThat(mAssistants.getAllowedClassificationTypes(mZero.id))
                .asList().doesNotContain(TYPE_CONTENT_RECOMMENDATION);

        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_CONTENT_RECOMMENDATION,
                true);

        assertThat(mAssistants.getAllowedClassificationTypes(mZero.id)).asList()
                .contains(TYPE_CONTENT_RECOMMENDATION);
    }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testSetAssistantClassificationTypeState_disallow() {
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_PROMOTION, false);
        assertThat(mAssistants.getAllowedClassificationTypes(mZero.id))
                .asList().doesNotContain(TYPE_PROMOTION);
    }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testClassificationTypes_forProfile_followsFullUser() {
        mAssistants.setAssistantClassificationTypeState(11, TYPE_NEWS, false);
        assertThat(mAssistants.getAllowedClassificationTypes(11))
                .asList().doesNotContain(TYPE_NEWS);

        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_PROMOTION, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_SOCIAL_MEDIA, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_CONTENT_RECOMMENDATION,
                false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_NEWS, true);

        assertThat(mAssistants.getAllowedClassificationTypes(11)) // 11 set up as profile of mZero
                .asList().containsExactly(TYPE_NEWS);
    }

    @Test
    @EnableFlags(Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI)
    public void testDisallowAdjustmentKeyType_readWriteXml() throws Exception {
        mAssistants.loadDefaultsFromConfig(true);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_SOCIAL_MEDIA, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_PROMOTION, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_NEWS, true);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_CONTENT_RECOMMENDATION,
                true);

        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.getAllowedClassificationTypes(mZero.id)).asList()
                .containsExactlyElementsIn(List.of(TYPE_NEWS, TYPE_CONTENT_RECOMMENDATION));
    }

    @Test
    @EnableFlags(android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION)
    public void testDefaultAllowedKeyAdjustments_readWriteXml() throws Exception {
        mAssistants.loadDefaultsFromConfig(true);

        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.getAllowedClassificationTypes(mZero.id)).asList()
                .containsExactlyElementsIn(List.of(TYPE_PROMOTION, TYPE_NEWS));
    }

    @Test
    @EnableFlags({Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI, Flags.FLAG_NM_SUMMARIZATION,
            Flags.FLAG_NM_SUMMARIZATION_UI})
    public void testSetAdjustmentSupportedForPackage_allowsAndDenies() {
        // Given that a package (for user 0) is allowed to have summarization adjustments
        String key = KEY_SUMMARIZATION;
        String allowedPackage = "allowed.package";

        assertThat(
                mAssistants.isAdjustmentAllowedForPackage(mZero.id, key, allowedPackage)).isTrue();
        assertThat(mAssistants.getAdjustmentDeniedPackages(mZero.id, key)).isEmpty();

        // and also for other users: one unrelated, one profile of the full user
        assertThat(
                mAssistants.isAdjustmentAllowedForPackage(mTen.id, key, allowedPackage)).isTrue();
        assertThat(mAssistants.getAdjustmentDeniedPackages(mTen.id, key)).isEmpty();
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
        assertThat(
                mAssistants.isAdjustmentAllowedForPackage(mTen.id, key, allowedPackage)).isTrue();
        assertThat(mAssistants.getAdjustmentDeniedPackages(mTen.id, key)).isEmpty();
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
    @EnableFlags({Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI, Flags.FLAG_NM_SUMMARIZATION,
            Flags.FLAG_NM_SUMMARIZATION_UI})
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
    @EnableFlags({Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI, Flags.FLAG_NM_SUMMARIZATION,
            Flags.FLAG_NM_SUMMARIZATION_UI})
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
    @EnableFlags({Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI, Flags.FLAG_NM_SUMMARIZATION,
            Flags.FLAG_NM_SUMMARIZATION_UI})
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
        // Set classification adjustment disallowed for these packages
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, KEY_TYPE, deniedPkg2, false);

        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, KEY_SUMMARIZATION,
                deniedPkg1)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, KEY_TYPE,
                deniedPkg2)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, KEY_SUMMARIZATION,
                deniedPkg3)).isFalse();
    }

    @Test
    @EnableFlags({Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI, Flags.FLAG_NM_SUMMARIZATION,
            Flags.FLAG_NM_SUMMARIZATION_UI})
    public void testSetAdjustmentSupportedForPackage_readWriteXml_multipleUsers()
            throws Exception {
        mAssistants.loadDefaultsFromConfig(true);
        String deniedPkg0 = "denied.Pkg1";
        String deniedPkg10 = "denied.Pkg10";
        String deniedPkg11 = "denied.Pkg11";
        // Set summarization adjustment disallowed for each of these packages for different users
        mAssistants.setAdjustmentSupportedForPackage(mZero.id, KEY_SUMMARIZATION, deniedPkg0,
                false);
        mAssistants.setAdjustmentSupportedForPackage(11, KEY_SUMMARIZATION, deniedPkg11, false);
        // Set classification adjustment disallowed for this package/user
        mAssistants.setAdjustmentSupportedForPackage(mTen.id, KEY_TYPE, deniedPkg10, false);

        writeXmlAndReload(USER_ALL);

        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, KEY_SUMMARIZATION,
                deniedPkg0)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mTen.id, KEY_TYPE,
                deniedPkg10)).isFalse();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(11, KEY_SUMMARIZATION,
                deniedPkg11)).isFalse();

        // but they don't affect other users
        assertThat(mAssistants.isAdjustmentAllowedForPackage(11, KEY_SUMMARIZATION,
                deniedPkg0)).isTrue();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mZero.id, KEY_TYPE,
                deniedPkg10)).isTrue();
        assertThat(mAssistants.isAdjustmentAllowedForPackage(mTen.id, KEY_SUMMARIZATION,
                deniedPkg11)).isTrue();
    }

    @Test
    @SuppressWarnings("GuardedBy")
    @EnableFlags({android.service.notification.Flags.FLAG_NOTIFICATION_CLASSIFICATION,
            Flags.FLAG_NOTIFICATION_CLASSIFICATION_UI, Flags.FLAG_NM_SUMMARIZATION,
            Flags.FLAG_NM_SUMMARIZATION_UI})
    public void testPullAdjustmentPreferencesStats_fillsOutStatsEvent()
            throws Exception {
        mAssistants.loadDefaultsFromConfig(true);

        // Scenario setup: the total list of users is 0, 10, 11 (managed profile of 0), 12, 13
        //   * user 0 has both bundles (KEY_TYPE) + summaries (KEY_SUMMARIZATION) supported
        //      * KEY_TYPE is disallowed; KEY_SUMMARIZATION allowed
        //   * user 13 has only KEY_TYPE, which is allowed
        //   * user 11 (managed profile) has only KEY_SUMMARIZATION, disallowed
        //   * other users are non-managed profiles; should not be logged even if both types are
        //     supported
        for (int user : List.of(mZero.id, mTen.id, 12, 13)) {
            // users with KEY_TYPE supported: all but 11
            mAssistants.setAdjustmentTypeSupportedState(user, KEY_TYPE, true);
        }
        mAssistants.setAdjustmentTypeSupportedState(11, KEY_TYPE, false);

        for (int user : List.of(mZero.id, mTen.id, 11, 12)) {
            // users with KEY_SUMMARIZATION supported: all but 13
            mAssistants.setAdjustmentTypeSupportedState(user, KEY_SUMMARIZATION, true);
        }
        mAssistants.setAdjustmentTypeSupportedState(13, KEY_SUMMARIZATION, false);

        // permissions for adjustments as described above
        mAssistants.disallowAdjustmentType(mZero.id, KEY_TYPE);
        mAssistants.allowAdjustmentType(mZero.id, KEY_SUMMARIZATION);
        mAssistants.disallowAdjustmentType(11, KEY_SUMMARIZATION);
        mAssistants.allowAdjustmentType(13, KEY_TYPE);

        // Enable specific bundle types for user 0
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_SOCIAL_MEDIA, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_PROMOTION, false);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_NEWS, true);
        mAssistants.setAssistantClassificationTypeState(mZero.id, TYPE_CONTENT_RECOMMENDATION,
                true);

        // and different ones for user 13
        mAssistants.setAssistantClassificationTypeState(13, TYPE_SOCIAL_MEDIA, true);
        mAssistants.setAssistantClassificationTypeState(13, TYPE_PROMOTION, false);
        mAssistants.setAssistantClassificationTypeState(13, TYPE_NEWS, false);
        mAssistants.setAssistantClassificationTypeState(13, TYPE_CONTENT_RECOMMENDATION,
                false);

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

        // user 0, KEY_TYPE (bundles)
        assertThat(atoms).containsKey(mZero.id);
        Map<Integer, NotificationAdjustmentPreferences> userZeroAtoms = atoms.get(mZero.id);
        assertThat(userZeroAtoms).containsKey(NotificationProtoEnums.KEY_TYPE);
        NotificationAdjustmentPreferences p0b = userZeroAtoms.get(NotificationProtoEnums.KEY_TYPE);
        assertThat(p0b.getAdjustmentAllowed()).isFalse();
        assertThat(p0b.getAllowedBundleTypesList()).containsExactly(
                BundleTypes.forNumber(NotificationProtoEnums.TYPE_NEWS),
                BundleTypes.forNumber(NotificationProtoEnums.TYPE_CONTENT_RECOMMENDATION));

        // user 0, KEY_SUMMARIZATION
        assertThat(userZeroAtoms).containsKey(NotificationProtoEnums.KEY_SUMMARIZATION);
        NotificationAdjustmentPreferences p0s = userZeroAtoms.get(
                NotificationProtoEnums.KEY_SUMMARIZATION);
        assertThat(p0s.getAdjustmentAllowed()).isTrue();

        // user 11, KEY_SUMMARIZATION
        assertThat(atoms).containsKey(11);
        assertThat(atoms.get(11)).containsKey(NotificationProtoEnums.KEY_SUMMARIZATION);
        NotificationAdjustmentPreferences p11s = atoms.get(11).get(
                NotificationProtoEnums.KEY_SUMMARIZATION);
        assertThat(p11s.getAdjustmentAllowed()).isFalse();

        // user 13, KEY_TYPE (bundles)
        assertThat(atoms).containsKey(13);
        assertThat(atoms.get(13)).containsKey(NotificationProtoEnums.KEY_TYPE);
        NotificationAdjustmentPreferences p13b = atoms.get(13).get(
                NotificationProtoEnums.KEY_TYPE);
        assertThat(p13b.getAdjustmentAllowed()).isTrue();
        assertThat(p13b.getAllowedBundleTypesList())
                .containsExactly(BundleTypes.forNumber(NotificationProtoEnums.TYPE_SOCIAL_MEDIA));
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
}