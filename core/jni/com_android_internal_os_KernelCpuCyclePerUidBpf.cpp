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

#include <cpucycleperuid_rust_ffi.h>

#include <vector>

#include "core_jni_helpers.h"

namespace android {

#include <android/log.h>
#include <bpf_cycleperuid.h>

static jboolean KernelCpuCyclePerUidBpf_isSupported(JNIEnv*, jobject) {
    return rust_is_bpf_program_present() && rust_is_rapl_available() ? JNI_TRUE : JNI_FALSE;
}

static jboolean KernelCpuCyclePerUidBpf_startTrackingInternal(JNIEnv*, jobject) {
    return rust_start_tracking() ? JNI_TRUE : JNI_FALSE;
}

static void KernelCpuCyclePerUidBpf_stopTrackingInternal(JNIEnv*, jobject) {
    rust_stop_tracking();
}

static jlong KernelCpuCyclePerUidBpf_readPackagePower(JNIEnv*, jobject) {
    return rust_read_package_power();
}

static jlong KernelCpuCyclePerUidBpf_readLastRecordedCycle(JNIEnv*, jobject) {
    return rust_read_last_recorded_cycle();
}

static jlong KernelCpuCyclePerUidBpf_readDesyncCount(JNIEnv*, jobject) {
    return rust_read_desync_count();
}

#define FFI_ARRAY_SIZE (MAX_TRACKED_UIDS * 2)

static jlongArray KernelCpuCyclePerUidBpf_readUidCpuCycles(JNIEnv* env, jobject) {
    std::vector<jlong> buffer(FFI_ARRAY_SIZE);
    size_t elements_written =
            rust_read_uid_cpu_cycles(reinterpret_cast<uint64_t*>(buffer.data()), buffer.size());

    if (elements_written == 0) {
        return env->NewLongArray(0);
    }

    if (elements_written > FFI_ARRAY_SIZE) {
        ALOGE("rust_read_uid_cpu_cycles_safe returned too many elements: %zu", elements_written);
        return env->NewLongArray(0);
    }

    jlongArray result = env->NewLongArray(elements_written);
    if (result == nullptr) {
        ALOGE("Failed to allocate jlongArray of size %zu", elements_written);
        return nullptr;
    }
    env->SetLongArrayRegion(result, 0, elements_written, buffer.data());
    return result;
}

static jlongArray KernelCpuCyclePerUidBpf_readUidPowerDelta(JNIEnv* env, jobject) {
    std::vector<jlong> buffer(FFI_ARRAY_SIZE);
    size_t elements_written =
            rust_read_uid_power_delta(reinterpret_cast<uint64_t*>(buffer.data()), buffer.size());

    if (elements_written == 0) {
        return env->NewLongArray(0);
    }

    if (elements_written > FFI_ARRAY_SIZE) {
        ALOGE("rust_read_uid_power_delta_safe returned too many elements: %zu", elements_written);
        return env->NewLongArray(0);
    }

    jlongArray result = env->NewLongArray(elements_written);
    if (result == nullptr) {
        ALOGE("Failed to allocate jlongArray of size %zu", elements_written);
        return nullptr;
    }
    env->SetLongArrayRegion(result, 0, elements_written, buffer.data());
    return result;
}

static const JNINativeMethod methods[] = {
        {"isSupportedInternal", "()Z", (void*)KernelCpuCyclePerUidBpf_isSupported},
        {"startTrackingInternal", "()Z", (void*)KernelCpuCyclePerUidBpf_startTrackingInternal},
        {"stopTrackingInternal", "()V", (void*)KernelCpuCyclePerUidBpf_stopTrackingInternal},
        {"readPackagePowerInternal", "()J", (void*)KernelCpuCyclePerUidBpf_readPackagePower},
        {"readLastRecordedCycleInternal", "()J",
         (void*)KernelCpuCyclePerUidBpf_readLastRecordedCycle},
        {"readDesyncCountInternal", "()J", (void*)KernelCpuCyclePerUidBpf_readDesyncCount},
        {"readUidCpuCyclesInternal", "()[J", (void*)KernelCpuCyclePerUidBpf_readUidCpuCycles},
        {"readUidPowerDeltaInternal", "()[J", (void*)KernelCpuCyclePerUidBpf_readUidPowerDelta},
};

int register_com_android_internal_os_KernelCpuCyclePerUidBpf(JNIEnv* env) {
    return RegisterMethodsOrDie(env, "com/android/internal/os/KernelCpuCyclePerUidBpf", methods,
                                NELEM(methods));
}

} // namespace android
