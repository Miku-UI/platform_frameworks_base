/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.server;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.testing.TestableContext;

import androidx.test.InstrumentationRegistry;

import com.android.internal.pm.parsing.pkg.PackageImpl;
import com.android.server.pm.UserManagerInternal;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.uri.UriGrantsManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;

public class UiServiceTestCase {
    @Mock protected PackageManagerInternal mPmi;
    @Mock protected UserManagerInternal mUmi;
    @Mock protected UriGrantsManagerInternal mUgmInternal;

    protected static final String PKG_N_MR1 = "com.example.n_mr1";
    protected static final String PKG_O = "com.example.o";
    protected static final String PKG_P = "com.example.p";
    protected static final String PKG_R = "com.example.r";

    @Rule
    public TestableContext mContext =
            spy(new TestableContext(InstrumentationRegistry.getContext(), null));

    protected final int mUid = Binder.getCallingUid();
    protected final @UserIdInt int mUserId = UserHandle.getUserId(mUid);
    protected final UserHandle mUser = UserHandle.of(mUserId);
    protected final String mPkg = mContext.getPackageName();
    protected final int UID_N_MR1 = UserHandle.getUid(mUserId, 1);
    protected final int UID_O = UserHandle.getUid(mUserId, 2);
    protected final int UID_P = UserHandle.getUid(mUserId, 3);
    protected final int UID_R = UserHandle.getUid(mUserId, 4);

    protected TestableContext getContext() {
        return mContext;
    }

    @Before
    public final void setup() {
        MockitoAnnotations.initMocks(this);

        // Share classloader to allow package access.
        System.setProperty("dexmaker.share_classloader", "true");

        // Assume some default packages
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPmi);
        when(mPmi.getPackageTargetSdkVersion(anyString()))
                .thenAnswer((iom) -> {
                    switch ((String) iom.getArgument(0)) {
                        case PKG_N_MR1:
                            return Build.VERSION_CODES.N_MR1;
                        case PKG_O:
                            return Build.VERSION_CODES.O;
                        case PKG_P:
                            return Build.VERSION_CODES.P;
                        case PKG_R:
                            return Build.VERSION_CODES.R;
                        default:
                            return Build.VERSION_CODES.CUR_DEVELOPMENT;
                    }
                });
        when(mPmi.isSameApp(eq(PKG_N_MR1), anyLong(), eq(UID_N_MR1), eq(mUserId))).thenReturn(true);
        when(mPmi.isSameApp(eq(PKG_O), anyLong(), eq(UID_O), eq(mUserId))).thenReturn(true);
        when(mPmi.isSameApp(eq(PKG_P), anyLong(), eq(UID_P), eq(mUserId))).thenReturn(true);
        when(mPmi.isSameApp(eq(PKG_R), anyLong(), eq(UID_R), eq(mUserId))).thenReturn(true);
        when(mPmi.isSameApp(eq(mContext.getPackageName()), anyLong(), eq(mUid), eq(mUserId)))
                .thenReturn(true);
        when(mPmi.isSameApp(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mPmi.getPackageUid(eq(PKG_N_MR1), anyLong(), anyInt())).thenReturn(UID_N_MR1);
        when(mPmi.getPackageUid(eq(PKG_O), anyLong(),  anyInt())).thenReturn(UID_O);
        when(mPmi.getPackageUid(eq(PKG_P), anyLong(),  anyInt())).thenReturn(UID_P);
        when(mPmi.getPackageUid(eq(PKG_R), anyLong(),  anyInt())).thenReturn(UID_R);
        when(mPmi.getPackageUid(eq(mContext.getPackageName()), anyLong(), eq(mUserId)))
                .thenReturn(mUid);
        when(mPmi.getPackage(UID_N_MR1)).thenReturn(
                (AndroidPackage) PackageImpl.forTesting(PKG_N_MR1, "test"));
        when(mPmi.getPackage(UID_O)).thenReturn(
                (AndroidPackage) PackageImpl.forTesting(PKG_O, "test"));
        when(mPmi.getPackage(UID_P)).thenReturn(
                (AndroidPackage) PackageImpl.forTesting(PKG_P, "test"));
        when(mPmi.getPackage(UID_R)).thenReturn(
                (AndroidPackage) PackageImpl.forTesting(PKG_R, "test"));

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mUmi);
        LocalServices.removeServiceForTest(UriGrantsManagerInternal.class);
        LocalServices.addService(UriGrantsManagerInternal.class, mUgmInternal);
        when(mUgmInternal.checkGrantUriPermission(
                anyInt(), anyString(), any(Uri.class), anyInt(), anyInt())).thenReturn(-1);

        Mockito.doReturn(new Intent()).when(mContext).registerReceiverAsUser(
                any(), any(), any(), any(), any());
        Mockito.doReturn(new Intent()).when(mContext).registerReceiver(any(), any());
        Mockito.doReturn(new Intent()).when(mContext).registerReceiver(any(), any(), anyInt());
        Mockito.doNothing().when(mContext).unregisterReceiver(any());
    }

    protected ApplicationInfo getApplicationInfo(String pkg, int uid) {
        final ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = pkg;
        applicationInfo.uid = uid;
        applicationInfo.sourceDir = mContext.getApplicationInfo().sourceDir;
        switch (pkg) {
            case PKG_N_MR1:
                applicationInfo.targetSdkVersion = Build.VERSION_CODES.N_MR1;
                break;
            case PKG_O:
                applicationInfo.targetSdkVersion = Build.VERSION_CODES.O;
                break;
            case PKG_P:
                applicationInfo.targetSdkVersion = Build.VERSION_CODES.P;
                break;
            case PKG_R:
                applicationInfo.targetSdkVersion = Build.VERSION_CODES.R;
                break;
            default:
                applicationInfo.targetSdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT;
                break;
        }
        return applicationInfo;
    }

    @After
    public final void cleanUpMockito() {
        Mockito.framework().clearInlineMocks();
    }

    protected static Intent eqIntent(Intent wanted) {
        return argThat(
                new ArgumentMatcher<Intent>() {
                    @Override
                    public boolean matches(Intent argument) {
                        return wanted.filterEquals(argument)
                                && wanted.getFlags() == argument.getFlags()
                                && equalBundles(wanted.getExtras(), argument.getExtras());
                    }

                    @Override
                    public String toString() {
                        return wanted.toString();
                    }

                    private boolean equalBundles(Bundle one, Bundle two) {
                        if (one == null && two == null) {
                            return true;
                        }
                        if ((one == null) != (two == null)) {
                            return false;
                        }
                        if (one.size() != two.size()) {
                            return false;
                        }

                        HashSet<String> setOne = new HashSet<>(one.keySet());
                        setOne.addAll(two.keySet());

                        for (String key : setOne) {
                            if (!one.containsKey(key) || !two.containsKey(key)) {
                                return false;
                            }

                            Object valueOne = one.get(key);
                            Object valueTwo = two.get(key);
                            if (valueOne instanceof Bundle
                                    && valueTwo instanceof Bundle
                                    && !equalBundles((Bundle) valueOne, (Bundle) valueTwo)) {
                                return false;
                            } else if (valueOne == null) {
                                if (valueTwo != null) {
                                    return false;
                                }
                            } else if (!valueOne.equals(valueTwo)) {
                                return false;
                            }
                        }
                        return true;
                    }
                });
    }
}
