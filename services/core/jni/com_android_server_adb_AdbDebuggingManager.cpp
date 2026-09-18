/*
 * Copyright (C) 2019 The Android Open Source Project
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

#define LOG_TAG "AdbDebuggingManager-JNI"

#define LOG_NDEBUG 0

#include <android-base/properties.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/utils.h>
#include <utils/Log.h>

#include "adb/IPairingServer.h"

namespace android {

using namespace adb::pairing;

// ----------------------------------------------------------------------------
namespace {

struct PairingResultWaiter {
    std::mutex mutex_;
    std::condition_variable cv_;
    std::optional<bool> is_valid_;
    PeerInfo peer_info_;

    static void ResultCallback(const PeerInfo* peer_info, void* opaque) {
        auto* p = reinterpret_cast<PairingResultWaiter*>(opaque);
        {
            std::unique_lock<std::mutex> lock(p->mutex_);
            if (peer_info) {
                memcpy(&(p->peer_info_), peer_info, sizeof(PeerInfo));
            }
            p->is_valid_ = (peer_info != nullptr);
        }
        p->cv_.notify_one();
    }
};

struct PairingSession {
    std::unique_ptr<IPairingServer> server;
    std::unique_ptr<PairingResultWaiter> waiter;

    ~PairingSession() {
        // Since PairingServer holds a reference to PairingResultWaiter, the order of destruction
        // matters.
        // TODO(b/474373608): Move the waiter into IPairingServer so we don't have to worry
        // about order.
        server.reset();
        waiter.reset();
    }
};

} // namespace

using android::PairingSession;

// Returns an opaque handle on success. Returns 0 on failure. Clean up with `native_pairing_destroy`
// once pairing is finished.
static jlong native_pairing_start(JNIEnv* env, jobject thiz, jstring javaGuid,
                                  jstring javaPassword) {
    auto session = std::make_unique<PairingSession>();

    // Server-side only sends its GUID on success.
    PeerInfo system_info = {.type = ADB_DEVICE_GUID};

    ScopedUtfChars guid = GET_UTF_OR_RETURN(env, javaGuid);
    memcpy(system_info.data, guid.c_str(), guid.size());

    ScopedUtfChars password = GET_UTF_OR_RETURN(env, javaPassword);

    // Create the pairing server
    session->server = std::unique_ptr<IPairingServer>(
            IPairingServer::CreateNoCert(reinterpret_cast<const uint8_t*>(password.c_str()),
                                         password.size(), &system_info));

    session->waiter = std::make_unique<PairingResultWaiter>();
    uint16_t port = session->server->Start(session->waiter->ResultCallback, session->waiter.get());
    if (port == 0) {
        ALOGE("Failed to start pairing server");
        return -1;
    }

    // Java layer now owns the PairingSession and is responsible for cleanup via
    // `pairing_server_destroy`.
    return reinterpret_cast<jlong>(session.release());
}

// Cancels the pairing session. If another thread is blocked on `native_pairing_wait`, calling this
// function will unblock it. Subsequent calls to `native_pairing_cancel` is a no-op.
static void native_pairing_cancel(JNIEnv* /* env */, jclass /* clazz */, jlong sessionHandle) {
    auto* s = reinterpret_cast<PairingSession*>(sessionHandle);
    s->server->StopListening();
}

// Blocks until pairing completes. `native_pairing_cancel` can be called on a separate thread
// to unblock this call. On success, returns a string containing the public key on the paired
// device, null on failure.
static jstring native_pairing_wait(JNIEnv* env, jobject thiz, jlong sessionHandle) {
    auto* s = reinterpret_cast<PairingSession*>(sessionHandle);
    ALOGI("Waiting for pairing server to complete");
    // Wait until pairing server triggers the PairingResultWaiter callback indicating a result has
    // been obtained.
    std::unique_lock<std::mutex> lock(s->waiter->mutex_);
    if (!s->waiter->is_valid_.has_value()) {
        s->waiter->cv_.wait(lock, [s]() { return s->waiter->is_valid_.has_value(); });
    }

    // Pairing failed or was cancelled.
    if (!*(s->waiter->is_valid_)) {
        return nullptr;
    }

    // Pairing succeed, and pairing server gave us the public key of the paired device.
    char* peer_public_key = reinterpret_cast<char*>(s->waiter->peer_info_.data);
    return env->NewStringUTF(peer_public_key);
}

// Returns the port number opened for pairing.
static jint native_pairing_get_port(JNIEnv* /* env */, jclass /* clazz */, jlong sessionHandle) {
    auto* s = reinterpret_cast<PairingSession*>(sessionHandle);
    return s->server->GetPort();
}

// Destroys the pairing session handle `sessionHandle`. Subsequent usage of `sessionHandle` is
// undefined.
static void native_pairing_destroy(JNIEnv* /* env */, jclass /* clazz */, jlong sessionHandle) {
    auto* s = reinterpret_cast<PairingSession*>(sessionHandle);
    delete s;
}
// ----------------------------------------------------------------------------

static const JNINativeMethod gPairingThreadMethods[] = {
        /* name, signature, funcPtr */
        {"native_pairing_start", "(Ljava/lang/String;Ljava/lang/String;)J",
         (void*)native_pairing_start},
        {"native_pairing_cancel", "(J)V", (void*)native_pairing_cancel},
        {"native_pairing_wait", "(J)Ljava/lang/String;", (void*)native_pairing_wait},
        {"native_pairing_get_port", "(J)I", (void*)native_pairing_get_port},
        {"native_pairing_destroy", "(J)V", (void*)native_pairing_destroy},
};

int register_android_server_AdbDebuggingManager(JNIEnv* env) {
    return jniRegisterNativeMethods(env, "com/android/server/adb/AdbPairingThread",
                                    gPairingThreadMethods, NELEM(gPairingThreadMethods));
}

} /* namespace android */
