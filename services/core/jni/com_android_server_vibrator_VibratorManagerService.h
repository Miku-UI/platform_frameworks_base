/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef _ANDROID_SERVER_VIBRATOR_MANAGER_SERVICE_H
#define _ANDROID_SERVER_VIBRATOR_MANAGER_SERVICE_H

#include <aidl/android/hardware/vibrator/BnVibratorCallback.h>
#include <aidl/android/hardware/vibrator/IVibrator.h>
#include <aidl/android/hardware/vibrator/IVibratorManager.h>
#include <android-base/thread_annotations.h>
#include <android/binder_manager.h>
#include <android/binder_parcel.h>
#include <android/binder_parcel_jni.h>
#include <utils/Log.h>
#include <vibratorservice/VibratorManagerHalController.h>

#include <condition_variable>
#include <mutex>
#include <vector>

#include "core_jni_helpers.h"
#include "jni.h"

namespace android {

// TODO(b/409002423): remove this once remove_hidl_support flag removed
extern vibrator::ManagerHalController* android_server_vibrator_VibratorManagerService_getManager();

// IVibratorCallback implementation using JNI to send callback ID to vibrator service.
class VibratorCallback : public aidl::android::hardware::vibrator::BnVibratorCallback {
public:
    VibratorCallback(JavaVM* jvm, jweak callback, jmethodID methodId, jlong callbackId)
          : mJvm(jvm), mCallbackRef(callback), mMethodId(methodId), mCallbackId(callbackId) {}
    virtual ~VibratorCallback() = default;

    ndk::ScopedAStatus onComplete() override {
        auto env = GetOrAttachJNIEnvironment(mJvm);
        if (env->IsSameObject(mCallbackRef, NULL)) {
            ALOGE("Null reference to vibrator service callbacks");
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
        }
        env->CallVoidMethod(mCallbackRef, mMethodId, mCallbackId);
        return ndk::ScopedAStatus::ok();
    }

private:
    JavaVM* mJvm;
    const jweak mCallbackRef;
    const jmethodID mMethodId;
    const jlong mCallbackId;
};

// Provides default HAL service declared on the device, using link-to-death to reload dead objects.
template <typename I>
class HalProvider {
public:
    HalProvider()
          : mDeathRecipient(AIBinder_DeathRecipient_new(onBinderDied)),
            mIsDeathRecipientLinked(false),
            mHal(nullptr) {
        AIBinder_DeathRecipient_setOnUnlinked(mDeathRecipient.get(), onBinderUnlinked);
    }
    virtual ~HalProvider() {
        // This will unlink all linked binders.
        AIBinder_DeathRecipient_delete(mDeathRecipient.release());
        {
            // Need to wait until onBinderUnlinked is called.
            std::unique_lock lock(mMutex);
            mDeathRecipientCv.wait(lock, [this] { return !mIsDeathRecipientLinked; });
        }
    }

    std::shared_ptr<I> getHal() {
        std::lock_guard<std::mutex> lock(mMutex);
        if (mHal) {
            return mHal;
        }
        mHal = loadHal();
        if (mHal == nullptr) {
            ALOGE("%s: Error connecting to HAL", __func__);
            return mHal;
        }
        auto binder = mHal->asBinder().get();
        if (binder == nullptr) {
            ALOGE("%s: Error getting HAL binder object", __func__);
            return mHal;
        }
        auto status = ndk::ScopedAStatus::fromStatus(
                AIBinder_linkToDeath(binder, mDeathRecipient.get(), this));
        if (status.isOk()) {
            mIsDeathRecipientLinked = true;
        } else {
            ALOGE("%s: Error linking to HAL binder death: %s", __func__,
                  status.getDescription().c_str());
        }
        return mHal;
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mMutex);
        if (mHal == nullptr) {
            return;
        }
        ALOGW("%s: clearing HAL client", __func__);
        auto binder = mHal->asBinder().get();
        if (binder && mIsDeathRecipientLinked) {
            auto status = ndk::ScopedAStatus::fromStatus(
                    AIBinder_unlinkToDeath(binder, mDeathRecipient.get(), this));
            if (!status.isOk()) {
                ALOGE("%s: Error unlinking to HAL binder death: %s", __func__,
                      status.getDescription().c_str());
            }
        }
        mHal = nullptr;
    }

