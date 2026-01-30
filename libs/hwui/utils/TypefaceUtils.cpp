/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "utils/TypefaceUtils.h"

#include <android-base/properties.h>
#include <log/log.h>

#include "FeatureFlags.h"
#include "Properties.h"
#include "SkStream.h"
#include "SkTypeface_fontations.h"
#include "include/ports/SkFontMgr_empty.h"

namespace android {

namespace {
const char* GetPropName(uirenderer::SkTypefaceBackend prop) {
    switch (prop) {
        case uirenderer::SkTypefaceBackend::kAuto:
            return "Auto";
        case uirenderer::SkTypefaceBackend::kFreeType:
            return "FreeType";
        case uirenderer::SkTypefaceBackend::kFontation:
            return "Fontation";
        default:
            return "Unknown";
    }
}

uirenderer::SkTypefaceBackend GetSkTypefaceBackendProp() {
    using T = std::underlying_type_t<uirenderer::SkTypefaceBackend>;
    return static_cast<uirenderer::SkTypefaceBackend>(base::GetIntProperty<T>(
            PROPERTY_SKTYPEFACE_BACKEND, static_cast<T>(uirenderer::SkTypefaceBackend::kAuto)));
}

bool useFontationSkTypeface() {
    static bool useFontation;
    static std::once_flag once;
    // We don't support runtime switch between FreeType and Fontation. Once we decided, use the same
    // backend forever.
    std::call_once(once, [&]() {
        auto textBackendProp = GetSkTypefaceBackendProp();
        switch (textBackendProp) {
            case uirenderer::SkTypefaceBackend::kFreeType:
                useFontation = false;
                break;
            case uirenderer::SkTypefaceBackend::kFontation:
                useFontation = true;
                break;
            case uirenderer::SkTypefaceBackend::kAuto:
            default:
                useFontation = text_feature::use_fontation_by_default();
                break;
        }
        ALOGI("Using %s backend (prop=%s)", useFontation ? "Fontation" : "FreeType",
              GetPropName(textBackendProp));
    });
    return useFontation;
}

// Return an SkFontMgr which is capable of turning bytes into a SkTypeface using Freetype.
// There are no other fonts inside this SkFontMgr (e.g. no system fonts).
sk_sp<SkFontMgr> FreeTypeFontMgr() {
    static sk_sp<SkFontMgr> mgr = SkFontMgr_New_Custom_Empty();
    return mgr;
}

}  // namespace

sk_sp<SkTypeface> makeSkTypeface(std::unique_ptr<SkStreamAsset> fontData,
                                 const SkFontArguments& args) {
    if (useFontationSkTypeface()) {
        return SkTypeface_Make_Fontations(std::move(fontData), args);
    } else {
        sk_sp<SkFontMgr> fm = android::FreeTypeFontMgr();
        return fm->makeFromStream(std::move(fontData), args);
    }
}

}  // namespace android
