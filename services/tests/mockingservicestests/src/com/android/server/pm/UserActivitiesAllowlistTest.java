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

import static android.content.ComponentName.unflattenFromString;

import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_ALLOWLISTING_DISABLED_BY_SHELL_CMD;
import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING;

import android.content.ComponentName;

import com.android.server.pm.GenericAllowlist.AllowlistMode;

import com.google.common.collect.ImmutableSet;

public final class UserActivitiesAllowlistTest
        extends GenericAllowlistTestCase<UserActivitiesAllowlist, ComponentName> {

    private static final String PERM_NAME_1 = "perm/one.is.the.loniest.number";
    private static final String PERM_NAME_2 = "perm/two.to.tango";
    private static final String PERM_NAME_3 = "perm/three.is.a.charm";

    private static final String TEMP_NAME_1 = "temp/one.is.the.loniest.number";
    private static final String TEMP_NAME_2 = "temp/two.to.tango";
    private static final String TEMP_NAME_3 = "temp/three.is.a.charm";

    private static final ComponentName PERM_ACTIVITY_1 = unflattenFromString(PERM_NAME_1);
    private static final ComponentName PERM_ACTIVITY_2 = unflattenFromString(PERM_NAME_2);
    private static final ComponentName PERM_ACTIVITY_3 = unflattenFromString(PERM_NAME_3);

    private static final ComponentName TEMP_ACTIVITY_1 = unflattenFromString(TEMP_NAME_1);
    private static final ComponentName TEMP_ACTIVITY_2 = unflattenFromString(TEMP_NAME_2);
    private static final ComponentName TEMP_ACTIVITY_3 = unflattenFromString(TEMP_NAME_3);

    private static final String SHORT_NAME = "i.am/.groot";
    private static final String FULL_NAME = "i.am/i.am.groot";
    private static final ComponentName ACTIVITY_SHORT_NAME = unflattenFromString(SHORT_NAME);
    private static final ComponentName ACTIVITY_FULL_NAME = unflattenFromString(FULL_NAME);


    private static final String NOT_ALLOWLISTED_NAME = "allowlisted/I.am...NOT";
    private static final ComponentName NOT_ALLOWLISTED_ACTIVITY =
            unflattenFromString(NOT_ALLOWLISTED_NAME);
    private static final String INVALID_NAME = "invalid.I.am"; // missing package

    public UserActivitiesAllowlistTest() {
        super("activity", "activities");
    }

    @Override
    protected UserActivitiesAllowlist createAllowlist(@AllowlistMode int mode,
            String... configAllowlist) {
        return new UserActivitiesAllowlist(mode, configAllowlist);
    }

    @Override
    protected ImmutableSet<Integer> getOverridingStatuses() {
        return ImmutableSet.of(
                STATUS_ALLOWED_ALLOWLISTING_DISABLED_BY_SHELL_CMD,
                STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING
                );
    }

    @Override
    protected String getInvalidName() {
        return INVALID_NAME;
    }

    @Override
    protected String getPermanentName1() {
        return PERM_NAME_1;
    }

    @Override
    protected String getPermanentName2() {
        return PERM_NAME_2;
    }

    @Override
    protected String getPermanentName3() {
        return PERM_NAME_3;
    }

    @Override
    protected String getTemporaryName1() {
        return TEMP_NAME_1;
    }

    @Override
    protected String getTemporaryName2() {
        return TEMP_NAME_2;
    }

    @Override
    protected String getTemporaryName3() {
        return TEMP_NAME_3;
    }

    @Override
    protected ComponentName getPermanentElement1() {
        return PERM_ACTIVITY_1;
    }

    @Override
    protected ComponentName getPermanentElement2() {
        return PERM_ACTIVITY_2;
    }

    @Override
    protected ComponentName getPermanentElement3() {
        return PERM_ACTIVITY_3;
    }

    @Override
    protected ComponentName getTemporaryElement1() {
        return TEMP_ACTIVITY_1;
    }

    @Override
    protected ComponentName getTemporaryElement2() {
        return TEMP_ACTIVITY_2;
    }

    @Override
    protected ComponentName getTemporaryElement3() {
        return TEMP_ACTIVITY_3;
    }

    @Override
    protected ComponentName getNotAllowlistedElement() {
        return NOT_ALLOWLISTED_ACTIVITY;
    }

    @Override
    protected String getNotAllowlistedName() {
        return NOT_ALLOWLISTED_NAME;
    }

    @Override
    protected String getShortName() {
        return SHORT_NAME;
    }

    @Override
    protected String getFullName() {
        return FULL_NAME;
    }

    @Override
    protected ComponentName getElementWithShortName() {
        return ACTIVITY_SHORT_NAME;
    }

    @Override
    protected ComponentName getElementWithFullName() {
        return ACTIVITY_FULL_NAME;
    }
}
