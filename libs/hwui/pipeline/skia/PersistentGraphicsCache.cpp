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

#include "PersistentGraphicsCache.h"

#include <SkData.h>
#include <SkRefCnt.h>
#include <SkString.h>
#include <ganesh/GrDirectContext.h>
#include <log/log.h>

#include <cstddef>
#include <memory>
#include <string>
#include <utility>

#include "Properties.h"
#include "ShaderCache.h"

#ifdef __linux__
#include <com_android_graphics_hwui_flags.h>
namespace hwui_flags = com::android::graphics::hwui::flags;
#else   // __linux__
namespace hwui_flags {
constexpr bool separate_pipeline_cache() {
    return false;
}
}  // namespace hwui_flags
#endif  // __linux__

namespace {

constexpr size_t kMaxPipelineSizeBytes = 2 * 1024 * 1024;

}  // namespace

namespace android {
namespace uirenderer {
namespace skiapipeline {

PersistentGraphicsCache& PersistentGraphicsCache::get() {
    static PersistentGraphicsCache cache;
    return cache;
}

void PersistentGraphicsCache::initPipelineCache(std::string path,
                                                useconds_t writeThrottleInterval) {
    if (!hwui_flags::separate_pipeline_cache()) {
        return;
    }

    mPipelineCache = std::make_unique<PipelineCache>(std::move(path), writeThrottleInterval);
}

void PersistentGraphicsCache::onVkFrameFlushed(GrDirectContext* context) {
    class RealGrDirectContext : public GrDirectContextWrapper {
    private:
        GrDirectContext* mContext;

    public:
        RealGrDirectContext(GrDirectContext* context) : mContext(context) {}

        bool canDetectNewVkPipelineCacheData() const override {
            return mContext->canDetectNewVkPipelineCacheData();
        }

        bool hasNewVkPipelineCacheData() const override {
            return mContext->hasNewVkPipelineCacheData();
        }

        void storeVkPipelineCacheData(size_t maxSize) override {
            return mContext->storeVkPipelineCacheData(maxSize);
        }

        GrDirectContext* unwrap() const override { return mContext; }
    };

    RealGrDirectContext wrapper(context);
    onVkFrameFlushed(&wrapper);
}

void PersistentGraphicsCache::onVkFrameFlushed(GrDirectContextWrapper* context) {
    if (!hwui_flags::separate_pipeline_cache()) {
        ShaderCache::get().onVkFrameFlushed(context->unwrap());
        return;
    }

    mCanDetectNewVkPipelineCacheData = context->canDetectNewVkPipelineCacheData();
    if (context->hasNewVkPipelineCacheData()) {
        context->storeVkPipelineCacheData(kMaxPipelineSizeBytes);
    }
}

sk_sp<SkData> PersistentGraphicsCache::load(const SkData& key) {
    if (!hwui_flags::separate_pipeline_cache()) {
        return ShaderCache::get().load(key);
    }

    if (mPipelineCache == nullptr) {
        LOG_ALWAYS_FATAL(
                "PersistentGraphicsCache::load: pipeline cache path was not initialized, aborting "
                "load");
        return nullptr;
    }

    auto data = mPipelineCache->tryLoad(key);
    if (data != nullptr) {
        return data;
    }

    return ShaderCache::get().load(key);
}

void PersistentGraphicsCache::store(const SkData& key, const SkData& data,
                                    const SkString& description) {
    if (!hwui_flags::separate_pipeline_cache()) {
        ShaderCache::get().store(key, data, description);
        return;
    }

    if (mPipelineCache == nullptr) {
        LOG_ALWAYS_FATAL(
                "PersistentGraphicsCache::store: pipeline cache path was not initialized, aborting "
                "store");
        return;
    }

    if (mPipelineCache->canStore(description)) {
        if (mCanDetectNewVkPipelineCacheData) {
            mPipelineCache->store(key, data);
        } else if (mLastPipelineCacheSize != data.size()) {
            mPipelineCache->store(key, data);
            mLastPipelineCacheSize = data.size();
        }
        return;
    }

    ShaderCache::get().store(key, data, description);
}

}  // namespace skiapipeline
}  // namespace uirenderer
}  // namespace android
