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

#include <ganesh/GrContextOptions.h>
#include <ganesh/GrDirectContext.h>
#include <sys/types.h>

#include <cstddef>
#include <memory>
#include <string>

#include "PipelineCache.h"

namespace android {
namespace uirenderer {
namespace skiapipeline {

// Delegate persistent cache operations to either the pipeline cache or the shader cache as
// appropriate
class PersistentGraphicsCache : public GrContextOptions::PersistentCache {
    static constexpr useconds_t kDefaultWriteThrottleInterval = 4 * 1000 * 1000;

public:
    static PersistentGraphicsCache& get();

    void initPipelineCache(std::string path,
                           useconds_t writeThrottleInterval = kDefaultWriteThrottleInterval);
    void onVkFrameFlushed(GrDirectContext* context);

    sk_sp<SkData> load(const SkData& key) override;
    void store(const SkData& key, const SkData& data, const SkString& description) override;

private:
    std::unique_ptr<PipelineCache> mPipelineCache;

    // Workarounds for devices without VK_EXT_pipeline_creation_cache_control
    bool mCanDetectNewVkPipelineCacheData;
    size_t mLastPipelineCacheSize;

    // Unit test infrastructure
    class GrDirectContextWrapper {
    public:
        virtual ~GrDirectContextWrapper() = default;

        virtual bool canDetectNewVkPipelineCacheData() const = 0;
        virtual bool hasNewVkPipelineCacheData() const = 0;
        virtual void storeVkPipelineCacheData(size_t maxSize) = 0;

        virtual GrDirectContext* unwrap() const = 0;
    };

    void onVkFrameFlushed(GrDirectContextWrapper* context);

    friend class PersistentGraphicsCacheTestUtils;
};

}  // namespace skiapipeline
}  // namespace uirenderer
}  // namespace android
