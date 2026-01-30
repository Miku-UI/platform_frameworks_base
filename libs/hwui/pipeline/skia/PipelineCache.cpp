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

#include "PipelineCache.h"

#include <SkData.h>
#include <SkRefCnt.h>
#include <SkString.h>
#include <android-base/unique_fd.h>
#include <errno.h>
#include <fcntl.h>
#include <log/log.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <utils/Trace.h>

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

namespace {

using key_size_t = uint32_t;

void releaseProc(const void* ptr, void* context) {
    const auto size = reinterpret_cast<size_t>(context);
    release(Memory{.data = const_cast<void*>(ptr), .size = size});
}

struct PipelineCacheData {
    key_size_t keySize;
    sk_sp<SkData> key;
    sk_sp<SkData> data;

    struct LoadResult {
        enum Outcome {
            Success,
            CouldNotAcquire,
            ZeroSize,
            NoKeySize,
            NoKey,
        };
        Outcome outcome;
        AcquireResult acquireResult;
    };

    static LoadResult load(const std::string& path, PipelineCacheData& cache) {
        Memory memory;
        auto result = acquire(path, memory);
        if (result.outcome != AcquireResult::Success) {
            return LoadResult{.outcome = LoadResult::CouldNotAcquire, .acquireResult = result};
        }

        if (memory.size == 0) {
            release(memory);
            return LoadResult{.outcome = LoadResult::ZeroSize};
        }

        if (memory.size < sizeof(key_size_t)) {
            release(memory);
            return LoadResult{.outcome = LoadResult::NoKeySize};
        }
        memcpy(&cache.keySize, memory.data, sizeof(key_size_t));

        if (memory.size < (sizeof(key_size_t) + cache.keySize)) {
            release(memory);
            return LoadResult{.outcome = LoadResult::NoKey};
        }
        cache.key = SkData::MakeWithCopy(static_cast<uint8_t*>(memory.data) + sizeof(key_size_t),
                                         cache.keySize);

        auto dataSize = memory.size - sizeof(key_size_t) - cache.keySize;
        cache.data = SkData::MakeWithProc(
                static_cast<uint8_t*>(memory.data) + sizeof(key_size_t) + cache.keySize, dataSize,
                releaseProc, reinterpret_cast<void*>(memory.size));

        return LoadResult{.outcome = LoadResult::Success};
    }
};

void logLoadWarning(PipelineCacheData::LoadResult result, const char* message) {
    if (result.outcome == PipelineCacheData::LoadResult::CouldNotAcquire) {
        // Missing file is a normal case (cache was never written - there is no failure)
        if ((result.acquireResult.outcome == AcquireResult::OpenFailed) &&
            (result.acquireResult.errnoValue == ENOENT)) {
            return;
        }

        ALOGW("%s; acquire outcome=%d, errnoValue=%d", message, result.acquireResult.outcome,
              result.acquireResult.errnoValue);
        return;
    }

    ALOGW("%s; outcome=%d", message, result.outcome);
}

}  // namespace

AcquireResult acquire(const std::string& path, Memory& memory) {
    android::base::unique_fd fd(open(path.c_str(), O_RDONLY));
    if (fd.get() == -1) {
        return AcquireResult{.outcome = AcquireResult::OpenFailed, .errnoValue = errno};
    }

    struct stat stat;
    auto result = fstat(fd.get(), &stat);
    if (result == -1) {
        return AcquireResult{.outcome = AcquireResult::FstatFailed, .errnoValue = errno};
    }
    if (stat.st_size == 0) {
        return AcquireResult{.outcome = AcquireResult::CannotMmapZeroSizeFile};
    }

    auto data = mmap(nullptr, stat.st_size, PROT_READ, MAP_SHARED, fd.get(), 0);
    if (data == reinterpret_cast<void*>(-1)) {
        return AcquireResult{.outcome = AcquireResult::MmapFailed, .errnoValue = errno};
    }

    memory.data = data;
    memory.size = stat.st_size;

    return AcquireResult{.outcome = AcquireResult::Success};
}

ReleaseResult release(Memory memory) {
    auto result = munmap(memory.data, memory.size);
    if (result == -1) {
        return ReleaseResult{.outcome = ReleaseResult::MunmapFailed, .errnoValue = errno};
    }

    return ReleaseResult{.outcome = ReleaseResult::Success};
}

