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
 * limitations under the License
 */

package com.android.server.appwidget;

import static android.appwidget.flags.Flags.remoteAdapterConversion;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManagerInternal;
import android.app.UiAutomation;
import android.app.admin.DevicePolicyManagerInternal;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetManagerInternal;
import android.appwidget.AppWidgetProviderInfo;
import android.appwidget.PendingHostUpdate;
import android.appwidget.flags.Flags;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ShortcutServiceInternal;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.SparseArray;
import android.util.Xml;
import android.widget.RemoteViews;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.frameworks.servicestests.R;
import com.android.internal.appwidget.IAppWidgetHost;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.LocalServices;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Tests for {@link AppWidgetManager} and {@link AppWidgetServiceImpl}.
 * To run: atest FrameworksServicesTests:AppWidgetServiceImplTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppWidgetServiceImplTest {

    @Rule
    public SetFlagsRule setFlagsRule = new SetFlagsRule();

    private static final int HOST_ID = 42;

    private TestContext mTestContext;
    private String mPkgName;
    private AppWidgetServiceImpl mService;
    private AppWidgetManager mManager;

    private ShortcutServiceInternal mMockShortcutService;
    private PackageManagerInternal mMockPackageManager;
    private AppOpsManagerInternal mMockAppOpsManagerInternal;
    private IAppWidgetHost mMockHost;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        LocalServices.removeServiceForTest(DevicePolicyManagerInternal.class);
        LocalServices.removeServiceForTest(ShortcutServiceInternal.class);
        LocalServices.removeServiceForTest(AppWidgetManagerInternal.class);
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.removeServiceForTest(AppOpsManagerInternal.class);

        mTestContext = new TestContext();
        mPkgName = mTestContext.getOpPackageName();
        mService = new AppWidgetServiceImpl(mTestContext);
        mManager = new AppWidgetManager(mTestContext, mService);

        mMockShortcutService = mock(ShortcutServiceInternal.class);
        mMockPackageManager = mock(PackageManagerInternal.class);
        mMockAppOpsManagerInternal = mock(AppOpsManagerInternal.class);
        mMockHost = mock(IAppWidgetHost.class);
        LocalServices.addService(ShortcutServiceInternal.class, mMockShortcutService);
        LocalServices.addService(PackageManagerInternal.class, mMockPackageManager);
        LocalServices.addService(AppOpsManagerInternal.class, mMockAppOpsManagerInternal);
        when(mMockPackageManager.filterAppAccess(anyString(), anyInt(), anyInt()))
                .thenReturn(false);
        mService.onStart();
        mService.systemServicesReady();

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testLoadDescription() {
        AppWidgetProviderInfo info =
                mManager.getInstalledProvidersForPackage(mPkgName, null).get(0);
        assertEquals(info.loadDescription(mTestContext), "widget description string");
    }

    @Test
    public void testParseSizeConfiguration() {
        AppWidgetProviderInfo info =
                mManager.getInstalledProvidersForPackage(mPkgName, null).get(0);

        assertThat(info.minWidth).isEqualTo(getDimensionResource(R.dimen.widget_min_width));
        assertThat(info.minHeight).isEqualTo(getDimensionResource(R.dimen.widget_min_height));
        assertThat(info.minResizeWidth)
                .isEqualTo(getDimensionResource(R.dimen.widget_min_resize_width));
        assertThat(info.minResizeHeight)
                .isEqualTo(getDimensionResource(R.dimen.widget_min_resize_height));
        assertThat(info.maxResizeWidth)
                .isEqualTo(getDimensionResource(R.dimen.widget_max_resize_width));
        assertThat(info.maxResizeHeight)
                .isEqualTo(getDimensionResource(R.dimen.widget_max_resize_height));
        assertThat(info.targetCellWidth)
                .isEqualTo(getIntegerResource(R.integer.widget_target_cell_width));
        assertThat(info.targetCellHeight)
                .isEqualTo(getIntegerResource(R.integer.widget_target_cell_height));
    }

    @Test
    public void testRequestPinAppWidget_otherProvider() {
        ComponentName otherProvider = null;
        for (AppWidgetProviderInfo provider : mManager.getInstalledProviders()) {
            if (!provider.provider.getPackageName().equals(mTestContext.getPackageName())) {
                otherProvider = provider.provider;
                break;
            }
        }
        if (otherProvider == null) {
            // No other provider found. Ignore this test.
        }
        assertFalse(mManager.requestPinAppWidget(otherProvider, null, null));
    }

    @Test
    @EnableFlags(Flags.FLAG_PLAY_STORE_PIN_WIDGETS)
    public void testRequestPinAppWidget_otherProvider_installPackagesPermission() {
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES);
        ComponentName otherProvider = null;
        int uid = 0;
        for (AppWidgetProviderInfo provider : mManager.getInstalledProviders()) {
            if (!provider.provider.getPackageName().equals(mTestContext.getPackageName())) {
                otherProvider = provider.provider;
                uid = provider.providerInfo.applicationInfo.uid;
                break;
            }
        }
        assumeNotNull(otherProvider);
        when(mMockShortcutService.requestPinAppWidget(anyString(), any(AppWidgetProviderInfo.class),
                eq(null), eq(null), anyInt())).thenReturn(true);
        when(mMockPackageManager.getPackageUid(eq(otherProvider.getPackageName()), anyLong(),
                anyInt())).thenReturn(uid);
        assertTrue(mManager.requestPinAppWidget(otherProvider, null, null));
    }

    @Test
    public void testRequestPinAppWidget() {
        ComponentName provider = new ComponentName(mTestContext, TestAppWidgetProvider.class);
        doReturn(true).when(mMockPackageManager).isSameApp(eq(mPkgName), anyInt(), anyInt());
        // Set up users.
        when(mMockShortcutService.requestPinAppWidget(anyString(),
                any(AppWidgetProviderInfo.class), eq(null), eq(null), anyInt()))
                .thenReturn(true);
        assertTrue(mManager.requestPinAppWidget(provider, null, null));

        final ArgumentCaptor<AppWidgetProviderInfo> providerCaptor =
                ArgumentCaptor.forClass(AppWidgetProviderInfo.class);
        verify(mMockShortcutService, times(1)).requestPinAppWidget(anyString(),
                providerCaptor.capture(), eq(null), eq(null), anyInt());
        assertEquals(provider, providerCaptor.getValue().provider);
    }

    @Test
    public void testIsRequestPinAppWidgetSupported() {
        // Set up users.
        when(mMockShortcutService.isRequestPinItemSupported(anyInt(), anyInt()))
                .thenReturn(true, false);
        assertTrue(mManager.isRequestPinAppWidgetSupported());
        assertFalse(mManager.isRequestPinAppWidgetSupported());

        verify(mMockShortcutService, times(2)).isRequestPinItemSupported(anyInt(),
                eq(LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET));
    }

    @EnableFlags(Flags.FLAG_REMOTE_ADAPTER_CONVERSION)
    @Test
    public void testProviderUpdatesReceived() throws Exception {
        int widgetId = setupHostAndWidget();
        RemoteViews view = new RemoteViews(mPkgName, android.R.layout.simple_list_item_1);
        mManager.updateAppWidget(widgetId, view);
        mManager.updateAppWidget(widgetId, view);
        mManager.updateAppWidget(widgetId, view);
        mManager.updateAppWidget(widgetId, view);

        flushMainThread();
        verify(mMockHost, times(4)).updateAppWidget(eq(widgetId), any(RemoteViews.class));

        reset(mMockHost);
        mManager.notifyAppWidgetViewDataChanged(widgetId, 22);
        flushMainThread();
        verify(mMockHost, never()).viewDataChanged(anyInt(), anyInt());
    }

    @Test
    public void testProviderUpdatesNotReceived() throws Exception {
        int widgetId = setupHostAndWidget();
        mService.stopListening(mPkgName, HOST_ID);
        RemoteViews view = new RemoteViews(mPkgName, android.R.layout.simple_list_item_1);
        mManager.updateAppWidget(widgetId, view);
        mManager.notifyAppWidgetViewDataChanged(widgetId, 22);

        flushMainThread();
        verify(mMockHost, times(0)).updateAppWidget(anyInt(), any(RemoteViews.class));
        verify(mMockHost, times(0)).viewDataChanged(anyInt(), eq(22));
    }

    @Test
    public void testNoUpdatesReceived_queueEmpty() {
        int widgetId = setupHostAndWidget();
        RemoteViews view = new RemoteViews(mPkgName, android.R.layout.simple_list_item_1);
        mManager.updateAppWidget(widgetId, view);
        mManager.notifyAppWidgetViewDataChanged(widgetId, 22);
        mService.stopListening(mPkgName, HOST_ID);

        List<PendingHostUpdate> updates = mService.startListening(
                mMockHost, mPkgName, HOST_ID, new int[0]).getList();
        assertTrue(updates.isEmpty());
    }

    /**
     * Sends placeholder widget updates to {@link #mManager}.
     * @param widgetId widget to update
     * @param viewIds a list of view ids for which
     *                {@link AppWidgetManager#notifyAppWidgetViewDataChanged} will be called
     */
    private void sendDummyUpdates(int widgetId, int... viewIds) {
        Random r = new Random();
        RemoteViews view = new RemoteViews(mPkgName, android.R.layout.simple_list_item_1);
        for (int i = r.nextInt(10) + 2; i >= 0; i--) {
            mManager.updateAppWidget(widgetId, view);
        }

        for (int viewId : viewIds) {
            mManager.notifyAppWidgetViewDataChanged(widgetId, viewId);
            for (int i = r.nextInt(3); i >= 0; i--) {
                mManager.updateAppWidget(widgetId, view);
            }
        }
    }

    @Test
    public void testNoUpdatesReceived_queueNonEmpty_noWidgetId() {
        int widgetId = setupHostAndWidget();
        mService.stopListening(mPkgName, HOST_ID);

        sendDummyUpdates(widgetId, 22, 23);
        List<PendingHostUpdate> updates = mService.startListening(
                mMockHost, mPkgName, HOST_ID, new int[0]).getList();
        assertTrue(updates.isEmpty());
    }

    @Test
    public void testUpdatesReceived_queueNotEmpty_widgetIdProvided() {
        int widgetId = setupHostAndWidget();
        int widgetId2 = bindNewWidget();
        mService.stopListening(mPkgName, HOST_ID);

        sendDummyUpdates(widgetId, 22, 23);
        sendDummyUpdates(widgetId2, 100, 101, 102);

        List<PendingHostUpdate> updates = mService.startListening(
                mMockHost, mPkgName, HOST_ID, new int[]{widgetId}).getList();
        // 3 updates corresponding to the first widget
        assertEquals(remoteAdapterConversion() ? 1 : 3, updates.size());
    }

    @Test
    public void testUpdatesReceived_queueNotEmpty_widgetIdProvided2() {
        int widgetId = setupHostAndWidget();
        int widgetId2 = bindNewWidget();
        mService.stopListening(mPkgName, HOST_ID);

        sendDummyUpdates(widgetId, 22, 23);
        sendDummyUpdates(widgetId2, 100, 101, 102);

        List<PendingHostUpdate> updates = mService.startListening(
                mMockHost, mPkgName, HOST_ID, new int[]{widgetId2}).getList();
        // 4 updates corresponding to the second widget
        assertEquals(remoteAdapterConversion() ? 1 : 4, updates.size());
    }

    @Test
    public void testReceiveBroadcastBehavior_enableAndUpdate() {
        TestAppWidgetProvider testAppWidgetProvider = new TestAppWidgetProvider();
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_ENABLE_AND_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{2});

        testAppWidgetProvider.onReceive(mTestContext, intent);

        assertTrue(testAppWidgetProvider.isBehaviorSuccess());
    }

    @Test
    public void testUpdatesReceived_queueNotEmpty_multipleWidgetIdProvided() {
        int widgetId = setupHostAndWidget();
        int widgetId2 = bindNewWidget();
        mService.stopListening(mPkgName, HOST_ID);

        sendDummyUpdates(widgetId, 22, 23);
        sendDummyUpdates(widgetId2, 100, 101, 102);

        List<PendingHostUpdate> updates = mService.startListening(
                mMockHost, mPkgName, HOST_ID, new int[]{widgetId, widgetId2}).getList();
        // 3 updates for first widget and 4 for second
        assertEquals(remoteAdapterConversion() ? 2 : 7 , updates.size());
    }

    @Test
    public void testUpdatesReceived_queueEmptyAfterStartListening() {
        int widgetId = setupHostAndWidget();
        int widgetId2 = bindNewWidget();
        mService.stopListening(mPkgName, HOST_ID);

        sendDummyUpdates(widgetId, 22, 23);
        sendDummyUpdates(widgetId2, 100, 101, 102);

        List<PendingHostUpdate> updates = mService.startListening(
                mMockHost, mPkgName, HOST_ID, new int[]{widgetId, widgetId2}).getList();
        // 3 updates for first widget and 4 for second
        assertEquals(remoteAdapterConversion() ? 2 : 7, updates.size());

        // Stop and start listening again
        mService.stopListening(mPkgName, HOST_ID);
        updates = mService.startListening(
                mMockHost, mPkgName, HOST_ID, new int[]{widgetId, widgetId2}).getList();
        assertTrue(updates.isEmpty());
    }

    @Test
    public void testIsFirstConfigActivityPending_without_config() {
        int widgetId = setupHostAndWidget();
        assertFalse(mService.isFirstConfigActivityPending(mPkgName, widgetId));
    }

    @Test
    public void testIsFirstConfigActivityPending_with_config() {
        mManager.updateAppWidgetProviderInfo(
                new ComponentName(mTestContext, TestAppWidgetProvider.class), "info_with_config");
        int widgetId = setupHostAndWidget();
        assertTrue(mService.isFirstConfigActivityPending(mPkgName, widgetId));

        mManager.setConfigActivityComplete(widgetId);
        assertFalse(mService.isFirstConfigActivityPending(mPkgName, widgetId));
    }

    @Test
    public void testGetInstalledProvidersForPackage() {
        List<AppWidgetProviderInfo> allProviders = mManager.getInstalledProviders();
        assertTrue(!allProviders.isEmpty());
        String packageName = allProviders.get(0).provider.getPackageName();
        List<AppWidgetProviderInfo> providersForPackage = mManager.getInstalledProvidersForPackage(
                packageName, null);
        // Remove providers from allProviders that don't have the given package name.
        Iterator<AppWidgetProviderInfo> iter = allProviders.iterator();
        while (iter.hasNext()) {
            if (!iter.next().provider.getPackageName().equals(packageName)) {
                iter.remove();
            }
        }
        assertEquals(allProviders.size(), providersForPackage.size());
        for (int i = 0; i < allProviders.size(); i++) {
            assertEquals(allProviders.get(i).provider, providersForPackage.get(i).provider);
        }
    }

    @Test
    public void testGetPreviewLayout() {
        AppWidgetProviderInfo info =
                mManager.getInstalledProvidersForPackage(mPkgName, null).get(0);

        assertThat(info.previewLayout).isEqualTo(R.layout.widget_preview);
    }

    @Test
    public void testWidgetProviderInfoPersistence() throws IOException {
        final AppWidgetProviderInfo original = new AppWidgetProviderInfo();
        original.minWidth = 40;
        original.minHeight = 40;
        original.maxResizeWidth = 250;
        original.maxResizeHeight = 120;
        original.targetCellWidth = 1;
        original.targetCellHeight = 1;
        original.updatePeriodMillis = 86400000;
        original.previewLayout = R.layout.widget_preview;
        original.label = "test";

        final File file = new File(mTestContext.getDataDir(), "appwidget_provider_info.xml");
        saveWidgetProviderInfoLocked(file, original);
        final AppWidgetProviderInfo target = loadAppWidgetProviderInfoLocked(file);

        assertThat(target.minWidth).isEqualTo(original.minWidth);
        assertThat(target.minHeight).isEqualTo(original.minHeight);
        assertThat(target.minResizeWidth).isEqualTo(original.minResizeWidth);
        assertThat(target.minResizeHeight).isEqualTo(original.minResizeHeight);
        assertThat(target.maxResizeWidth).isEqualTo(original.maxResizeWidth);
        assertThat(target.maxResizeHeight).isEqualTo(original.maxResizeHeight);
        assertThat(target.targetCellWidth).isEqualTo(original.targetCellWidth);
        assertThat(target.targetCellHeight).isEqualTo(original.targetCellHeight);
        assertThat(target.updatePeriodMillis).isEqualTo(original.updatePeriodMillis);
        assertThat(target.previewLayout).isEqualTo(original.previewLayout);
    }

    @Test
    public void testBackupRestoreControllerStatePersistence() throws IOException {
        // Setup mock data
        final Set<String> mockPrunedApps = getMockPrunedApps();
        final SparseArray<
                List<AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord>
                > mockUpdatesByProvider = getMockUpdates();
        final  SparseArray<
                List<AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord>
                > mockUpdatesByHost = getMockUpdates();
        final AppWidgetServiceImpl.BackupRestoreController.State state =
                new AppWidgetServiceImpl.BackupRestoreController.State(
                        mockPrunedApps, mockUpdatesByProvider, mockUpdatesByHost);

        final File file = new File(mTestContext.getDataDir(), "state.xml");
        saveBackupRestoreControllerState(file, state);
        final AppWidgetServiceImpl.BackupRestoreController.State target =
                loadStateLocked(file);
        assertNotNull(target);
        final SparseArray<List<AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord>>
                actualUpdatesByProvider = target.getUpdatesByProvider();
        assertNotNull(actualUpdatesByProvider);
        final SparseArray<List<AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord>>
                actualUpdatesByHost = target.getUpdatesByHost();
        assertNotNull(actualUpdatesByHost);

        assertEquals(mockPrunedApps, target.getPrunedApps());
        for (int i = 0; i < mockUpdatesByProvider.size(); i++) {
            final int key = mockUpdatesByProvider.keyAt(i);
            verifyRestoreUpdateRecord(
                    actualUpdatesByProvider.get(key), mockUpdatesByProvider.get(key));
        }
        for (int i = 0; i < mockUpdatesByHost.size(); i++) {
            final int key = mockUpdatesByHost.keyAt(i);
            verifyRestoreUpdateRecord(
                    actualUpdatesByHost.get(key), mockUpdatesByHost.get(key));
        }
    }

    private void verifyRestoreUpdateRecord(
            @NonNull final List<AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord>
                    actualUpdates,
            @NonNull final List<AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord>
                    expectedUpdates) {
        assertEquals(expectedUpdates.size(), actualUpdates.size());
        for (int i = 0; i < expectedUpdates.size(); i++) {
            final AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord expected =
                    expectedUpdates.get(i);
            final AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord actual =
                    actualUpdates.get(i);
            assertEquals(expected.oldId, actual.oldId);
            assertEquals(expected.newId, actual.newId);
            assertEquals(expected.notified, actual.notified);
        }
    }

    @NonNull
    private static Set<String> getMockPrunedApps() {
        final Set<String> mockPrunedApps = new ArraySet<>(10);
        for (int i = 0; i < 10; i++) {
            mockPrunedApps.add("com.example.app" + i);
        }
        return mockPrunedApps;
    }

    @NonNull
    private static SparseArray<
            List<AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord>
            > getMockUpdates() {
        final SparseArray<List<
                AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord>> ret =
                new SparseArray<>(4);
        ret.put(0, new ArrayList<>());
        for (int i = 0; i < 5; i++) {
            final AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord record =
                    new AppWidgetServiceImpl.BackupRestoreController.RestoreUpdateRecord(
                            5 - i, i);
            record.notified = (i % 2 == 1);
            final int key = (i < 3) ? 1 : 2;
            if (!ret.contains(key)) {
                ret.put(key, new ArrayList<>());
            }
            ret.get(key).add(record);
        }
        ret.put(3, new ArrayList<>());
        return ret;
    }

    private int setupHostAndWidget() {
        List<PendingHostUpdate> updates = mService.startListening(
                mMockHost, mPkgName, HOST_ID, new int[0]).getList();
        assertTrue(updates.isEmpty());
        return bindNewWidget();
    }

    private int bindNewWidget() {
        ComponentName provider = new ComponentName(mTestContext, TestAppWidgetProvider.class);
        int widgetId = mService.allocateAppWidgetId(mPkgName, HOST_ID);
        assertTrue(mManager.bindAppWidgetIdIfAllowed(widgetId, provider));
        assertEquals(provider, mManager.getAppWidgetInfo(widgetId).provider);

        return widgetId;
    }

    private void flushMainThread() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        // Wait for main thread
        new Handler(mTestContext.getMainLooper()).post(latch::countDown);
        latch.await();
        // Wait for service thread
        latch = new CountDownLatch(1);
        mService.getServiceThread().getThreadHandler().post(latch::countDown);
        latch.await();
    }

    private int getDimensionResource(int resId) {
        return mTestContext.getResources().getDimensionPixelSize(resId);
    }

    private int getIntegerResource(int resId) {
        return mTestContext.getResources().getInteger(resId);
    }

    private static void saveBackupRestoreControllerState(
            @NonNull final File dst,
            @Nullable final AppWidgetServiceImpl.BackupRestoreController.State state)
            throws IOException {
        Objects.requireNonNull(dst);
        if (state == null) {
            return;
        }
        final AtomicFile file = new AtomicFile(dst);
        final FileOutputStream stream = file.startWrite();
        final TypedXmlSerializer out = Xml.resolveSerializer(stream);
        out.startDocument(null, true);
        AppWidgetXmlUtil.writeBackupRestoreControllerState(out, state);
        out.endDocument();
        file.finishWrite(stream);
    }

    private static AppWidgetServiceImpl.BackupRestoreController.State loadStateLocked(
            @NonNull final File dst) {
        Objects.requireNonNull(dst);
        final AtomicFile file = new AtomicFile(dst);
        try (FileInputStream stream = file.openRead()) {
            final TypedXmlPullParser parser = Xml.resolvePullParser(stream);
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
                // drain whitespace, comments, etc.
            }
            return AppWidgetXmlUtil.readBackupRestoreControllerState(parser);
        } catch (IOException | XmlPullParserException e) {
            return null;
        }
    }

    private static void saveWidgetProviderInfoLocked(@NonNull final File dst,
            @Nullable final AppWidgetProviderInfo info)
            throws IOException {
        Objects.requireNonNull(dst);
        if (info == null) {
            return;
        }
        final AtomicFile file = new AtomicFile(dst);
        final FileOutputStream stream = file.startWrite();
        final TypedXmlSerializer out = Xml.resolveSerializer(stream);
        out.startDocument(null, true);
        out.startTag(null, "p");
        AppWidgetXmlUtil.writeAppWidgetProviderInfoLocked(out, info);
        out.endTag(null, "p");
        out.endDocument();
        file.finishWrite(stream);
    }

    public static AppWidgetProviderInfo loadAppWidgetProviderInfoLocked(@NonNull final File dst) {
        Objects.requireNonNull(dst);
        final AtomicFile file = new AtomicFile(dst);
        try (FileInputStream stream = file.openRead()) {
            final TypedXmlPullParser parser = Xml.resolvePullParser(stream);
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
                // drain whitespace, comments, etc.
            }
            final String nodeName = parser.getName();
            if (!"p".equals(nodeName)) {
                return null;
            }
            return AppWidgetXmlUtil.readAppWidgetProviderInfoLocked(parser);
        } catch (IOException | XmlPullParserException e) {
            return null;
        }
    }

    private class TestContext extends ContextWrapper {

        public TestContext() {
            super(InstrumentationRegistry.getInstrumentation().getTargetContext());
        }

        @Override
        public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
                IntentFilter filter, String broadcastPermission, Handler scheduler) {
            // ignore.
            return null;
        }

        @Override
        public void unregisterReceiver(BroadcastReceiver receiver) {
            // ignore.
        }

        @Override
        public void enforceCallingOrSelfPermission(String permission, String message) {
            // ignore.
        }

        @Override
        public void sendBroadcastAsUser(Intent intent, UserHandle user) {
            // ignore.
        }

        @Override
        public void sendBroadcastAsUser(
                Intent intent, UserHandle user, String receiverPermission, Bundle options) {
            // Ignore
        }
    }
}
