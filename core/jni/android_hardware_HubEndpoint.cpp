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

#define LOG_TAG "HubEndpointJNI"

#include <android/looper.h>
#include <android_chre_flags.h>
#include <nativehelper/JNIHelp.h>
#include <unistd.h>

#include <cinttypes>
#include <cstdio>
#include <map>
#include <memory>
#include <optional>
#include <set>
#include <string>
#include <variant>
#include <vector>

#include "android_os_MessageQueue.h"
#include "core_jni_helpers.h"
#include "data_flow/host/notification_manager.h"
#include "data_flow/host/region_manager.h"
#include "data_flow/host/remote_consumer.h"
#include "data_flow/untyped_queue.h"
#include "jni.h"
#include "pw_bytes/span.h"
#include "pw_function/function.h"
#include "utils/Log.h"

using namespace android;
using ::aidl::android::hardware::contexthub::DataFlowAlertFds;
using ::aidl::android::hardware::contexthub::DataFlowId;
using ::aidl::android::hardware::contexthub::DataFlowSinkContext;
using ::aidl::android::hardware::contexthub::EndpointId;
using ::aidl::android::hardware::contexthub::SharedDataRegion;
using android::chre::flags::fmcq_support_variable_sized_data_flow_fix;
using android::contexthub::data_flow::AllocatorRegion;
using android::contexthub::data_flow::ConsumerManager;
using android::contexthub::data_flow::ConsumerPolicyBuilder;
using android::contexthub::data_flow::createRemoteConsumer;
using android::contexthub::data_flow::DataNotifier;
using android::contexthub::data_flow::NotificationManager;
using android::contexthub::data_flow::NotificationPolicy;
using android::contexthub::data_flow::queueLayout;
using android::contexthub::data_flow::RegionManager;
using android::contexthub::data_flow::RemoteEndpointId;
using android::contexthub::data_flow::RemoteNotifyArgs;
using android::contexthub::data_flow::UntypedConsumer;
using android::contexthub::data_flow::UntypedProducer;
using android::contexthub::data_flow::VariableDataConsumer;
using android::contexthub::data_flow::VariableDataProducer;

static JavaVM* gVm = nullptr;

#define RETURN_ON_FALSE(condition, retval, errorString) \
    if (!(condition)) {                                 \
        ALOGE("%s", errorString);                       \
        return retval;                                  \
    }

/** Rounds up the given value to the nearest multiple of the given power of 2. */
template <typename T, typename U>
constexpr T roundUpToMultiple(T value, U powerOf2) {
    T kUnalignedBits = static_cast<T>(powerOf2) - 1;
    return value + kUnalignedBits & ~kUnalignedBits;
}

class SourceWrapper {
public:
    SourceWrapper(std::variant<UntypedProducer, VariableDataProducer>&& producer,
                  AllocatorRegion allocator,
                  NotificationManager::NotificationDataHandle notificationDataHandle)
          : mProducer(std::move(producer)),
            mAllocator(allocator),
            mNotificationDataHandle(notificationDataHandle) {}

    SourceWrapper(SourceWrapper&& other)
          : mProducer(std::move(other.mProducer)),
            mAllocator(other.mAllocator),
            mNotificationDataHandle(other.mNotificationDataHandle) {}

    std::variant<UntypedProducer, VariableDataProducer>& getProducer() {
        return *mProducer;
    }

    AllocatorRegion* getAllocator() {
        return &mAllocator;
    }

    NotificationManager::NotificationDataHandle getNotificationDataHandle() {
        return mNotificationDataHandle;
    }

    std::optional<DataFlowId> getDataFlowId() {
        return mDataFlowId;
    }
    void setDataFlowId(DataFlowId dataFlowId) {
        mDataFlowId = dataFlowId;
    }

    std::set<EndpointId>& getOffloadSinks() {
        return mOffloadSinks;
    }

    void removeOffloadSink(EndpointId endpointId) {
        mOffloadSinks.erase(endpointId);
    }

    void reset() {
        mProducer.reset();
    }

private:
    std::optional<std::variant<UntypedProducer, VariableDataProducer>> mProducer;
    AllocatorRegion mAllocator;
    NotificationManager::NotificationDataHandle mNotificationDataHandle;
    std::optional<DataFlowId> mDataFlowId;

    std::set<EndpointId> mOffloadSinks;
};

class SinkWrapper {
public:
    SinkWrapper(std::variant<UntypedConsumer, VariableDataConsumer>&& consumer,
                DataFlowId dataFlowId, EndpointId sourceId)
          : mConsumer(std::move(consumer)), mDataFlowId(dataFlowId), mSourceId(sourceId) {}
    SinkWrapper(SinkWrapper&& other)
          : mConsumer(std::move(other.mConsumer)),
            mDataFlowId(other.mDataFlowId),
            mSourceId(other.mSourceId) {}

    std::variant<UntypedConsumer, VariableDataConsumer>& getConsumer() {
        return mConsumer;
    }

    DataFlowId getDataFlowId() {
        return mDataFlowId;
    }

    EndpointId getSourceId() {
        return mSourceId;
    }

    ~SinkWrapper() {
        if (mConsumer.index() == 0) {
            std::get<UntypedConsumer>(mConsumer).disable();
        } else {
            std::get<VariableDataConsumer>(mConsumer).disable();
        }
    }

private:
    std::variant<UntypedConsumer, VariableDataConsumer> mConsumer;
    DataFlowId mDataFlowId;
    EndpointId mSourceId;
};

class HubEndpointResource {
public:
    class EpollWaiter : public NotificationManager::EpollWaiter {
    public:
        EpollWaiter(HubEndpointResource* resource) : mResource(resource) {}
        void addFd(int fd) override {
            ALOGI("EpollWaiter::addFd: fd=%d", fd);
            auto fn = [](int fd, int events, void* data) -> int {
                ALOGI("Looper cb called: fd=%d events=%d data=%p", fd, events, data);
                static_cast<EpollWaiter*>(data)->handleNotification(fd, /* error= */ false);
                return 1;
            };
            mResource->mMessageQueue->getLooper()->addFd(fd, fd, ALOOPER_EVENT_INPUT, fn,
                                                         /* data= */ this);
        }

