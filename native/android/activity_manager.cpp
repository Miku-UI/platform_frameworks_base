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

#define LOG_NDEBUG 0
#define LOG_TAG "AActivityManager"
#include <utils/Log.h>

#include <android/activity_manager.h>
#include <android/app/BnProcessObserver.h>
#include <android/app/IProcessObserver.h>
#include <android/binder_status.h>
#include <android/binder_auto_utils.h>
#include <android/app/RunningAppProcessInfo.h>
#include <binder/ActivityManager.h>

#include <mutex>
#include <vector>

namespace android {
namespace activitymanager {

// Global instance of ActivityManager, service is obtained only on first use.
static ActivityManager gAm;
// String tag used with ActivityManager.
static const String16& getTag() {
    static String16 tag("libandroid");
    return tag;
}

struct UidObserver : public BnUidObserver, public virtual IBinder::DeathRecipient {
    explicit UidObserver(const AActivityManager_onUidImportance& cb,
                         int32_t cutpoint, void* cookie)
          : mCallback(cb), mImportanceCutpoint(cutpoint), mCookie(cookie), mRegistered(false) {}
    bool registerSelf();
    void unregisterSelf();

    // IUidObserver
    void onUidGone(uid_t uid, bool disabled) override;
    void onUidActive(uid_t uid) override;
    void onUidIdle(uid_t uid, bool disabled) override;
    void onUidStateChanged(uid_t uid, int32_t procState, int64_t procStateSeq,
                           int32_t capability) override;
    void onUidProcAdjChanged(uid_t uid, int32_t adj) override;

    // IBinder::DeathRecipient implementation
    void binderDied(const wp<IBinder>& who) override;

    static int32_t procStateToImportance(int32_t procState);
    static int32_t importanceToProcState(int32_t importance);

