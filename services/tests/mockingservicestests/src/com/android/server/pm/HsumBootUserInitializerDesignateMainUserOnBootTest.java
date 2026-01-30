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

import static com.android.server.pm.HsumBootUserInitializer.designateMainUserOnBoot;

import android.annotation.Nullable;
import android.util.Log;

import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class HsumBootUserInitializerDesignateMainUserOnBootTest
        extends AbstractHsumBootUserInitializerConstructorHelpersTestCase {

    private final boolean mIsDebuggable;
    private final boolean mSysPropDesignateMainUser;
    private final boolean mFlagDemoteMainUser;
    private final boolean mConfigCreateInitialUser;
    private final boolean mConfigDesignateMainUser;
    private final boolean mConfigIsMainUserPermanentAdmin;
    private final boolean mResult;

    // Indices used on cloneBaseline()
    private static final int INDEX_DEBUGGABLE = 0;
    private static final int INDEX_SYSPROP = 1;
    private static final int INDEX_RESULT = 6;

    /**
     * Arguments baseline, i.e., the behavior without emulating using a system property.
     */
    private static final Object[][] BASELINE = {
        // Note: entries below are broken in 3 lines to make them easier to read / maintain:
        // - build type and emulation
        // - input (configs)
        // - expected output

        // original (only 2 configs used)
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(false), CFG_CREAT_INITIAL(false), CFG_DESIGNATE(false), CFG_IS_PERM_ADM(false),
                RESULT(false)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(false), CFG_CREAT_INITIAL(false), CFG_DESIGNATE(false), CFG_IS_PERM_ADM(true),
                RESULT(true)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(false), CFG_CREAT_INITIAL(false), CFG_DESIGNATE(true), CFG_IS_PERM_ADM(false),
                RESULT(true)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(false), CFG_CREAT_INITIAL(false), CFG_DESIGNATE(true), CFG_IS_PERM_ADM(true),
                RESULT(true)
        },
        // added FLAG and CFG_CREATE_INITIAL
        // FLAG(false), CFG_CREATE_INITIAL(true) - everything but first equals to original
        {
                // This is special case used to guard the config by the flag (RESULT true)
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(false), CFG_CREAT_INITIAL(true), CFG_DESIGNATE(false), CFG_IS_PERM_ADM(false),
                RESULT(true)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(false), CFG_CREAT_INITIAL(true), CFG_DESIGNATE(false), CFG_IS_PERM_ADM(true),
                RESULT(true)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(false), CFG_CREAT_INITIAL(true), CFG_DESIGNATE(true), CFG_IS_PERM_ADM(false),
                RESULT(true)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(false), CFG_CREAT_INITIAL(true), CFG_DESIGNATE(true), CFG_IS_PERM_ADM(true),
                RESULT(true)
        },
        // FLAG(true), CFG_CREATE_INITIAL(false) - everything equals to original
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(true), CFG_CREAT_INITIAL(false), CFG_DESIGNATE(false), CFG_IS_PERM_ADM(false),
                RESULT(false)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(true), CFG_CREAT_INITIAL(false), CFG_DESIGNATE(false), CFG_IS_PERM_ADM(true),
                RESULT(true)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(true), CFG_CREAT_INITIAL(false), CFG_DESIGNATE(true), CFG_IS_PERM_ADM(false),
                RESULT(true)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(true), CFG_CREAT_INITIAL(false), CFG_DESIGNATE(true), CFG_IS_PERM_ADM(true),
                RESULT(true)
        },
        // FLAG(true), CFG_CREATE_INITIAL(true) - everything equals to original
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(true), CFG_CREAT_INITIAL(true), CFG_DESIGNATE(false), CFG_IS_PERM_ADM(false),
                RESULT(false)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(true), CFG_CREAT_INITIAL(true), CFG_DESIGNATE(false), CFG_IS_PERM_ADM(true),
                RESULT(true)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(true), CFG_CREAT_INITIAL(true), CFG_DESIGNATE(true), CFG_IS_PERM_ADM(false),
                RESULT(true)
        },
        {
                DEBUGGABLE(false), SYSPROP(false),
                FLAG(true), CFG_CREAT_INITIAL(true), CFG_DESIGNATE(true), CFG_IS_PERM_ADM(true),
                RESULT(true)
        }
    };

    /** Useless javadoc to make checkstyle happy... */
    @Parameters(name =
            "{index}: dbgBuild={0},sysprop={1},flag={2},cfgCreateIU={3},cfgDesignateMU={4},cfgIsMUPermAdm={5},result={6}")
    public static List<Object[]> junitParametersPassedToConstructor() {
        List<Object[]> parameters = new ArrayList<>(BASELINE.length * 4);

        // User build, sysprop not set - baseline
        parameters.addAll(Arrays.asList(BASELINE));

        // User build, sysprop not set - everything should be the same as basline
        parameters.addAll(cloneBaseline(DEBUGGABLE(false), SYSPROP(false), /* result= */ null));

        // Debuggable build - result should be value of property (false)
        parameters.addAll(cloneBaseline(DEBUGGABLE(true), SYSPROP(false), RESULT(false)));

        // Debuggable build - result should be value of property (true)
        parameters.addAll(cloneBaseline(DEBUGGABLE(true), SYSPROP(true), RESULT(true)));

        return parameters;
    }

    public HsumBootUserInitializerDesignateMainUserOnBootTest(boolean isDebuggable,
            boolean sysPropDesignateMainUser, boolean flagDemoteMainUser,
            boolean configCreateInitialUser, boolean configDesignateMainUser,
            boolean configIsMainUserPermanentAdmin,  boolean result) {
        mSysPropDesignateMainUser = sysPropDesignateMainUser;
        mIsDebuggable = isDebuggable;
        mFlagDemoteMainUser = flagDemoteMainUser;
        mConfigCreateInitialUser = configCreateInitialUser;
        mConfigDesignateMainUser = configDesignateMainUser;
        mConfigIsMainUserPermanentAdmin = configIsMainUserPermanentAdmin;
        mResult = result;
        Log.v(mTag, "Constructor: isDebuggable=" + isDebuggable
                + ", sysPropDesignateMainUser=" + sysPropDesignateMainUser
                + ", flagDemoteMainUser=" + flagDemoteMainUser
                + ", configCreateInitialUser=" + configCreateInitialUser
                + ", configDesignateMainUser=" + configDesignateMainUser
                + ", configIsMainUserPermanentAdmin=" + configIsMainUserPermanentAdmin
                + ", result=" + result);
    }

    @Test
    public void testDesignateMainUserOnBoot() {
        mockSysPropDesignateMainUser(mSysPropDesignateMainUser);
        mockIsDebuggable(mIsDebuggable);
        setDemoteMainUserFlag(mFlagDemoteMainUser);
        mockConfigDesignateMainUser(mConfigDesignateMainUser);
        mockConfigIsMainUserPermanentAdmin(mConfigIsMainUserPermanentAdmin);
        mockConfigCreateInitialUser(mConfigCreateInitialUser);

        boolean result = designateMainUserOnBoot(mMockContext);

        expect.withMessage("designateMainUserOnBoot()").that(result).isEqualTo(mResult);
    }

    /**
     * Clones the baseline, changing some values.
     *
     * @param debuggable new value of {@code isDebuggable}
     * @param sysprop new value of {@code sysprop}
     * @param result if not {@code null}, new value of {@code result}
     *
     * @return the clone
     */
    private static List<Object[]> cloneBaseline(boolean debuggable, boolean sysprop,
            @Nullable Boolean result) {
        Object[][] clone = Arrays.stream(BASELINE)
                .map(row -> Arrays.copyOf(row, row.length))
                .toArray(Object[][]::new);
        for (var parameters : clone) {
            parameters[INDEX_DEBUGGABLE] = debuggable;
            parameters[INDEX_SYSPROP] = sysprop;
            if (result != null) {
                parameters[INDEX_RESULT] = result;
            }
        }
        return Arrays.asList(clone);
    }
}
