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

#include "CanvasTransform.h"

#include <SkAndroidFrameworkUtils.h>
#include <SkBlendMode.h>
#include <SkColorFilter.h>
#include <SkGradientShader.h>
#include <SkHighContrastFilter.h>
#include <SkPaint.h>
#include <SkShader.h>
#include <log/log.h>

#include <algorithm>
#include <cmath>

#include "Properties.h"
#include "utils/Color.h"

namespace android::uirenderer {

SkColor4f makeLight(SkColor4f color) {
    Lab lab = sRGBToLab(color);
    float invertedL = std::min(110 - lab.L, 100.0f);
    if (invertedL > lab.L) {
        lab.L = invertedL;
        return LabToSRGB(lab, color.fA);
    } else {
        return color;
    }
}

SkColor4f makeDark(SkColor4f color) {
    Lab lab = sRGBToLab(color);
    float invertedL = std::min(110 - lab.L, 100.0f);
    if (invertedL < lab.L) {
        lab.L = invertedL;
        return LabToSRGB(lab, color.fA);
    } else {
        return color;
    }
}

SkColor4f invert(SkColor4f color) {
    Lab lab = sRGBToLab(color);
    lab.L = 100 - lab.L;
    return LabToSRGB(lab, color.fA);
}

SkColor4f transformColor(ColorTransform transform, SkColor4f color) {
    switch (transform) {
        case ColorTransform::Light:
            return makeLight(color);
        case ColorTransform::Dark:
            return makeDark(color);
        case ColorTransform::Invert:
            return invert(color);
        default:
            return color;
    }
}

SkColor4f transformColorInverse(ColorTransform transform, SkColor4f color) {
    switch (transform) {
        case ColorTransform::Dark:
            return makeLight(color);
        case ColorTransform::Light:
            return makeDark(color);
        default:
            return color;
    }
}

/**
 * Invert's the paint's current color filter by composing it with an inversion filter.
 *
 * Relies on the undocumented behavior that makeComposed() will just return this if inner is null.
 */
static void composeWithInvertedColorFilter(SkPaint& paint) {
    SkHighContrastConfig config;
    config.fInvertStyle = SkHighContrastConfig::InvertStyle::kInvertLightness;
    paint.setColorFilter(SkHighContrastFilter::Make(config)->makeComposed(paint.refColorFilter()));
}

static void applyColorTransform(ColorTransform transform, SkPaint& paint) {
    if (transform == ColorTransform::None) return;

    SkColor4f newColor = transformColor(transform, paint.getColor4f());
    paint.setColor(newColor);

    if (paint.getShader()) {
        SkAndroidFrameworkUtils::LinearGradientInfo info;
        std::array<SkColor4f, 10> _colorStorage;
        std::array<SkScalar, _colorStorage.size()> _offsetStorage;
        info.fColorCount = _colorStorage.size();
        info.fColors = _colorStorage.data();
        info.fColorOffsets = _offsetStorage.data();

        if (SkAndroidFrameworkUtils::ShaderAsALinearGradient(paint.getShader(), &info) &&
            info.fColorCount <= _colorStorage.size()) {
            for (int i = 0; i < info.fColorCount; i++) {
                info.fColors[i] = transformColor(transform, info.fColors[i]);
            }
            paint.setShader(SkGradientShader::MakeLinear(
                    info.fPoints, info.fColors, nullptr, info.fColorOffsets, info.fColorCount,
                    info.fTileMode, info.fGradientFlags, nullptr));
        }
    }

    if (paint.getColorFilter()) {
        SkBlendMode mode;
        SkColor color;

        // TODO: LRU this or something to avoid spamming new color mode filters
        if (paint.getColorFilter()->asAColorMode(&color, &mode)) {
            SkColor4f transformedColor = transformColor(transform, SkColor4f::FromColor(color));
            paint.setColorFilter(SkColorFilters::Blend(transformedColor, nullptr, mode));
        } else if (transform == ColorTransform::Invert) {
            // Handle matrix and others type of filters
            composeWithInvertedColorFilter(paint);
        }
    }
}

static BitmapPalette paletteForColorHSV(SkColor color) {
    float hsv[3];
    SkColorToHSV(color, hsv);
    return hsv[2] >= .5f ? BitmapPalette::Light : BitmapPalette::Dark;
}

BitmapPalette filterPalette(const SkPaint* paint, BitmapPalette palette) {
    if ((palette != BitmapPalette::Light && palette != BitmapPalette::Dark) || !paint ||
        !paint->getColorFilter()) {
        return palette;
    }

    SkColor4f color = palette == BitmapPalette::Light ? SkColors::kWhite : SkColors::kBlack;
    sk_sp<SkColorSpace> srgb = SkColorSpace::MakeSRGB();
    color = paint->getColorFilter()->filterColor4f(color, srgb.get(), srgb.get());
    return paletteForColorHSV(color.toSkColor());
}

bool transformPaint(ColorTransform transform, SkPaint* paint) {
    // TODO
    applyColorTransform(transform, *paint);
    return true;
}

bool transformPaint(ColorTransform transform, SkPaint* paint, BitmapPalette palette) {
    bool shouldInvert = false;
    if (transform == ColorTransform::Invert) {
        if (palette != BitmapPalette::GrayScale && palette != BitmapPalette::Barcode &&
            palette != BitmapPalette::Colorful) {
            // When the transform is Invert we invert any image that is not deemed "colorful",
            // "gray-scale" or a barcode, regardless of calculated image brightness.
            shouldInvert = true;
        }
    }
    palette = filterPalette(paint, palette);
    if (palette == BitmapPalette::Light && transform == ColorTransform::Dark) {
        shouldInvert = true;
    }
    if (palette == BitmapPalette::Dark && transform == ColorTransform::Light) {
        shouldInvert = true;
    }
    if (shouldInvert) {
        composeWithInvertedColorFilter(*paint);
    }
    return shouldInvert;
}

}  // namespace android::uirenderer
