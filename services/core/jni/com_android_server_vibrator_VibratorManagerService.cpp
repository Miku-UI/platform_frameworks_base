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

#define LOG_TAG "VibratorManagerService"

#include "com_android_server_vibrator_VibratorManagerService.h"

#include <aidl/android/hardware/vibrator/IVibratorManager.h>
#include <android/binder_ibinder_jni.h>
#include <nativehelper/JNIHelp.h>
#include <utils/Log.h>
#include <utils/misc.h>
#include <vibratorservice/VibratorManagerHalController.h>

#include <unordered_map>

#include "android_runtime/AndroidRuntime.h"
#include "core_jni_helpers.h"
#include "jni.h"

namespace android {

using BnVibratorCallback = aidl::android::hardware::vibrator::BnVibratorCallback;
using CompositeEffect = aidl::android::hardware::vibrator::CompositeEffect;
using CompositePwleV2 = aidl::android::hardware::vibrator::CompositePwleV2;
using Effect = aidl::android::hardware::vibrator::Effect;
using EffectStrength = aidl::android::hardware::vibrator::EffectStrength;
using IVibrator = aidl::android::hardware::vibrator::IVibrator;
using IVibratorManager = aidl::android::hardware::vibrator::IVibratorManager;
using IVibrationSession = aidl::android::hardware::vibrator::IVibrationSession;
using PrimitivePwle = aidl::android::hardware::vibrator::PrimitivePwle;
using VendorEffect = aidl::android::hardware::vibrator::VendorEffect;
using VibrationSessionConfig = aidl::android::hardware::vibrator::VibrationSessionConfig;

// Used to attach HAL callbacks to JNI environment and send them back to vibrator manager service.
static JavaVM* sJvm = nullptr;
static jmethodID sMethodIdOnSyncedVibrationComplete;
static jmethodID sMethodIdOnVibrationSessionComplete;
static jmethodID sMethodIdOnVibrationComplete;

// TODO(b/409002423): remove this once remove_hidl_support flag removed
static std::mutex gManagerMutex;
static vibrator::ManagerHalController* gManager GUARDED_BY(gManagerMutex) = nullptr;

// Log debug messages about metrics events logged to statsd.
// Enable this via "adb shell setprop persist.log.tag.VibratorManagerService DEBUG"
// or "adb shell setprop persist.log.tag.Vibrator_All DEBUG" (requires reboot).
const bool DEBUG = __android_log_is_loggable(ANDROID_LOG_DEBUG, LOG_TAG, ANDROID_LOG_INFO) ||
        __android_log_is_loggable(ANDROID_LOG_DEBUG, "Vibrator_All", ANDROID_LOG_INFO);

// IVibratorCallback implementation for HalVibrator.Callbacks.
class VibrationCallback : public BnVibratorCallback {
public:
    VibrationCallback(jweak callback, jint vibratorId, jlong vibrationId, jlong stepId)
          : mCallbackRef(callback),
            mVibratorId(vibratorId),
            mVibrationId(vibrationId),
            mStepId(stepId) {}
    virtual ~VibrationCallback() = default;

    ndk::ScopedAStatus onComplete() override {
        if (DEBUG) {
            ALOGD("%s(%d, %d, %d)", __func__, static_cast<int32_t>(mVibratorId),
                  static_cast<int32_t>(mVibrationId), static_cast<int32_t>(mStepId));
        }
        auto env = GetOrAttachJNIEnvironment(sJvm);
        if (env->IsSameObject(mCallbackRef, NULL)) {
            ALOGE("Null reference to vibrator service callbacks");
            return ndk::ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE);
        }
        env->CallVoidMethod(mCallbackRef, sMethodIdOnVibrationComplete, mVibratorId, mVibrationId,
                            mStepId);
        return ndk::ScopedAStatus::ok();
    }

private:
    const jweak mCallbackRef;
    const jint mVibratorId;
    const jlong mVibrationId;
    const jlong mStepId;
};

