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

#include "OutOfProcessRendering.h"

#include <include/gpu/ganesh/GrDirectContext.h>
#include <include/gpu/ganesh/SkSurfaceGanesh.h>

#include "HardwareBitmapUploader.h"

#ifdef __ANDROID__
#include <android/ipcrenderbuffer/RenderBufferOps.h>
#include <com_android_graphics_libgui_flags.h>
#include <gui/GraphicBuffersRegisterInfo.h>
#include <gui/GraphicBuffersUnregisterInfo.h>
#include <gui/ISurfaceComposer.h>
#include <gui/LocklessQueue.h>
#include <gui/TraceUtils.h>
#include <include/android/GrAHardwareBufferUtils.h>
#include <inttypes.h>
#include <log/log.h>
#include <private/android/AHardwareBufferHelpers.h>
#include <private/gui/ComposerService.h>
#include <ui/GraphicBuffer.h>

#include <thread>
#include <unordered_map>

#include "../AutoBackendTextureRelease.h"
#endif

namespace android {
namespace uirenderer {

#ifdef __ANDROID__

sp<GraphicBuffer> allocGraphicBufferFromBitmap(const SkImageInfo& info) {
    uint32_t usage = GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_SW_WRITE_RARELY;

    // Map SkColorType to Android PixelFormat
    auto formatInfo = HardwareBitmapUploader::determineFormat(info);
    if (!formatInfo.isSupported || !formatInfo.valid) {
        ALOGE("Missing format: %d", info.colorType());
        // Fallback or skip if format is not supported by GraphicBuffer easily
        return nullptr;
    }
    PixelFormat format = static_cast<PixelFormat>(formatInfo.bufferFormat);

    sp<GraphicBuffer> buffer = sp<GraphicBuffer>::make(info.width(), info.height(), format, 1,
                                                       usage, "Bitmap::makeImage-Shadow");

    status_t err = buffer->initCheck();
    if (err != NO_ERROR) {
        ALOGE("Failed to allocate GraphicBuffer shadow: %d", err);
        return nullptr;
    }

    return buffer;
}

void copyBitmapToGraphicBuffer(sp<GraphicBuffer> dst, const SkBitmap& src) {
    ATRACE_CALL();

    void* data = nullptr;
    status_t err = dst->lock(GRALLOC_USAGE_SW_WRITE_RARELY, &data);
    if (err != NO_ERROR || !data) {
        ALOGE("Failed to lock GraphicBuffer shadow: %d", err);
        return;
    }

    size_t bufferRowBytes = dst->getStride() * src.info().bytesPerPixel();
    SkPixmap dstPixmap(src.info(), data, bufferRowBytes);
    bool result = src.readPixels(dstPixmap);

    dst->unlock();

    if (!result) {
        ALOGE("Failed to copy pixels to GraphicBuffer shadow");
        return;
    }
}

OoprNode::~OoprNode() {
    if (mLastImage) {
        OoprClient::getInstance()->deregisterBuffer(mLastImage);
    }
    if (mTextureRelease) {
        mTextureRelease->unref(false);
    }
}

OoprBitmap::~OoprBitmap() {
    if (mLastImage) {
        OoprClient::getInstance()->deregisterBuffer(mLastImage);
    }
}

OoprClient* OoprClient::getInstance() {
    static OoprClient sInstance;
    return &sInstance;
}

OoprClient::OoprClient()
        : mToken(sp<BBinder>::make()), mCache(std::make_unique<IPCClientResourceCache>()) {}

OoprClient::~OoprClient() = default;

void OoprClient::enableOutOfProcessRendering() {
    if (com_android_graphics_libgui_flags_out_of_process_rendering()) {
        mEnableOOPR = true;
    } else {
        LOG_ALWAYS_FATAL(
                "Flag com.android.graphics.libgui.flags.out_of_process_rendering is disabled!");
    }
}

IPCClientResourceCache& OoprClient::getIPCResourceCache() {
    return *mCache;
}

sp<BBinder> OoprClient::getDefaultRenderResourceToken() {
    return mToken;
}

OoprLayerResult OoprClient::createLayerSurface(uint32_t width, uint32_t height,
                                               GrDirectContext* context) {
    LOG_ALWAYS_FATAL_IF(
            !mEnableOOPR,
            "Flag com.android.graphics.libgui.flags.out_of_process_rendering is disabled!");

    LOG_ALWAYS_FATAL_IF(!context, "Skia GrDirectContext is null");

    sp<GraphicBuffer> buffer = sp<GraphicBuffer>::make(
            width, height, PIXEL_FORMAT_RGBA_8888, 1,
            GraphicBuffer::USAGE_HW_TEXTURE | GraphicBuffer::USAGE_HW_RENDER, "OOPRLayer");
    if (buffer->initCheck() != NO_ERROR) {
        ALOGE("Failed to allocate GraphicBuffer for layer");
        return {};
    }

    ATRACE_FORMAT("createLayerSurface bufferId=%llu", buffer->getId());
    AutoBackendTextureRelease* textureRelease =
            new AutoBackendTextureRelease(context, buffer->toAHardwareBuffer());
    if (!textureRelease->getTexture().isValid()) {
        textureRelease->unref(false);
        return {};
    }

    // We don't need to know when the surface / SkImage is released because
    // the NodeResources lifetime matches the RenderNode will outlive the SkImage.
    sk_sp<SkSurface> surface = SkSurfaces::WrapBackendTexture(
            context, textureRelease->getTexture(), kTopLeft_GrSurfaceOrigin, 0,
            kRGBA_8888_SkColorType, nullptr, nullptr, nullptr, nullptr);

    if (!surface) {
        textureRelease->unref(false);
        return {};
    }

    OoprLayerResult result;
    result.surface = std::move(surface);
    result.resources = std::make_unique<OoprNode>();
    result.resources->mBuffer = buffer;
    result.resources->mTextureRelease = textureRelease;

    return result;
}

void OoprNode::registerSnapshot(const sk_sp<SkImage>& image) {
    auto oopr = OoprClient::getInstance();

    if (!oopr->isEnabled()) return;

    ATRACE_CALL();

    if (!image) return;

    if (mLastImage && mLastImage->uniqueID() == image->uniqueID()) {
        return;
    }

    sk_sp<SkImage> oldImage = mLastImage;
    mLastImage = image;

    if (!mBuffer) {
        ALOGE("Buffer unexpectedly null!");
        return;
    }

    if (oldImage) {
        oopr->deregisterBuffer(oldImage);
    }
    oopr->registerBuffer(mBuffer, image);
}

bool OoprClient::isEnabled() {
    return mEnableOOPR;
}

void OoprBitmap::createAndRegisterShadowBuffer(const SkBitmap& bitmap, sk_sp<SkImage> image) {
    auto oopr = OoprClient::getInstance();

    if (!oopr->isEnabled()) return;

    ATRACE_CALL();

    // Check if the image has changed. If not, we're done.
    if (mLastImage && mLastImage->uniqueID() == image->uniqueID()) {
        return;
    }

    sk_sp<SkImage> oldImage = mLastImage;
    mLastImage = image;

    if (oldImage) {
        oopr->deregisterBuffer(oldImage);
    }
    oopr->registerBitmap(bitmap, image);
}

// This function is called from Bitmap.cpp, potentially from any thread.
// However, all bitmap mutation must be finished before work is submitted to the render thread
// so there is no chance of races.
void OoprClient::registerBuffer(const sp<GraphicBuffer>& buffer, const sk_sp<SkImage>& image) {
    if (!mEnableOOPR || !image) return;
    ATRACE_FORMAT("registerBuffer bufferId=%llu imageId=%u", buffer->getId(), image->uniqueID());

    IPCClientBitmap clientBitmap;
    clientBitmap.id = buffer->getId();
    clientBitmap.buffer = buffer;
    mCache->bitmaps.emplace(image->uniqueID(), clientBitmap);

    Registration r;
    r.buffer = buffer;
    r.image = image;
    mRegistrations.push_back(r);
}

void OoprClient::registerBitmap(const SkBitmap& bitmap, const sk_sp<SkImage>& image) {
    if (!mEnableOOPR || !image) return;
    ATRACE_FORMAT("registerBitmap imageId=%u bitmap=%u", image->uniqueID(),
                  bitmap.getGenerationID());

    IPCClientBitmap clientBitmap;
    clientBitmap.id = image->uniqueID();
    clientBitmap.bitmap = bitmap;
    mCache->bitmaps.emplace(clientBitmap.id, clientBitmap);

    Registration r;
    r.image = image;
    r.bitmap = bitmap;
    mRegistrations.push_back(r);
}

void OoprClient::sendPendingBitmapRegistrations(RenderCommandBuffer* cmds) {
    if (!mEnableOOPR) {
        return;
    }
    ATRACE_CALL();

    gui::GraphicBuffersUnregisterInfo unregisterInfo;
    gui::GraphicBuffersRegisterInfo registerInfo;

    registerInfo.renderResourceToken = mToken;
    unregisterInfo.renderResourceToken = mToken;

    for (const auto& reg : mRegistrations) {
        if (reg.buffer) {
            registerInfo.buffers.push_back(reg.buffer);
        } else {
            UploadBitmap::Create(cmds->mRegion.get(), reg.image->uniqueID(), reg.bitmap);
        }
    }
    mRegistrations.clear();

    for (const auto& dereg : mDeregistrations) {
        if (dereg.bufferId) {
            unregisterInfo.bufferIds.push_back(dereg.bufferId);
        } else {
            FreeBitmap::Create(cmds->mRegion.get(), dereg.imageId);
        }
    }
    mDeregistrations.clear();

    if (!registerInfo.buffers.empty()) {
        ComposerService::getComposerService()->registerGraphicBuffers(registerInfo);
    }

    if (!unregisterInfo.bufferIds.empty()) {
        ComposerService::getComposerService()->unregisterGraphicBuffers(unregisterInfo);
    }
}

void OoprClient::deregisterBuffer(const sk_sp<SkImage>& image) {
    if (!mEnableOOPR) {
        return;
    }

    ATRACE_FORMAT("deregisterBuffer  bitmap=%u", image->uniqueID());

    auto it = mCache->bitmaps.find(image->uniqueID());
    if (it == mCache->bitmaps.end()) {
        return;
    }

    Deregistration d;
    d.imageId = image->uniqueID();
    if (it->second.buffer) {
        d.bufferId = it->second.buffer->getId();
    }
    mDeregistrations.push_back(d);

    mCache->bitmaps.erase(it);
}

#endif

}  // namespace uirenderer
}  // namespace android