        void removeFd(int fd) override {
            ALOGI("EpollWaiter::removeFd: fd=%d", fd);
            mResource->mMessageQueue->getLooper()->removeFd(fd);
        }

    private:
        HubEndpointResource* mResource;
    };

    HubEndpointResource(sp<MessageQueue> messageQueue, jobject callbackObject, jlong hubId,
                        jlong endpointId)
          : mMessageQueue(messageQueue),
            mCallbackObject(callbackObject),
            mEndpointId({.id = endpointId, .hubId = hubId}) {
        auto waiter = std::make_unique<EpollWaiter>(this);
        auto notificationCb = [this](DataFlowId id, bool waking) {
            ALOGI("NotificationCallback: hub id=0x%" PRIx64 " id=%" PRIu32 " waking=%d", id.hubId,
                  id.id, waking);
            JNIEnv* env = GetOrAttachJNIEnvironment(gVm, JNI_VERSION_1_6);
            if (env == nullptr) {
                ALOGE("Failed to get JNIEnv");
                return;
            }

            // TODO(b/460528144): Cache the jclass/jmethodId
            jclass callbackClass = env->GetObjectClass(mCallbackObject);
            jmethodID methodId =
                    env->GetMethodID(callbackClass, "onNotificationCallback", "(JIZ)V");
            env->CallVoidMethod(mCallbackObject, methodId, id.hubId, id.id, waking);
            env->DeleteLocalRef(callbackClass);
        };
        mNotificationManager = NotificationManager::create(std::move(waiter), notificationCb);
    }

    ~HubEndpointResource() {
        for (auto& [regionId, sourceWrapper] : mRegionIdToSource) {
            removeSourceWrapper(regionId, &sourceWrapper);
        }

        for (auto& [dataFlowId, sinkWrapper] : mDataFlowIdToSink) {
            removeSinkWrapper(dataFlowId, sinkWrapper.get());
        }
    }

    sp<MessageQueue> getMessageQueue() {
        return mMessageQueue;
    }

    jobject getCallbackObject() {
        return mCallbackObject;
    }

    EndpointId getEndpointId() {
        return mEndpointId;
    }

    long getHubId() {
        return mEndpointId.hubId;
    }

    RegionManager* getRegionManager() {
        return &mRegionManager;
    }

    NotificationManager* getNotificationManager() {
        return mNotificationManager.get();
    }

    DataNotifier& getNotifier() {
        return mNotifier;
    }

    SourceWrapper* getSourceWrapper(int regionId) {
        auto it = mRegionIdToSource.find(regionId);
        if (it == mRegionIdToSource.end()) {
            return nullptr;
        }
        return &it->second;
    }

    void removeSourceWrapper(int regionId, SourceWrapper* sourceWrapper = nullptr,
                             bool doErase = false) {
        if (sourceWrapper == nullptr) {
            sourceWrapper = getSourceWrapper(regionId);
            if (sourceWrapper == nullptr) {
                ALOGE("removeSourceWrapper: sourceWrapper is null, regionId=%d", regionId);
                return;
            }
        }

        auto dataFlowId = sourceWrapper->getDataFlowId();
        size_t regionRefCnt = 0;
        if (dataFlowId.has_value()) {
            pw::Status status =
                    mNotificationManager->removeHostProducerDataFlow(dataFlowId.value().id);
            if (!status.ok()) {
                ALOGE("removeHostProducerDataFlow: status=%s", status.str());
            }
            // Destroy the producer to ensure that any resources are released (including sink
            // descriptors) before any shared memory region allocators are destroyed and the regions
            // are unmapped.
            // NOTE: We could ideally call stop() on the producer, then wait for the consumers to
            // gracefully exist, however that is not strictly necessary.
            sourceWrapper->reset();
            auto result = mRegionManager.unlinkHostProducerDataFlow(dataFlowId.value().id);
            if (!result.ok()) {
                ALOGE("unlinkHostProducerDataFlow: status=%s", status.str());
            }
            regionRefCnt = result.value();
        }

        if (doErase) {
            mRegionIdToSource.erase(regionId);
        }

        if (regionRefCnt == 0) {
            pw::Status status = mRegionManager.unmapHostProducerRegion(regionId);
            if (!status.ok()) {
                ALOGE("unmapHostProducerRegion: regionId=%d, status=%s", regionId, status.str());
            }
        }
    }

    void addSourceWrapper(int regionId, SourceWrapper&& sourceWrapper) {
        mRegionIdToSource.emplace(regionId, std::move(sourceWrapper));
    }

    SinkWrapper* getSinkWrapperFromDataFlowId(DataFlowId dataFlowId) {
        auto it = mDataFlowIdToSink.find(dataFlowId);
        if (it == mDataFlowIdToSink.end()) {
            return nullptr;
        }
        return it->second.get();
    }

    SinkWrapper* getSinkWrapperFromSourceId(EndpointId sourceId) {
        auto it = mSourceIdToSink.find(sourceId);
        if (it == mSourceIdToSink.end()) {
            return nullptr;
        }
        return it->second;
    }

    void removeSinkWrapper(DataFlowId dataFlowId, SinkWrapper* sinkWrapper = nullptr,
                           bool doErase = false) {
        if (sinkWrapper == nullptr) {
            sinkWrapper = getSinkWrapperFromDataFlowId(dataFlowId);
            if (sinkWrapper == nullptr) {
                ALOGE("removeSinkWrapper: sinkWrapper is null, dataFlowHubId=%" PRId64
                      ", dataFlowId=%d",
                      dataFlowId.hubId, dataFlowId.id);
                return;
            }
        }

        pw::Status status = mNotificationManager->disableHostConsumer(dataFlowId);
        if (!status.ok()) {
            ALOGE("disableHostConsumer: dataFlowId.hubId=%" PRId64 ", dataFlowId.id=%d, status=%s",
                  dataFlowId.hubId, dataFlowId.id, status.str());
        }

        // Disable the consumer instance to ensure that it doesn't access shared memory after it
        // is unmapped.
        std::visit([](auto& consumer) { consumer.disable(); }, sinkWrapper->getConsumer());

        if (doErase) {
            // NOTE: This points into mDataFlowIdToSink, so erase it first.
            mSourceIdToSink.erase(sinkWrapper->getSourceId());
            mDataFlowIdToSink.erase(dataFlowId);
        }

        status = mRegionManager.unlinkHostConsumerDataFlow(dataFlowId);
        if (!status.ok()) {
            ALOGE("unlinkHostConsumerDataFlow: dataFlowId.hubId=%" PRId64
                  ", dataFlowId.id=%d, status=%s",
                  dataFlowId.hubId, dataFlowId.id, status.str());
        }
    }