    AActivityManager_onUidImportance mCallback;
    int32_t mImportanceCutpoint;
    void* mCookie;
    std::mutex mRegisteredLock;
    bool mRegistered GUARDED_BY(mRegisteredLock);
};

//static
int32_t UidObserver::procStateToImportance(int32_t procState) {
    // TODO: remove this after adding Importance to onUidStateChanged callback.
    if (procState == ActivityManager::PROCESS_STATE_NONEXISTENT) {
        return AACTIVITYMANAGER_IMPORTANCE_GONE;
    } else if (procState >= ActivityManager::PROCESS_STATE_HOME) {
        return AACTIVITYMANAGER_IMPORTANCE_CACHED;
    } else if (procState == ActivityManager::PROCESS_STATE_HEAVY_WEIGHT) {
        return AACTIVITYMANAGER_IMPORTANCE_CANT_SAVE_STATE;
    } else if (procState >= ActivityManager::PROCESS_STATE_TOP_SLEEPING) {
        return AACTIVITYMANAGER_IMPORTANCE_TOP_SLEEPING;
    } else if (procState >= ActivityManager::PROCESS_STATE_SERVICE) {
        return AACTIVITYMANAGER_IMPORTANCE_SERVICE;
    } else if (procState >= ActivityManager::PROCESS_STATE_TRANSIENT_BACKGROUND) {
        return AACTIVITYMANAGER_IMPORTANCE_PERCEPTIBLE;
    } else if (procState >= ActivityManager::PROCESS_STATE_IMPORTANT_FOREGROUND) {
        return AACTIVITYMANAGER_IMPORTANCE_VISIBLE;
    } else if (procState >= ActivityManager::PROCESS_STATE_FOREGROUND_SERVICE) {
        return AACTIVITYMANAGER_IMPORTANCE_FOREGROUND_SERVICE;
    } else {
        return AACTIVITYMANAGER_IMPORTANCE_FOREGROUND;
    }
}

//static
int32_t UidObserver::importanceToProcState(int32_t importance) {
    // TODO: remove this after adding Importance to onUidStateChanged callback.
    if (importance == AACTIVITYMANAGER_IMPORTANCE_GONE) {
        return ActivityManager::PROCESS_STATE_NONEXISTENT;
    } else if (importance >= AACTIVITYMANAGER_IMPORTANCE_CACHED) {
        return ActivityManager::PROCESS_STATE_HOME;
    } else if (importance >= AACTIVITYMANAGER_IMPORTANCE_CANT_SAVE_STATE) {
        return ActivityManager::PROCESS_STATE_HEAVY_WEIGHT;
    } else if (importance >= AACTIVITYMANAGER_IMPORTANCE_TOP_SLEEPING) {
        return ActivityManager::PROCESS_STATE_TOP_SLEEPING;
    } else if (importance >= AACTIVITYMANAGER_IMPORTANCE_SERVICE) {
        return ActivityManager::PROCESS_STATE_SERVICE;
    } else if (importance >= AACTIVITYMANAGER_IMPORTANCE_PERCEPTIBLE) {
        return ActivityManager::PROCESS_STATE_TRANSIENT_BACKGROUND;
    } else if (importance >= AACTIVITYMANAGER_IMPORTANCE_VISIBLE) {
        return ActivityManager::PROCESS_STATE_IMPORTANT_FOREGROUND;
    } else if (importance >= AACTIVITYMANAGER_IMPORTANCE_FOREGROUND_SERVICE) {
        return ActivityManager::PROCESS_STATE_FOREGROUND_SERVICE;
    } else {
        return ActivityManager::PROCESS_STATE_TOP;
    }
}


void UidObserver::onUidGone(uid_t uid, bool disabled __unused) {
    std::scoped_lock lock{mRegisteredLock};

    if (mRegistered && mCallback) {
        mCallback(uid, AACTIVITYMANAGER_IMPORTANCE_GONE, mCookie);
    }
}

void UidObserver::onUidActive(uid_t uid __unused) {}

void UidObserver::onUidIdle(uid_t uid __unused, bool disabled __unused) {}

void UidObserver::onUidProcAdjChanged(uid_t uid __unused, int32_t adj __unused) {}

void UidObserver::onUidStateChanged(uid_t uid, int32_t procState,
                                    int64_t procStateSeq __unused,
                                    int32_t capability __unused) {
    std::scoped_lock lock{mRegisteredLock};

    if (mRegistered && mCallback) {
        mCallback(uid, procStateToImportance(procState), mCookie);
    }
}

void UidObserver::binderDied(const wp<IBinder>& /*who*/) {
    // ActivityManager is dead, try to re-register.
    {
        std::scoped_lock lock{mRegisteredLock};
        // If client already unregistered, don't try to re-register.
        if (!mRegistered) {
            return;
        }
        // Clear mRegistered to re-register.
        mRegistered = false;
    }
    registerSelf();
}

bool UidObserver::registerSelf() {
    std::scoped_lock lock{mRegisteredLock};
    if (mRegistered) {
        return true;
    }

    status_t res = gAm.linkToDeath(this);
    if (res != OK) {
        ALOGE("UidObserver: Failed to linkToDeath with ActivityManager (err %d)", res);
        return false;
    }

    // TODO: it seems only way to get all changes is to set cutoff to PROCESS_STATE_UNKNOWN.
    // But there is no equivalent of PROCESS_STATE_UNKNOWN in the UidImportance.
    // If mImportanceCutpoint is < 0, use PROCESS_STATE_UNKNOWN instead.
    res = gAm.registerUidObserver(
            this,
            ActivityManager::UID_OBSERVER_GONE | ActivityManager::UID_OBSERVER_PROCSTATE,
            (mImportanceCutpoint < 0) ? ActivityManager::PROCESS_STATE_UNKNOWN
                                      : importanceToProcState(mImportanceCutpoint),
            getTag());
    if (res != OK) {
        ALOGE("UidObserver: Failed to register with ActivityManager (err %d)", res);
        gAm.unlinkToDeath(this);
        return false;
    }

    mRegistered = true;
    ALOGV("UidObserver: Registered with ActivityManager");
    return true;
}

void UidObserver::unregisterSelf() {
    std::scoped_lock lock{mRegisteredLock};

    if (mRegistered) {
        gAm.unregisterUidObserver(this);
        gAm.unlinkToDeath(this);
        mRegistered = false;
    }

    ALOGV("UidObserver: Unregistered with ActivityManager");
}

class ProcessObserver : public app::BnProcessObserver {
public:
    explicit ProcessObserver(void* cookie)
          : mOnProcessStarted(nullptr),
            mOnForegroundActivitiesChanged(nullptr),
            mOnForegroundServicesChanged(nullptr),
            mOnProcessDied(nullptr),
            mCookie(cookie) {}

    void setOnProcessStarted(AActivityManager_onProcessStarted cb) { mOnProcessStarted = cb; }
    void setOnForegroundActivitiesChanged(AActivityManager_onForegroundActivitiesChanged cb) {
        mOnForegroundActivitiesChanged = cb;
    }
    void setOnForegroundServicesChanged(AActivityManager_onForegroundServicesChanged cb) {
        mOnForegroundServicesChanged = cb;
    }
    void setOnProcessDied(AActivityManager_onProcessDied cb) { mOnProcessDied = cb; }

