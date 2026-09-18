/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.packageinstaller.stats;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class StatsUtil {

    public static final int PIA_INSTALL_STAGE_UNKNOWN = 0;
    public static final int PIA_INSTALL_STAGE_STAGING = 1;
    public static final int PIA_INSTALL_STAGE_READY = 2;
    public static final int PIA_INSTALL_STAGE_USER_ACTION_REQUIRED = 3;
    public static final int PIA_INSTALL_STAGE_INSTALLING = 4;
    public static final int PIA_INSTALL_STAGE_SUCCESS = 5;
    public static final int PIA_INSTALL_STAGE_FAILED = 6;
    public static final int PIA_INSTALL_STAGE_VERIFICATION_CONFIRMATION_REQUIRED = 7;
    public static final int PIA_INSTALL_STAGE_VERIFICATION_FAILURE = 8;


    /** The status of encoding fetch. */
    @IntDef(
            value = {
                    PIA_INSTALL_STAGE_UNKNOWN,
                    PIA_INSTALL_STAGE_STAGING,
                    PIA_INSTALL_STAGE_READY,
                    PIA_INSTALL_STAGE_USER_ACTION_REQUIRED,
                    PIA_INSTALL_STAGE_INSTALLING,
                    PIA_INSTALL_STAGE_SUCCESS,
                    PIA_INSTALL_STAGE_FAILED,
                    PIA_INSTALL_STAGE_VERIFICATION_CONFIRMATION_REQUIRED,
                    PIA_INSTALL_STAGE_VERIFICATION_FAILURE
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PiaInstallStage {}

}