// Provides IVibrator instances loaded from IVibratorManager.
class ManagedVibratorProvider : public HalProvider<IVibrator> {
public:
    ManagedVibratorProvider(std::shared_ptr<HalProvider<IVibratorManager>> managerProvider,
                            int32_t vibratorId)
          : mManagerProvider(std::move(managerProvider)), mVibratorId(vibratorId) {}
    virtual ~ManagedVibratorProvider() = default;

private:
    std::shared_ptr<HalProvider<IVibratorManager>> mManagerProvider;
    const int32_t mVibratorId;

    std::shared_ptr<IVibrator> loadHal() override {
        auto managerHal = mManagerProvider->getHal();
        if (managerHal == nullptr) {
            ALOGE("%s: Error loading manager HAL to get vibrator id=%d", __func__, mVibratorId);
            return nullptr;
        }
        std::shared_ptr<IVibrator> hal;
        auto status = managerHal->getVibrator(mVibratorId, &hal);
        if (!status.isOk() || hal == nullptr) {
            ALOGE("%s: Error on getVibrator(%d): %s", __func__, mVibratorId, status.getMessage());
            return nullptr;
        }
        return hal;
    }
};

class NativeVibratorManagerService {
public:
    // TODO(b/409002423): remove this once remove_hidl_support flag removed
    NativeVibratorManagerService(JNIEnv* env, jobject callbackListener)
          : mHal(std::make_unique<vibrator::ManagerHalController>()),
            mCallbackListener(env->NewGlobalRef(callbackListener)),
            mManagerCallbacks(nullptr),
            mVibratorCallbacks(nullptr),
            mManagerHalProvider(nullptr) {
        LOG_ALWAYS_FATAL_IF(mHal == nullptr, "Unable to find reference to vibrator manager hal");
        LOG_ALWAYS_FATAL_IF(mCallbackListener == nullptr,
                            "Unable to create global reference to vibration callback handler");
    }

    NativeVibratorManagerService(JNIEnv* env, jobject managerCallbacks, jobject vibratorCallbacks)
          : mHal(nullptr),
            mCallbackListener(nullptr),
            mManagerCallbacks(env->NewWeakGlobalRef(managerCallbacks)),
            mVibratorCallbacks(env->NewWeakGlobalRef(vibratorCallbacks)),
            mManagerHalProvider(defaultProviderForDeclaredService<IVibratorManager>()) {
        LOG_ALWAYS_FATAL_IF(mManagerCallbacks == nullptr,
                            "Unable to create global reference to vibrator manager callbacks");
        LOG_ALWAYS_FATAL_IF(mVibratorCallbacks == nullptr,
                            "Unable to create global reference to vibrator callbacks");
    }

    ~NativeVibratorManagerService() {
        auto jniEnv = GetOrAttachJNIEnvironment(sJvm);
        if (mCallbackListener) {
            jniEnv->DeleteGlobalRef(mCallbackListener);
        }
        if (mManagerCallbacks) {
            jniEnv->DeleteWeakGlobalRef(mManagerCallbacks);
        }
        if (mVibratorCallbacks) {
            jniEnv->DeleteWeakGlobalRef(mVibratorCallbacks);
        }
    }

    jweak managerCallbacks() {
        return mManagerCallbacks;
    }

    jweak vibratorCallbacks() {
        return mVibratorCallbacks;
    }

    std::shared_ptr<IVibratorManager> managerHal() {
        return mManagerHalProvider ? mManagerHalProvider->getHal() : nullptr;
    }

    std::shared_ptr<IVibrator> vibratorHal(int32_t vibratorId) {
        if (mVibratorHalProviders.find(vibratorId) == mVibratorHalProviders.end()) {
            mVibratorHalProviders[vibratorId] = mManagerHalProvider
                    ? std::make_unique<ManagedVibratorProvider>(mManagerHalProvider, vibratorId)
                    : defaultProviderForDeclaredService<IVibrator>();
        }
        if (mVibratorHalProviders[vibratorId] == nullptr) {
            return nullptr;
        }
        return mVibratorHalProviders[vibratorId]->getHal();
    }

    void processManagerStatus(ndk::ScopedAStatus& status, const char* logLabel) {
        if (!status.isOk()) {
            ALOGE("%s: %s", logLabel, status.getDescription().c_str());
            if (status.getExceptionCode() == EX_TRANSACTION_FAILED) {
                ALOGE("%s: Resetting vibrator manager provider", logLabel);
                mManagerHalProvider->clear();
            }
        }
    }

