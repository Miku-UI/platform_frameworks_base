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

#define LOG_TAG "SimpleNativeService"

#include "SimpleNativeService.h"

#include <android/binder_ibinder.h>
#include <log/log.h>

ndk::ScopedAStatus SimpleNativeService::getPid(int32_t* pid) {
    *pid = getpid();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus SimpleNativeService::getUid(int32_t* uid) {
    *uid = getuid();
    return ndk::ScopedAStatus::ok();
}

// This instance doesn't need to be guarded by locks because it's only accessed by ANativeService
// callbacks, which are executed on the main thread.
std::shared_ptr<SimpleNativeService> gService;

extern "C" AIBinder* onBind(ANativeService* _Nonnull /* service */, uint64_t /* bindToken */,
                            char const* _Nullable /* action */, char const* _Nullable /* data */) {
    ndk::SpAIBinder service = gService->asBinder();
    AIBinder_incStrong(service.get());
    return service.get();
}

extern "C" void onDestroy(ANativeService* _Nonnull /* service */) {
    gService = nullptr;
}

extern "C" void ANativeService_onCreate(ANativeService* service) {
    gService = ndk::SharedRefBase::make<SimpleNativeService>();

    ANativeService_setOnBindCallback(service, onBind);
    ANativeService_setOnDestroyCallback(service, onDestroy);
}
