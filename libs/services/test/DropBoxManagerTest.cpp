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

#include <android-base/logging.h>
#include <android-base/file.h>
#include <android/os/DropBoxManager.h>
#include <binder/Status.h>
#include <gtest/gtest.h>
#include <stdio.h>
#include <utils/String16.h>

#include <filesystem>
#include <fstream>
#include <iostream>
#include <optional>
#include <sstream>

// #define LOG_TAG "DropBoxManagerTestCpp";

const char *DROPBOX_DIR = "/data/system/dropbox";
const char *TEST_TAG = "foo";
const char *TEST_CONTENTS = "bar\nbaz\n";

bool dropbox_addtext(const char *tag, const char *text) {
    android::String16 tag16(tag);
    android::sp<android::os::DropBoxManager> dropbox(new android::os::DropBoxManager());
    android::binder::Status status = dropbox->addText(tag16, text);
    if (!status.isOk()) {
        LOG(ERROR) << "Failed to write " << tag << " to DropBox: " << status.exceptionMessage();
        return false;
    }
    LOG(INFO) << "Sent " << tag << " to DropBox";
    return true;
}

std::optional<std::filesystem::path> find_dropbox_file(bool delete_it) {
    std::optional<std::filesystem::path> found;
    for (const auto &entry : std::filesystem::directory_iterator(DROPBOX_DIR)) {
        if (!entry.is_regular_file()) {
            continue;
        }
        const std::string filename = entry.path().filename().string();
        if (!filename.starts_with(TEST_TAG)) {
            continue;
        }
        found = entry.path();
        if (delete_it) {
            std::filesystem::remove(entry.path());
        } else {
            break;
        }
    }
    return found;
}

TEST(DropBoxManagerTest, AddText) {
    find_dropbox_file(true);
    ASSERT_TRUE(dropbox_addtext(TEST_TAG, TEST_CONTENTS));
    auto path = find_dropbox_file(false);
    ASSERT_TRUE(path.has_value());
    std::string contents;
    android::base::ReadFileToString(path.value(), &contents);
    ASSERT_EQ(TEST_CONTENTS, contents);
}