    void processVibratorStatus(int32_t vibratorId, ndk::ScopedAStatus& status,
                               const char* logLabel) {
        if (!status.isOk()) {
            ALOGE("%s: %s", logLabel, status.getDescription().c_str());
            if (status.getExceptionCode() == EX_TRANSACTION_FAILED) {
                ALOGE("%s: Resetting vibrator %d provider", logLabel, vibratorId);
                if (mVibratorHalProviders[vibratorId]) {
                    mVibratorHalProviders[vibratorId]->clear();
                }
            }
        }
    }

    // TODO(b/409002423): remove functions below once remove_hidl_support flag removed
    vibrator::ManagerHalController* hal() const { return mHal.get(); }

    std::function<void()> createSyncedVibrationCallback(jlong vibrationId) {
        return [vibrationId, this]() {
            auto jniEnv = GetOrAttachJNIEnvironment(sJvm);
            jniEnv->CallVoidMethod(mCallbackListener, sMethodIdOnSyncedVibrationComplete,
                                   vibrationId);
        };
    }

    std::function<void()> createVibrationSessionCallback(jlong sessionId) {
        return [sessionId, this]() {
            auto jniEnv = GetOrAttachJNIEnvironment(sJvm);
            jniEnv->CallVoidMethod(mCallbackListener, sMethodIdOnVibrationSessionComplete,
                                   sessionId);
            std::lock_guard<std::mutex> lock(mSessionMutex);
            auto it = mSessions.find(sessionId);
            if (it != mSessions.end()) {
                mSessions.erase(it);
            }
        };
    }

    bool startSession(jlong sessionId, const std::vector<int32_t>& vibratorIds) {
        VibrationSessionConfig config;
        auto callback = createVibrationSessionCallback(sessionId);
        auto result = hal()->startSession(vibratorIds, config, callback);
        if (!result.isOk()) {
            return false;
        }

        std::lock_guard<std::mutex> lock(mSessionMutex);
        mSessions[sessionId] = std::move(result.value());
        return true;
    }

    void closeSession(jlong sessionId) {
        std::lock_guard<std::mutex> lock(mSessionMutex);
        auto it = mSessions.find(sessionId);
        if (it != mSessions.end()) {
            it->second->close();
            // Keep session, it can still be aborted.
        }
    }

    void abortSession(jlong sessionId) {
        std::lock_guard<std::mutex> lock(mSessionMutex);
        auto it = mSessions.find(sessionId);
        if (it != mSessions.end()) {
            it->second->abort();
            mSessions.erase(it);
        }
    }

    void clearSessions() {
        hal()->clearSessions();
        std::lock_guard<std::mutex> lock(mSessionMutex);
        mSessions.clear();
    }

private:
    // TODO(b/409002423): remove this once remove_hidl_support flag removed
    std::mutex mSessionMutex;
    const std::unique_ptr<vibrator::ManagerHalController> mHal;
    std::unordered_map<jlong, std::shared_ptr<IVibrationSession>> mSessions
            GUARDED_BY(mSessionMutex);
    const jobject mCallbackListener;

    const jweak mManagerCallbacks;
    const jweak mVibratorCallbacks;
    std::shared_ptr<HalProvider<IVibratorManager>> mManagerHalProvider;
    std::unordered_map<int32_t, std::unique_ptr<HalProvider<IVibrator>>> mVibratorHalProviders;
};

// TODO(b/409002423): remove this once remove_hidl_support flag removed
vibrator::ManagerHalController* android_server_vibrator_VibratorManagerService_getManager() {
    std::lock_guard<std::mutex> lock(gManagerMutex);
    return gManager;
}

jint vibrationResultFromStatus(ndk::ScopedAStatus& status, int32_t successValue,
                               const char* logLabel) {
    if (status.isOk()) {
        return static_cast<jint>(successValue);
    }
    if (status.getExceptionCode() == EX_UNSUPPORTED_OPERATION ||
        status.getStatus() == STATUS_UNKNOWN_TRANSACTION) {
        // STATUS_UNKNOWN_TRANSACTION means the HAL implementation is an older version, so this
        // is the same as the operation being unsupported by this HAL.
        return 0;
    }
    return -1;
}

