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

#include "hwui/Bitmap.h"

#include <SkCanvas.h>
#include <SkPaintFilterCanvas.h>

#include <memory>

namespace android::uirenderer {

// LINT.IfChange(UsageHint)
enum class UsageHint {
    // Note: Constant values should match RenderNode.java UsageHint.
    Unknown = 0,
    Background = 1,
    Foreground = 2,
    // Contains foreground (usually text), like a button or chip
    Container = 3,
    NavigationBarBackground = 4
};
// LINT.ThenChange(/graphics/java/android/graphics/RenderNode.java:UsageHint)

enum class ColorTransform {
    None,
    Light,
    Dark,
    Invert
};

// True if the paint was modified, false otherwise
bool transformPaint(ColorTransform transform, SkPaint* paint);

bool transformPaint(ColorTransform transform, SkPaint* paint, BitmapPalette palette);

SkColor4f transformColor(ColorTransform transform, SkColor4f color);
SkColor4f transformColorInverse(ColorTransform transform, SkColor4f color);

/** Returns a palette corrected in case it is tinted by the given paint's filter */
BitmapPalette filterPalette(const SkPaint* paint, BitmapPalette palette);

}  // namespace android::uirenderer
