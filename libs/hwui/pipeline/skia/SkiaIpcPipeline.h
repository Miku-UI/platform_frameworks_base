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

#pragma once

#include <android-base/thread_annotations.h>
#include <android/gui/FrameTimelineInfo.h>
#include <android/hardware_buffer.h>
#include <android/ipcrenderbuffer/IPCRecordingCanvas.h>
#include <android/ipcrenderbuffer/RenderBufferOps.h>
#include <gui/FrameTimestamps.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/SurfaceControl.h>

#include <memory>
#include <mutex>
#include <queue>
#include <tuple>
#include <unordered_map>
#include <vector>

#include "include/gpu/ganesh/SkSurfaceGanesh.h"
#include "pipeline/skia/SkiaPipeline.h"

namespace android {

namespace uirenderer {
class OoprClient;

namespace skiapipeline {

class SkiaIpcPipeline : public SkiaPipeline {
public:
    SkiaIpcPipeline(renderthread::RenderThread& thread);
    ~SkiaIpcPipeline();

    bool pinImages(std::vector<SkImage*>& mutableImages) override { return false; }
    bool pinImages(LsaVector<sk_sp<Bitmap>>& images) override { return false; }
    void unpinImages() override {}

    // If the given node didn't have a layer surface, or had one of the wrong size, this method
    // creates a new one and returns true. Otherwise does nothing and returns false.
    bool createOrUpdateLayer(RenderNode* node, const DamageAccumulator& damageAccumulator,
                             ErrorHandler* errorHandler) override;
    void renderLayersImpl(const LayerUpdateQueue& layers, bool opaque) override;
    void setHardwareBuffer(AHardwareBuffer* hardwareBuffer) override {}
    bool hasHardwareBuffer() override { return false; }

    renderthread::MakeCurrentResult makeCurrent() override;
    renderthread::Frame getFrame() override;
    renderthread::IRenderPipeline::DrawResult draw(
            const renderthread::Frame& frame, const SkRect& screenDirty, const SkRect& dirty,
            const LightGeometry& lightGeometry, LayerUpdateQueue* layerUpdateQueue,
            const Rect& contentDrawBounds, bool opaque, const LightInfo& lightInfo,
            const std::vector<sp<RenderNode>>& renderNodes, FrameInfoVisualizer* profiler,
            const renderthread::HardwareBufferRenderParams& bufferParams,
            std::mutex& profilerLock) override;
    bool swapBuffers(const renderthread::Frame& frame, IRenderPipeline::DrawResult& drawResult,
                     const SkRect& screenDirty, FrameInfo* currentFrameInfo,
                     bool* requireSwap) override;
    DeferredLayerUpdater* createTextureLayer() override { return nullptr; }
    bool setSurface(ANativeWindow* surface, renderthread::SwapBehavior swapBehavior) override;

    void setSurfaceControl(const sp<SurfaceControl>& surfaceControl) override;

    [[nodiscard]] android::base::unique_fd flush() override {
        return android::base::unique_fd(-1);
    };
    void onStop() override {}
    bool isSurfaceReady() override {
        return mSurfaceControl != nullptr && mSurfaceControl->isValid();
    }
    bool isContextReady() override { return true; }

    const SkM44& getPixelSnapMatrix() const override {
        static const SkM44 sSnapMatrix = SkM44();
        return sSnapMatrix;
    }

    void mergeWithNextTransaction(SurfaceComposerClient::Transaction* t,
                                  uint64_t frameNumber) override;
    void applyPendingTransactions(uint64_t frameNumber) override;
    void clearSyncTransaction() override;
    SurfaceComposerClient::Transaction* gatherPendingTransactions(uint64_t frameNumber) override;
    bool syncNextTransaction(std::function<void(SurfaceComposerClient::Transaction*)> callback,
                             bool acquireSingleBuffer) override;

    void setCornerRadiiCallback(
        std::function<void(const gui::CornerRadii&)> cornerRadiiCallback) override;
    void setWaitForBufferReleaseCallback(std::function<void(int64_t)> callback) override;

    ANativeWindow* getSurface() override { return nullptr; }
    uint64_t getFrameNumber() override;
    int getFrameTimestamps(uint64_t frameNumber, nsecs_t* outRequestedPresentTime,
                           nsecs_t* outAcquireTime, nsecs_t* outLatchTime,
                           nsecs_t* outFirstRefreshStartTime, nsecs_t* outLastRefreshStartTime,
                           nsecs_t* outGpuCompositionDoneTime, nsecs_t* outDisplayPresentTime,
                           nsecs_t* outDequeueReadyTime, nsecs_t* outReleaseTime) override;

    void updateRenderTargetSize(uint64_t width, uint64_t height) override;

    void setFrameTimelineInfo(const struct ANativeWindowFrameTimelineInfo& info) override;
    int64_t getLastDequeueDuration() override;

    bool hasRenderTarget() override;

private:
    sp<IBinder> mApplyToken;

    bool mergePendingTransactions(SurfaceComposerClient::Transaction* t,
                                  uint64_t frameNumber) REQUIRES(mLock);

    std::shared_ptr<IPCRecordingCanvas> mIPCRecordingCanvas;
    IPCClientResourceCache mResourceCache;
    sp<SurfaceControl> mSurfaceControl;
    std::vector<std::tuple<uint64_t /* framenumber */, SurfaceComposerClient::Transaction>>
            mPendingTransactions GUARDED_BY(mLock);
    std::queue<std::pair<uint64_t, FrameTimelineInfo>> mPendingFrameTimelines;
    SurfaceComposerClient::Transaction* mSyncTransaction GUARDED_BY(mLock) = nullptr;
    std::function<void(SurfaceComposerClient::Transaction*)> mTransactionReadyCallback
            GUARDED_BY(mLock);

    std::mutex mLock;

    uint64_t mWidth = 0;
    uint64_t mHeight = 0;

    OoprClient* mOoprClient;

    static constexpr size_t kFrameEventsSize = 10;
    FrameEvents mFrameEvents[kFrameEventsSize] GUARDED_BY(mLock);
    size_t mFrameEventIndex GUARDED_BY(mLock) = 0;
    FrameEvents* getFrameEvents(uint64_t frameNumber) REQUIRES(mLock);

    std::queue<sp<SurfaceControl>> mSurfaceControlsWithPendingCallback GUARDED_BY(mLock);

    void transactionCallback(nsecs_t latchTime, const sp<Fence>& presentFence,
                             const std::vector<SurfaceControlStats>& stats);

    struct CallbackHandler {
        std::mutex lock;
        SkiaIpcPipeline* pipeline;
        void onTransactionCompleted(nsecs_t latchTime, const sp<Fence>& presentFence,
                                    const std::vector<SurfaceControlStats>& stats) {
            std::lock_guard<std::mutex> lg(lock);
            if (pipeline) {
                pipeline->transactionCallback(latchTime, presentFence, stats);
            }
        }
    };
    std::shared_ptr<CallbackHandler> mCallbackHandler;
};

} /* namespace skiapipeline */
} /* namespace uirenderer */
} /* namespace android */