static NativeVibratorManagerService* toNativeService(jlong ptr, const char* logLabel) {
    auto service = reinterpret_cast<NativeVibratorManagerService*>(ptr);
    if (service == nullptr) {
        ALOGE("%s: native service not initialized", logLabel);
    }
    return service;
}

static std::shared_ptr<IVibratorManager> loadManagerHal(NativeVibratorManagerService* service,
                                                        const char* logLabel) {
    if (service == nullptr) {
        return nullptr;
    }
    auto hal = service->managerHal();
    if (hal == nullptr) {
        ALOGE("%s: vibrator manager HAL not available", logLabel);
    }
    return hal;
}

static std::shared_ptr<IVibrator> loadVibratorHal(NativeVibratorManagerService* service,
                                                  int32_t vibratorId, const char* logLabel) {
    if (service == nullptr) {
        return nullptr;
    }
    auto hal = service->vibratorHal(vibratorId);
    if (hal == nullptr) {
        ALOGE("%s: vibrator HAL not available for vibrator id %d", logLabel, vibratorId);
    }
    return hal;
}

static void destroyNativeService(void* ptr) {
    ALOGD("%s", __func__);
    auto service = reinterpret_cast<NativeVibratorManagerService*>(ptr);
    if (service) {
        std::lock_guard<std::mutex> lock(gManagerMutex);
        gManager = nullptr;
        delete service;
    }
}

// TODO(b/409002423): remove this once remove_hidl_support flag removed
static jlong nativeInit(JNIEnv* env, jclass /* clazz */, jobject callbackListener) {
    ALOGD("%s", __func__);
    std::unique_ptr<NativeVibratorManagerService> service =
            std::make_unique<NativeVibratorManagerService>(env, callbackListener);
    {
        std::lock_guard<std::mutex> lock(gManagerMutex);
        gManager = service->hal();
    }
    return reinterpret_cast<jlong>(service.release());
}

static jlong nativeNewInit(JNIEnv* env, jclass /* clazz */, jobject managerCallbacks,
                           jobject vibratorCallbacks) {
    ALOGD("%s", __func__);
    auto service = std::make_unique<NativeVibratorManagerService>(env, managerCallbacks,
                                                                  vibratorCallbacks);
    auto managerHal = loadManagerHal(service.get(), __func__);
    if (managerHal) {
        // Pre-load all vibrator HALs.
        std::vector<int32_t> vibratorIds;
        if (managerHal->getVibratorIds(&vibratorIds).isOk()) {
            for (auto vibratorId : vibratorIds) {
                loadVibratorHal(service.get(), vibratorId, __func__);
            }
        }
    } else {
        // No vibrator manager, pre-load default vibrator with ID = 0.
        loadVibratorHal(service.get(), 0, __func__);
    }
    return reinterpret_cast<jlong>(service.release());
}

static jlong nativeGetFinalizer(JNIEnv* /* env */, jclass /* clazz */) {
    ALOGD("%s", __func__);
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(&destroyNativeService));
}

static jboolean nativeTriggerSyncedWithCallback(JNIEnv* env, jclass /* clazz */, jlong ptr,
                                                jlong vibrationId) {
    if (DEBUG) {
        ALOGD("%s(%d)", __func__, static_cast<int32_t>(vibrationId));
    }
    auto service = toNativeService(ptr, __func__);
    auto hal = loadManagerHal(service, __func__);
    if (hal == nullptr) {
        return JNI_FALSE;
    }
    auto callback = ndk::SharedRefBase::make<VibratorCallback>(sJvm, service->managerCallbacks(),
                                                               sMethodIdOnSyncedVibrationComplete,
                                                               vibrationId);
    auto status = hal->triggerSynced(callback);
    service->processManagerStatus(status, __func__);
    return status.isOk() ? JNI_TRUE : JNI_FALSE;
}