    bool hasCallbacks() const {
        return mOnProcessStarted || mOnForegroundActivitiesChanged ||
               mOnForegroundServicesChanged || mOnProcessDied;
    }

    binder::Status onProcessStarted(int pid, int processUid, int packageUid,
                                    const std::string& packageName,
                                    const std::string& processName) override {
        if (mOnProcessStarted) {
            mOnProcessStarted(pid, processUid, packageUid, packageName.c_str(),
                              processName.c_str(), mCookie);
        }
        return binder::Status::ok();
    }

    binder::Status onForegroundActivitiesChanged(int pid, int uid,
                                                 bool foregroundActivities) override {
        if (mOnForegroundActivitiesChanged) {
            const AActivityManager_ForegroundActivitiesState state =
                    foregroundActivities
                            ? AACTIVITYMANAGER_HAS_FOREGROUND_ACTIVITIES
                            : AACTIVITYMANAGER_NO_FOREGROUND_ACTIVITIES;
            mOnForegroundActivitiesChanged(pid, uid, state, mCookie);
        }
        return binder::Status::ok();
    }

    binder::Status onForegroundServicesChanged(int pid, int uid, int32_t serviceTypes) override {
        if (mOnForegroundServicesChanged) {
            mOnForegroundServicesChanged(pid, uid, serviceTypes, mCookie);
        }
        return binder::Status::ok();
    }

    binder::Status onProcessDied(int pid, int uid) override {
        if (mOnProcessDied) {
            mOnProcessDied(pid, uid, mCookie);
        }
        return binder::Status::ok();
    }

private:
    AActivityManager_onProcessStarted mOnProcessStarted;
    AActivityManager_onForegroundActivitiesChanged mOnForegroundActivitiesChanged;
    AActivityManager_onForegroundServicesChanged mOnForegroundServicesChanged;
    AActivityManager_onProcessDied mOnProcessDied;
    void* mCookie;
};

} // activitymanager
} // android

using namespace android;
using namespace activitymanager;

struct AActivityManager_UidImportanceListener : public UidObserver {
};

AActivityManager_UidImportanceListener* AActivityManager_addUidImportanceListener(
        AActivityManager_onUidImportance onUidImportance, int32_t importanceCutpoint, void* cookie) {
    sp<UidObserver> observer(new UidObserver(onUidImportance, importanceCutpoint, cookie));
    if (observer == nullptr || !observer->registerSelf()) {
        return nullptr;
    }
    observer->incStrong((void *)AActivityManager_addUidImportanceListener);
    return static_cast<AActivityManager_UidImportanceListener*>(observer.get());
}

void AActivityManager_removeUidImportanceListener(
        AActivityManager_UidImportanceListener* listener) {
    if (listener != nullptr) {
        UidObserver* observer = static_cast<UidObserver*>(listener);
        observer->unregisterSelf();
        observer->decStrong((void *)AActivityManager_addUidImportanceListener);
    }
}

struct AActivityManager_ProcessObserver {
    sp<ProcessObserver> observer;
    bool registered = false;
};

AActivityManager_ProcessObserver* AActivityManager_createProcessObserver(void* cookie) {
    auto handle = new AActivityManager_ProcessObserver();
    if (!handle) {
        return nullptr;
    }
    handle->observer = sp<ProcessObserver>::make(cookie);
    if (handle->observer == nullptr) {
        delete handle;
        return nullptr;
    }
    return handle;
}

void AActivityManager_destroyProcessObserver(AActivityManager_ProcessObserver* observer) {
    if (observer == nullptr) {
        return;
    }
    if (observer->registered) {
        gAm.unregisterProcessObserver(observer->observer);
    }
    delete observer;
}

void AActivityManager_ProcessObserver_setOnProcessStarted(
        AActivityManager_ProcessObserver* observer, AActivityManager_onProcessStarted callback) {
    if (observer && observer->observer) {
        observer->observer->setOnProcessStarted(callback);
    }
}

void AActivityManager_ProcessObserver_setOnForegroundActivitiesChanged(
        AActivityManager_ProcessObserver* observer,
        AActivityManager_onForegroundActivitiesChanged callback) {
    if (observer && observer->observer) {
        observer->observer->setOnForegroundActivitiesChanged(callback);
    }
}

void AActivityManager_ProcessObserver_setOnForegroundServicesChanged(
        AActivityManager_ProcessObserver* observer,
        AActivityManager_onForegroundServicesChanged callback) {
    if (observer && observer->observer) {
        observer->observer->setOnForegroundServicesChanged(callback);
    }
}

