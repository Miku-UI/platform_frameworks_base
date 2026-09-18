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
#include "pipeline/skia/SkiaIpcPipeline.h"

#include <android/ipcrenderbuffer/IPCRecordingCanvas.h>
#include <com_android_graphics_libgui_flags.h>
#include <gui/TraceUtils.h>
#include <include/core/SkImage.h>
#include <include/core/SkSurface.h>
#include <system/window.h>
#include <utils/Timers.h>

#include <cstddef>
#include <optional>

#include "DeviceInfo.h"
#include "LightingInfo.h"
#include "RenderNodeDrawable.h"
#include "hwui/OutOfProcessRendering.h"
#include "renderthread/Frame.h"
#include "utils/Color.h"

using namespace android::uirenderer::renderthread;
namespace android {
namespace uirenderer {
namespace skiapipeline {

SkiaIpcPipeline::SkiaIpcPipeline(renderthread::RenderThread& thread)
        : SkiaPipeline(thread), mOoprClient(OoprClient::getInstance()) {
    mIPCRecordingCanvas = std::make_shared<IPCRecordingCanvas>(mOoprClient->getIPCResourceCache());
    mApplyToken = sp<BBinder>::make();
    mOoprClient->enableOutOfProcessRendering();

    mCallbackHandler = std::make_shared<CallbackHandler>();
    mCallbackHandler->pipeline = this;

    std::lock_guard<std::mutex> lg(mLock);
    for (size_t i = 0; i < kFrameEventsSize; i++) {
        mFrameEvents[i].valid = false;
        mFrameEvents[i].frameNumber = 0;
    }
}

SkiaIpcPipeline::~SkiaIpcPipeline() {
    {
        std::lock_guard<std::mutex> lg(mCallbackHandler->lock);
        mCallbackHandler->pipeline = nullptr;
    }
    std::function<void(SurfaceComposerClient::Transaction*)> syncCallback;
    SurfaceComposerClient::Transaction* syncTransaction = nullptr;
    SurfaceComposerClient::Transaction t;
    bool hasPending = false;
    {
        std::lock_guard<std::mutex> lg(mLock);
        syncCallback = mTransactionReadyCallback;
        syncTransaction = mSyncTransaction;
        hasPending = mergePendingTransactions(&t, std::numeric_limits<uint64_t>::max());
    }
    if (syncTransaction) {
        syncTransaction->merge(std::move(t));
        syncCallback(syncTransaction);
    } else if (hasPending) {
        t.setApplyToken(mApplyToken).apply(false, true);
    }
}

void SkiaIpcPipeline::setSurfaceControl(const sp<SurfaceControl>& surfaceControl) {
    if (surfaceControl != nullptr && surfaceControl->isValid()) {
        SurfaceComposerClient::Transaction()
                .setRenderResourceToken(surfaceControl,
                                        mOoprClient->getDefaultRenderResourceToken())
                .setRenderCommandBuffer(surfaceControl,
                                        mIPCRecordingCanvas->getRenderCommandBufferProducer())
                .apply();
    }
    mSurfaceControl = surfaceControl;
}

void SkiaIpcPipeline::renderLayersImpl(const LayerUpdateQueue& layers, bool opaque) {
    // Render all layers that need to be updated, in order.
    ATRACE_CALL();
    for (size_t i = 0; i < layers.entries().size(); i++) {
        RenderNode* layerNode = layers.entries()[i].renderNode.get();
        // only schedule repaint if node still on layer - possible it may have been
        // removed during a dropped frame, but layers may still remain scheduled so
        // as not to lose info on what portion is damaged
        if (CC_UNLIKELY(layerNode->getLayerSurface() == nullptr)) {
            continue;
        }

        SkSurface* layerSurface = layerNode->getLayerSurface();

        IPCRecordingCanvas* layerCanvas = mIPCRecordingCanvas.get();

        layerCanvas->beginRenderTarget(layerNode->getOoprResources()->getBuffer()->getId());

        {
            SkAutoCanvasRestore saver(layerCanvas, true);
            AutoLightingInfoRestore restoreLightingInfo(layerNode);
            layerCanvas->clear(SK_ColorTRANSPARENT);
            RenderNodeDrawable root(layerNode, layerCanvas, false);
            root.forceDraw(layerCanvas);
        }

        layerCanvas->endRenderTarget();

        sk_sp<SkImage> layerImage = layerSurface->makeImageSnapshot();
        layerNode->getOoprResources()->registerSnapshot(layerImage);

        layerNode->getSkiaLayer()->hasRenderedSinceRepaint = false;
    }
}

// If the given node didn't have a layer surface, or had one of the wrong size, this method
// creates a new one and returns true. Otherwise does nothing and returns false.
bool SkiaIpcPipeline::createOrUpdateLayer(RenderNode* node,
                                          const DamageAccumulator& damageAccumulator,
                                          ErrorHandler* errorHandler) {
    if (node->getLayerSurface() && node->getLayerSurface()->width() == node->getWidth() &&
        node->getLayerSurface()->height() == node->getHeight()) {
        return false;
    }

    auto result = mOoprClient->createLayerSurface(node->getWidth(), node->getHeight(),
                                                  mRenderThread.getGrContext());
    if (!result.surface) {
        return false;
    }

    node->setLayerSurface(std::move(result.surface));
    node->getOoprResources() = std::move(result.resources);
    return true;
}

MakeCurrentResult SkiaIpcPipeline::makeCurrent() {
    return MakeCurrentResult::AlreadyCurrent;
}

Frame SkiaIpcPipeline::getFrame() {
    // Need to plumb "surface size" from ViewRoot
    return Frame(mWidth, mHeight, 0);
}

IRenderPipeline::DrawResult SkiaIpcPipeline::draw(
        const Frame& frame, const SkRect& screenDirty, const SkRect& dirty,
        const LightGeometry& lightGeometry, LayerUpdateQueue* layerUpdateQueue,
        const Rect& contentDrawBounds, bool opaque, const LightInfo& lightInfo,
        const std::vector<sp<RenderNode>>& renderNodes, FrameInfoVisualizer* profiler,
        const HardwareBufferRenderParams& bufferParams, std::mutex& profilerLock) {
    std::function<void(SurfaceComposerClient::Transaction*)> transactionReadyCallback;
    LightingInfo::updateLighting(lightGeometry, lightInfo);
    SkCanvas* canvas = mIPCRecordingCanvas.get();
    ATRACE_CALL();

    if (!mIPCRecordingCanvas->canRecord()) {
        IRenderPipeline::DrawResult result;
        result.success = false;
        return result;
    }

    // This should be the size plummed down from ViewRoot instead.
    mIPCRecordingCanvas->storeSize(mWidth, mHeight);
    // draw all layers up front
    mIPCRecordingCanvas->startRecording();

    renderLayersImpl(*layerUpdateQueue, opaque);
    layerUpdateQueue->clear();
    renderFrameImpl(dirty, renderNodes, opaque, contentDrawBounds, canvas, SkMatrix::I());

    // Send queued bitmaps registrations (e.g. created during this frame)
    mOoprClient->sendPendingBitmapRegistrations(mIPCRecordingCanvas->getRenderCommandBuffer());

    mIPCRecordingCanvas->endRecording();

    return {true, IRenderPipeline::DrawResult::kUnknownTime, android::base::unique_fd{}};
}

static std::optional<SurfaceControlStats> findMatchingStat(
        const std::vector<SurfaceControlStats>& stats, const sp<SurfaceControl>& sc) {
    for (auto stat : stats) {
        if (SurfaceControl::isSameSurface(sc, stat.surfaceControl)) {
            return stat;
        }
    }
    return std::nullopt;
}

FrameEvents* SkiaIpcPipeline::getFrameEvents(uint64_t frameNumber) {
    for (size_t i = 0; i < kFrameEventsSize; i++) {
        if (mFrameEvents[i].valid && mFrameEvents[i].frameNumber == frameNumber) {
            return &mFrameEvents[i];
        }
    }
    return nullptr;
}

void SkiaIpcPipeline::transactionCallback(nsecs_t /*latchTime*/, const sp<Fence>& /*presentFence*/,
                                          const std::vector<SurfaceControlStats>& stats) {
    std::lock_guard<std::mutex> lg(mLock);
    if (!mSurfaceControlsWithPendingCallback.empty()) {
        sp<SurfaceControl> pendingSC = mSurfaceControlsWithPendingCallback.front();
        mSurfaceControlsWithPendingCallback.pop();

        std::optional<SurfaceControlStats> statOptional = findMatchingStat(stats, pendingSC);
        if (statOptional) {
            auto& stat = *statOptional;
            if (stat.latchTime > 0) {
                std::shared_ptr<FenceTime> gpuCompositionDoneFenceTime =
                        std::make_shared<FenceTime>(stat.frameEventStats.gpuCompositionDoneFence);
                std::shared_ptr<FenceTime> presentFenceTime =
                        std::make_shared<FenceTime>(stat.presentFence);

                FrameEvents* events = getFrameEvents(stat.frameEventStats.frameNumber);
                if (events) {
                    events->latchTime = stat.latchTime;
                    events->firstRefreshStartTime = stat.frameEventStats.refreshStartTime;
                    events->gpuCompositionDoneFence = gpuCompositionDoneFenceTime;
                    events->displayPresentFence = presentFenceTime;
                }
            }
        }
    }
}

bool SkiaIpcPipeline::swapBuffers(const Frame& frame, IRenderPipeline::DrawResult& drawResult,
                                  const SkRect& screenDirty, FrameInfo* currentFrameInfo,
                                  bool* requireSwap) {
    currentFrameInfo->markSwapBuffers();
    *requireSwap = drawResult.success;
    if (!drawResult.success) {
        return false;
    }

    SurfaceComposerClient::Transaction pendingTransactions;
    std::function<void(SurfaceComposerClient::Transaction*)> syncCallback;
    SurfaceComposerClient::Transaction* syncTransaction = nullptr;

    auto callbackThunk = [handler = mCallbackHandler](
                                 void* /*context*/, nsecs_t latchTime,
                                 const sp<Fence>& presentFence,
                                 const std::vector<SurfaceControlStats>& stats) {
        handler->onTransactionCompleted(latchTime, presentFence, stats);
    };

    {
        std::lock_guard<std::mutex> lg(mLock);
        mergePendingTransactions(&pendingTransactions, getFrameNumber());
        syncCallback = mTransactionReadyCallback;
        syncTransaction = mSyncTransaction;

        mSyncTransaction = nullptr;
        mTransactionReadyCallback = nullptr;

        mFrameEventIndex = (mFrameEventIndex + 1) % kFrameEventsSize;
        FrameEvents* events = &mFrameEvents[mFrameEventIndex];
        events->frameNumber = getFrameNumber();
        events->postedTime = systemTime();
        events->requestedPresentTime = 0;
        events->valid = true;

        events->latchTime = FrameEvents::TIMESTAMP_PENDING;
        events->firstRefreshStartTime = FrameEvents::TIMESTAMP_PENDING;
        events->lastRefreshStartTime = FrameEvents::TIMESTAMP_PENDING;
        events->dequeueReadyTime = FrameEvents::TIMESTAMP_PENDING;
        events->acquireFence = FenceTime::NO_FENCE;
        events->gpuCompositionDoneFence = FenceTime::NO_FENCE;
        events->displayPresentFence = FenceTime::NO_FENCE;
        events->releaseFence = FenceTime::NO_FENCE;

        mSurfaceControlsWithPendingCallback.push(mSurfaceControl);
    }

    if (syncTransaction != nullptr) {
        syncTransaction->setRenderCommandBufferFrameId(mSurfaceControl, getFrameNumber());
        syncTransaction->addTransactionCompletedCallback(callbackThunk, nullptr);
        syncTransaction->merge(std::move(pendingTransactions));
        syncCallback(syncTransaction);
    } else {
        SurfaceComposerClient::Transaction transaction;
        transaction.setRenderCommandBufferFrameId(mSurfaceControl, getFrameNumber());
        transaction.addTransactionCompletedCallback(callbackThunk, nullptr);
        transaction.merge(std::move(pendingTransactions));
        transaction.setApplyToken(mApplyToken);
        transaction.apply(false, true);
    }
    return true;
}

bool SkiaIpcPipeline::setSurface(ANativeWindow* surface, SwapBehavior swapBehavior) {
    return true;
}

void SkiaIpcPipeline::mergeWithNextTransaction(SurfaceComposerClient::Transaction* t,
                                               uint64_t frameNumber) {
    std::lock_guard<std::mutex> lg(mLock);
    // BLASTBufferQueue uses mLastAcquiredFrameNumber...are we off by one here?
    if (getFrameNumber() >= frameNumber) {
        t->apply();
    } else {
        mPendingTransactions.emplace_back(frameNumber, *t);
        t->clear();
    }
}

void SkiaIpcPipeline::applyPendingTransactions(uint64_t frameNumber) {
    SurfaceComposerClient::Transaction t;
    std::lock_guard<std::mutex> lg(mLock);
    mergePendingTransactions(&t, frameNumber);
    t.setApplyToken(mApplyToken).apply(false, true);
}

void SkiaIpcPipeline::clearSyncTransaction() {
    std::lock_guard<std::mutex> lg(mLock);
    mTransactionReadyCallback = nullptr;
    if (mSyncTransaction) {
        delete mSyncTransaction;
        mSyncTransaction = nullptr;
    }
}

SurfaceComposerClient::Transaction* SkiaIpcPipeline::gatherPendingTransactions(
        uint64_t frameNumber) {
    auto t = new SurfaceComposerClient::Transaction();
    std::lock_guard<std::mutex> lg(mLock);
    mergePendingTransactions(t, frameNumber);
    return t;
}

void SkiaIpcPipeline::setCornerRadiiCallback(
        std::function<void(const gui::CornerRadii&)> cornerRadiiCallback) {
    // Stub implementation
}

void SkiaIpcPipeline::setWaitForBufferReleaseCallback(std::function<void(int64_t)> callback) {
    // Stub implementation
}

bool SkiaIpcPipeline::syncNextTransaction(
        std::function<void(SurfaceComposerClient::Transaction*)> callback,
        bool acquireSingleBuffer) {
    std::lock_guard<std::mutex> lg(mLock);
    // TODO(b/463722837) implement continuous sync
    (void)acquireSingleBuffer;

    if (!isSurfaceReady()) {
        return false;
    }

    mTransactionReadyCallback = callback;
    mSyncTransaction = new SurfaceComposerClient::Transaction();
    return true;
}

bool SkiaIpcPipeline::mergePendingTransactions(SurfaceComposerClient::Transaction* t,
                                               uint64_t frameNumber) {
    while (!mPendingFrameTimelines.empty()) {
        auto& [targetFrameNumber, ftlInfo] = mPendingFrameTimelines.front();
        if (targetFrameNumber > frameNumber) {
            break;
        }
        if (targetFrameNumber == frameNumber) {
            t->setFrameTimelineInfo(ftlInfo);
        }
        mPendingFrameTimelines.pop();
    }

    if (mPendingTransactions.empty()) {
        return false;
    }
    auto mergeTransaction =
            [&t, currentFrameNumber = frameNumber](
                    std::tuple<uint64_t, SurfaceComposerClient::Transaction> pendingTransaction) {
                auto& [targetFrameNumber, transaction] = pendingTransaction;
                if (currentFrameNumber < targetFrameNumber) {
                    return false;
                }
                t->merge(std::move(transaction));
                return true;
            };

    mPendingTransactions.erase(std::remove_if(mPendingTransactions.begin(),
                                              mPendingTransactions.end(), mergeTransaction),
                               mPendingTransactions.end());
    return true;
}

uint64_t SkiaIpcPipeline::getFrameNumber() {
    return mIPCRecordingCanvas->getRenderCommandBufferProducer()->getFrameNumber();
}

int SkiaIpcPipeline::getFrameTimestamps(uint64_t frameNumber, nsecs_t* outRequestedPresentTime,
                                        nsecs_t* outAcquireTime, nsecs_t* outLatchTime,
                                        nsecs_t* outFirstRefreshStartTime,
                                        nsecs_t* outLastRefreshStartTime,
                                        nsecs_t* outGpuCompositionDoneTime,
                                        nsecs_t* outDisplayPresentTime,
                                        nsecs_t* outDequeueReadyTime, nsecs_t* outReleaseTime) {
    std::lock_guard<std::mutex> lg(mLock);
    FrameEvents* events = getFrameEvents(frameNumber);
    if (!events) {
        return -1;
    }
    auto getTimestamp = [](const std::shared_ptr<FenceTime>& fence) {
        nsecs_t signalTime = fence->getSignalTime();
        if (signalTime == Fence::SIGNAL_TIME_PENDING) {
            return FrameEvents::TIMESTAMP_PENDING;
        }
        return signalTime;
    };
    if (outRequestedPresentTime) *outRequestedPresentTime = events->requestedPresentTime;
    if (outAcquireTime) *outAcquireTime = getTimestamp(events->acquireFence);
    if (outLatchTime) *outLatchTime = events->latchTime;
    if (outFirstRefreshStartTime) *outFirstRefreshStartTime = events->firstRefreshStartTime;
    if (outLastRefreshStartTime) *outLastRefreshStartTime = events->lastRefreshStartTime;
    if (outGpuCompositionDoneTime)
        *outGpuCompositionDoneTime = getTimestamp(events->gpuCompositionDoneFence);
    if (outDisplayPresentTime) {
        *outDisplayPresentTime = getTimestamp(events->displayPresentFence);
        if (com::android::graphics::libgui::flags::debug_gpu_present_times()) {
            ATRACE_FORMAT_INSTANT(
                    "450351988: SkiaIpcPipeline::getFrameTimestamps: frameNumber=%" PRId64
                    " presentTime=%" PRId64 " acquireFence=%" PRId64,
                    frameNumber, *outDisplayPresentTime,
                    outAcquireTime ? *outAcquireTime : getTimestamp(events->acquireFence));
        }
    }
    if (outDequeueReadyTime) *outDequeueReadyTime = events->dequeueReadyTime;
    if (outReleaseTime) *outReleaseTime = getTimestamp(events->releaseFence);
    return 0;
}

void SkiaIpcPipeline::updateRenderTargetSize(uint64_t width, uint64_t height) {
    mWidth = width;
    mHeight = height;
}

void SkiaIpcPipeline::setFrameTimelineInfo(const struct ANativeWindowFrameTimelineInfo& info) {
    FrameTimelineInfo ftlInfo;
    ftlInfo.vsyncId = info.frameTimelineVsyncId;
    ftlInfo.inputEventId = info.inputEventId;
    ftlInfo.startTimeNanos = info.startTimeNanos;
    ftlInfo.useForRefreshRateSelection = info.useForRefreshRateSelection;
    ftlInfo.skippedFrameVsyncId = info.skippedFrameVsyncId;
    ftlInfo.skippedFrameStartTimeNanos = info.skippedFrameStartTimeNanos;
    ftlInfo.vsyncResyncedJitterNanos = info.vsyncResyncedJitterNanos;
    ftlInfo.dequeueBufferDurationNanos = info.dequeueBufferDurationNanos;
    ftlInfo.animationTime = info.animationTime;
    mPendingFrameTimelines.emplace(info.frameNumber, ftlInfo);
}

// TODO(b/485971052): Finish this.
int64_t SkiaIpcPipeline::getLastDequeueDuration() {
    return 0;
}

bool SkiaIpcPipeline::hasRenderTarget() {
    return mSurfaceControl != nullptr;
}

} /* namespace skiapipeline */
} /* namespace uirenderer */
} /* namespace android */
