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

package com.android.extensions.computercontrol;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.companion.virtual.IVirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IComputerControlSessionCallback;
import android.content.pm.PackageManager;
import android.hardware.display.IVirtualDisplayCallback;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.testing.TestableContext;
import android.view.Display;
import android.view.Surface;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.IAccessibilityManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executors;

@RequiresFlagsEnabled(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_ACCESS)
@RunWith(AndroidJUnit4.class)
public class ComputerControlExtensionsTest {
    private static final int CALLBACK_TIMEOUT_MS = 1_000;
    private static final int DISPLAY_DPI = 100;
    private static final int DISPLAY_HEIGHT = 200;
    private static final int DISPLAY_WIDTH = 300;
    private static final Surface DISPLAY_SURFACE = new Surface();
    private static final boolean DISPLAY_ALWAYS_UNLOCKED = true;
    private static final String SESSION_NAME = "test";
    private static final int ERROR_CODE = -7;

    @Rule
    public final TestableContext mContext = spy(
            new TestableContext(InstrumentationRegistry.getInstrumentation().getTargetContext()));

    @Mock private PackageManager mPackageManager;
    @Mock private IAccessibilityManager mIAccessibilityManager;
    @Mock private IVirtualDeviceManager mIVirtualDeviceManager;
    @Mock private IComputerControlSession mIComputerControlSession;
    @Mock private ComputerControlSession.Callback mSessionCallback;
    @Mock private IVirtualDisplayCallback mVirtualDisplayCallback;

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void testGetVersion() {
        assertThat(ComputerControlExtensions.getVersion())
                .isEqualTo(ComputerControlExtensions.EXTENSIONS_VERSION);
    }

    @Test
    public void getInstance_missingVirtualDeviceManager_returnsNull() {
        mContext.addMockSystemService(VirtualDeviceManager.class, null);
        mContext.setMockPackageManager(mPackageManager);

        when(mPackageManager.hasSystemFeature(
                     PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(true);

        assertThat(ComputerControlExtensions.getInstance(mContext)).isNull();
    }

    @Test
    public void getInstance_missingSystemFeature_returnsNull() {
        mContext.setMockPackageManager(mPackageManager);

        when(mPackageManager.hasSystemFeature(
                     PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(false);

        assertThat(ComputerControlExtensions.getInstance(mContext)).isNull();
    }

    @Test
    public void getInstance_returnsNonNull() {
        mContext.setMockPackageManager(mPackageManager);

        when(mPackageManager.hasSystemFeature(
                     PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(true);

        assertThat(ComputerControlExtensions.getInstance(mContext)).isNotNull();
    }

    @Test
    public void requestSession_withNullParams_throwsNullPointerException() {
        mContext.setMockPackageManager(mPackageManager);

        when(mPackageManager.hasSystemFeature(
                     PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(true);

        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);
        assertThrows(NullPointerException.class, () -> extensions.requestSession(null,
                Executors.newSingleThreadExecutor(), mSessionCallback));
    }

    @Test
    public void requestSession_withNullExecutor_throwsNullPointerException() {
        mContext.setMockPackageManager(mPackageManager);

        when(mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(true);

        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);
        assertThrows(NullPointerException.class, () -> extensions.requestSession(createParams(),
                null, mSessionCallback));
    }

    @Test
    public void requestSession_withNullCallback_throwsNullPointerException() {
        mContext.setMockPackageManager(mPackageManager);

        when(mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(true);

        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);
        assertThrows(NullPointerException.class, () -> extensions.requestSession(createParams(),
                Executors.newSingleThreadExecutor(), null));
    }

    @Test
    public void requestSession_withoutPermission_throwsException() {
        mContext.setMockPackageManager(mPackageManager);

        when(mPackageManager.hasSystemFeature(
                     PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(true);

        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);

        // By default, the CTS process is not allowlisted for the required knownSigner permission.
        assertThrows(SecurityException.class, () -> extensions.requestSession(createParams(),
                Executors.newSingleThreadExecutor(), mSessionCallback));

    }

    @Test
    public void requestSession_success() throws Exception {
        when(mPackageManager.hasSystemFeature(
                     PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(true);
        mContext.addMockSystemService(AccessibilityManager.class,
                new AccessibilityManager(mContext, mContext.getMainThreadHandler(),
                        mIAccessibilityManager, 0, true));
        mContext.addMockSystemService(VirtualDeviceManager.class,
                new VirtualDeviceManager(mIVirtualDeviceManager, mContext));
        doAnswer(inv -> {
            ((IComputerControlSessionCallback) (inv.getArgument(2))).onSessionCreated(
                    Display.INVALID_DISPLAY, mVirtualDisplayCallback, mIComputerControlSession);
            return null;
        }).when(mIVirtualDeviceManager).requestComputerControlSession(any(), any(), any());

        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);
        extensions.requestSession(
                createParams(), Executors.newSingleThreadExecutor(), mSessionCallback);

        verify(mIVirtualDeviceManager).requestComputerControlSession(any(), any(), any());
        verify(mSessionCallback, timeout(CALLBACK_TIMEOUT_MS)).onSessionCreated(any());
    }

    @Test
    public void requestSession_failure() throws Exception {
        when(mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS))
                .thenReturn(true);
        mContext.addMockSystemService(AccessibilityManager.class,
                new AccessibilityManager(mContext, mContext.getMainThreadHandler(),
                        mIAccessibilityManager, 0, true));
        mContext.addMockSystemService(VirtualDeviceManager.class,
                new VirtualDeviceManager(mIVirtualDeviceManager, mContext));
        doAnswer(inv -> {
            ((IComputerControlSessionCallback) (inv.getArgument(2)))
                    .onSessionCreationFailed(ERROR_CODE);
            return null;
        }).when(mIVirtualDeviceManager).requestComputerControlSession(any(), any(), any());

        ComputerControlExtensions extensions = ComputerControlExtensions.getInstance(mContext);
        extensions.requestSession(
                createParams(), Executors.newSingleThreadExecutor(), mSessionCallback);

        verify(mIVirtualDeviceManager).requestComputerControlSession(any(), any(), any());
        verify(mSessionCallback, timeout(CALLBACK_TIMEOUT_MS)).onSessionCreationFailed(ERROR_CODE);
    }

    private ComputerControlSession.Params createParams() {
        return new ComputerControlSession.Params.Builder(mContext)
                .setDisplayDpi(DISPLAY_DPI)
                .setDisplayHeightPx(DISPLAY_HEIGHT)
                .setDisplayWidthPx(DISPLAY_WIDTH)
                .setDisplaySurface(DISPLAY_SURFACE)
                .setName(SESSION_NAME)
                .setDisplayAlwaysUnlocked(DISPLAY_ALWAYS_UNLOCKED)
                .build();
    }
}