void AActivityManager_ProcessObserver_setOnProcessDied(
        AActivityManager_ProcessObserver* observer, AActivityManager_onProcessDied callback) {
    if (observer && observer->observer) {
        observer->observer->setOnProcessDied(callback);
    }
}

binder_status_t AActivityManager_registerProcessObserver(
        AActivityManager_ProcessObserver* observer) {
    if (observer == nullptr || observer->observer == nullptr) {
        return STATUS_BAD_VALUE;
    }
    if (observer->registered) {
        return STATUS_INVALID_OPERATION;
    }
    if (!observer->observer->hasCallbacks()) {
        return STATUS_BAD_VALUE;
    }

    status_t status = gAm.registerProcessObserver(observer->observer);
    if (status != OK) {
        ALOGE("ProcessObserver: Failed to register with ActivityManager (err %d)", status);
        return ndk::ScopedAStatus::fromStatus(status).getStatus();
    }

    observer->registered = true;
    return STATUS_OK;
}

void AActivityManager_unregisterProcessObserver(AActivityManager_ProcessObserver* observer) {
    if (observer == nullptr || !observer->registered) {
        return;
    }
    gAm.unregisterProcessObserver(observer->observer);
    observer->registered = false;
}

struct ARunningAppProcessInfo {
    pid_t pid;
    uid_t uid;
    std::string processName;
    std::vector<std::string> pkgList;
    int32_t importance;
    std::vector<const char*> pkgListCStr;
};

struct ARunningAppProcessInfoList {
    std::vector<ARunningAppProcessInfo> list;
};

binder_status_t AActivityManager_getRunningAppProcesses(
        ARunningAppProcessInfoList** outProcessInfoList) {
    if (outProcessInfoList == nullptr) {
        return STATUS_BAD_VALUE;
    }

    std::vector<app::RunningAppProcessInfo> processes;
    status_t status = gAm.getRunningAppProcesses(&processes);
    if (status != OK) {
        return ndk::ScopedAStatus::fromStatus(status).getStatus();
    }

    auto* list = new ARunningAppProcessInfoList();
    list->list.reserve(processes.size());

    for (const auto& p : processes) {
        ARunningAppProcessInfo info;
        info.pid = p.pid;
        info.uid = p.uid;
        info.processName = p.processName;
        if (p.pkgList.has_value()) {
            for (const auto& pkg : p.pkgList.value()) {
                if (pkg.has_value()) {
                    info.pkgList.push_back(pkg.value());
                }
            }
        }
        info.importance = p.importance;

        info.pkgListCStr.reserve(info.pkgList.size());
        for (const auto& pkg : info.pkgList) {
            info.pkgListCStr.push_back(pkg.c_str());
        }
        list->list.emplace_back(std::move(info));
    }

    *outProcessInfoList = list;
    return STATUS_OK;
}

void AActivityManager_RunningAppProcessInfoList_destroy(const ARunningAppProcessInfoList* list) {
    delete list;
}

size_t AActivityManager_RunningAppProcessInfoList_getSize(const ARunningAppProcessInfoList* list) {
    return list->list.size();
}

const ARunningAppProcessInfo* AActivityManager_RunningAppProcessInfoList_get(
        const ARunningAppProcessInfoList* list, size_t index) {
    if (index >= list->list.size()) {
        return nullptr;
    }
    return &list->list[index];
}

bool AActivityManager_isUidActive(uid_t uid) {
    return gAm.isUidActive(uid, getTag());
}

int32_t AActivityManager_getUidImportance(uid_t uid) {
    return UidObserver::procStateToImportance(gAm.getUidProcessState(uid, getTag()));
}

pid_t ARunningAppProcessInfo_getPid(const ARunningAppProcessInfo* info) { return info->pid; }

uid_t ARunningAppProcessInfo_getUid(const ARunningAppProcessInfo* info) { return info->uid; }

const char* ARunningAppProcessInfo_getProcessName(const ARunningAppProcessInfo* info) {
    return info->processName.c_str();
}

const char* const* ARunningAppProcessInfo_getPackageList(const ARunningAppProcessInfo* info,
                                                         size_t* outNumPackages) {
    *outNumPackages = info->pkgListCStr.size();
    if (info->pkgListCStr.empty()) {
        return nullptr;
    }
    return info->pkgListCStr.data();
}

int32_t ARunningAppProcessInfo_getImportance(const ARunningAppProcessInfo* info) {
    return info->importance;
}