    void addSinkWrapper(DataFlowId dataFlowId, EndpointId sourceId,
                        std::unique_ptr<SinkWrapper> sinkWrapper) {
        auto sinkPtr = sinkWrapper.get();
        mDataFlowIdToSink.emplace(dataFlowId, std::move(sinkWrapper));
        mSourceIdToSink.emplace(sourceId, sinkPtr);
    }

    void notifyOffloadConsumer(const RemoteEndpointId& id) {
        EndpointId endpointId{.id = id.aidlId.endpointId, .hubId = id.aidlId.hubId};
        auto status = mNotificationManager->notifyOffloadConsumer(endpointId,
                                                                  /* waking= */ true);
        if (!status.ok()) {
            ALOGE("notifyOffloadConsumer: status=%d", status.code());
        }
    }

    void notifyOffloadProducer(const RemoteEndpointId& id) {
        EndpointId endpointId{.id = id.aidlId.endpointId, .hubId = id.aidlId.hubId};
        auto* sinkWrapper = getSinkWrapperFromSourceId(endpointId);
        if (sinkWrapper == nullptr) {
            ALOGW("notifyOffloadProducer: couldn't find sink wrapper");
            return;
        }
        auto status = mNotificationManager->notifyOffloadProducer(sinkWrapper->getDataFlowId(),
                                                                  /* waking= */ true);
        if (!status.ok()) {
            ALOGE("notifyOffloadProducer: status=%d", status.code());
        }
    }

private:
    sp<MessageQueue> mMessageQueue;
    jobject mCallbackObject;
    EndpointId mEndpointId;
    RegionManager mRegionManager;
    std::shared_ptr<NotificationManager> mNotificationManager;
    DataNotifier mNotifier;

    std::map<int, SourceWrapper> mRegionIdToSource;
    std::map<DataFlowId, std::unique_ptr<SinkWrapper>> mDataFlowIdToSink;
    // NOTE: This points into mDataFlowIdToSink.
    std::map<EndpointId, SinkWrapper*> mSourceIdToSink;
};

static jlong android_hardware_HubEndpoint_init(JNIEnv* env, jobject /* thiz */, jobject queueObject,
                                               jobject callbackObject, jlong hubId,
                                               jlong endpointId) {
    HubEndpointResource* resource =
            new HubEndpointResource(android_os_MessageQueue_getMessageQueue(env, queueObject),
                                    env->NewGlobalRef(callbackObject), hubId, endpointId);
    return reinterpret_cast<jlong>(resource);
}

