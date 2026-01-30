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

#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <signal.h>
#include <utils/Log.h>

#include <bit>

namespace android {

namespace {

constexpr uint8_t SV_TAG_MASK = 0xFu;
constexpr uint8_t LONG_METHOD_TRACING_TYPE_ID = 0;

static inline sigval sv_encode_int(uint32_t data) {
    // The least significant 4 bits may be non-zero, but since `data` represents durations
    // in milliseconds in the long method tracing case (typically 2000ms+), a loss of up
    // to 15ms of precision is acceptable. we only emit a warning instead of an error to flag
    // this but allow the operation to continue.
    if ((data & SV_TAG_MASK) != 0) {
        ALOGW("Low 4 bits of int payload 0x%x might be overwritten by tag", data);
    }
    sigval value = {0};
    value.sival_int = static_cast<int>(data);

    std::uintptr_t raw = std::bit_cast<std::uintptr_t>(value) & ~SV_TAG_MASK;
    raw |= (static_cast<std::uintptr_t>(LONG_METHOD_TRACING_TYPE_ID) & SV_TAG_MASK);

    return std::bit_cast<sigval>(raw);
}

static jboolean android_server_utils_LongMethodTracer_trigger(JNIEnv*, jclass, jint pid,
                                                              jint duration_ms) {
    if (pid <= 0 || duration_ms <= 0) {
        ALOGW("Invalid arguments: pid=%d, durationMs=%d", pid, duration_ms);
        return JNI_FALSE;
    }

    sigval value = sv_encode_int(duration_ms);
    if (sigqueue(pid, SIGUSR1, value) != 0) {
        ALOGW("sigqueue failed for pid=%d with signal=%d", pid, SIGUSR1);
        return JNI_FALSE;
    }
    ALOGI("Long method tracing triggered for pid=%d for %d ms", pid, value.sival_int);
    return JNI_TRUE;
}

static const JNINativeMethod gMethods[] = {
        {"nativeTrigger", "(II)Z", (void*)android_server_utils_LongMethodTracer_trigger},
};

} // namespace

int register_android_server_utils_LongMethodTracer(JNIEnv* env) {
    return jniRegisterNativeMethods(env, "com/android/server/utils/LongMethodTracer", gMethods,
                                    NELEM(gMethods));
}

} // namespace android
