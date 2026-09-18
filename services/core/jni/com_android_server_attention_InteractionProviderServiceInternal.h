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

#include <android_runtime/AndroidRuntime.h>
#include <attention/InteractionProvider.h>
#include <attention/NativeInteractionManager.h>
#include <jni.h>

#include <memory>

namespace android {

class NativeInteractionProviderServiceInternal
      : public virtual attention::NativeInteractionManager {
public:
    NativeInteractionProviderServiceInternal(jobject serviceObj);
    ~NativeInteractionProviderServiceInternal();

    bool registerInteractionProvider(
            std::shared_ptr<attention::InteractionProvider> interactionProvider) override;
    bool unregisterInteractionProvider(
            std::shared_ptr<attention::InteractionProvider> interactionProvider) override;

private:
    static inline JNIEnv* jniEnv() {
        return AndroidRuntime::getJNIEnv();
    }
    jobject mServiceObj;

    std::unordered_map<std::shared_ptr<attention::InteractionProvider>,
                       /*IInteractionProvider*/ jobject>
            mRegisteredInteractionProviders;
};

} // namespace android
