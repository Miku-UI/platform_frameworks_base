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

package com.android.server.am.psc;

import android.annotation.IntDef;
import android.annotation.IntRange;

import com.android.server.am.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A central collection of constants related to the Process State Controller (PSC),
 * specifically for Out-Of-Memory (OOM) adjustments and process scheduler groups.
 */
public final class Constants {
    private Constants() {}

    // OOM adjustment scores for processes in various states:
    @IntRange(from = NATIVE_ADJ, to = UNKNOWN_ADJ)
    @IntDef(value = {
            INVALID_ADJ,
            UNKNOWN_ADJ,
            CACHED_APP_MAX_ADJ,
            CACHED_APP_MIN_ADJ,
            CACHED_APP_LMK_FIRST_ADJ,
            CACHED_APP_IMPORTANCE_LEVELS,
            SERVICE_B_ADJ,
            PREVIOUS_APP_MAX_ADJ_LADDERED,
            PREVIOUS_APP_ADJ,
            HOME_APP_ADJ,
            SERVICE_ADJ,
            HEAVY_WEIGHT_APP_ADJ,
            BACKUP_APP_ADJ,
            PERCEPTIBLE_LOW_APP_ADJ,
            PERCEPTIBLE_MEDIUM_APP_ADJ,
            PERCEPTIBLE_APP_ADJ,
            VISIBLE_APP_MAX_ADJ_LADDERED,
            VISIBLE_APP_ADJ,
            VISIBLE_APP_LAYER_MAX,
            PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ,
            FOREGROUND_APP_ADJ,
            PERSISTENT_SERVICE_ADJ,
            PERSISTENT_PROC_ADJ,
            SYSTEM_ADJ,
            NATIVE_ADJ
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface OomAdjust {}

    // Uninitialized value for any major or minor adj fields
    public static final int INVALID_ADJ = -10000;

    // Adjustment used in certain places where we don't know it yet.
    // (Generally this is something that is going to be cached, but we
    // don't know the exact value in the cached range to assign yet.)
    public static final int UNKNOWN_ADJ = 1001;

    // This is a process only hosting activities that are not visible,
    // so it can be killed without any disruption.
    public static final int CACHED_APP_MAX_ADJ = 999;
    public static final int CACHED_APP_MIN_ADJ = 900;

    // This is the oom_adj level that we allow to die first. This cannot be equal to
    // CACHED_APP_MAX_ADJ unless processes are actively being assigned an oom_score_adj of
    // CACHED_APP_MAX_ADJ.
    public static final int CACHED_APP_LMK_FIRST_ADJ = 950;

    // Number of levels we have available for different service connection group importance
    // levels.
    public static final int CACHED_APP_IMPORTANCE_LEVELS = 5;

    // The B list of SERVICE_ADJ -- these are the old and decrepit
    // services that aren't as shiny and interesting as the ones in the A list.
    public static final int SERVICE_B_ADJ = 800;

    // TODO: Remove this constant and add PREVIOUS_APP_MAX_ADJ to the IntDef bucket
    //  once the oomadjuster_prev_laddering flag logic is removed.
    public static final int PREVIOUS_APP_MAX_ADJ_LADDERED = 799;

    // This is the process of the previous application that the user was in.
    // This process is kept above other things, because it is very common to
    // switch back to the previous app.  This is important both for recent
    // task switch (toggling between the two top recent apps) as well as normal
    // UI flow such as clicking on a URI in the e-mail app to view in the browser,
    // and then pressing back to return to e-mail.
    public static final int PREVIOUS_APP_ADJ = 700;

    @OomAdjust
    public static final int PREVIOUS_APP_MAX_ADJ =
            Flags.oomadjusterPrevLaddering() ? PREVIOUS_APP_MAX_ADJ_LADDERED
                    : PREVIOUS_APP_ADJ;

    // This is a process holding the home application -- we want to try
    // avoiding killing it, even if it would normally be in the background,
    // because the user interacts with it so much.
    public static final int HOME_APP_ADJ = 600;

    // This is a process holding an application service -- killing it will not
    // have much of an impact as far as the user is concerned.
    public static final int SERVICE_ADJ = 500;

    // This is a process with a heavy-weight application.  It is in the
    // background, but we want to try to avoid killing it.  Value set in
    // system/rootdir/init.rc on startup.
    public static final int HEAVY_WEIGHT_APP_ADJ = 400;

    // This is a process currently hosting a backup operation.  Killing it
    // is not entirely fatal but is generally a bad idea.
    public static final int BACKUP_APP_ADJ = 300;

    // This is a process bound by the system (or other app) that's more important than services but
    // not so perceptible that it affects the user immediately if killed.
    public static final int PERCEPTIBLE_LOW_APP_ADJ = 250;

    // This is a process hosting services that are not perceptible to the user but the
    // client (system) binding to it requested to treat it as if it is perceptible and avoid killing
    // it if possible.
    public static final int PERCEPTIBLE_MEDIUM_APP_ADJ = 225;

    // This is a process only hosting components that are perceptible to the
    // user, and we really want to avoid killing them, but they are not
    // immediately visible. An example is background music playback.
    public static final int PERCEPTIBLE_APP_ADJ = 200;

    // TODO: Remove this constant and add VISIBLE_APP_MAX_ADJ to the IntDef bucket
    //  once the oomadjuster_vis_laddering and remove_lru_spam_prevention flags are removed.
    public static final int VISIBLE_APP_MAX_ADJ_LADDERED = 199;

    // This is a process only hosting activities that are visible to the
    // user, so we'd prefer they don't disappear.
    public static final int VISIBLE_APP_ADJ = 100;

    @OomAdjust
    public static final int VISIBLE_APP_MAX_ADJ = Flags.oomadjusterVisLaddering()
            && Flags.removeLruSpamPrevention() ? VISIBLE_APP_MAX_ADJ_LADDERED : VISIBLE_APP_ADJ;

    public static final int VISIBLE_APP_LAYER_MAX = PERCEPTIBLE_APP_ADJ - VISIBLE_APP_ADJ - 1;

    // This is a process that was recently TOP and moved to FGS. Continue to treat it almost
    // like a foreground app for a while.
    // @see TOP_TO_FGS_GRACE_PERIOD
    public static final int PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ = 50;

    // This is the process running the current foreground app.  We'd really
    // rather not kill it!
    public static final int FOREGROUND_APP_ADJ = 0;

    // This is a process that the system or a persistent process has bound to,
    // and indicated it is important.
    public static final int PERSISTENT_SERVICE_ADJ = -700;

    // This is a system persistent process, such as telephony.  Definitely
    // don't want to kill it, but doing so is not completely fatal.
    public static final int PERSISTENT_PROC_ADJ = -800;

    // The system process runs at the default adjustment.
    public static final int SYSTEM_ADJ = -900;

    // Special code for native processes that are not being managed by the system (so
    // don't have an oom adj assigned by the system).
    public static final int NATIVE_ADJ = -1000;

    @IntDef(prefix = { "SCHED_GROUP_" }, value = {
            SCHED_GROUP_UNDEFINED,
            SCHED_GROUP_BACKGROUND,
            SCHED_GROUP_RESTRICTED,
            SCHED_GROUP_DEFAULT,
            SCHED_GROUP_TOP_APP,
            SCHED_GROUP_TOP_APP_BOUND,
            SCHED_GROUP_FOREGROUND_WINDOW
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SchedGroup {}

    // Activity manager's version of an undefined schedule group
    public static final int SCHED_GROUP_UNDEFINED = Integer.MIN_VALUE;
    // Activity manager's version of Process.THREAD_GROUP_BACKGROUND
    public static final int SCHED_GROUP_BACKGROUND = 0;
    // Activity manager's version of Process.THREAD_GROUP_RESTRICTED
    public static final int SCHED_GROUP_RESTRICTED = 1;
    // Activity manager's version of Process.THREAD_GROUP_DEFAULT
    public static final int SCHED_GROUP_DEFAULT = 2;
    // Activity manager's version of Process.THREAD_GROUP_TOP_APP
    public static final int SCHED_GROUP_TOP_APP = 3;
    // Activity manager's version of Process.THREAD_GROUP_TOP_APP
    // Disambiguate between actual top app and processes bound to the top app
    public static final int SCHED_GROUP_TOP_APP_BOUND = 4;
    // Activity manager's version of Process.THREAD_GROUP_FOREGROUND_WINDOW
    // The priority is like between default and top-app.
    public static final int SCHED_GROUP_FOREGROUND_WINDOW = 5;
}
