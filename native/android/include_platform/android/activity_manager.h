/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef __AACTIVITYMANAGER_H__
#define __AACTIVITYMANAGER_H__

#include <sys/cdefs.h>
#include <sys/types.h>
#include <android/binder_status.h>

__BEGIN_DECLS

struct AActivityManager_UidImportanceListener;
typedef struct AActivityManager_UidImportanceListener AActivityManager_UidImportanceListener;

struct AActivityManager_ProcessObserver;
typedef struct AActivityManager_ProcessObserver AActivityManager_ProcessObserver;

struct ARunningAppProcessInfo;
typedef struct ARunningAppProcessInfo ARunningAppProcessInfo;

struct ARunningAppProcessInfoList;
typedef struct ARunningAppProcessInfoList ARunningAppProcessInfoList;

/**
 * Callback interface when Uid Importance has changed for a uid.
 *
 * This callback will be called on an arbitrary thread. Calls to a given listener will be
 * serialized.
 *
 * @param uid the uid for which the importance has changed.
 * @param uidImportance the new uidImportance for the uid.
 * @cookie the same cookie when the UidImportanceListener was added.
 *
 * Introduced in API 31.
 */
typedef void (*AActivityManager_onUidImportance)(uid_t uid, int32_t uidImportance,
                                                 void* _Nullable cookie);

/**
 * ActivityManager Uid Importance constants.
 *
 * Introduced in API 31.
 */
enum {
    /**
     * Constant for Uid Importance: This process is running the
     * foreground UI; that is, it is the thing currently at the top of the screen
     * that the user is interacting with.
     */
    AACTIVITYMANAGER_IMPORTANCE_FOREGROUND = 100,

    /**
     * Constant for Uid Importance: This process is running a foreground
     * service, for example to perform music playback even while the user is
     * not immediately in the app.  This generally indicates that the process
     * is doing something the user actively cares about.
     */
    AACTIVITYMANAGER_IMPORTANCE_FOREGROUND_SERVICE = 125,

    /**
     * Constant for Uid Importance: This process is running something
     * that is actively visible to the user, though not in the immediate
     * foreground.  This may be running a window that is behind the current
     * foreground (so paused and with its state saved, not interacting with
     * the user, but visible to them to some degree); it may also be running
     * other services under the system's control that it inconsiders important.
     */
    AACTIVITYMANAGER_IMPORTANCE_VISIBLE = 200,

    /**
     * Constant for Uid Importance: This process is not something the user
     * is directly aware of, but is otherwise perceptible to them to some degree.
     */
    AACTIVITYMANAGER_IMPORTANCE_PERCEPTIBLE = 230,

    /**
     * Constant for Uid Importance: This process contains services
     * that should remain running.  These are background services apps have
     * started, not something the user is aware of, so they may be killed by
     * the system relatively freely (though it is generally desired that they
     * stay running as long as they want to).
     */
    AACTIVITYMANAGER_IMPORTANCE_SERVICE = 300,

    /**
     * Constant for Uid Importance: This process is running the foreground
     * UI, but the device is asleep so it is not visible to the user.  Though the
     * system will try hard to keep its process from being killed, in all other
     * ways we consider it a kind of cached process, with the limitations that go
     * along with that state: network access, running background services, etc.
     */
    AACTIVITYMANAGER_IMPORTANCE_TOP_SLEEPING = 325,

    /**
     * Constant for Uid Importance: This process is running an
     * application that can not save its state, and thus can't be killed
     * while in the background.  This will be used with apps that have
     * {@link android.R.attr#cantSaveState} set on their application tag.
     */
    AACTIVITYMANAGER_IMPORTANCE_CANT_SAVE_STATE = 350,

    /**
     * Constant for Uid Importance: This process process contains
     * cached code that is expendable, not actively running any app components
     * we care about.
     */
    AACTIVITYMANAGER_IMPORTANCE_CACHED = 400,