static jobject nativeStartSessionWithCallback(JNIEnv* env, jclass /* clazz */, jlong ptr,
                                              jlong sessionId, jintArray vibratorIds) {
    if (DEBUG) {
        ALOGD("%s(%d)", __func__, static_cast<int32_t>(sessionId));
    }
    auto service = toNativeService(ptr, __func__);
    auto hal = loadManagerHal(service, __func__);
    if (hal == nullptr) {
        return nullptr;
    }
    std::shared_ptr<IVibrationSession> session;
    VibrationSessionConfig config;
    auto callback = ndk::SharedRefBase::make<VibratorCallback>(sJvm, service->managerCallbacks(),
                                                               sMethodIdOnVibrationSessionComplete,
                                                               sessionId);
    jsize size = env->GetArrayLength(vibratorIds);
    std::vector<int32_t> ids(size);
    env->GetIntArrayRegion(vibratorIds, 0, size, reinterpret_cast<jint*>(ids.data()));
    auto status = hal->startSession(ids, config, callback, &session);
    service->processManagerStatus(status, __func__);
    return AIBinder_toJavaBinder(env, session->asBinder().get());
}

static jint nativeVibratorOnWithCallback(JNIEnv* env, jclass /* clazz */, jlong ptr,
                                         jint vibratorId, jlong vibrationId, jlong stepId,
                                         jint durationMs) {
    if (DEBUG) {
        ALOGD("%s(%d, %d, %d)", __func__, static_cast<int32_t>(vibratorId),
              static_cast<int32_t>(vibrationId), static_cast<int32_t>(stepId));
    }
    auto service = toNativeService(ptr, __func__);
    auto hal = loadVibratorHal(service, static_cast<int32_t>(vibratorId), __func__);
    if (service == nullptr || hal == nullptr) {
        return -1;
    }
    auto callback = ndk::SharedRefBase::make<VibrationCallback>(service->vibratorCallbacks(),
                                                                vibratorId, vibrationId, stepId);
    int32_t millis = static_cast<int32_t>(durationMs);
    auto status = hal->on(millis, callback);
    service->processVibratorStatus(static_cast<int32_t>(vibratorId), status, __func__);
    return vibrationResultFromStatus(status, millis, __func__);
}

static jint nativeVibratorPerformVendorEffectWithCallback(JNIEnv* env, jclass /* clazz */,
                                                          jlong ptr, jint vibratorId,
                                                          jlong vibrationId, jlong stepId,
                                                          jobject vendorEffect) {
    if (DEBUG) {
        ALOGD("%s(%d, %d, %d)", __func__, static_cast<int32_t>(vibratorId),
              static_cast<int32_t>(vibrationId), static_cast<int32_t>(stepId));
    }
    auto service = toNativeService(ptr, __func__);
    auto hal = loadVibratorHal(service, static_cast<int32_t>(vibratorId), __func__);
    if (service == nullptr || hal == nullptr) {
        return -1;
    }
    auto effect = fromJavaParcel<VendorEffect>(env, vendorEffect);
    auto callback = ndk::SharedRefBase::make<VibrationCallback>(service->vibratorCallbacks(),
                                                                vibratorId, vibrationId, stepId);
    auto status = hal->performVendorEffect(effect, callback);
    service->processVibratorStatus(static_cast<int32_t>(vibratorId), status, __func__);
    return vibrationResultFromStatus(status, INT32_MAX, __func__);
}

static jint nativeVibratorPerformEffectWithCallback(JNIEnv* env, jclass /* clazz */, jlong ptr,
                                                    jint vibratorId, jlong vibrationId,
                                                    jlong stepId, jint effectId,
                                                    jint effectStrength) {
    if (DEBUG) {
        ALOGD("%s(%d, %d, %d)", __func__, static_cast<int32_t>(vibratorId),
              static_cast<int32_t>(vibrationId), static_cast<int32_t>(stepId));
    }
    auto service = toNativeService(ptr, __func__);
    auto hal = loadVibratorHal(service, static_cast<int32_t>(vibratorId), __func__);
    if (service == nullptr || hal == nullptr) {
        return -1;
    }
    int32_t durationMs;
    auto effect = static_cast<Effect>(effectId);
    auto strength = static_cast<EffectStrength>(effectStrength);
    auto callback = ndk::SharedRefBase::make<VibrationCallback>(service->vibratorCallbacks(),
                                                                vibratorId, vibrationId, stepId);
    auto status = hal->perform(effect, strength, callback, &durationMs);
    service->processVibratorStatus(static_cast<int32_t>(vibratorId), status, __func__);
    return vibrationResultFromStatus(status, durationMs, __func__);
}

