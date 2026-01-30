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

#include <SkData.h>
#include <SkRefCnt.h>
#include <SkString.h>
#include <ganesh/GrDirectContext.h>
#include <gtest/gtest.h>
#include <sys/types.h>

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <string>

#include "Properties.h"
#include "pipeline/skia/PersistentGraphicsCache.h"
#include "tests/common/TestUtils.h"

// RENDERTHREAD_TEST declares both SkiaVK and SkiaGL variants.
#define VK_ONLY()                                                                         \
    if (Properties::getRenderPipelineType() != RenderPipelineType::SkiaVulkan) {          \
        GTEST_SKIP() << "This test is only applicable to RenderPipelineType::SkiaVulkan"; \
    }

#define ENSURE_FLAG_ENABLED()                                                                    \
    if (!hwui_flags::separate_pipeline_cache()) {                                                \
        GTEST_SKIP() << "This test is only applicable when the separate_pipeline_cache aconfig " \
                        "flag is enabled";                                                       \
    }

using namespace android::uirenderer;
using namespace android::uirenderer::skiapipeline;

namespace {

constexpr char kFilename[] = "pipeline_cache.bin";

template <typename T>
sk_sp<SkData> createData(const T value) {
    return SkData::MakeWithCopy(&value, sizeof(value));
}

// Hardcoded Skia enum value - tests may break if Skia changes the key.
sk_sp<SkData> getPipelineCacheKey() {
    static sk_sp<SkData> keyData = createData<uint32_t>(1);
    return keyData;
}

}  // namespace

namespace android {
namespace uirenderer {
namespace skiapipeline {

class PersistentGraphicsCacheTestUtils {
private:
    class MockGrDirectContextWrapper : public PersistentGraphicsCache::GrDirectContextWrapper {
    private:
        bool mCanDetectNewVkPipelineCacheData;
        bool mHasNewVkPipelineCacheData;
        GrDirectContext* mRealContext;

    public:
        MockGrDirectContextWrapper(bool canDetectNewVkPipelineCacheData,
                                   bool hasNewVkPipelineCacheData, GrDirectContext* realContext)
                : mCanDetectNewVkPipelineCacheData(canDetectNewVkPipelineCacheData)
                , mHasNewVkPipelineCacheData(hasNewVkPipelineCacheData)
                , mRealContext(realContext) {}

        bool canDetectNewVkPipelineCacheData() const override {
            return mCanDetectNewVkPipelineCacheData;
        }

        bool hasNewVkPipelineCacheData() const override { return mHasNewVkPipelineCacheData; }

        void storeVkPipelineCacheData(size_t maxSize) override {
            mRealContext->storeVkPipelineCacheData(maxSize);
        }

        GrDirectContext* unwrap() const override { return mRealContext; }
    };

    static void reset(PersistentGraphicsCache& cache) {
        cache.~PersistentGraphicsCache();
        new (&cache) PersistentGraphicsCache();
    }

public:
    static PersistentGraphicsCache& newCache(const std::string& path,
                                             useconds_t writeThrottleInterval = 0) {
        auto& cache = PersistentGraphicsCache::get();
        reset(cache);
        cache.initPipelineCache(path, writeThrottleInterval);
        return cache;
    }

    static void onVkFrameFlushed(PersistentGraphicsCache& cache,
                                 bool canDetectNewVkPipelineCacheData,
                                 bool hasNewVkPipelineCacheData, GrDirectContext* realContext) {
        MockGrDirectContextWrapper wrapper(canDetectNewVkPipelineCacheData,
                                           hasNewVkPipelineCacheData, realContext);
        cache.onVkFrameFlushed(&wrapper);
    }
};

}  // namespace skiapipeline
}  // namespace uirenderer
}  // namespace android

TEST(PersistentGraphicsCacheTest, emptyFile_loadKey_isEmptyByDefault) {
    // Arrange
    ENSURE_FLAG_ENABLED();

    auto file = TestFile::ensureExistsEmpty(kFilename);
    ASSERT_TRUE(file.has_value());

    auto& cache = PersistentGraphicsCacheTestUtils::newCache(file->path());

    // Act
    auto result = cache.load(*getPipelineCacheKey());

    // Assert
    ASSERT_EQ(nullptr, result);
}

TEST(PersistentGraphicsCacheTest, store_load_returnsIdenticalData) {
    // Arrange
    ENSURE_FLAG_ENABLED();

    auto file = TestFile::ensureExistsEmpty(kFilename);
    ASSERT_TRUE(file.has_value());

    auto& cache = PersistentGraphicsCacheTestUtils::newCache(file->path());

    auto monitorCreateResult = FileEventMonitor::create(file->path());
    ASSERT_SUCCESS(monitorCreateResult);

    uint64_t dataValue = 5;
    auto key = createData<uint32_t>(10);
    auto data = createData(dataValue);

    // Act
    cache.store(*key, *data, SkString("VkPipelineCache"));
    ASSERT_EQ(FileEventMonitor::AwaitResult::Success,
              monitorCreateResult.monitor->awaitWriteOrTimeout());
    auto result = PersistentGraphicsCacheTestUtils::newCache(file->path()).load(*key);

    // Assert
    ASSERT_NE(nullptr, result);
    ASSERT_EQ(sizeof(dataValue), result->size());
    ASSERT_EQ(0, memcmp(&dataValue, result->data(), sizeof(dataValue)));
}

