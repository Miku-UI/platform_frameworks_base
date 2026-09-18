/*
 * Copyright (C) 2026 The Android Open Source Project
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

#define LOG_TAG "InteractionProvider-JNI"

#include "com_android_server_attention_InteractionProviderServiceInternal.h"

#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedLocalRef.h>

#include "android_util_Binder.h"
#include "com_android_input_flags.h"
#include "jni_wrappers.h"
#include "utils/misc.h"

namespace input_flags = com::android::input::flags;

namespace android {
namespace {

/**
 * A deleter for jobject global references.
 */
struct GlobalRefDeleter {
    void operator()(jobject obj) {
        if (obj) {
            AndroidRuntime::getJNIEnv()->DeleteGlobalRef(obj);
        }
    }
};

using ScopedGlobalRef = std::shared_ptr<_jobject>;

static ScopedGlobalRef makeScopedGlobalRef(JNIEnv* env, jobject obj) {
    if (obj == nullptr) return nullptr;
    return ScopedGlobalRef(MakeGlobalRefOrDie(env, obj), GlobalRefDeleter());
}

} // namespace

static struct {
    jclass clazz;
    jmethodID registerInteractionProvider;
    jmethodID unregisterInteractionProvider;
} gServiceClassInfo;

static struct {
    jclass clazz;
    jmethodID constructor;
} gNativeInteractionProviderClassInfo;

static struct {
    jclass clazz;
    jmethodID onInteractionsAvailable;
} gInteractionCallbackClassInfo;

static struct {
    jclass clazz;
    jmethodID constructor;
    jfieldID interactionTypes;
    jfieldID interactionTimeMillis;
} gInteractionStateClassInfo;

static struct {
    jclass clazz;
    jmethodID constructor;
    jmethodID add;
} gArrayListClassInfo;

NativeInteractionProviderServiceInternal::NativeInteractionProviderServiceInternal(
        jobject serviceObj) {
    mServiceObj = jniEnv()->NewGlobalRef(serviceObj);
}

NativeInteractionProviderServiceInternal::~NativeInteractionProviderServiceInternal() {
    JNIEnv* env = jniEnv();
    for (auto const& [provider, providerObj] : mRegisteredInteractionProviders) {
        env->DeleteGlobalRef(providerObj);
    }
    env->DeleteGlobalRef(mServiceObj);
}

bool NativeInteractionProviderServiceInternal::registerInteractionProvider(
        std::shared_ptr<attention::InteractionProvider> interactionProvider) {
    JNIEnv* env = jniEnv();

    ScopedLocalRef<jobject>
            interactionProviderObj(env,
                                   env->NewObject(gNativeInteractionProviderClassInfo.clazz,
                                                  gNativeInteractionProviderClassInfo.constructor,
                                                  reinterpret_cast<jlong>(
                                                          interactionProvider.get())));
    if (!interactionProviderObj.get()) {
        ALOGE("Failed to obtain interaction provider obj for registerInteractionProvider.");
        return false;
    }

    const jboolean registered =
            env->CallBooleanMethod(mServiceObj, gServiceClassInfo.registerInteractionProvider,
                                   interactionProviderObj.get());
    if (registered) {
        jobject globalInteractionProviderObj = env->NewGlobalRef(interactionProviderObj.get());
        mRegisteredInteractionProviders[interactionProvider] = globalInteractionProviderObj;
    }
    return registered;
}

bool NativeInteractionProviderServiceInternal::unregisterInteractionProvider(
        std::shared_ptr<attention::InteractionProvider> interactionProvider) {
    auto providersIt = mRegisteredInteractionProviders.find(interactionProvider);
    if (providersIt == mRegisteredInteractionProviders.end()) {
        return false;
    }

    JNIEnv* env = jniEnv();
    jobject providerGlobalRef = providersIt->second;
    const jboolean unregistered =
            env->CallBooleanMethod(mServiceObj, gServiceClassInfo.unregisterInteractionProvider,
                                   providerGlobalRef);
    if (unregistered) {
        // Global reference can be safely released now.
        env->DeleteGlobalRef(providerGlobalRef);
        mRegisteredInteractionProviders.erase(interactionProvider);
    }
    return unregistered;
}

