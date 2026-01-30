/*
 * Copyright (C) 2016 The Android Open Source Project
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

#define LOG_TAG "DropBoxManager"

#include <android/os/DropBoxManager.h>

#include <android-base/unique_fd.h>
#include <binder/IServiceManager.h>
#include <binder/ParcelFileDescriptor.h>
#include <com/android/internal/os/IDropBoxManagerService.h>
#include <cutils/log.h>

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>

namespace android {
namespace os {

using namespace ::com::android::internal::os;

DropBoxManager::DropBoxManager()
{
}

DropBoxManager::~DropBoxManager()
{
}

Status
DropBoxManager::addText(const String16& tag, const string& text)
{
    return addData(tag, reinterpret_cast<uint8_t const*>(text.c_str()), text.size(), IS_TEXT);
}

Status
DropBoxManager::addData(const String16& tag, uint8_t const* data,
        size_t size, int flags)
{
    sp<IDropBoxManagerService> service = interface_cast<IDropBoxManagerService>(
        defaultServiceManager()->getService(android::String16("dropbox")));
    if (service == NULL) {
        return Status::fromExceptionCode(Status::EX_NULL_POINTER, "can't find dropbox service");
    }
    ALOGD("About to call service->add()");
    vector<uint8_t> dataArg;
    dataArg.assign(data, data + size);
    Status status = service->addData(tag, dataArg, flags);
    ALOGD("service->add returned %s", status.toString8().c_str());
    return status;
}

Status
DropBoxManager::addFile(const String16& tag, const string& filename, int flags)
{
    int fd = open(filename.c_str(), O_RDONLY);
    if (fd == -1) {
        string message("addFile can't open file: ");
        message += filename;
        ALOGW("DropboxManager: %s", message.c_str());
        return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE, message.c_str());
    }
    return addFile(tag, fd, flags);
}

Status
DropBoxManager::addFile(const String16& tag, int fd, int flags)
{
    if (fd == -1) {
        string message("invalid fd (-1) passed to to addFile");
        ALOGW("DropboxManager: %s", message.c_str());
        return Status::fromExceptionCode(Status::EX_ILLEGAL_STATE, message.c_str());
    }
    sp<IDropBoxManagerService> service = interface_cast<IDropBoxManagerService>(
        defaultServiceManager()->getService(android::String16("dropbox")));
    if (service == NULL) {
        return Status::fromExceptionCode(Status::EX_NULL_POINTER, "can't find dropbox service");
    }
    ALOGD("About to call service->add()");
    android::base::unique_fd uniqueFd(fd);
    android::os::ParcelFileDescriptor parcelFd(std::move(uniqueFd));
    Status status = service->addFile(tag, parcelFd, flags);
    ALOGD("service->add returned %s", status.toString8().c_str());
    return status;
}

}} // namespace android::os