    static void onBinderDied(void* cookie) {
        HalProvider* provider = reinterpret_cast<HalProvider*>(cookie);
        if (provider) {
            ALOGW("%s: resetting HAL", __func__);
            provider->handleBinderDeath();
        } else {
            ALOGE("%s: null cookie", __func__);
        }
    }

    static void onBinderUnlinked(void* cookie) {
        HalProvider* provider = reinterpret_cast<HalProvider*>(cookie);
        if (provider) {
            ALOGW("%s: resetting HAL death recipient", __func__);
            provider->handleBinderUnlinked();
        } else {
            ALOGE("%s: null cookie", __func__);
        }
    }

private:
    std::mutex mMutex;
    ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;
    bool mIsDeathRecipientLinked;
    std::condition_variable mDeathRecipientCv;
    std::shared_ptr<I> mHal GUARDED_BY(mMutex);

    void handleBinderDeath() {
        std::lock_guard<std::mutex> lock(mMutex);
        mHal = nullptr;
    }

    void handleBinderUnlinked() {
        {
            std::lock_guard<std::mutex> lock(mMutex);
            mIsDeathRecipientLinked = false;
        }
        mDeathRecipientCv.notify_all();
    }

    virtual std::shared_ptr<I> loadHal() = 0;
};

// Provides default service declared on the device, using link-to-death to reload dead objects.
template <typename I>
class DefaultProvider : public HalProvider<I> {
public:
    DefaultProvider() = default;
    virtual ~DefaultProvider() = default;

private:
    std::shared_ptr<I> loadHal() override {
        auto halName = std::string(I::descriptor) + "/default";
        auto hal = I::fromBinder(ndk::SpAIBinder(AServiceManager_checkService(halName.c_str())));
        if (hal == nullptr) {
            ALOGE("%s: Error connecting to %s", halName.c_str(), __func__);
        }
        return hal;
    }
};

// Returns a new provider for the default HAL service declared on the device, null if not declared.
template <typename I>
std::unique_ptr<HalProvider<I>> defaultProviderForDeclaredService() {
    if (AServiceManager_isDeclared((std::string(I::descriptor) + "/default").c_str())) {
        return std::make_unique<DefaultProvider<I>>();
    }
    return nullptr;
}

// Returns a new parcelable from given java parcel object.
template <typename I>
I fromParcel(JNIEnv* env, AParcel* parcel) {
    I parcelable;
    if (binder_status_t status = parcelable.readFromParcel(parcel); status != STATUS_OK) {
        jniThrowExceptionFmt(env, "android/os/BadParcelableException",
                             "Failed to readFromParcel, status %d (%s)", status, strerror(-status));
    }
    return parcelable;
}

// Returns a new parcelable from given java parcel object.
template <typename I>
I fromJavaParcel(JNIEnv* env, jobject data) {
    I parcelable;
    if (AParcel* parcel = AParcel_fromJavaParcel(env, data); parcel != nullptr) {
        parcelable = fromParcel<I>(env, parcel);
        AParcel_delete(parcel);
    } else {
        jniThrowExceptionFmt(env, "android/os/BadParcelableException",
                             "Failed to AParcel_fromJavaParcel, for nullptr");
    }
    return parcelable;
}

// Returns a new array of parcelables from given java parcel object.
template <typename I>
std::vector<I> vectorFromJavaParcel(JNIEnv* env, jobject data) {
    int32_t size;
    std::vector<I> result;
    if (AParcel* parcel = AParcel_fromJavaParcel(env, data); parcel != nullptr) {
        if (binder_status_t status = AParcel_readInt32(parcel, &size); status == STATUS_OK) {
            for (int i = 0; i < size; i++) {
                result.push_back(fromParcel<I>(env, parcel));
            }
            AParcel_delete(parcel);
        } else {
            jniThrowExceptionFmt(env, "android/os/BadParcelableException",
                                 "Failed to readInt32 for array length, status %d (%s)", status,
                                 strerror(-status));
        }
    } else {
        jniThrowExceptionFmt(env, "android/os/BadParcelableException",
                             "Failed to AParcel_fromJavaParcel, for nullptr");
    }
    return result;
}

} // namespace android

#endif // _ANDROID_SERVER_VIBRATOR_MANAGER_SERVICE_H