static jobject getSourceInteractions(JNIEnv* env, jobject thiz, jlong nativePtr) {
    attention::InteractionProvider* interactionProvider =
            reinterpret_cast<attention::InteractionProvider*>(nativePtr);

    const auto interactions = interactionProvider->getSourceInteractions();

    jobject jInteractions =
            env->NewObject(gArrayListClassInfo.clazz, gArrayListClassInfo.constructor);

    for (const auto& interactionState : interactions) {
        ScopedLocalRef<jobject> interactionObj(env,
                                               env->NewObject(gInteractionStateClassInfo.clazz,
                                                              gInteractionStateClassInfo
                                                                      .constructor));
        env->SetIntField(interactionObj.get(), gInteractionStateClassInfo.interactionTypes,
                         interactionState.interactionTypes);
        env->SetLongField(interactionObj.get(), gInteractionStateClassInfo.interactionTimeMillis,
                          interactionState.interactionTimeMillis);

        env->CallBooleanMethod(jInteractions, gArrayListClassInfo.add, interactionObj.get());
    }
    return jInteractions;
}

static void requestWakeupCallback(JNIEnv* env, jobject thiz, jlong nativePtr, jobject jCallback) {
    LOG_ALWAYS_FATAL_IF(jCallback == nullptr, "requestWakeupCallback: callback must not be null");

    attention::InteractionProvider* interactionProvider =
            reinterpret_cast<attention::InteractionProvider*>(nativePtr);

    ScopedGlobalRef globalCallback = makeScopedGlobalRef(env, jCallback);
    interactionProvider->requestWakeupCallback([globalCallback]() {
        // New env is required for the lambda.
        AndroidRuntime::getJNIEnv()
                ->CallVoidMethod(globalCallback.get(),
                                 gInteractionCallbackClassInfo.onInteractionsAvailable);
    });
}

static const JNINativeMethod gNativeInteractionProviderMethods[] = {
        /* name, signature, funcPtr */
        {"getSourceInteractions", "(J)Ljava/util/List;", (void*)getSourceInteractions},
        {"requestWakeupCallback", "(JLcom/android/server/attention/InteractionWakeupCallback;)V",
         (void*)requestWakeupCallback},
};

static void registerInteractionProviderMethods(JNIEnv* env) {
    int res = jniRegisterNativeMethods(env,
                                       "com/android/server/attention/"
                                       "NativeInteractionProvider",
                                       gNativeInteractionProviderMethods,
                                       NELEM(gNativeInteractionProviderMethods));
    (void)res; // Faked use when LOG_NDEBUG.
}

int register_com_android_server_attention_InteractionProviderServiceInternal(JNIEnv* env) {
    if (!input_flags::enable_attention_service_apis()) {
        return 0;
    }
    registerInteractionProviderMethods(env);

    // InteractionProviderServiceInternal
    jclass clazz =
            FindClassOrDie(env, "com/android/server/attention/InteractionProviderServiceInternal");
    gServiceClassInfo.clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));

    gServiceClassInfo.registerInteractionProvider =
            GetMethodIDOrDie(env, clazz, "registerInteractionProvider",
                             "(Lcom/android/server/attention/InteractionProvider;)Z");

    gServiceClassInfo.unregisterInteractionProvider =
            GetMethodIDOrDie(env, clazz, "unregisterInteractionProvider",
                             "(Lcom/android/server/attention/InteractionProvider;)Z");

    // InteractionWakeupCallback
    clazz = FindClassOrDie(env, "com/android/server/attention/InteractionWakeupCallback");
    gInteractionCallbackClassInfo.clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    gInteractionCallbackClassInfo.onInteractionsAvailable =
            GetMethodIDOrDie(env, clazz, "onInteractionsAvailable", "()V");

    // NativeInteractionProvider
    clazz = FindClassOrDie(env, "com/android/server/attention/NativeInteractionProvider");
    gNativeInteractionProviderClassInfo.clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    gNativeInteractionProviderClassInfo.constructor =
            GetMethodIDOrDie(env, clazz, "<init>", "(J)V");

    // InteractionState
    clazz = FindClassOrDie(env, "android/attention/InteractionState");
    gInteractionStateClassInfo.clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    gInteractionStateClassInfo.constructor = GetMethodIDOrDie(env, clazz, "<init>", "()V");
    gInteractionStateClassInfo.interactionTypes =
            GetFieldIDOrDie(env, clazz, "interactionTypes", "I");
    gInteractionStateClassInfo.interactionTimeMillis =
            GetFieldIDOrDie(env, clazz, "interactionTimeMillis", "J");

    // ArrayList
    clazz = FindClassOrDie(env, "java/util/ArrayList");
    gArrayListClassInfo.clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
    gArrayListClassInfo.constructor =
            GetMethodIDOrDie(env, gArrayListClassInfo.clazz, "<init>", "()V");
    gArrayListClassInfo.add =
            GetMethodIDOrDie(env, gArrayListClassInfo.clazz, "add", "(Ljava/lang/Object;)Z");

    return 0;
}

} // namespace android