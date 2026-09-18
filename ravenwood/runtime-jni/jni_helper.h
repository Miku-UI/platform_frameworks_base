/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <jni.h>
#include <log/log.h>
#include <nativehelper/JNIHelp.h>

constexpr const char* kRunnerState = "android/platform/test/ravenwood/RavenwoodRunnerState";
constexpr const char* kRuntimeNative = "com/android/ravenwood/RavenwoodRuntimeNative";
constexpr const char* kNativeLoader = "android/platform/test/ravenwood/RavenwoodNativeLoader";

static inline JNIEnv* GetJNIEnvOrDie(JavaVM* vm) {
    JNIEnv* env = nullptr;
    vm->GetEnv((void**)&env, JNI_VERSION_1_4);
    LOG_ALWAYS_FATAL_IF(env == nullptr, "Could not retrieve JNIEnv.");
    return env;
}

static inline jclass FindClassOrDie(JNIEnv* env, const char* class_name) {
    jclass clazz = env->FindClass(class_name);
    LOG_ALWAYS_FATAL_IF(clazz == NULL, "Unable to find class %s", class_name);
    return clazz;
}

template <typename T>
static inline T MakeGlobalRefOrDie(JNIEnv* env, T in) {
    jobject res = env->NewGlobalRef(in);
    LOG_ALWAYS_FATAL_IF(res == NULL, "Unable to create global reference.");
    return static_cast<T>(res);
}

static inline jclass FindGlobalClassOrDie(JNIEnv* env, const char* class_name) {
    return MakeGlobalRefOrDie(env, FindClassOrDie(env, class_name));
}

static inline jmethodID GetStaticMethodIDOrDie(JNIEnv* env, jclass clazz, const char* method_name,
                                               const char* method_signature) {
    jmethodID res = env->GetStaticMethodID(clazz, method_name, method_signature);
    LOG_ALWAYS_FATAL_IF(res == NULL, "Unable to find static method %s with signature %s",
                        method_name, method_signature);
    return res;
}