PipelineCacheStore::PipelineCacheStore(useconds_t writeThrottleInterval)
        : mWriteThrottleInterval(writeThrottleInterval)
        , mMutex()
        , mConditionVariable()
        , mStoreRequest()
        , mExit(false)
        , mThread(&PipelineCacheStore::runThread, this) {}

PipelineCacheStore::~PipelineCacheStore() {
    mExit = true;
    mConditionVariable.notify_one();
    mThread.join();
}

void PipelineCacheStore::runThread() {
    while (true) {
        {
            std::unique_lock<std::mutex> lock(mMutex);
            mConditionVariable.wait(lock, [this] { return mExit || mStoreRequest.has_value(); });
        }

        if (mExit) {
            return;
        }

        {
            ATRACE_NAME("PipelineCacheStore::runThread (delay to throttle cache requests)");
            // Frequent sequential cache writes will be written at most once per interval to reduce
            // I/O activity.
            usleep(mWriteThrottleInterval);
        }

        StoreRequest storeRequest;
        {
            std::lock_guard<std::mutex> lock(mMutex);

            storeRequest = std::move(mStoreRequest.value());
            mStoreRequest.reset();
        }

        {
            ATRACE_NAME("PipelineCacheStore::runThread (write to file cache)");

            android::base::unique_fd fd(creat(storeRequest.path.c_str(), S_IRUSR | S_IWUSR));
            if (fd.get() == -1) {
                ALOGE("PipelineCacheStore::runThread: could not open pipeline cache file (errno = "
                      "%d)",
                      errno);
                continue;
            }

            auto written = write(fd.get(), storeRequest.data.data(), storeRequest.data.size());
            if (written == -1) {
                ALOGE("PipelineCacheStore::runThread: could not write to pipeline cache file "
                      "(errno = %d)",
                      errno);
                continue;
            }

            ATRACE_INT64("HWUI pipeline cache size", written);
        }
    }
}

void PipelineCacheStore::store(std::string path, std::vector<uint8_t> data) {
    ATRACE_NAME("PipelineCacheStore::store (lock mutex and notify condition)");

    {
        std::lock_guard<std::mutex> lock(mMutex);
        mStoreRequest = StoreRequest{
                .path = std::move(path),
                .data = std::move(data),
        };
    }

    mConditionVariable.notify_one();
}

PipelineCache::PipelineCache(std::string storePath, useconds_t writeThrottleInterval)
        : mStorePath(std::move(storePath))
        , mPipelineCacheStore(writeThrottleInterval)
        , mKey(SkData::MakeEmpty())
        , mData(SkData::MakeEmpty()) {
    PipelineCacheData cache;
    auto result = PipelineCacheData::load(mStorePath, cache);
    if (result.outcome != PipelineCacheData::LoadResult::Success) {
        logLoadWarning(
                result,
                "PipelineCache::PipelineCache: could not load cache key (cache will be dropped)");
        return;
    }

    mKey = cache.key;
    mData = cache.data;
}

sk_sp<SkData> PipelineCache::tryLoad(const SkData& key) {
    ATRACE_NAME("PipelineCache::tryLoad");

    if (!key.equals(mKey.get())) {
        return nullptr;
    }

    if (mData == nullptr) {
        ALOGW("PipelineCache::tryLoad: multiple data loads, incurring a load cost on the critical "
              "path");

        PipelineCacheData cache;
        auto result = PipelineCacheData::load(mStorePath, cache);
        if (result.outcome != PipelineCacheData::LoadResult::Success) {
            logLoadWarning(
                    result,
                    "PipelineCache::tryLoad: could not load cache key (cache will be dropped)");
            return nullptr;
        }

        return std::move(cache.data);
    }

    return std::move(mData);
}

bool PipelineCache::canStore(const SkString& description) const {
    return description == SkString("VkPipelineCache");
}

void PipelineCache::store(const SkData& key, const SkData& data) {
    ATRACE_NAME("PipelineCache::store");

    mKey = SkData::MakeWithCopy(key.data(), key.size());

    auto dataSize = sizeof(key_size_t) + key.size() + data.size();
    std::vector<uint8_t> pendingData(dataSize);
    auto ptr = pendingData.data();

    auto keySize = static_cast<key_size_t>(key.size());
    memcpy(ptr, &keySize, sizeof(key_size_t));
    ptr += sizeof(key_size_t);

    memcpy(ptr, key.data(), key.size());
    ptr += key.size();

    memcpy(ptr, data.data(), data.size());

    mPipelineCacheStore.store(mStorePath, std::move(pendingData));
}
