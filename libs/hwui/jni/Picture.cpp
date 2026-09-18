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

#include "Picture.h"
#include "SkStream.h"
#include "include/core/SkSerialProcs.h"
#include "include/encode/SkPngEncoder.h"
#include "include/codec/SkPngDecoder.h"
#include "include/codec/SkPixmapUtils.h"
#include "include/codec/SkEncodedOrigin.h"

#include <memory>
#include <hwui/Canvas.h>

namespace android {

Picture::Picture(const Picture* src) {
    if (NULL != src) {
        mWidth = src->width();
        mHeight = src->height();
        if (NULL != src->mPicture.get()) {
            mPicture = src->mPicture;
        } else if (NULL != src->mRecorder.get()) {
            mPicture = src->makePartialCopy();
        }
    } else {
        mWidth = 0;
        mHeight = 0;
    }
}

Picture::Picture(sk_sp<SkPicture>&& src) {
    mPicture = std::move(src);
    mWidth = 0;
    mHeight = 0;
}

Canvas* Picture::beginRecording(int width, int height) {
    mPicture.reset(NULL);
    mRecorder.reset(new SkPictureRecorder);
    mWidth = width;
    mHeight = height;
    SkCanvas* canvas = mRecorder->beginRecording(SkIntToScalar(width), SkIntToScalar(height));
    return Canvas::create_canvas(canvas);
}

void Picture::endRecording() {
    if (NULL != mRecorder.get()) {
        mPicture = mRecorder->finishRecordingAsPicture();
        mRecorder.reset(NULL);
    }
}

int Picture::width() const {
    return mWidth;
}

int Picture::height() const {
    return mHeight;
}

Picture* Picture::CreateFromStream(SkStream* stream) {
    Picture* newPict = new Picture;

    SkDeserialProcs procs;
    procs.fImageDataProc = [](sk_sp<SkData> data,
                              std::optional<SkAlphaType> at, void*) -> sk_sp<SkImage> {
        auto codec = SkPngDecoder::Decode(data, nullptr, nullptr);
        if (codec == nullptr) {
            return nullptr;
        }
        SkImageInfo info = codec->getInfo();
        if (at.has_value()) {
            info = info.makeAlphaType(*at);
        } else if (kUnpremul_SkAlphaType == info.alphaType()) {
            info = info.makeAlphaType(kPremul_SkAlphaType);
        }
        if (SkEncodedOriginSwapsWidthHeight(codec->getOrigin())) {
            info = SkPixmapUtils::SwapWidthHeight(info);
        }
        return std::get<0>(codec->getImage(info));
    };
    sk_sp<SkPicture> skPicture = SkPicture::MakeFromStream(stream, &procs);
    if (NULL != skPicture) {
        newPict->mPicture = skPicture;

        const SkIRect cullRect = skPicture->cullRect().roundOut();
        newPict->mWidth = cullRect.width();
        newPict->mHeight = cullRect.height();
    }

    return newPict;
}

void Picture::serialize(SkWStream* stream) const {
    SkSerialProcs procs;
    procs.fImageProc = [](SkImage* img, void*) -> SkSerialReturnType {
        // TODO when migrating to Graphite, readback won't work, so we will only be
        // able to serialize raster-backed or lazy images. It is unclear if this
        // is an actual problem, so probably adding an assert like the !isTextureBacked()
        // will probably be helpful in verifying that.
        auto raster = img->makeRasterImage(nullptr, SkImage::kDisallow_CachingHint);
        if (!raster) {
            return nullptr;
        }
        return SkPngEncoder::Encode(nullptr, raster.get(), SkPngEncoder::Options{});
    };
    if (NULL != mRecorder.get()) {
        this->makePartialCopy()->serialize(stream, &procs);
    } else if (NULL != mPicture.get()) {
        mPicture->serialize(stream, &procs);
    } else {
        // serialize "empty" picture
        SkPictureRecorder recorder;
        recorder.beginRecording(0, 0);
        recorder.finishRecordingAsPicture()->serialize(stream, &procs);
    }
}

void Picture::draw(Canvas* canvas) {
    if (NULL != mRecorder.get()) {
        this->endRecording();
        SkASSERT(NULL != mPicture.get());
    }

    if (mPicture) {
        canvas->drawPicture(*mPicture);
    }
}

sk_sp<SkPicture> Picture::makePartialCopy() const {
    SkASSERT(NULL != mRecorder.get());

    SkPictureRecorder reRecorder;

    SkCanvas* canvas = reRecorder.beginRecording(mWidth, mHeight);
    mRecorder->partialReplay(canvas);
    return reRecorder.finishRecordingAsPicture();
}

}; // namespace android
