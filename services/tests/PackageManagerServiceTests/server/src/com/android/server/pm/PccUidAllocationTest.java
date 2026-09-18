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

package com.android.server.pm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.pm.test.service.server.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class PccUidAllocationTest {

    private static final String TEST_PKG_NAME = "com.android.frameworks.coretests.pcc_test_app";

    private PackageManager mPackageManager;
    private PackageInstaller mPackageInstaller;
    private Context mContext;

    private static class LocalIntentReceiver {
        private final SynchronousQueue<Intent> mResult = new SynchronousQueue<>();

        private IIntentSender.Stub mLocalSender = new IIntentSender.Stub() {
            @Override
            public void send(int code, Intent intent, String resolvedType, IBinder whitelistToken,
                    IIntentReceiver finishedReceiver, String requiredPermission, Bundle options) {
                try {
                    mResult.offer(intent, 5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        public IntentSender getIntentSender() {
            return new IntentSender((IIntentSender) mLocalSender);
        }

        public Intent getResult() {
            try {
                return mResult.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mPackageManager = mContext.getPackageManager();
        mPackageInstaller = mPackageManager.getPackageInstaller();
        installPackageFromRawResource();
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(TEST_PKG_NAME);
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPccUidAllocation() {
        try {
            ApplicationInfo appInfo = mPackageManager.getApplicationInfo(TEST_PKG_NAME, 0);
            assertTrue("PCC UID for PCC package should be in the PCC range.",
                    Process.isPrivateComputeCoreUid(appInfo.pccUid));
        } catch (PackageManager.NameNotFoundException e) {
            fail("Test package not found: " + e.getMessage());
        }
    }

    @Test
    @RequiresFlagsDisabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPccUidAllocation_FlagDisabled() {
        try {
            ApplicationInfo appInfo = mPackageManager.getApplicationInfo(TEST_PKG_NAME, 0);
            assertEquals(Process.INVALID_UID, appInfo.pccUid);
            assertFalse("PCC UID should not be assigned if PCC flag disabled",
                    Process.isPrivateComputeCoreUid(appInfo.pccUid));
        } catch (PackageManager.NameNotFoundException e) {
            fail("Test package not found: " + e.getMessage());
        }
    }

    @Test
    @RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testPccUidRemovedOnUpgradeWithoutPccComponents() throws Exception {
        try {
            ApplicationInfo appInfo = mPackageManager.getApplicationInfo(TEST_PKG_NAME, 0);
            assertTrue("PCC UID for PCC package should be in the PCC range.",
                    Process.isPrivateComputeCoreUid(appInfo.pccUid));
        } catch (PackageManager.NameNotFoundException e) {
            fail("Test package not found: " + e.getMessage());
        }

        installPackageFromRawResourceUpgradeToNoPccComponents();

        try {
            ApplicationInfo appInfo = mPackageManager.getApplicationInfo(TEST_PKG_NAME, 0);
            assertEquals(Process.INVALID_UID, appInfo.pccUid);
            assertFalse("PCC UID should not be assigned if no PCC components are present",
                    Process.isPrivateComputeCoreUid(appInfo.pccUid));
        } catch (PackageManager.NameNotFoundException e) {
            fail("Test package not found: " + e.getMessage());
        }
    }

    private void installPackageFromRawResource() throws Exception {
        int sessionId = createSession();
        PackageInstaller.Session session = mPackageInstaller.openSession(sessionId);
        writeFileToSession(session, R.raw.PccTestApp, "base.apk");
        commitSession(session);
    }

    private void installPackageFromRawResourceUpgradeToNoPccComponents() throws Exception {
        int sessionId = createSession();
        PackageInstaller.Session session = mPackageInstaller.openSession(sessionId);
        writeFileToSession(session, R.raw.PccTestAppUpgradeToNoPccComponents, "base.apk");
        commitSession(session);
    }

    private void uninstallPackage(String packageName) throws Exception {
        LocalIntentReceiver receiver = new LocalIntentReceiver();
        mPackageInstaller.uninstall(packageName, receiver.getIntentSender());
        receiver.getResult();
    }

    private int createSession() throws IOException {
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        return mPackageInstaller.createSession(params);
    }

    private void writeFileToSession(PackageInstaller.Session session, int resourceId, String name)
            throws IOException {
        try (OutputStream out = session.openWrite(name, 0, -1);
                InputStream in = mContext.getResources().openRawResource(resourceId)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            session.fsync(out);
        }
    }

    private void commitSession(PackageInstaller.Session session) {
        LocalIntentReceiver receiver = new LocalIntentReceiver();
        session.commit(receiver.getIntentSender());
        Intent result = receiver.getResult();
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        assertEquals(PackageInstaller.STATUS_SUCCESS, status);
    }
}