    /**
     * Constant for Uid Importance: This process does not exist.
     */
    AACTIVITYMANAGER_IMPORTANCE_GONE = 1000,
};

/**
 * Adds a UidImportanceListener to the ActivityManager.
 *
 * This API requires android.Manifest.permission.PACKAGE_USAGE_STATS permission.
 *
 * @param onUidImportance the listener callback that will receive change reports.
 *
 * @param importanceCutpoint the level of importance in which the caller is interested
 * in differences. For example, if AACTIVITYMANAGER_IMPORTANCE_PERCEPTIBLE is used
 * here, you will receive a call each time a uid's importance transitions between being
 * <= AACTIVITYMANAGER_IMPORTANCE_PERCEPTIBLE and > AACTIVITYMANAGER_IMPORTANCE_PERCEPTIBLE.
 *
 * @param cookie a cookie that will be passed back to the listener callback.
 *
 * @return an opaque pointer of AActivityManager_UidImportanceListener, or nullptr
 * upon failure. Upon success, the returned AActivityManager_UidImportanceListener pointer
 * must be removed and released through AActivityManager_removeUidImportanceListener.
 */
AActivityManager_UidImportanceListener* _Nullable AActivityManager_addUidImportanceListener(
        AActivityManager_onUidImportance _Nullable onUidImportance,
        int32_t importanceCutpoint,
        void* _Nullable cookie) __INTRODUCED_IN(31);

/**
 * Removes a UidImportanceListener that was added with AActivityManager_addUidImportanceListener.
 *
 * When this returns, it's guaranteed the listener callback will no longer be invoked.
 *
 * @param listener the UidImportanceListener to be removed.
 */
void AActivityManager_removeUidImportanceListener(
        AActivityManager_UidImportanceListener* _Nullable listener) __INTRODUCED_IN(31);

/**
 * Queries if a uid is currently active.
 *
 * This API requires android.Manifest.permission.PACKAGE_USAGE_STATS permission.
 *
 * @return true if the uid is active, false otherwise.
 */
bool AActivityManager_isUidActive(uid_t uid) __INTRODUCED_IN(31);

/**
 * Queries the current Uid Importance value of a uid.
 *
 * This API requires android.Manifest.permission.PACKAGE_USAGE_STATS permission.
 *
 * @param uid the uid for which the importance value is queried.
 * @return the current uid importance value for uid.
 */
int32_t AActivityManager_getUidImportance(uid_t uid) __INTRODUCED_IN(31);

/**
 * The state of foreground activities in a process.
 */
typedef enum AActivityManager_ForegroundActivitiesState : int32_t {
    /**
     * The process has no foreground activities.
     *
     * Introduced in API 37.
     */
    AACTIVITYMANAGER_NO_FOREGROUND_ACTIVITIES = 0,
    /**
     * The process has one or more foreground activities.
     *
     * Introduced in API 37.
     */
    AACTIVITYMANAGER_HAS_FOREGROUND_ACTIVITIES = 1,
} AActivityManager_ForegroundActivitiesState;

/**
 * Callback interface for being notified when a process is started.
 *
 * @param pid The pid of the process.
 * @param processUid The UID associated with the process.
 * @param packageUid The UID associated with the package.
 * @param packageName The name of the package.
 * @param processName The name of the process.
 * @param cookie The cookie provided when the listener was registered.
 */
typedef void (*AActivityManager_onProcessStarted)(pid_t pid, uid_t processUid, uid_t packageUid,
                                                  const char* _Nonnull packageName,
                                                  const char* _Nonnull processName,
                                                  void* _Nonnull cookie);

/**
 * Callback interface for being notified when the foreground activities of a process change.
 *
 * @param pid The pid of the process.
 * @param uid The UID of the process.
 * @param state The foreground activities state.
 * @param cookie The cookie provided when the listener was registered.
 */
typedef void (*AActivityManager_onForegroundActivitiesChanged)(
        pid_t pid, uid_t uid, AActivityManager_ForegroundActivitiesState state,
        void* _Nonnull cookie);

