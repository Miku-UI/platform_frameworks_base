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

#pragma once

#include <SkCanvas.h>
#include <SkPaintFilterCanvas.h>

#include "hwui/Bitmap.h"
#include "utils/Color.h"
#include "utils/Macros.h"

namespace android::uirenderer {

/**
 * The result of counting the color area.
 */
enum Polarity {
    /** The result is too close to make a definite determination */
    Unknown = 0,
    /** Majority light fills */
    Light,
    /** Majority dark fills */
    Dark
};

/**
 * Tracks the app's overall polarity (i.e. dark or light theme) by counting the areas of backgrounds
 * and their colors. This is used to determine if we should force invert the app, for instance if
 * the user prefers dark theme but this app is mainly light.
 *
 * The idea is that we count the fill colors of any background-type draw calls: drawRect(),
 * drawColor(), etc. If the area of light fills drawn to the screen is greater than the area of dark
 * fills drawn to the screen, we can reasonably guess that the app is light theme, and vice-versa.
 */
class ColorArea {
public:
    ColorArea() {}
    ~ColorArea() {}

    /**
     * Counts the given area of a draw call that is reasonably expected to draw a background:
     * drawRect, drawColor, etc.
     *
     * @param area the total area of the draw call's fill (approximate)
     * @param paint the paint used to fill the area. If the paint is not a fill, the area will not
     *              be added.
     */
    void addArea(uint64_t area, const SkPaint& paint);

    /**
     * See [addArea(uint64_t, SkPaint&)]
     */
    void addArea(const SkRect& rect, const SkPaint* paint);

    /**
     * See [addArea(uint64_t, SkPaint&)]
     */
    void addArea(const SkRect& rect, const SkPaint& paint, android::BitmapPalette palette);

    /**
     * See [addArea(long, SkPaint&)]
     */
    void addArea(int32_t width, int32_t height, const SkPaint* paint);

    /**
     * See [addArea(uint64_t, SkPaint&)]
     */
    void addArea(uint64_t area, SkColor4f color);

    /**
     * Prefer [addArea(uint64_t, SkPaint&)], unless the area you're measuring doesn't have a paint
     * with measurable colors.
     *
     * @param area the total area of the draw call's fill (approximate)
     * @param polarity whether the color of the given area is light or dark
     */
    void addArea(uint64_t area, Polarity polarity);

    /**
     * Adds the source's area to this area. This is so you can sum up the areas of a bunch of child
     * nodes.
     */
    void merge(const ColorArea& source);

    /** Resets the object back to the initial state */
    void reset();

    int getParentWidth() const;
    void setParentWidth(int width);
    int getParentHeight() const;
    void setParentHeight(int height);

    /** Returns the best guess of the polarity of this area */
    Polarity getPolarity() const;

private:
    int mParentWidth = -1;
    int mParentHeight = -1;

    uint64_t mLight = 0;
    uint64_t mDark = 0;
};

}  // namespace android::uirenderer