static jintArray android_hardware_HubEndpoint_createDataFlowInfo(JNIEnv* env, jobject /* thiz */,
                                                                 jlong handle, jint regionId,
                                                                 jlong regionSize, jint regionFd,
                                                                 jint elementSize, jint alignment,
                                                                 jint minElementCount,
                                                                 jint maxElementCount) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, nullptr, "Invalid handle");

    SharedDataRegion region = {};
    region.id = regionId;
    // Since allocations will likely use full pages, round up to the nearest page size.
    region.sizeBytes = roundUpToMultiple(regionSize, getpagesize());
    region.sharedMemory = ndk::ScopedFileDescriptor(dup(regionFd));
    auto alloc = resource->getRegionManager()->mapHostProducerRegion(std::move(region));
    RETURN_ON_FALSE(alloc.ok(), nullptr,
                    (std::string("Failed to map producer region: ") + alloc.status().str())
                            .c_str());

    constexpr size_t kBlockCapacityInBytes = 1024;
    EndpointId endpointId = resource->getEndpointId();
    RemoteNotifyArgs args = {.fn = pw::bind_member<&HubEndpointResource::notifyOffloadConsumer>(
                                     resource),
                             .id = {.aidlId = {.hubId = endpointId.hubId,
                                               .endpointId = endpointId.id}}};

    std::optional<std::variant<UntypedProducer, VariableDataProducer>> producer = std::nullopt;
    size_t queueOffset;
    if (fmcq_support_variable_sized_data_flow_fix()) {
        bool isFixedSize = elementSize > 0;
        std::optional<std::pair<size_t, size_t>> fixedSizeConfig = std::nullopt;
        size_t minBlockCount = 1;
        size_t maxBlockCount = kBlockCapacityInBytes;
        if (isFixedSize) {
            fixedSizeConfig = std::make_pair(static_cast<size_t>(elementSize),
                                             static_cast<size_t>(alignment));
            size_t minElementSize = static_cast<size_t>(elementSize) * minElementCount;
            size_t maxElementSize = static_cast<size_t>(elementSize) * maxElementCount;
            minBlockCount = 1 + (minElementSize - 1) / kBlockCapacityInBytes;
            maxBlockCount = 1 + (maxElementSize - 1) / kBlockCapacityInBytes;
        }

        if (isFixedSize) {
            pw::Result<UntypedProducer> producerRes =
                    UntypedProducer::createRemote(alloc.value(),
                                                  kBlockCapacityInBytes /
                                                          static_cast<size_t>(elementSize),
                                                  static_cast<size_t>(elementSize),
                                                  static_cast<size_t>(alignment), maxBlockCount,
                                                  minBlockCount, resource->getNotifier(),
                                                  std::move(args));
            RETURN_ON_FALSE(producerRes.ok(), nullptr,
                            (std::string("Failed to create fixed size producer: ") +
                             producerRes.status().str())
                                    .c_str());
            producer = std::move(*producerRes);
        } else {
            pw::Result<VariableDataProducer> producerRes =
                    VariableDataProducer::createRemote(alloc.value(), kBlockCapacityInBytes,
                                                       maxBlockCount, minBlockCount,
                                                       resource->getNotifier(), std::move(args));
            RETURN_ON_FALSE(producerRes.ok(), nullptr,
                            (std::string("Failed to create variable size producer: ") +
                             producerRes.status().str())
                                    .c_str());
            producer = std::move(*producerRes);
        }

        if (producer->index() == 0) {
            queueOffset = std::get<UntypedProducer>(*producer).getQueueOffset();
        } else {
            queueOffset = std::get<VariableDataProducer>(*producer).getQueueOffset();
        }
    } else {
        size_t minElementSize = static_cast<size_t>(elementSize) * minElementCount;
        size_t maxElementSize = static_cast<size_t>(elementSize) * maxElementCount;
        // The min block count >= 1, and maxBlockCount >= minBlockCount
        size_t minBlockCount = 1 + (minElementSize - 1) / kBlockCapacityInBytes;
        size_t maxBlockCount = 1 + (maxElementSize - 1) / kBlockCapacityInBytes;
        auto producerRes = UntypedProducer::createRemote(alloc.value(),
                                                         kBlockCapacityInBytes /
                                                                 static_cast<size_t>(elementSize),
                                                         static_cast<size_t>(elementSize),
                                                         static_cast<size_t>(alignment),
                                                         maxBlockCount, minBlockCount,
                                                         resource->getNotifier(), std::move(args));
        RETURN_ON_FALSE(producerRes.ok(), nullptr,
                        (std::string("Failed to create producer: ") + producerRes.status().str())
                                .c_str());
        queueOffset = producerRes->getQueueOffset();
        producer = std::move(producerRes.value());
    }

    auto flow = resource->getNotificationManager()->prepareHostProducerDataFlowInfo();
    RETURN_ON_FALSE(flow.ok(), nullptr,
                    (std::string("Failed to prepare host producer data flow: ") +
                     flow.status().str())
                            .c_str());
    auto [dataFlowInfo, notificationDataHandle] = std::move(flow.value());
    dataFlowInfo.region.id = regionId;
    dataFlowInfo.metadataOffsetBytes = queueOffset;

    std::vector<jint> out;
    out.push_back(dataFlowInfo.metadataOffsetBytes);
    out.push_back(dup(dataFlowInfo.alertFds.waking.get()));
    out.push_back(dup(dataFlowInfo.alertFds.nonWaking.get()));
    out.push_back(dup(dataFlowInfo.alertFds.halAck.get()));
    jintArray javaArray = env->NewIntArray(out.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create int array");
    env->SetIntArrayRegion(javaArray, 0, out.size(), out.data());

    resource->addSourceWrapper(regionId,
                               SourceWrapper(std::move(*producer), alloc.value(),
                                             notificationDataHandle));

    return javaArray;
}

static jboolean android_hardware_HubEndpoint_activateDataFlow(JNIEnv* env, jobject thiz,
                                                              jlong handle, jint dataFlowId,
                                                              jint regionId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, JNI_FALSE, "Source not found for regionId");

    auto status =
            resource->getNotificationManager()
                    ->activateHostProducerDataFlow(dataFlowId,
                                                   sourceWrapper->getNotificationDataHandle());
    RETURN_ON_FALSE(status.ok(), JNI_FALSE,
                    (std::string("Failed to activate host producer data flow: ") + status.str())
                            .c_str());
    status = resource->getRegionManager()->linkHostProducerDataFlowToRegion(regionId, dataFlowId);
    RETURN_ON_FALSE(status.ok(), JNI_FALSE,
                    (std::string("Failed to link host producer data flow to region: ") +
                     status.str())
                            .c_str());

    sourceWrapper->setDataFlowId({
            .hubId = resource->getHubId(),
            .id = static_cast<int32_t>(dataFlowId),
    });

    return JNI_TRUE; // Success
}