static jint nativeVibratorComposeEffectWithCallback(JNIEnv* env, jclass /* clazz */, jlong ptr,
                                                    jint vibratorId, jlong vibrationId,
                                                    jlong stepId, jobject compositeEffects) {
    if (DEBUG) {
        ALOGD("%s(%d, %d, %d)", __func__, static_cast<int32_t>(vibratorId),
              static_cast<int32_t>(vibrationId), static_cast<int32_t>(stepId));
    }
    auto service = toNativeService(ptr, __func__);
    auto hal = loadVibratorHal(service, static_cast<int32_t>(vibratorId), __func__);
    if (service == nullptr || hal == nullptr) {
        return -1;
    }
    auto effects = vectorFromJavaParcel<CompositeEffect>(env, compositeEffects);
    auto callback = ndk::SharedRefBase::make<VibrationCallback>(service->vibratorCallbacks(),
                                                                vibratorId, vibrationId, stepId);
    auto status = hal->compose(effects, callback);
    service->processVibratorStatus(static_cast<int32_t>(vibratorId), status, __func__);
    return vibrationResultFromStatus(status, INT32_MAX, __func__);
}

static jint nativeVibratorComposePwleEffectWithCallback(JNIEnv* env, jclass /* clazz */, jlong ptr,
                                                        jint vibratorId, jlong vibrationId,
                                                        jlong stepId, jobject pwles) {
    if (DEBUG) {
        ALOGD("%s(%d, %d, %d)", __func__, static_cast<int32_t>(vibratorId),
              static_cast<int32_t>(vibrationId), static_cast<int32_t>(stepId));
    }
    auto service = toNativeService(ptr, __func__);
    auto hal = loadVibratorHal(service, static_cast<int32_t>(vibratorId), __func__);
    if (service == nullptr || hal == nullptr) {
        return -1;
    }
    auto effects = vectorFromJavaParcel<PrimitivePwle>(env, pwles);
    auto callback = ndk::SharedRefBase::make<VibrationCallback>(service->vibratorCallbacks(),
                                                                vibratorId, vibrationId, stepId);
    auto status = hal->composePwle(effects, callback);
    service->processVibratorStatus(static_cast<int32_t>(vibratorId), status, __func__);
    return vibrationResultFromStatus(status, INT32_MAX, __func__);
}

static jint nativeVibratorComposePwleV2EffectWithCallback(JNIEnv* env, jclass /* clazz */,
                                                          jlong ptr, jint vibratorId,
                                                          jlong vibrationId, jlong stepId,
                                                          jobject composite) {
    if (DEBUG) {
        ALOGD("%s(%d, %d, %d)", __func__, static_cast<int32_t>(vibratorId),
              static_cast<int32_t>(vibrationId), static_cast<int32_t>(stepId));
    }
    auto service = toNativeService(ptr, __func__);
    auto hal = loadVibratorHal(service, static_cast<int32_t>(vibratorId), __func__);
    if (service == nullptr || hal == nullptr) {
        return -1;
    }
    auto compositePwleV2 = fromJavaParcel<CompositePwleV2>(env, composite);
    auto callback = ndk::SharedRefBase::make<VibrationCallback>(service->vibratorCallbacks(),
                                                                vibratorId, vibrationId, stepId);
    auto status = hal->composePwleV2(compositePwleV2, callback);
    service->processVibratorStatus(static_cast<int32_t>(vibratorId), status, __func__);
    return vibrationResultFromStatus(status, INT32_MAX, __func__);
}

// TODO(b/409002423): remove functions below once remove_hidl_support flag removed