RENDERTHREAD_TEST(PersistentGraphicsCacheTest, hasPipelineCreationCacheControl_newCache_isStored) {
    // Arrange
    ENSURE_FLAG_ENABLED();
    VK_ONLY();

    auto context = renderThread.getGrContext();

    auto file = TestFile::ensureExistsEmpty(kFilename);
    ASSERT_TRUE(file.has_value());

    auto& cache = PersistentGraphicsCacheTestUtils::newCache(file->path());

    auto monitorCreateResult = FileEventMonitor::create(file->path());
    ASSERT_SUCCESS(monitorCreateResult);

    // Act
    PersistentGraphicsCacheTestUtils::onVkFrameFlushed(cache,
                                                       /* canDetectNewVkPipelineCacheData= */ true,
                                                       /* hasNewPipelineCache= */ true, context);

    // Assert
    ASSERT_EQ(FileEventMonitor::AwaitResult::Success,
              monitorCreateResult.monitor->awaitWriteOrTimeout());
    auto result =
            PersistentGraphicsCacheTestUtils::newCache(file->path()).load(*getPipelineCacheKey());
    ASSERT_NE(nullptr, result);
    ASSERT_NE(nullptr, result->data());
    ASSERT_GT(result->size(), 0);
}

RENDERTHREAD_TEST(PersistentGraphicsCacheTest,
                  hasPipelineCreationCacheControl_oldCache_isNotStored) {
    // Arrange
    ENSURE_FLAG_ENABLED();
    VK_ONLY();

    auto context = renderThread.getGrContext();

    auto file = TestFile::ensureExistsEmpty(kFilename);
    ASSERT_TRUE(file.has_value());

    auto& cache = PersistentGraphicsCacheTestUtils::newCache(file->path());

    auto monitorCreateResult = FileEventMonitor::create(file->path());
    ASSERT_SUCCESS(monitorCreateResult);

    // Act
    PersistentGraphicsCacheTestUtils::onVkFrameFlushed(cache,
                                                       /* canDetectNewVkPipelineCacheData= */ true,
                                                       /* hasNewPipelineCache= */ false, context);

    // Assert
    ASSERT_EQ(FileEventMonitor::AwaitResult::TimedOut,
              monitorCreateResult.monitor->awaitWriteOrTimeout());
    auto result =
            PersistentGraphicsCacheTestUtils::newCache(file->path()).load(*getPipelineCacheKey());
    ASSERT_EQ(nullptr, result);
}

RENDERTHREAD_TEST(PersistentGraphicsCacheTest,
                  noPipelineCreationCacheControl_newCacheBySize_isStored) {
    // Arrange
    ENSURE_FLAG_ENABLED();
    VK_ONLY();

    auto context = renderThread.getGrContext();

    auto file = TestFile::ensureExistsEmpty(kFilename);
    ASSERT_TRUE(file.has_value());

    auto& cache = PersistentGraphicsCacheTestUtils::newCache(file->path());

    auto monitorCreateResult = FileEventMonitor::create(file->path());
    ASSERT_SUCCESS(monitorCreateResult);

    // Act
    // Current cache size is 0, so cache is new by size
    PersistentGraphicsCacheTestUtils::onVkFrameFlushed(cache,
                                                       /* canDetectNewVkPipelineCacheData= */ false,
                                                       /* hasNewPipelineCache= */ true, context);

    // Assert
    ASSERT_EQ(FileEventMonitor::AwaitResult::Success,
              monitorCreateResult.monitor->awaitWriteOrTimeout());
    auto result =
            PersistentGraphicsCacheTestUtils::newCache(file->path()).load(*getPipelineCacheKey());
    ASSERT_NE(nullptr, result);
    ASSERT_NE(nullptr, result->data());
    ASSERT_GT(result->size(), 0);
}

RENDERTHREAD_TEST(PersistentGraphicsCacheTest,
                  noPipelineCreationCacheControl_oldCacheBySize_isNotStored) {
    // Arrange
    ENSURE_FLAG_ENABLED();
    VK_ONLY();

    auto context = renderThread.getGrContext();

    auto file = TestFile::ensureExistsEmpty(kFilename);
    ASSERT_TRUE(file.has_value());

    auto& cache = PersistentGraphicsCacheTestUtils::newCache(file->path());

    auto monitorCreateResult = FileEventMonitor::create(file->path());
    ASSERT_SUCCESS(monitorCreateResult);

    // Current cache size is 0, so cache is new by size
    PersistentGraphicsCacheTestUtils::onVkFrameFlushed(cache,
                                                       /* canDetectNewVkPipelineCacheData= */ false,
                                                       /* hasNewPipelineCache= */ true, context);
    ASSERT_EQ(FileEventMonitor::AwaitResult::Success,
              monitorCreateResult.monitor->awaitWriteOrTimeout());

    // Act
    // Cache size has not changed, so cache is old by size
    PersistentGraphicsCacheTestUtils::onVkFrameFlushed(cache,
                                                       /* canDetectNewVkPipelineCacheData= */ false,
                                                       /* hasNewPipelineCache= */ true, context);

    // Assert
    ASSERT_EQ(FileEventMonitor::AwaitResult::TimedOut,
              monitorCreateResult.monitor->awaitWriteOrTimeout());
}