static jintArray android_hardware_HubEndpoint_enableHostSink(
        JNIEnv* env, jobject /* thiz */, jlong handle, jint regionId, jlong regionSize,
        jint regionFd, jlong dataFlowHubId, jint dataFlowId, jlong sourceId,
        jint notifyHostFdsWaking, jint notifyHostFdsNonWaking, jint notifyHostFdsHalAck,
        jint notifyOffloadFdsWaking, jint notifyOffloadFdsNonWaking, jlong queueOffset,
        jlong metadataOffset) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, nullptr, "Invalid handle");
    RegionManager::RegionToMap regionToMap = {
            .id = regionId,
            .fd = ndk::ScopedFileDescriptor(dup(regionFd)),
            .size = static_cast<size_t>(regionSize),
    };

    DataFlowId id = {};
    id.hubId = dataFlowHubId;
    id.id = dataFlowId;
    auto result = resource->getRegionManager()->mapHostConsumerRegions(std::move(regionToMap),
                                                                       std::nullopt, id);
    RETURN_ON_FALSE(result.ok(), nullptr,
                    (std::string("Failed to map sink region: ") + result.status().str()).c_str());
    auto [region, _] = std::move(result.value());

    DataFlowAlertFds notifyHostFds = {.waking = ndk::ScopedFileDescriptor(dup(notifyHostFdsWaking)),
                                      .nonWaking = ndk::ScopedFileDescriptor(
                                              dup(notifyHostFdsNonWaking)),
                                      .halAck =
                                              ndk::ScopedFileDescriptor(dup(notifyHostFdsHalAck))};
    DataFlowAlertFds notifyOffloadFds = {.waking = ndk::ScopedFileDescriptor(
                                                 dup(notifyOffloadFdsWaking)),
                                         .nonWaking = ndk::ScopedFileDescriptor(
                                                 dup(notifyOffloadFdsNonWaking))};
    auto status = resource->getNotificationManager()
                          ->enableHostConsumerFromEventFds(id, std::move(notifyHostFds),
                                                           std::move(notifyOffloadFds));
    RETURN_ON_FALSE(status.ok(), nullptr,
                    (std::string("Failed to enable host sink: ") + status.str()).c_str());

    EndpointId endpointId = resource->getEndpointId();
    RemoteNotifyArgs args = {.fn = pw::bind_member<&HubEndpointResource::notifyOffloadProducer>(
                                     resource),
                             .id = {.aidlId = {.hubId = endpointId.hubId,
                                               .endpointId = endpointId.id}}};

    std::optional<std::variant<UntypedConsumer, VariableDataConsumer>> consumer = std::nullopt;
    std::vector<jint> out;
    if (fmcq_support_variable_sized_data_flow_fix()) {
        pw::Result<std::variant<UntypedConsumer, VariableDataConsumer>> consumerRes =
                createRemoteConsumer(region, /* descRegion= */ std::nullopt, queueOffset,
                                     metadataOffset, std::move(args));
        RETURN_ON_FALSE(consumerRes.ok(), nullptr,
                        (std::string("Failed to create sink: ") + consumerRes.status().str())
                                .c_str());
        consumer = std::move(consumerRes.value());

        if (consumer->index() == 0) {
            out.push_back(std::get<UntypedConsumer>(*consumer).getElementSize());
            out.push_back(std::get<UntypedConsumer>(*consumer).getElementAlignment());
        } else {
            out.push_back(-1); // element size
            out.push_back(1);  // element alignment
        }
    } else {
        auto consumerRes = UntypedConsumer::createRemote(region, std::nullopt, queueOffset,
                                                         metadataOffset, std::move(args));
        RETURN_ON_FALSE(consumerRes.ok(), nullptr,
                        (std::string("Failed to create sink: ") + consumerRes.status().str())
                                .c_str());

        out.push_back(consumerRes->getElementSize());
        out.push_back(consumerRes->getElementAlignment());
        consumer = std::move(consumerRes.value());
    }

    jintArray javaArray = env->NewIntArray(out.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create int array");
    env->SetIntArrayRegion(javaArray, 0, out.size(), out.data());

    EndpointId sourceEndpointId{.id = sourceId, .hubId = dataFlowHubId};
    resource->addSinkWrapper(id, sourceEndpointId,
                             std::make_unique<SinkWrapper>(std::move(*consumer), id,
                                                           sourceEndpointId));

    return javaArray;
}

static jint android_hardware_HubEndpoint_sourcePush(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                    jint regionId, jbyteArray data,
                                                    jboolean allOrNothing) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, JNI_FALSE, "Source not found for regionId");

    std::variant<UntypedProducer, VariableDataProducer>& producer = sourceWrapper->getProducer();

    jbyte* dataPtr = env->GetByteArrayElements(data, nullptr);
    RETURN_ON_FALSE(dataPtr != nullptr, 0, "Failed to get byte array elements");

    pw::ConstByteSpan byteSpan(reinterpret_cast<const std::byte*>(dataPtr),
                               env->GetArrayLength(data));
    size_t numElementsPushed = 0;
    if (producer.index() == 0) {
        pw::Result<size_t> pushResult =
                std::get<UntypedProducer>(producer).push(byteSpan, allOrNothing);
        env->ReleaseByteArrayElements(data, dataPtr, JNI_ABORT);
        RETURN_ON_FALSE(pushResult.ok(), 0,
                        (std::string("Failed to push data to queue: ") + pushResult.status().str())
                                .c_str());
        numElementsPushed = pushResult.value();
    } else if (fmcq_support_variable_sized_data_flow_fix()) {
        pw::Status status = std::get<VariableDataProducer>(producer).push(byteSpan);
        env->ReleaseByteArrayElements(data, dataPtr, JNI_ABORT);
        RETURN_ON_FALSE(status.ok(), 0,
                        (std::string("Failed to push variable data to queue: ") + status.str())
                                .c_str());
        numElementsPushed = 1;
    } else {
        return 0;
    }

    return static_cast<jint>(numElementsPushed);
}

static jboolean android_hardware_HubEndpoint_sourceFull(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                        jint regionId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, JNI_FALSE, "Source not found for regionId");

    std::variant<UntypedProducer, VariableDataProducer>& producer = sourceWrapper->getProducer();
    if (producer.index() == 0) {
        return static_cast<jboolean>(std::get<UntypedProducer>(producer).full());
    } else if (fmcq_support_variable_sized_data_flow_fix()) {
        return static_cast<jboolean>(std::get<VariableDataProducer>(producer).full());
    } else {
        return JNI_FALSE;
    }
}

static jint android_hardware_HubEndpoint_sourceSize(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                    jint regionId, jboolean includeReserved) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, 0, "Source not found for regionId");

    std::variant<UntypedProducer, VariableDataProducer>& producer = sourceWrapper->getProducer();
    if (producer.index() == 0) {
        return static_cast<jint>(std::get<UntypedProducer>(producer).size(includeReserved));
    } else if (fmcq_support_variable_sized_data_flow_fix()) {
        return static_cast<jint>(std::get<VariableDataProducer>(producer).size(includeReserved));
    } else {
        return 0;
    }
}