static jlong nativeGetCapabilities(JNIEnv* env, jclass /* clazz */, jlong servicePtr) {
    NativeVibratorManagerService* service =
            reinterpret_cast<NativeVibratorManagerService*>(servicePtr);
    if (service == nullptr) {
        ALOGE("nativeGetCapabilities failed because native service was not initialized");
        return 0;
    }
    auto result = service->hal()->getCapabilities();
    return result.isOk() ? static_cast<jlong>(result.value()) : 0;
}

static jintArray nativeGetVibratorIds(JNIEnv* env, jclass /* clazz */, jlong servicePtr) {
    NativeVibratorManagerService* service =
            reinterpret_cast<NativeVibratorManagerService*>(servicePtr);
    if (service == nullptr) {
        ALOGE("nativeGetVibratorIds failed because native service was not initialized");
        return nullptr;
    }
    auto result = service->hal()->getVibratorIds();
    if (!result.isOk()) {
        return nullptr;
    }
    std::vector<int32_t> vibratorIds = result.value();
    jintArray ids = env->NewIntArray(vibratorIds.size());
    env->SetIntArrayRegion(ids, 0, vibratorIds.size(), reinterpret_cast<jint*>(vibratorIds.data()));
    return ids;
}

static jboolean nativePrepareSynced(JNIEnv* env, jclass /* clazz */, jlong servicePtr,
                                    jintArray vibratorIds) {
    NativeVibratorManagerService* service =
            reinterpret_cast<NativeVibratorManagerService*>(servicePtr);
    if (service == nullptr) {
        ALOGE("nativePrepareSynced failed because native service was not initialized");
        return JNI_FALSE;
    }
    jsize size = env->GetArrayLength(vibratorIds);
    std::vector<int32_t> ids(size);
    env->GetIntArrayRegion(vibratorIds, 0, size, reinterpret_cast<jint*>(ids.data()));
    return service->hal()->prepareSynced(ids).isOk() ? JNI_TRUE : JNI_FALSE;
}

static jboolean nativeTriggerSynced(JNIEnv* env, jclass /* clazz */, jlong servicePtr,
                                    jlong vibrationId) {
    NativeVibratorManagerService* service =
            reinterpret_cast<NativeVibratorManagerService*>(servicePtr);
    if (service == nullptr) {
        ALOGE("nativeTriggerSynced failed because native service was not initialized");
        return JNI_FALSE;
    }
    auto callback = service->createSyncedVibrationCallback(vibrationId);
    return service->hal()->triggerSynced(callback).isOk() ? JNI_TRUE : JNI_FALSE;
}

static void nativeCancelSynced(JNIEnv* env, jclass /* clazz */, jlong servicePtr) {
    NativeVibratorManagerService* service =
            reinterpret_cast<NativeVibratorManagerService*>(servicePtr);
    if (service == nullptr) {
        ALOGE("nativeCancelSynced failed because native service was not initialized");
        return;
    }
    service->hal()->cancelSynced();
}

static jboolean nativeStartSession(JNIEnv* env, jclass /* clazz */, jlong servicePtr,
                                   jlong sessionId, jintArray vibratorIds) {
    NativeVibratorManagerService* service =
            reinterpret_cast<NativeVibratorManagerService*>(servicePtr);
    if (service == nullptr) {
        ALOGE("nativeStartSession failed because native service was not initialized");
        return JNI_FALSE;
    }
    jsize size = env->GetArrayLength(vibratorIds);
    std::vector<int32_t> ids(size);
    env->GetIntArrayRegion(vibratorIds, 0, size, reinterpret_cast<jint*>(ids.data()));
    return service->startSession(sessionId, ids) ? JNI_TRUE : JNI_FALSE;
}

static void nativeEndSession(JNIEnv* env, jclass /* clazz */, jlong servicePtr, jlong sessionId,
                             jboolean shouldAbort) {
    NativeVibratorManagerService* service =
            reinterpret_cast<NativeVibratorManagerService*>(servicePtr);
    if (service == nullptr) {
        ALOGE("nativeEndSession failed because native service was not initialized");
        return;
    }
    if (shouldAbort) {
        service->abortSession(sessionId);
    } else {
        service->closeSession(sessionId);
    }
}

