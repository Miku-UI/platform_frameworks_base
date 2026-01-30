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

package android.app;

/**
 * Information about an app process.
 *
 * For historical reasons, there is also an identical struct defined in Java
 * (android.app.ActivityManager.RunningAppProcessInfo), which existed
 * before this AIDL struct is created. The existing Java class exposes public
 * APIs in a way that can not be directly replaced with this new AIDL struct.
 * Therefore both the Java class and this AIDL struct would continue to
 * co-exist. The Java class continues to provide a public API, and internally
 * uses this AIDL struct for parceling / unparceling.
 *
 * @hide
 */
parcelable RunningAppProcessInfo {
    /**
     * The name of the process that this object is associated with
     */
    @utf8InCpp String processName;

    /**
     * The pid of this process; 0 if none
     */
    int pid;

    /**
     * The user id of this process.
     */
    int uid;

    /**
     * All packages that have been loaded into the process.
     */
    @nullable @utf8InCpp String[] pkgList;

    /**
     * Additional packages loaded into the process as dependency.
     */
    @nullable @utf8InCpp String[] pkgDeps;

    /**
     * Constant for {@link #flags}: this is an app that is unable to
     * correctly save its state when going to the background,
     * so it can not be killed while in the background.
     */
    const int FLAG_CANT_SAVE_STATE = 1<<0;

    /**
     * Constant for {@link #flags}: this process is associated with a
     * persistent system app.
     */
    const int FLAG_PERSISTENT = 1<<1;

    /**
     * Constant for {@link #flags}: this process is associated with a
     * persistent system app.
     */
    const int FLAG_HAS_ACTIVITIES = 1<<2;

    /**
     * Flags of information.  May be any of
     * {@link #FLAG_CANT_SAVE_STATE}.
     */
    int flags;

    /**
     * Last memory trim level reported to the process: corresponds to
     * the values supplied to {@link android.content.ComponentCallbacks2#onTrimMemory(int)
     * ComponentCallbacks2.onTrimMemory(int)}.
     */
    int lastTrimLevel;

    /**
     * Constant for {@link #importance}: This process is running the
     * foreground UI; that is, it is the thing currently at the top of the screen
     * that the user is interacting with.
     */
    const int IMPORTANCE_FOREGROUND = 100;

    /**
     * Constant for {@link #importance}: This process is running a foreground
     * service, for example to perform music playback even while the user is
     * not immediately in the app.  This generally indicates that the process
     * is doing something the user actively cares about.
     */
    const int IMPORTANCE_FOREGROUND_SERVICE = 125;

    /**
     * Constant for {@link #importance}: This process is running something
     * that is actively visible to the user, though not in the immediate
     * foreground.  This may be running a window that is behind the current
     * foreground (so paused and with its state saved, not interacting with
     * the user, but visible to them to some degree); it may also be running
     * other services under the system's control that it inconsiders important.
     */
    const int IMPORTANCE_VISIBLE = 200;

    /**
     * Constant for {@link #importance}: {@link #IMPORTANCE_PERCEPTIBLE} had this wrong value
     * before {@link Build.VERSION_CODES#O}.  Since the {@link Build.VERSION_CODES#O} SDK,
     * the value of {@link #IMPORTANCE_PERCEPTIBLE} has been fixed.
     *
     * <p>The system will return this value instead of {@link #IMPORTANCE_PERCEPTIBLE}
     * on Android versions below {@link Build.VERSION_CODES#O}.
     *
     * <p>On Android version {@link Build.VERSION_CODES#O} and later, this value will still be
     * returned for apps with the target API level below {@link Build.VERSION_CODES#O}.
     * For apps targeting version {@link Build.VERSION_CODES#O} and later,
     * the correct value {@link #IMPORTANCE_PERCEPTIBLE} will be returned.
     */
    const int IMPORTANCE_PERCEPTIBLE_PRE_26 = 130;

    /**
     * Constant for {@link #importance}: This process is not something the user
     * is directly aware of, but is otherwise perceptible to them to some degree.
     */
    const int IMPORTANCE_PERCEPTIBLE = 230;

    /**
     * Constant for {@link #importance}: {@link #IMPORTANCE_CANT_SAVE_STATE} had
     * this wrong value
     * before {@link Build.VERSION_CODES#O}.  Since the {@link Build.VERSION_CODES#O} SDK,
     * the value of {@link #IMPORTANCE_CANT_SAVE_STATE} has been fixed.
     *
     * <p>The system will return this value instead of {@link #IMPORTANCE_CANT_SAVE_STATE}
     * on Android versions below {@link Build.VERSION_CODES#O}.
     *
     * <p>On Android version {@link Build.VERSION_CODES#O} after, this value will still be
     * returned for apps with the target API level below {@link Build.VERSION_CODES#O}.
     * For apps targeting version {@link Build.VERSION_CODES#O} and later,
     * the correct value {@link #IMPORTANCE_CANT_SAVE_STATE} will be returned.
     */
    const int IMPORTANCE_CANT_SAVE_STATE_PRE_26 = 170;

    /**
     * Constant for {@link #importance}: This process contains services
     * that should remain running.  These are background services apps have
     * started, not something the user is aware of, so they may be killed by
     * the system relatively freely (though it is generally desired that they
     * stay running as long as they want to).
     */
    const int IMPORTANCE_SERVICE = 300;

    /**
     * Constant for {@link #importance}: This process is running the foreground
     * UI, but the device is asleep so it is not visible to the user.  Though the
     * system will try hard to keep its process from being killed, in all other
     * ways we consider it a kind of cached process, with the limitations that go
     * along with that state: network access, running background services, etc.
     */
    const int IMPORTANCE_TOP_SLEEPING = 325;

    /**
     * Constant for {@link #importance}: This process is running an
     * application that can not save its state, and thus can't be killed
     * while in the background.  This will be used with apps that have
     * {@link android.R.attr#cantSaveState} set on their application tag.
     */
    const int IMPORTANCE_CANT_SAVE_STATE = 350;

    /**
     * Constant for {@link #importance}: This process process contains
     * cached code that is expendable, not actively running any app components
     * we care about.
     */
    const int IMPORTANCE_CACHED = 400;

    /**
     * @deprecated Renamed to {@link #IMPORTANCE_CACHED}.
     */
    const int IMPORTANCE_BACKGROUND = IMPORTANCE_CACHED;

    /**
     * Constant for {@link #importance}: This process does not exist.
     */
    const int IMPORTANCE_GONE = 1000;

    /**
     * The relative importance level that the system places on this process.
     * These constants are numbered so that "more important" values are
     * always smaller than "less important" values.
     */
    int importance;

    /**
     * An additional ordering within a particular {@link #importance}
     * category, providing finer-grained information about the relative
     * utility of processes within a category.  This number means nothing
     * except that a smaller values are more recently used (and thus
     * more important).  Currently an LRU value is only maintained for
     * the {@link #IMPORTANCE_CACHED} category, though others may
     * be maintained in the future.
     */
    int lru;

    /**
     * Constant for {@link #importanceReasonCode}: nothing special has
     * been specified for the reason for this level.
     */
    const int REASON_UNKNOWN = 0;

    /**
     * Constant for {@link #importanceReasonCode}: one of the application's
     * content providers is being used by another process.  The pid of
     * the client process is in {@link #importanceReasonPid} and the
     * target provider in this process is in
     * {@link #importanceReasonComponent}.
     */
    const int REASON_PROVIDER_IN_USE = 1;

    /**
     * Constant for {@link #importanceReasonCode}: one of the application's
     * content providers is being used by another process.  The pid of
     * the client process is in {@link #importanceReasonPid} and the
     * target provider in this process is in
     * {@link #importanceReasonComponent}.
     */
    const int REASON_SERVICE_IN_USE = 2;

    /**
     * The reason for {@link #importance}, if any.
     */
    int importanceReasonCode;

    /**
     * For the specified values of {@link #importanceReasonCode}, this
     * is the process ID of the other process that is a client of this
     * process.  This will be 0 if no other process is using this one.
     */
    int importanceReasonPid;

    /**
     * For the specified values of {@link #importanceReasonCode}, this
     * is the name of the component that is being used in this process.
     */
    @nullable @utf8InCpp String importanceReasonComponent;

    /**
     * When {@link #importanceReasonPid} is non-0, this is the importance
     * of the other pid.
     */
    int importanceReasonImportance;

    /**
     * Current process state, as per PROCESS_STATE_* constants.
     */
    int processState;

    /**
     * Whether the app is focused in multi-window environment.
     */
    boolean isFocused;

    /**
     * Copy of {@link com.android.server.am.ProcessRecord#lastActivityTime} of the process.
     */
    long lastActivityTime;
}