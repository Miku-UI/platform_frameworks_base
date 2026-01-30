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

#include <SkData.h>
#include <SkRefCnt.h>
#include <SkString.h>
#include <sys/types.h>

#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <optional>
#include <string>
#include <thread>
#include <vector>

struct Memory {
    void* data;
    size_t size;
};

struct AcquireResult {
    enum Outcome {
        Success,
        OpenFailed,
        FstatFailed,
        CannotMmapZeroSizeFile,
        MmapFailed,
    };
    Outcome outcome;
    int errnoValue;
};

AcquireResult acquire(const std::string& path, Memory& memory);

struct ReleaseResult {
    enum Outcome {
        Success,
        MunmapFailed,
    };
    Outcome outcome;
    int errnoValue;
};

ReleaseResult release(Memory memory);

class PipelineCacheStore {
public:
    PipelineCacheStore(useconds_t writeThrottleInterval);

    ~PipelineCacheStore();

    PipelineCacheStore(const PipelineCacheStore&) = delete;
    PipelineCacheStore& operator=(const PipelineCacheStore&) = delete;

    // Address must be stable as data is accessed in background thread
    PipelineCacheStore(PipelineCacheStore&&) = delete;
    PipelineCacheStore& operator=(PipelineCacheStore&&) = delete;

    void store(std::string path, std::vector<uint8_t> data);

private:
    void runThread();

    useconds_t mWriteThrottleInterval;

    std::mutex mMutex;
    std::condition_variable mConditionVariable;

    struct StoreRequest {
        std::string path;
        std::vector<uint8_t> data;
    };
    std::optional<StoreRequest> mStoreRequest;

    std::atomic_bool mExit;
    std::thread mThread;
};

class PipelineCache {
public:
    PipelineCache(std::string storePath, useconds_t writeThrottleInterval);

    sk_sp<SkData> tryLoad(const SkData& key);
    bool canStore(const SkString& description) const;
    void store(const SkData& key, const SkData& data);

private:
    std::string mStorePath;
    PipelineCacheStore mPipelineCacheStore;

    sk_sp<SkData> mKey;
    sk_sp<SkData> mData;
};