static void nativeClearSessions(JNIEnv* env, jclass /* clazz */, jlong servicePtr) {
    NativeVibratorManagerService* service =
            reinterpret_cast<NativeVibratorManagerService*>(servicePtr);
    if (service == nullptr) {
        ALOGE("nativeClearSessions failed because native service was not initialized");
        return;
    }
    service->clearSessions();
}

inline static constexpr auto sNativeInitMethodSignature =
        "(Lcom/android/server/vibrator/HalVibratorManager$Callbacks;)J";

inline static constexpr auto sNativeNewInitMethodSignature =
        "(Lcom/android/server/vibrator/HalVibratorManager$Callbacks;"
        "Lcom/android/server/vibrator/HalVibrator$Callbacks;)J";

static const JNINativeMethod method_table[] = {
        {"nativeInit", sNativeInitMethodSignature, (void*)nativeInit},
        {"nativeNewInit", sNativeNewInitMethodSignature, (void*)nativeNewInit},
        {"nativeGetFinalizer", "()J", (void*)nativeGetFinalizer},
        {"nativeGetCapabilities", "(J)J", (void*)nativeGetCapabilities},
        {"nativeGetVibratorIds", "(J)[I", (void*)nativeGetVibratorIds},
        {"nativePrepareSynced", "(J[I)Z", (void*)nativePrepareSynced},
        {"nativeTriggerSynced", "(JJ)Z", (void*)nativeTriggerSynced},
        {"nativeCancelSynced", "(J)V", (void*)nativeCancelSynced},
        {"nativeStartSession", "(JJ[I)Z", (void*)nativeStartSession},
        {"nativeEndSession", "(JJZ)V", (void*)nativeEndSession},
        {"nativeClearSessions", "(J)V", (void*)nativeClearSessions},
        {"nativeTriggerSyncedWithCallback", "(JJ)Z", (void*)nativeTriggerSyncedWithCallback},
        {"nativeStartSessionWithCallback", "(JJ[I)Landroid/os/IBinder;",
         (void*)nativeStartSessionWithCallback},
        {"nativeVibratorOnWithCallback", "(JIJJI)I", (void*)nativeVibratorOnWithCallback},
        {"nativeVibratorPerformVendorEffectWithCallback", "(JIJJLandroid/os/Parcel;)I",
         (void*)nativeVibratorPerformVendorEffectWithCallback},
        {"nativeVibratorPerformEffectWithCallback", "(JIJJII)I",
         (void*)nativeVibratorPerformEffectWithCallback},
        {"nativeVibratorComposeEffectWithCallback", "(JIJJLandroid/os/Parcel;)I",
         (void*)nativeVibratorComposeEffectWithCallback},
        {"nativeVibratorComposePwleEffectWithCallback", "(JIJJLandroid/os/Parcel;)I",
         (void*)nativeVibratorComposePwleEffectWithCallback},
        {"nativeVibratorComposePwleV2EffectWithCallback", "(JIJJLandroid/os/Parcel;)I",
         (void*)nativeVibratorComposePwleV2EffectWithCallback},
};

int register_android_server_vibrator_VibratorManagerService(JavaVM* jvm, JNIEnv* env) {
    sJvm = jvm;
    jclass managerCallbacksClass =
            FindClassOrDie(env, "com/android/server/vibrator/HalVibratorManager$Callbacks");
    jclass vibratorCallbacksClass =
            FindClassOrDie(env, "com/android/server/vibrator/HalVibrator$Callbacks");
    sMethodIdOnSyncedVibrationComplete =
            GetMethodIDOrDie(env, managerCallbacksClass, "onSyncedVibrationComplete", "(J)V");
    sMethodIdOnVibrationSessionComplete =
            GetMethodIDOrDie(env, managerCallbacksClass, "onVibrationSessionComplete", "(J)V");
    sMethodIdOnVibrationComplete =
            GetMethodIDOrDie(env, vibratorCallbacksClass, "onVibrationStepComplete", "(IJJ)V");
    return jniRegisterNativeMethods(env, "com/android/server/vibrator/VibratorManagerService",
                                    method_table, NELEM(method_table));
}

}; // namespace android
