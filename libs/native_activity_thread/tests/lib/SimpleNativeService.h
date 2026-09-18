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

#pragma once

#include <aidl/android/nativeservice/simple/BnSimpleNativeService.h>
#include <android/binder_auto_utils.h>
#include <android/binder_status.h>
#include <android/native_service.h>

// An implementation of ISimpleNativeService
class SimpleNativeService : public aidl::android::nativeservice::simple::BnSimpleNativeService {
public:
    SimpleNativeService() = default;
    virtual ~SimpleNativeService() = default;

    ndk::ScopedAStatus getPid(int32_t* pid) override;
    ndk::ScopedAStatus getUid(int32_t* uid) override;
};
