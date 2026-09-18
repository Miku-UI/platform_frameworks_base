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

#include <SkBitmap.h>
#include <SkImage.h>
#include <SkSurface.h>
#include <binder/Binder.h>
#include <utils/StrongPointer.h>

#include <mutex>

class GrDirectContext;

namespace android {
class GraphicBuffer;
class RenderCommandBuffer;
}

namespace android {
namespace uirenderer {
class AutoBackendTextureRelease;
}
}  // namespace android

#ifdef __ANDROID__
#include <android/hardware_buffer.h>
#endif

namespace android {
#ifdef __ANDROID__
struct IPCClientResourceCache;
#endif

namespace uirenderer {

#ifdef __ANDROID__
class OoprClient;

struct OoprNode {
public:
    ~OoprNode();
    void registerSnapshot(const sk_sp<SkImage>& image);

    const sp<GraphicBuffer>& getBuffer() const { return mBuffer; }

private:
    friend class OoprClient;
    sp<GraphicBuffer> mBuffer;
    AutoBackendTextureRelease* mTextureRelease = nullptr;
    sk_sp<SkImage> mLastImage;
};

struct OoprBitmap {
public:
    ~OoprBitmap();
    void createAndRegisterShadowBuffer(const SkBitmap& bitmap, sk_sp<SkImage> image);

private:
    sp<GraphicBuffer> mShadowBuffer;
    sk_sp<SkImage> mLastImage;
};

struct OoprLayerResult {
    sk_sp<SkSurface> surface;
    std::unique_ptr<OoprNode> resources;
};

class OoprClient {
public:
    static OoprClient* getInstance();

    IPCClientResourceCache& getIPCResourceCache();
    sp<BBinder> getDefaultRenderResourceToken();
    void enableOutOfProcessRendering();
    bool isEnabled();

    OoprLayerResult createLayerSurface(uint32_t width, uint32_t height, GrDirectContext* context);
    void registerBuffer(const sp<GraphicBuffer>& buffer, const sk_sp<SkImage>& image);
    void registerBitmap(const SkBitmap& bitmap, const sk_sp<SkImage>& image);
    void deregisterBuffer(const sk_sp<SkImage>& image);

    void sendPendingBitmapRegistrations(RenderCommandBuffer* cmds);

private:
    OoprClient();
    virtual ~OoprClient();

    bool mEnableOOPR = false;
    sp<BBinder> mToken;
    std::unique_ptr<IPCClientResourceCache> mCache;

    struct Registration {
        sp<GraphicBuffer> buffer;
        sk_sp<SkImage> image;
        SkBitmap bitmap;
    };

    struct Deregistration {
        uint64_t imageId;
        uint64_t bufferId = 0;
    };

    std::vector<Registration> mRegistrations;
    std::vector<Deregistration> mDeregistrations;
};

#endif

}  // namespace uirenderer
}  // namespace android
