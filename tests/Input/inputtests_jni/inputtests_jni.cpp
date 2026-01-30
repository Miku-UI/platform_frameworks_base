/*
 * Copyright 2025 The Android Open Source Project
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

#define LOG_TAG "NativeInputTests"

#include <aidl/com/android/test/input/BnInputTests.h>
#include <android-base/logging.h>
#include <android/binder_ibinder_jni.h>

#include "FrameInfo.h"
#include "FrameMetricsObserver.h"

namespace android {
namespace {

size_t toIndex(android::uirenderer::FrameInfoIndex index) {
    return static_cast<size_t>(index);
}
} // namespace

class InputTestsService : public aidl::com::android::test::input::BnInputTests {
public:
    InputTestsService() {}

private:
    ::ndk::ScopedAStatus reportTimeline(int64_t observerPtr, int32_t inputEventId,
                                        int64_t gpuCompletedTime, int64_t presentTime) override {
        uirenderer::FrameMetricsObserver *rawObserver =
                reinterpret_cast<uirenderer::FrameMetricsObserver *>(observerPtr);
        sp<uirenderer::FrameMetricsObserver> observer =
                sp<uirenderer::FrameMetricsObserver>::fromExisting(rawObserver);
        uirenderer::FrameInfoBuffer frameData;
        frameData[toIndex(uirenderer::FrameInfoIndex::InputEventId)] = inputEventId;
        frameData[toIndex(uirenderer::FrameInfoIndex::GpuCompleted)] = gpuCompletedTime;
        frameData[toIndex(uirenderer::FrameInfoIndex::DisplayPresentTime)] = presentTime;
        observer->notify(frameData);
        return ndk::ScopedAStatus::ok();
    }
};

static jobject createNativeService(JNIEnv *env, jclass) {
    std::shared_ptr<InputTestsService> service = ndk::SharedRefBase::make<InputTestsService>();
    // The call `AIBinder_toJavaBinder` increments the refcount, so this will
    // prevent "service" from getting destructed. The ownership will now be
    // transferred to Java.
    return AIBinder_toJavaBinder(env, service->asBinder().get());
}
} // namespace android

extern "C" JNIEXPORT jobject JNICALL
Java_com_android_test_input_SpyInputEventReceiver_createNativeService(JNIEnv *env, jclass clazz) {
    return android::createNativeService(env, clazz);
}
