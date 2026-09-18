/*
 * Copyright (C) 2014 The Android Open Source Project
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
#ifndef OUTLINE_H
#define OUTLINE_H

#include <SkPath.h>

#include <optional>

#include "Rect.h"
#include "utils/MathUtils.h"

namespace android {
namespace uirenderer {

class Outline {
public:
    enum class Type { None = 0, Empty = 1, Path = 2, RoundRect = 3 };

    Outline() : mShouldClip(false), mType(Type::None), mRadius(0), mAlpha(0.0f) {}

    bool setRoundRect(int left, int top, int right, int bottom, float radius, float alpha) {
        if (mType == Type::RoundRect && left == mBounds.left && right == mBounds.right &&
            top == mBounds.top && bottom == mBounds.bottom && radius == mRadius &&
            alpha == mAlpha) {
            // nothing to change, don't do any work
            return false;
        }
        mAlpha = alpha;
        mType = Type::RoundRect;
        mPath.reset();  // updated lazily
        mBounds.set(left, top, right, bottom);
        mRadius = radius;
        return true;
    }

    bool setPath(const SkPath* outline, float alpha) {
        if (!outline) {
            return setEmpty();
        }
        if (mType == Type::Path && *outline == mPath && alpha == mAlpha) {
            // nothing to change, don't do any work
            return false;
        }
        mAlpha = alpha;
        mType = Type::Path;
        mPath = *outline;
        mBounds.set(outline->getBounds());
        return true;
    }

    bool setEmpty() {
        bool dirty = false;
        if (mType != Type::Empty && mType != Type::None) {
            mPath.reset();
            mAlpha = 0.0f;
            dirty = true;
        }
        mType = Type::Empty;
        return dirty;
    }

    bool setNone() {
        bool dirty = false;
        if (mType != Type::Empty && mType != Type::None) {
            mPath.reset();
            mAlpha = 0.0f;
            dirty = true;
        }
        mType = Type::None;
        return dirty;
    }

    bool isEmpty() const { return mType == Type::Empty; }

    float getAlpha() const { return mAlpha; }

    void setShouldClip(bool clip) { mShouldClip = clip; }

    bool getShouldClip() const { return mShouldClip; }

    bool willClip() const { return mShouldClip; }

    bool willComplexClip() const {
        return mShouldClip && (mType != Type::RoundRect || MathUtils::isPositive(mRadius));
    }

    bool getAsRoundRect(Rect* outRect, float* outRadius) const {
        if (mType == Type::RoundRect) {
            outRect->set(mBounds);
            *outRadius = mRadius;
            return true;
        }
        return false;
    }

    const SkPath* getPath() const {
        if (mType == Type::None || mType == Type::Empty) return nullptr;

        if (!mPath) {
            // Type::Path stores the path upfront, the only deferred case is Type::RoundRect.
            LOG_ALWAYS_FATAL_IF(mType != Type::RoundRect, "Unexpected Outline type.");
            const SkRect rect = mBounds.toSkRect();
            mPath = MathUtils::isPositive(mRadius) ? SkPath::RRect(rect, mRadius, mRadius)
                                                   : SkPath::Rect(rect);
        }

        return &mPath.value();
    }

    Type getType() const { return mType; }

    const Rect& getBounds() const { return mBounds; }

    float getRadius() const { return mRadius; }

private:
    bool mShouldClip;
    Type mType;
    Rect mBounds;
    float mRadius;
    float mAlpha;

    mutable std::optional<SkPath> mPath;
};

} /* namespace uirenderer */
} /* namespace android */

#endif /* OUTLINE_H */