static jbyteArray android_hardware_HubEndpoint_sinkRequestData(JNIEnv* env, jobject /*thiz*/,
                                                               jlong handle, jlong dataFlowHubId,
                                                               jint dataFlowId, jint elementCount,
                                                               jboolean allOrNothing) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, nullptr, "Invalid handle");

    SinkWrapper* sinkWrapper =
            resource->getSinkWrapperFromDataFlowId({.hubId = dataFlowHubId, .id = dataFlowId});
    RETURN_ON_FALSE(sinkWrapper != nullptr, nullptr, "Sink not found for dataFlowId");

    std::variant<UntypedConsumer, VariableDataConsumer>& consumer = sinkWrapper->getConsumer();
    size_t headSize = 0;
    if (allOrNothing) {
        if (consumer.index() == 0) {
            pw::Result<size_t> sizeResult = std::get<UntypedConsumer>(consumer).size();
            RETURN_ON_FALSE(sizeResult.ok(), nullptr,
                            (std::string("Failed to get sink size: ") + sizeResult.status().str())
                                    .c_str());
            if (elementCount > static_cast<jint>(sizeResult.value())) {
                return nullptr;
            }
        } else if (fmcq_support_variable_sized_data_flow_fix()) {
            pw::Result<size_t> headSizeResult =
                    std::get<VariableDataConsumer>(consumer).getHeadSize();
            RETURN_ON_FALSE(headSizeResult.ok(), nullptr,
                            (std::string("Failed to get sink head element size: ") +
                             headSizeResult.status().str())
                                    .c_str());
            headSize = headSizeResult.value();
            if (elementCount > static_cast<jint>(headSizeResult.value())) {
                return nullptr;
            }
        } else {
            return nullptr;
        }
    }

    std::vector<std::byte> output;
    if (consumer.index() == 0) {
        output.resize(elementCount * std::get<UntypedConsumer>(consumer).getElementSize());
    } else if (fmcq_support_variable_sized_data_flow_fix()) {
        output.resize(headSize);
    } else {
        return nullptr;
    }

    pw::ByteSpan outputSpan(output.data(), output.size());
    pw::Status popStatus;
    if (consumer.index() == 0) {
        popStatus = std::get<UntypedConsumer>(consumer).pop(outputSpan);
    } else if (fmcq_support_variable_sized_data_flow_fix()) {
        popStatus = std::get<VariableDataConsumer>(consumer).pop(outputSpan);
    } else {
        return nullptr;
    }

    RETURN_ON_FALSE(popStatus.ok(), nullptr,
                    (std::string("Failed to pop data from queue: ") + popStatus.str()).c_str());

    jbyteArray javaArray = env->NewByteArray(outputSpan.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create byte array");

    env->SetByteArrayRegion(javaArray, 0, outputSpan.size(),
                            reinterpret_cast<const jbyte*>(outputSpan.data()));
    return javaArray;
}

static jboolean android_hardware_HubEndpoint_sinkSyncToSource(JNIEnv* env, jobject /*thiz*/,
                                                              jlong handle, jlong dataFlowHubId,
                                                              jint dataFlowId, jint offset) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    SinkWrapper* sinkWrapper =
            resource->getSinkWrapperFromDataFlowId({.hubId = dataFlowHubId, .id = dataFlowId});
    RETURN_ON_FALSE(sinkWrapper != nullptr, JNI_FALSE, "Sink not found for dataFlowId");

    std::variant<UntypedConsumer, VariableDataConsumer>& consumer = sinkWrapper->getConsumer();
    pw::Status syncStatus;
    if (consumer.index() == 0) {
        syncStatus = std::get<UntypedConsumer>(consumer).resync(offset);
    } else if (fmcq_support_variable_sized_data_flow_fix()) {
        syncStatus = std::get<VariableDataConsumer>(consumer).resync(offset);
    } else {
        return JNI_FALSE;
    }

    RETURN_ON_FALSE(syncStatus.ok(), JNI_FALSE,
                    (std::string("Failed to resync sink: ") + syncStatus.str()).c_str());
    return JNI_TRUE;
}

static jboolean android_hardware_HubEndpoint_sinkSourceCanOverwriteReadPosition(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jlong dataFlowHubId, jint dataFlowId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, JNI_FALSE, "Invalid handle");

    SinkWrapper* sinkWrapper =
            resource->getSinkWrapperFromDataFlowId({.hubId = dataFlowHubId, .id = dataFlowId});
    RETURN_ON_FALSE(sinkWrapper != nullptr, JNI_FALSE, "Sink not found for dataFlowId");

    std::variant<UntypedConsumer, VariableDataConsumer>& consumer = sinkWrapper->getConsumer();
    pw::Result<bool> overwritableResult;
    if (consumer.index() == 1) {
        overwritableResult = std::get<UntypedConsumer>(consumer).isOverwritable();
    } else if (fmcq_support_variable_sized_data_flow_fix()) {
        overwritableResult = std::get<VariableDataConsumer>(consumer).isOverwritable();
    } else {
        return JNI_FALSE;
    }

    RETURN_ON_FALSE(overwritableResult.ok(), JNI_FALSE,
                    (std::string("Failed to get sink overwritable: ") +
                     overwritableResult.status().str())
                            .c_str());
    return static_cast<jboolean>(overwritableResult.value());
}

static jint android_hardware_HubEndpoint_sinkSize(JNIEnv* env, jobject /*thiz*/, jlong handle,
                                                  jlong dataFlowHubId, jint dataFlowId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    SinkWrapper* sinkWrapper =
            resource->getSinkWrapperFromDataFlowId({.hubId = dataFlowHubId, .id = dataFlowId});
    RETURN_ON_FALSE(sinkWrapper != nullptr, 0, "Sink not found for dataFlowId");

    std::variant<UntypedConsumer, VariableDataConsumer>& consumer = sinkWrapper->getConsumer();
    pw::Result<size_t> sizeResult;
    if (consumer.index() == 0) {
        sizeResult = std::get<UntypedConsumer>(consumer).size();
    } else if (fmcq_support_variable_sized_data_flow_fix()) {
        sizeResult = std::get<VariableDataConsumer>(consumer).size();
    } else {
        return 0;
    }

    RETURN_ON_FALSE(sizeResult.ok(), 0,
                    (std::string("Failed to get sink size: ") + sizeResult.status().str()).c_str());
    return static_cast<jint>(sizeResult.value());
}