/**
 * Callback interface for being notified when the foreground services of a process change.
 *
 * @param pid The pid of the process.
 * @param uid The UID of the process.
 * @param serviceTypes The types of foreground services.
 * @param cookie The cookie provided when the listener was registered.
 */
typedef void (*AActivityManager_onForegroundServicesChanged)(pid_t pid, uid_t uid,
                                                             int32_t serviceTypes,
                                                             void* _Nonnull cookie);

/**
 * Callback interface for being notified when a process dies.
 *
 * @param pid The pid of the process.
 * @param uid The UID of the process.
 * @param cookie The cookie provided when the listener was registered.
 */
typedef void (*AActivityManager_onProcessDied)(pid_t pid, uid_t uid, void* _Nonnull cookie);

/**
 * Creates a process observer.
 *
 * The returned observer is not active until registered with
 * AActivityManager_registerProcessObserver().
 *
 * The returned observer must be destroyed with AActivityManager_destroyProcessObserver().
 *
 * @param cookie A cookie that will be passed back to the listener callbacks.
 * @return An opaque pointer of AActivityManager_ProcessObserver, or nullptr upon failure.
 */
AActivityManager_ProcessObserver* _Nullable AActivityManager_createProcessObserver(
        void* _Nonnull cookie) __INTRODUCED_IN(37);

/**
 * Destroys a process observer.
 *
 * If the observer is registered, it will be unregistered first.
 *
 * @param observer The observer to destroy.
 */
void AActivityManager_destroyProcessObserver(
        AActivityManager_ProcessObserver* _Nonnull observer) __INTRODUCED_IN(37);

/**
 * Sets the callback for when a process starts. Must be called before registering the observer.
 * @param observer The process observer.
 * @param callback The callback to set. Can be null to unset.
 */
void AActivityManager_ProcessObserver_setOnProcessStarted(
        AActivityManager_ProcessObserver* _Nonnull observer,
        AActivityManager_onProcessStarted _Nullable callback) __INTRODUCED_IN(37);

/**
 * Sets the callback for when foreground activities change. Must be called before registering the
 * observer.
 * @param observer The process observer.
 * @param callback The callback to set. Can be null to unset.
 */
void AActivityManager_ProcessObserver_setOnForegroundActivitiesChanged(
        AActivityManager_ProcessObserver* _Nonnull observer,
        AActivityManager_onForegroundActivitiesChanged _Nullable callback) __INTRODUCED_IN(37);

/**
 * Sets the callback for when foreground services change. Must be called before registering the
 * observer.
 * @param observer The process observer.
 * @param callback The callback to set. Can be null to unset.
 */
void AActivityManager_ProcessObserver_setOnForegroundServicesChanged(
        AActivityManager_ProcessObserver* _Nonnull observer,
        AActivityManager_onForegroundServicesChanged _Nullable callback) __INTRODUCED_IN(37);

/**
 * Sets the callback for when a process dies. Must be called before registering the observer.
 * @param observer The process observer.
 * @param callback The callback to set. Can be null to unset.
 */
void AActivityManager_ProcessObserver_setOnProcessDied(
        AActivityManager_ProcessObserver* _Nonnull observer,
        AActivityManager_onProcessDied _Nullable callback) __INTRODUCED_IN(37);

/**
 * Registers a process observer to receive callbacks about process state changes.
 *
 * At least one callback must be set on the observer.
 *
 * This API requires android.Manifest.permission.PACKAGE_USAGE_STATS permission.
 *
 * @param observer The observer to register.
 * @return STATUS_OK on success, or a negative error code on failure.
 */
binder_status_t AActivityManager_registerProcessObserver(
        AActivityManager_ProcessObserver* _Nonnull observer) __INTRODUCED_IN(37);

/**
 * Unregisters a process observer that was registered with AActivityManager_registerProcessObserver.
 *
 * When this returns, it's guaranteed the listener callbacks will no longer be invoked.
 *
 * @param observer The AActivityManager_ProcessObserver to be removed.
 */
