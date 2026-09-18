/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "ColorArea.h"

#include <SkAndroidFrameworkUtils.h>
#include <com_android_graphics_hwui_flags.h>

#include "CanvasTransform.h"
#include "utils/Color.h"
#include "utils/MathUtils.h"

namespace hwui_flags = com::android::graphics::hwui::flags;

namespace android::uirenderer {

constexpr static float kMinimumAlphaToConsiderArea = 200.0f / 255.0f;

inline uint64_t calculateArea(int32_t width, int32_t height) {
    // HWUI doesn't draw anything with negative width or height
    if (width <= 0 || height <= 0) return 0;

    return width * height;
}

void ColorArea::addArea(const SkRect& rect, const SkPaint* paint) {
    addArea(rect.width(), rect.height(), paint);
}

void ColorArea::addArea(int32_t width, int32_t height, const SkPaint* paint) {
    if (!paint) return;
    addArea(calculateArea(width, height), *paint);
}

void ColorArea::addArea(uint64_t area, const SkPaint& paint) {
    if (paint.getStyle() == SkPaint::Style::kStroke_Style) return;
    if (CC_UNLIKELY(paint.nothingToDraw())) return;

    if (paint.getShader()) {
        if (!hwui_flags::edt_gradient_detection()) {
            return;
        }

        GradientLightness result = GradientLightness::Unknown;
        SkAndroidFrameworkUtils::LinearGradientInfo info;
        std::array<SkColor4f, 10> _colorStorage;
        std::array<SkScalar, _colorStorage.size()> _offsetStorage;
        info.fColorCount = _colorStorage.size();
        info.fColors = _colorStorage.data();
        info.fColorOffsets = _offsetStorage.data();

        if (SkAndroidFrameworkUtils::ShaderAsALinearGradient(paint.getShader(), &info) &&
            info.fColorCount <= _colorStorage.size()) {
            result = computeGradient(info);
        }

        switch (result) {
            case GradientLightness::Light:
                addArea(area, Light);
                break;
            case GradientLightness::Dark:
                addArea(area, Dark);
                break;
            case GradientLightness::Unknown:
                // do nothing
                break;
        }

        return;
    }

    addArea(area, paint.getColor4f());
}

ColorArea::GradientLightness ColorArea::computeGradient(
        SkAndroidFrameworkUtils::LinearGradientInfo& info) {
    if (info.fColorCount == 0) {
        return GradientLightness::Unknown;
    }
    if (info.fColorCount == 1) {
        Lab lab = sRGBToLab(info.fColors[0]);
        return lab.L > 50 ? GradientLightness::Light : GradientLightness::Dark;
    }

    // TODO(b/455294182): The current evaluation considers that the gradient is a single item and
    //  we calculate the average lightness of the gradient(assuming the lightness is linear in a
    //  linear gradient). To improve the overall lightness evaluation, we could include "how light"
    //  or "how dark" the slices are in the gradient and the detailed area for them to help us
    //  detect the overall average lightness.
    float averagedLightness = 0;
    for (int i = 0; i < info.fColorCount - 1; i++) {
        Lab labCurrent = sRGBToLab(info.fColors[i]);
        Lab labNext = sRGBToLab(info.fColors[i + 1]);
        averagedLightness += (labCurrent.L + labNext.L) / 2.f *
                             (info.fColorOffsets[i + 1] - info.fColorOffsets[i]);
    }

    if (averagedLightness > 50) {
        return GradientLightness::Light;
    } else {
        return GradientLightness::Dark;
    }
}

void ColorArea::addArea(const SkRect& bounds, const SkPaint& paint,
                        android::BitmapPalette palette) {
    palette = filterPalette(&paint, palette);
    auto area = calculateArea(bounds.width(), bounds.height());
    switch (palette) {
        case android::BitmapPalette::Light:
            addArea(area, Light);
            break;
        case android::BitmapPalette::Dark:
            addArea(area, Dark);
            break;
        case android::BitmapPalette::Colorful:
        case android::BitmapPalette::Barcode:
        case android::BitmapPalette::GrayScale:
        case android::BitmapPalette::Unknown:
            addArea(area, Unknown);
            break;
    }
}

void ColorArea::addArea(uint64_t area, SkColor4f color) {
    if (CC_UNLIKELY(color.fA < kMinimumAlphaToConsiderArea)) return;

    // TODO(b/381930266): optimize by detecting common black/white/grey colors and avoid converting
    //  also maybe cache colors or something?
    Lab lab = sRGBToLab(color);
    // TODO(b/372558459): add a case for a middle L that is grey, and don't count it?
    addArea(area, lab.L > 50 ? Light : Dark);
}

void ColorArea::addArea(uint64_t area, Polarity polarity) {
    // HWUI doesn't draw anything with negative width or height
    if (area <= 0) return;

    if (polarity == Light) {
        mLight += area;
    } else if (polarity == Dark) {
        mDark += area;
    }
}

Polarity ColorArea::getPolarity() const {
    if (mLight == mDark) {  // also covers the case if it was just reset()
        return Polarity::Unknown;
    }
    if (mLight > mDark) {
        return Polarity::Light;
    } else {
        return Polarity::Dark;
    }
}

void ColorArea::reset() {
    mParentHeight = -1;
    mParentWidth = -1;
    mLight = 0;
    mDark = 0;
}

void ColorArea::merge(const ColorArea& source) {
    mLight += source.mLight;
    mDark += source.mDark;
}

int ColorArea::getParentWidth() const {
    return mParentWidth;
}

void ColorArea::setParentWidth(int width) {
    mParentWidth = width;
}

int ColorArea::getParentHeight() const {
    return mParentHeight;
}

void ColorArea::setParentHeight(int height) {
    mParentHeight = height;
}

}  // namespace android::uirenderer