static jintArray android_hardware_HubEndpoint_addOffloadSink(JNIEnv* env, jobject /*thiz*/,
                                                             jlong handle, jint regionId,
                                                             jlong hubId, jlong endpointId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, nullptr, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, nullptr, "Source not found for regionId");
    auto dataFlowId = sourceWrapper->getDataFlowId();
    RETURN_ON_FALSE(dataFlowId.has_value(), nullptr, "Data flow ID not set for source");

    EndpointId sinkEndpointId;
    sinkEndpointId.hubId = hubId;
    sinkEndpointId.id = endpointId;
    auto result = resource->getNotificationManager()
                          ->addOffloadConsumerAndGetEventFds(dataFlowId.value().id, sinkEndpointId);
    RETURN_ON_FALSE(result.ok(), nullptr,
                    (std::string("Failed to add offload sink: ") + result.status().str()).c_str());

    std::vector<jint> out;
    out.push_back(dup(result->waking.get()));
    out.push_back(dup(result->nonWaking.get()));
    jintArray javaArray = env->NewIntArray(out.size());
    RETURN_ON_FALSE(javaArray != nullptr, nullptr, "Failed to create int array");
    env->SetIntArrayRegion(javaArray, 0, out.size(), out.data());

    sourceWrapper->getOffloadSinks().insert(sinkEndpointId);

    return javaArray;
}

static ConsumerPolicyBuilder createConsumerPolicyBuilder(jint notificationPolicy,
                                                         jint notificationPolicyData,
                                                         jboolean canOverwrite) {
    ConsumerPolicyBuilder policy;
    switch (static_cast<NotificationPolicy>(notificationPolicy)) {
        case NotificationPolicy::kNever:
            policy.setNeverNotify();
            break;
        case NotificationPolicy::kOpportunistic:
            policy.setOpportunistic(notificationPolicyData);
            break;
        case NotificationPolicy::kHighWaterMark:
            policy.setHighWaterMark(notificationPolicyData);
            break;
        case NotificationPolicy::kPeriodic:
            policy.setPeriodic(notificationPolicyData);
            break;
        case NotificationPolicy::kStreaming:
            policy.setStreaming();
            break;
        default:
            ALOGE("Invalid notification policy: %d", notificationPolicy);
            break;
    }
    if (canOverwrite) {
        policy.setOverwritable();
    }
    return policy;
}

static jint android_hardware_HubEndpoint_mapOffloadSinkRegion(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint sourceRegionId, jint dataFlowId,
        jlong sinkHubId, jlong sinkEndpointId, jint regionId, jlong regionSize, jint regionFd,
        jint notificationPolicy, jint notificationPolicyData, jboolean canOverwrite) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(sourceRegionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, 0, "Source not found for regionId");

    SharedDataRegion region = {.id = regionId,
                               .sharedMemory = ndk::ScopedFileDescriptor(dup(regionFd)),
                               .sizeBytes = regionSize};
    EndpointId id = {.id = sinkEndpointId, .hubId = sinkHubId};
    auto result = resource->getRegionManager()->mapOffloadConsumerRegion(std::move(region), id,
                                                                         dataFlowId);

    RETURN_ON_FALSE(result.ok(), 0,
                    (std::string("Failed to map offload sink region: ") + result.status().str())
                            .c_str());

    RemoteEndpointId sinkId = {.aidlId = {.hubId = sinkHubId, .endpointId = sinkEndpointId}};
    ConsumerPolicyBuilder policy =
            createConsumerPolicyBuilder(notificationPolicy, notificationPolicyData, canOverwrite);

    std::variant<UntypedProducer, VariableDataProducer>& producer = sourceWrapper->getProducer();
    pw::Result<uint32_t> consDescOffsetRes;
    if (producer.index() == 0) {
        ConsumerManager consumerManager = std::get<UntypedProducer>(producer).getConsumerManager();
        consDescOffsetRes = consumerManager.addConsumer(sinkId, policy, &result.value());
    } else if (fmcq_support_variable_sized_data_flow_fix()) {
        ConsumerManager consumerManager =
                std::get<VariableDataProducer>(producer).getConsumerManager();
        consDescOffsetRes = consumerManager.addConsumer(sinkId, policy, &result.value());
    } else {
        return 0;
    }

    RETURN_ON_FALSE(consDescOffsetRes.ok(), 0,
                    (std::string("Failed to add sink: ") + consDescOffsetRes.status().str())
                            .c_str());

    return consDescOffsetRes.value();
}

static jint android_hardware_HubEndpoint_addOffloadSinkInPrimaryRegion(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint sourceRegionId, jlong sinkHubId,
        jlong sinkEndpointId, jint notificationPolicy, jint notificationPolicyData,
        jboolean canOverwrite) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    RETURN_ON_FALSE(resource != nullptr, 0, "Invalid handle");

    SourceWrapper* sourceWrapper = resource->getSourceWrapper(sourceRegionId);
    RETURN_ON_FALSE(sourceWrapper != nullptr, 0, "Source not found for regionId");

    RemoteEndpointId sinkId = {.aidlId = {.hubId = sinkHubId, .endpointId = sinkEndpointId}};
    ConsumerPolicyBuilder policy =
            createConsumerPolicyBuilder(notificationPolicy, notificationPolicyData, canOverwrite);

    std::variant<UntypedProducer, VariableDataProducer>& producer = sourceWrapper->getProducer();
    pw::Result<uint32_t> consDescOffsetRes;
    if (producer.index() == 0) {
        ConsumerManager consumerManager = std::get<UntypedProducer>(producer).getConsumerManager();
        consDescOffsetRes = consumerManager.addConsumer(sinkId, policy);
    } else if (fmcq_support_variable_sized_data_flow_fix()) {
        ConsumerManager consumerManager =
                std::get<VariableDataProducer>(producer).getConsumerManager();
        consDescOffsetRes = consumerManager.addConsumer(sinkId, policy);
    } else {
        return 0;
    }

    RETURN_ON_FALSE(consDescOffsetRes.ok(), 0,
                    (std::string("Failed to add sink: ") + consDescOffsetRes.status().str())
                            .c_str());

    return consDescOffsetRes.value();
}