void AActivityManager_unregisterProcessObserver(
        AActivityManager_ProcessObserver* _Nonnull observer) __INTRODUCED_IN(37);

/**
 * Gets a list of running application processes.
 *
 * This API requires android.Manifest.permission.PACKAGE_USAGE_STATS permission.
 *
 * On success, `outProcessInfoList` will be populated with a list of running processes.
 * The caller owns this list and must release it by calling
 * AActivityManager_RunningAppProcessInfoList_destroy().
 *
 * @param outProcessInfoList A pointer that will be set to the list of running processes.
 * @return STATUS_OK on success, or a negative error code on failure.
 */
binder_status_t AActivityManager_getRunningAppProcesses(
        ARunningAppProcessInfoList* _Nullable* _Nonnull outProcessInfoList) __INTRODUCED_IN(37);

/**
 * Destroys a list of running app process info.
 *
 * @param list The list to destroy, obtained from AActivityManager_getRunningAppProcesses.
 */
void AActivityManager_RunningAppProcessInfoList_destroy(
        const ARunningAppProcessInfoList* _Nullable list) __INTRODUCED_IN(37);

/**
 * Gets the number of entries in the list.
 *
 * @param list The list.
 * @return The number of entries.
 */
size_t AActivityManager_RunningAppProcessInfoList_getSize(
        const ARunningAppProcessInfoList* _Nonnull list) __INTRODUCED_IN(37);

/**
 * Gets an entry from the list.
 *
 * The returned pointer is valid only for the lifetime of the list. Do not free it.
 *
 * @param list The list.
 * @param index The index of the entry to get.
 * @return The entry, or nullptr if the index is out of bounds.
 */
const ARunningAppProcessInfo* _Nullable AActivityManager_RunningAppProcessInfoList_get(
        const ARunningAppProcessInfoList* _Nonnull list, size_t index) __INTRODUCED_IN(37);

/**
 * Gets the pid of the process.
 * @param info The process info object from an ARunningAppProcessInfoList.
 * @return The process ID.
 */
pid_t ARunningAppProcessInfo_getPid(const ARunningAppProcessInfo* _Nonnull info)
        __INTRODUCED_IN(37);

/**
 * Gets the uid of the process.
 * @param info The process info object from an ARunningAppProcessInfoList.
 * @return The user ID.
 */
uid_t ARunningAppProcessInfo_getUid(const ARunningAppProcessInfo* _Nonnull info)
        __INTRODUCED_IN(37);

/**
 * Gets the name of the process.
 * The returned string is valid for the lifetime of the ARunningAppProcessInfo object.
 * @param info The process info object from an ARunningAppProcessInfoList.
 * @return The process name in UTF-8.
 */
const char* _Nonnull ARunningAppProcessInfo_getProcessName(
        const ARunningAppProcessInfo* _Nonnull info) __INTRODUCED_IN(37);

/**
 * Gets the list of packages in the process.
 * The returned array and its strings are valid for the lifetime of the ARunningAppProcessInfo
 * object.
 * @param info The process info object from an ARunningAppProcessInfoList.
 * @param outNumPackages Pointer to a size_t to store the number of packages.
 * @return An array of package names in UTF-8. The array is owned by the ARunningAppProcessInfo
 * object. Returns nullptr if there are no packages.
 */
const char* _Nonnull const* _Nullable ARunningAppProcessInfo_getPackageList(
        const ARunningAppProcessInfo* _Nonnull info, size_t* _Nonnull outNumPackages)
        __INTRODUCED_IN(37);

/**
 * Gets the importance of the process.
 * @param info The process info object from an ARunningAppProcessInfoList.
 * @return The importance level. See AACTIVITYMANAGER_IMPORTANCE_* constants.
 */
int32_t ARunningAppProcessInfo_getImportance(const ARunningAppProcessInfo* _Nonnull info)
        __INTRODUCED_IN(37);

__END_DECLS

#endif  // __AACTIVITYMANAGER_H__
