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

import static android.multiuser.Flags.FLAG_DEMOTE_MAIN_USER;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.server.pm.HsumBootUserInitializer.SYSPROP_DESIGNATE_MAIN_USER;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.multiuser.Flags;
import android.os.Build;
import android.os.SystemProperties;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Log;

import com.android.internal.R;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;

// Base class for testing static methods used to create the constructor parameters
@RunWith(Parameterized.class)
abstract class AbstractHsumBootUserInitializerConstructorHelpersTestCase {

    @Rule
    public final Expect expect = Expect.create();

    @Rule
    public final ExtendedMockitoRule extendedMockito = new ExtendedMockitoRule.Builder(this)
            .mockStatic(Build.class)
            .mockStatic(SystemProperties.class)
            .build();

    @Rule
    public final SetFlagsRule setFlagsRule =
            new SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT);

    protected final String mTag = getClass().getSimpleName();

    @Mock
    protected Context mMockContext;

    @Mock
    private Resources mMockResources;

    @Before
    public final void setCommonFixtures() {
        when(mMockContext.getResources()).thenReturn(mMockResources);
    }

    protected final void mockConfigDesignateMainUser(boolean value) {
        Log.v(mTag, "mockConfigDesignateMainUser(" + value + ")");
        when(mMockResources.getBoolean(R.bool.config_designateMainUser)).thenReturn(value);
    }

    protected final void mockConfigIsMainUserPermanentAdmin(boolean value) {
        Log.v(mTag, "mockConfigIsMainUserPermanentAdmin(" + value + ")");
        when(mMockResources.getBoolean(R.bool.config_isMainUserPermanentAdmin)).thenReturn(value);
    }

    protected final void mockConfigCreateInitialUser(boolean value) {
        Log.v(mTag, "mockConfigCreateInitialUser(" + value + ")");
        when(mMockResources.getBoolean(R.bool.config_createInitialUser)).thenReturn(value);
    }

    protected final void mockIsDebuggable(boolean value) {
        Log.v(mTag, "mockIsDebuggable(" + value + ")");
        doReturn(value).when(Build::isDebuggable);
    }

    protected final void mockSysPropDesignateMainUser(boolean value) {
        Log.v(mTag, "mockSysPropDesignateMainUser(" + value + ")");
        doReturn(value).when(
                () -> SystemProperties.getBoolean(eq(SYSPROP_DESIGNATE_MAIN_USER), anyBoolean()));
    }

    @SuppressWarnings("deprecation") // TODO(b/341129262): SetFlagsRule methods are deprecated
    protected final void setDemoteMainUserFlag(boolean value) {
        boolean before =  Flags.demoteMainUser();
        if (before == value) {
            Log.v(mTag, "setDemoteMainUserFlag(): already " + value);
            return;
        }
        Log.v(mTag, "setDemoteMainUserFlag(): changing from " + before + " to " + value);
        if (value) {
            setFlagsRule.enableFlags(FLAG_DEMOTE_MAIN_USER);
        } else {
            setFlagsRule.disableFlags(FLAG_DEMOTE_MAIN_USER);
        }
    }


    // Helper methods to make values parameterized values easier to read
    // NOTE: not really "Generated code", but that's the only why to calm down checkstyle, otherwise
    // it will complain they should be all upper case
    // CHECKSTYLE:OFF Generated code

    protected static boolean DEBUGGABLE(boolean value) {
        return value;
    }

    protected static boolean SYSPROP(boolean value) {
        return value;
    }

    protected static boolean FLAG(boolean value) {
        return value;
    }

    protected static boolean CFG_DESIGNATE(boolean value) {
        return value;
    }

    protected static boolean CFG_IS_PERM_ADM(boolean value) {
        return value;
    }

    // NOTE: called CREAT on purpose as lines fit. After all, there's a creat() method on UNIX...
    protected static boolean CFG_CREAT_INITIAL(boolean value) {
        return value;
    }

    protected static boolean RESULT(boolean value) {
        return value;
    }
    // CHECKSTYLE:ON Generated code
}
