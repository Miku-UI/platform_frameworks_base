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

#include <android-base/scopeguard.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstdint>
#include <span>
#include <string_view>
#include <vector>

#include "pipeline/skia/PipelineCache.h"
#include "tests/common/TestUtils.h"

using namespace android::uirenderer;

using ::testing::ElementsAre;

namespace {

constexpr char kFilename[] = "blobcache.bin";

}

TEST(PipelineCacheTest, noFile_acquire_fails) {
    // Arrange
    auto file = TestFile::ensureDoesNotExist(kFilename);
    ASSERT_TRUE(file.has_value());

    // Act
    Memory mem;
    auto result = acquire(file->path(), mem);

    // Assert
    ASSERT_EQ(AcquireResult::OpenFailed, result.outcome);
}

TEST(PipelineCacheTest, existingFile_acquire_succeeds) {
    // Arrange
    auto file = TestFile::ensureExistsEmpty(kFilename);
    ASSERT_TRUE(file.has_value());
    file->write("data");

    // Act
    Memory mem;
    auto result = acquire(file->path(), mem);
    auto _ = android::base::make_scope_guard([=]() { release(mem); });

    // Assert
    ASSERT_EQ(AcquireResult::Success, result.outcome);
    ASSERT_EQ(std::string_view("data"),
              std::string_view(static_cast<const char*>(mem.data), mem.size));
}

TEST(PipelineCacheTest, existingFile_acquireAndRelease_succeeds) {
    // Arrange
    auto file = TestFile::ensureExistsEmpty(kFilename);
    ASSERT_TRUE(file.has_value());
    file->write("data");

    // Act
    Memory mem;
    auto acquireResult = acquire(file->path(), mem);
    auto releaseResult = release(mem);

    // Assert
    ASSERT_EQ(AcquireResult::Success, acquireResult.outcome);
    ASSERT_EQ(ReleaseResult::Success, releaseResult.outcome);
}

TEST(PipelineCacheTest, existingFile_backgroundWrite_overwritesData) {
    // Arrange
    auto file = TestFile::ensureExistsEmpty(kFilename);
    ASSERT_TRUE(file.has_value());
    file->write("data");
    auto monitorCreateResult = FileEventMonitor::create(file->path());
    ASSERT_SUCCESS(monitorCreateResult);
    PipelineCacheStore cache(0);

    // Act
    cache.store(file->path(), SkData::MakeWithCString("a"), SkData::MakeWithCString("b"));

    // Assert
    ASSERT_EQ(FileEventMonitor::AwaitResult::Success,
              monitorCreateResult.monitor->awaitWriteOrTimeout());
    Memory mem;
    auto result = acquire(file->path(), mem);
    auto _ = android::base::make_scope_guard([=]() { release(mem); });
    ASSERT_EQ(AcquireResult::Success, result.outcome);
    // Data is prefixed with the size of the key as uint32_t
    // Strings are null-terminated
    ASSERT_THAT(std::span(static_cast<const uint8_t*>(mem.data), mem.size),
                ElementsAre(2, 0, 0, 0, 'a', '\0', 'b', '\0'));
}