static void android_hardware_HubEndpoint_updateSinkPolicy(JNIEnv* env, jobject /*thiz*/,
                                                          jlong handle, jint regionId,
                                                          jlong sinkHubId, jlong sinkEndpointId,
                                                          jint notificationPolicy,
                                                          jint notificationPolicyData,
                                                          jboolean canOverwrite) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
        if (sourceWrapper != nullptr) {
            RemoteEndpointId sinkId = {
                    .aidlId = {.hubId = sinkHubId, .endpointId = sinkEndpointId}};
            ConsumerPolicyBuilder policy =
                    createConsumerPolicyBuilder(notificationPolicy, notificationPolicyData,
                                                canOverwrite);
            std::variant<UntypedProducer, VariableDataProducer>& producer =
                    sourceWrapper->getProducer();
            if (producer.index() == 0) {
                ConsumerManager consumerManager =
                        std::get<UntypedProducer>(producer).getConsumerManager();
                consumerManager.updateConsumerPolicy(sinkId, policy);
            } else if (fmcq_support_variable_sized_data_flow_fix()) {
                ConsumerManager consumerManager =
                        std::get<VariableDataProducer>(producer).getConsumerManager();
                consumerManager.updateConsumerPolicy(sinkId, policy);
            }
        }
    }
}

static void android_hardware_HubEndpoint_removeOffloadSink(JNIEnv* env, jobject /* thiz */,
                                                           jlong handle, jint regionId,
                                                           jlong sinkHubId, jlong sinkEndpointId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        SourceWrapper* sourceWrapper = resource->getSourceWrapper(regionId);
        if (sourceWrapper != nullptr) {
            EndpointId id = {.id = sinkEndpointId, .hubId = sinkHubId};
            sourceWrapper->removeOffloadSink(id);
        }
    }
}

static void android_hardware_HubEndpoint_removeHostSource(JNIEnv* env, jobject /* thiz */,
                                                          jlong handle, jint regionId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        resource->removeSourceWrapper(regionId, /* sourceWrapper= */ nullptr, /* doErase= */ true);
    }
}

static void android_hardware_HubEndpoint_removeHostSink(JNIEnv* env, jobject /* thiz */,
                                                        jlong handle, jlong dataFlowHubId,
                                                        jint dataFlowId) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        resource->removeSinkWrapper({.hubId = dataFlowHubId, .id = dataFlowId},
                                    /* sinkWrapper= */ nullptr, /* doErase= */ true);
    }
}

static jintArray android_hardware_HubEndpoint_getSharedDataSupportVersion(JNIEnv* env,
                                                                          jclass /* clazz */) {
    std::vector<jint> out;
    out.push_back(android::contexthub::data_flow::kVersion.major);
    out.push_back(android::contexthub::data_flow::kVersion.minor);
    out.push_back(android::contexthub::data_flow::kVersion.patch);
    out.push_back(android::contexthub::data_flow::kMinCompatibleMajorVersion);

    jintArray javaArray = env->NewIntArray(out.size());
    if (javaArray != nullptr) {
        env->SetIntArrayRegion(javaArray, 0, out.size(), out.data());
    }
    return javaArray;
}

static void android_hardware_HubEndpoint_deinit(JNIEnv* env, jobject thiz, jlong handle) {
    HubEndpointResource* resource = reinterpret_cast<HubEndpointResource*>(handle);
    if (resource != nullptr) {
        env->DeleteGlobalRef(resource->getCallbackObject());
        delete resource;
    }
}

static const JNINativeMethod method_table[] = {
        {"native_getSharedDataSupportVersion", "()[I",
         (void*)android_hardware_HubEndpoint_getSharedDataSupportVersion},
        {"native_init",
         "(Landroid/os/MessageQueue;Landroid/hardware/contexthub/"
         "HubEndpoint$DataFlowJniCallback;JJ)J",
         (void*)android_hardware_HubEndpoint_init},
        {"native_createDataFlowInfo", "(JIJIIIII)[I",
         (void*)android_hardware_HubEndpoint_createDataFlowInfo},
        {"native_activateDataFlow", "(JII)Z", (void*)android_hardware_HubEndpoint_activateDataFlow},
        {"native_enableHostSink", "(JIJIJIJIIIIIJJ)[I",
         (void*)android_hardware_HubEndpoint_enableHostSink},
        {"native_sourcePush", "(JI[BZ)I", (void*)android_hardware_HubEndpoint_sourcePush},
        {"native_sourceFull", "(JI)Z", (void*)android_hardware_HubEndpoint_sourceFull},
        {"native_sourceSize", "(JIZ)I", (void*)android_hardware_HubEndpoint_sourceSize},
        {"native_sinkRequestData", "(JJIIZ)[B",
         (void*)android_hardware_HubEndpoint_sinkRequestData},
        {"native_sinkSyncToSource", "(JJII)Z",
         (void*)android_hardware_HubEndpoint_sinkSyncToSource},
        {"native_sinkSourceCanOverwriteReadPosition", "(JJI)Z",
         (void*)android_hardware_HubEndpoint_sinkSourceCanOverwriteReadPosition},
        {"native_sinkSize", "(JJI)I", (void*)android_hardware_HubEndpoint_sinkSize},
        {"native_addOffloadSink", "(JIJJ)[I", (void*)android_hardware_HubEndpoint_addOffloadSink},
        {"native_mapOffloadSinkRegion", "(JIIJJIJIIIZ)I",
         (void*)android_hardware_HubEndpoint_mapOffloadSinkRegion},
        {"native_addOffloadSinkInPrimaryRegion", "(JIJJIIZ)I",
         (void*)android_hardware_HubEndpoint_addOffloadSinkInPrimaryRegion},
        {"native_updateSinkPolicy", "(JIJJIIZ)V",
         (void*)android_hardware_HubEndpoint_updateSinkPolicy},
        {"native_removeOffloadSink", "(JIJJ)V",
         (void*)android_hardware_HubEndpoint_removeOffloadSink},
        {"native_removeHostSource", "(JI)V", (void*)android_hardware_HubEndpoint_removeHostSource},
        {"native_removeHostSink", "(JJI)V", (void*)android_hardware_HubEndpoint_removeHostSink},
        {"native_deinit", "(J)V", (void*)android_hardware_HubEndpoint_deinit},
};

int register_android_hardware_HubEndpoint(JNIEnv* env) {
    if (env->GetJavaVM(&gVm) != JNI_OK) {
        ALOGE("Failed to get JavaVM from JNIEnv: %p", env);
    }
    return RegisterMethodsOrDie(env, "android/hardware/contexthub/HubEndpoint", method_table,
                                NELEM(method_table));
}
