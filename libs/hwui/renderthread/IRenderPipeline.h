/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <SkColorSpace.h>
#include <SkRect.h>
#include <android-base/unique_fd.h>
#ifdef __ANDROID__
#include <gui/BLASTBufferQueue.h>
#include <gui/SurfaceControl.h>
#endif
#include <utils/RefBase.h>


#include "ColorMode.h"
#include "DamageAccumulator.h"
#include "FrameInfoVisualizer.h"
#include "HardwareBufferRenderParams.h"
#include "LayerUpdateQueue.h"
#include "Lighting.h"
#include "SwapBehavior.h"
#include "hwui/Bitmap.h"

struct ANativeWindow;
struct ANativeWindowFrameTimelineInfo;

namespace android {

namespace uirenderer {

class DeferredLayerUpdater;
class ErrorHandler;
class TaskManager;

namespace renderthread {

enum class MakeCurrentResult { AlreadyCurrent, Failed, Succeeded };

class Frame;

class IRenderPipeline {
public:
    virtual MakeCurrentResult makeCurrent() = 0;
    virtual Frame getFrame() = 0;

    // Result of IRenderPipeline::draw
    struct DrawResult {
        // True if draw() succeeded, false otherwise
        bool success = false;
        // If drawing was successful, reports the time at which command
        // submission occurred. -1 if this time is unknown.
        static constexpr nsecs_t kUnknownTime = -1;
        nsecs_t commandSubmissionTime = kUnknownTime;
        android::base::unique_fd presentFence;
    };
    virtual DrawResult draw(const Frame& frame, const SkRect& screenDirty, const SkRect& dirty,
                            const LightGeometry& lightGeometry, LayerUpdateQueue* layerUpdateQueue,
                            const Rect& contentDrawBounds, bool opaque, const LightInfo& lightInfo,
                            const std::vector<sp<RenderNode>>& renderNodes,
                            FrameInfoVisualizer* profiler,
                            const HardwareBufferRenderParams& bufferParams,
                            std::mutex& profilerLock) = 0;
    virtual bool swapBuffers(const Frame& frame, IRenderPipeline::DrawResult&,
                             const SkRect& screenDirty, FrameInfo* currentFrameInfo,
                             bool* requireSwap) = 0;
    virtual DeferredLayerUpdater* createTextureLayer() = 0;
    [[nodiscard]] virtual android::base::unique_fd flush() = 0;
    virtual void setHardwareBuffer(AHardwareBuffer* hardwareBuffer) = 0;
    virtual bool hasHardwareBuffer() = 0;
    virtual bool setSurface(ANativeWindow* window, SwapBehavior swapBehavior) = 0;
#ifdef __ANDROID__
    virtual void setSurfaceControl(const sp<SurfaceControl>&) {}
    virtual void setBLASTBufferQueue(const sp<BLASTBufferQueue>&) {}
    virtual void setCornerRadiiCallback(std::function<void(const gui::CornerRadii&)>) {}
    virtual void setWaitForBufferReleaseCallback(std::function<void(int64_t)>) {}
    virtual bool syncNextTransaction(std::function<void(SurfaceComposerClient::Transaction*)>,
                                     bool) {
        return false;
    }
    virtual void mergeWithNextTransaction(SurfaceComposerClient::Transaction*, uint64_t) {}
    virtual void applyPendingTransactions(uint64_t) {}
    virtual void clearSyncTransaction() {}
    virtual SurfaceComposerClient::Transaction* gatherPendingTransactions(uint64_t) {
        return new SurfaceComposerClient::Transaction();
    }
#endif
    // Only used on SkiaIpcPipeline so we provide an empty default impl
    virtual void updateRenderTargetSize(uint64_t width, uint64_t height) {}

    virtual void onStop() = 0;
    virtual bool isSurfaceReady() = 0;
    virtual bool isContextReady() = 0;
    virtual void onDestroyHardwareResources() = 0;
    virtual void renderLayers(const LightGeometry& lightGeometry,
                              LayerUpdateQueue* layerUpdateQueue, bool opaque,
                              const LightInfo& lightInfo) = 0;
    virtual bool createOrUpdateLayer(RenderNode* node, const DamageAccumulator& damageAccumulator,
                                     ErrorHandler* errorHandler) = 0;
    virtual bool pinImages(std::vector<SkImage*>& mutableImages) = 0;
    virtual bool pinImages(LsaVector<sk_sp<Bitmap>>& images) = 0;
    virtual void unpinImages() = 0;

    virtual void setSurfaceColorProperties(ColorMode colorMode) = 0;
    virtual SkColorType getSurfaceColorType() const = 0;
    virtual sk_sp<SkColorSpace> getSurfaceColorSpace() = 0;
    virtual void setPictureCapturedCallback(
            const std::function<void(sk_sp<SkPicture>&&)>& callback) = 0;

    virtual void setTargetSdrHdrRatio(float ratio) = 0;
    virtual const SkM44& getPixelSnapMatrix() const = 0;

    virtual ANativeWindow* getSurface() = 0;
    virtual uint64_t getFrameNumber() = 0;
    virtual int getFrameTimestamps(uint64_t frameNumber, nsecs_t* outRequestedPresentTime,
                                   nsecs_t* outAcquireTime, nsecs_t* outLatchTime,
                                   nsecs_t* outFirstRefreshStartTime,
                                   nsecs_t* outLastRefreshStartTime,
                                   nsecs_t* outGpuCompositionDoneTime,
                                   nsecs_t* outDisplayPresentTime, nsecs_t* outDequeueReadyTime,
                                   nsecs_t* outReleaseTime) = 0;
    virtual void setFrameTimelineInfo(const ANativeWindowFrameTimelineInfo& info) = 0;
    virtual int64_t getLastDequeueDuration() = 0;

    virtual bool hasRenderTarget() = 0;

    virtual ~IRenderPipeline() {}
};

} /* namespace renderthread */
} /* namespace uirenderer */
} /* namespace android */
