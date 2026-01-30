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

#define LOG_TAG "ZygoteProcess"
#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <binder/IInterface.h>
#include <cutils/sockets.h>
#include <flatbuffers/flatbuffers.h>
#include <libzygote_schemas/messages.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include <cstring>

#include "android_util_Binder.h"
#include "com_android_internal_os_Zygote.h"
#include "core_jni_helpers.h"

#define ZYGOTE_NEXT_SOCKET_NAME "zygote_next"

#define RESPONSE_DATA_BUF_SIZE 1024

namespace {

android::base::unique_fd get_zygote_socket_fd() {
    android::base::unique_fd fd(socket(AF_UNIX, SOCK_SEQPACKET, 0));
    if (fd.ok()) {
        if (socket_local_client_connect(fd.get(), ZYGOTE_NEXT_SOCKET_NAME,
                                        ANDROID_SOCKET_NAMESPACE_RESERVED, SOCK_SEQPACKET) == -1) {
            PLOG(ERROR) << "Failed to connect to the zygote socket";
            fd.reset();
        }
    } else {
        PLOG(ERROR) << "Failed to create a socket";
    }
    return fd;
}

static std::optional<ScopedUtfChars> extract_jstring(JNIEnv* env, jstring managed_string) {
    if (managed_string == nullptr) {
        return std::nullopt;
    }
    return ScopedUtfChars(env, managed_string);
}

} // namespace

namespace android {

static jint android_os_ZygoteProcess_startNativeProcess(JNIEnv* env, jclass /* classObj */,
                                                        jint uid, jint gid, jlong startSeq,
                                                        jstring packageName, jstring niceName,
                                                        jint targetSdkVersion,
                                                        jboolean startChildZygote,
                                                        jint runtimeFlags, jstring seInfo) {
    static base::unique_fd fd;
    if (!fd.ok()) {
        fd = get_zygote_socket_fd();
        if (!fd.ok()) {
            PLOG(ERROR) << "Failed to get the zygote socket fd!";
            return -1;
        }
    }

    auto packageNameStr = extract_jstring(env, packageName);
    auto niceNameStr = extract_jstring(env, niceName);
    auto seInfoStr = extract_jstring(env, seInfo);
    const char* packageNamePtr = packageNameStr ? packageNameStr.value().c_str() : nullptr;
    const char* niceNamePtr = niceNameStr ? niceNameStr.value().c_str() : nullptr;
    const char* seInfoPtr = seInfoStr ? seInfoStr.value().c_str() : nullptr;

    flatbuffers::FlatBufferBuilder builder;
    auto spawnAndroidNativeCmd =
            CreateSpawnAndroidNativeDirect(builder, packageNamePtr, seInfoPtr, startSeq,
                                           static_cast<unsigned>(runtimeFlags));
    int32_t priority_initial = -20;
    int32_t priority_final = 0;
    jintArray gids = nullptr;
    bool is_child_zygote = startChildZygote == JNI_TRUE;
    jlong capabilities = zygote::CalculateCapabilities(env, uid, gid, gids, is_child_zygote);
    jlong bounding_capabilities = zygote::CalculateBoundingCapabilities(env, uid, gid, gids);
    uint64_t cap_effective = capabilities;
    uint64_t cap_permitted = capabilities;
    uint64_t cap_bound = bounding_capabilities;
    uint64_t cap_inheritable = cap_permitted | cap_bound;
    std::vector<uint32_t> secondary_groups;
    std::vector<RLimitData> rlimits;
    auto spawnCmd =
            CreateSpawnDirect(builder, uid, gid, niceNamePtr, priority_initial, priority_final,
                              cap_effective, cap_permitted, cap_inheritable, cap_bound,
                              &secondary_groups, &rlimits, SpawnPayload_SpawnAndroidNative,
                              spawnAndroidNativeCmd.Union());
    auto parcel = CreateParcel(builder, Message_Spawn, spawnCmd.Union());
    builder.Finish(parcel);
    uint8_t* buf = builder.GetBufferPointer();
    ssize_t size = builder.GetSize();

    ssize_t written = write(fd.get(), buf, size);
    if (written == -1 || written != size) {
        PLOG(ERROR) << "Failed to write to the socket written=" << written;
        fd.reset();
        return -1;
    }

    uint8_t response_buf[RESPONSE_DATA_BUF_SIZE];
    memset(response_buf, 0, sizeof(response_buf));
    int received = read(fd.get(), response_buf, sizeof(response_buf));
    if (received == -1) {
        PLOG(ERROR) << __func__ << ": Failed to receive the response";
        fd.reset();
        return -1;
    }
    flatbuffers::Verifier verifier(response_buf, received);
    if (!VerifyParcelBuffer(verifier)) {
        LOG(ERROR) << "Failed to verify the response";
        return -1;
    }
    const auto* response = GetParcel(response_buf);
    switch (response->message_type()) {
        case Message_SpawnResponse: {
            const SpawnResponse* spawn_res = response->message_as_SpawnResponse();
            return spawn_res->pid();
        }
        default:
            LOG(ERROR) << "Received an unexpected type response";
            return -1;
    }
}

// ----------------------------------------------------------------------------

static const JNINativeMethod method_table[] = {
        /* name, signature, funcPtr */
        {"nativeStartNativeProcess",
         "(IIJLjava/lang/String;Ljava/lang/String;IZILjava/lang/String;)I",
         (void*)android_os_ZygoteProcess_startNativeProcess},
};

int register_android_os_ZygoteProcess(JNIEnv* env) {
    return RegisterMethodsOrDie(env, "android/os/ZygoteProcess", method_table, NELEM(method_table));
}

}; // namespace android
