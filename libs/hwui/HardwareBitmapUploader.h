/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <SkColorType.h>
#include <SkRefCnt.h>
#include <hwui/Bitmap.h>

#ifdef __ANDROID__
#include <GLES2/gl2.h>
#include <android/hardware_buffer.h>
#include <vulkan/vulkan.h>
#endif

class SkBitmap;

namespace android::uirenderer {

#ifdef __ANDROID__
struct FormatInfo {
    AHardwareBuffer_Format bufferFormat;
    GLint format;
    GLint type;
    VkFormat vkFormat;
    bool isSupported = false;
    bool valid = true;
};
#endif

class HardwareBitmapUploader {
public:
    static void initialize();
    static void terminate();

    static sk_sp<Bitmap> allocateHardwareBitmap(const SkBitmap& sourceBitmap);

#ifdef __ANDROID__
    static bool hasFP16Support();
    static bool has1010102Support();
    static bool has10101010Support();
    static bool hasAlpha8Support();

    static FormatInfo determineFormat(const SkImageInfo& info, bool usingGL = false);
#else
    static bool hasFP16Support() {
        return true;
    }
    static bool has1010102Support() { return true; }
    static bool has10101010Support() { return true; }
    static bool hasAlpha8Support() { return true; }
#endif
};

}  // namespace android::uirenderer
