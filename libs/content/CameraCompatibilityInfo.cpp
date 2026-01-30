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

#define LOG_TAG "CameraCompatibilityInfo"

#include <android/content/res/CameraCompatibilityInfo.h>
#include <utils/Log.h>
#include <binder/Parcel.h>
#include <binder/Parcelable.h>
#include <optional>

namespace android {
namespace content {
namespace res {
    using android::ui::Rotation;
    status_t CameraCompatibilityInfo::writeToParcel(Parcel* parcel) const {
        if (parcel == nullptr) {
            return BAD_VALUE;
        }
        parcel->writeInt32(mRotateAndCropRotation.transform(ui::toRotationInt).value_or(-1));
        parcel->writeBool(mShouldOverrideSensorOrientation);
        parcel->writeBool(mShouldLetterboxForCameraCompat);
        parcel->writeInt32(mDisplayRotationSandbox.transform(ui::toRotationInt).value_or(-1));
        parcel->writeBool(mShouldAllowTransformInverseDisplay);

        return OK;
    }

    status_t CameraCompatibilityInfo::readFromParcel(const Parcel* parcel) {
        if (parcel == nullptr) {
            return BAD_VALUE;
        }
        int32_t tmpInt;
        parcel->readInt32(&tmpInt);
        if (tmpInt < 0) {
            mRotateAndCropRotation = std::nullopt;
        } else {
            mRotateAndCropRotation = ui::toRotation(tmpInt);
        }
        parcel->readBool(&mShouldOverrideSensorOrientation);
        parcel->readBool(&mShouldLetterboxForCameraCompat);
        parcel->readInt32(&tmpInt);
        if (tmpInt < 0) {
            mDisplayRotationSandbox = std::nullopt;
        } else {
            mDisplayRotationSandbox = ui::toRotation(tmpInt);
        }
        parcel->readBool(&mShouldAllowTransformInverseDisplay);
        return OK;
    }

    bool CameraCompatibilityInfo::shouldRotateAndCrop() const {
        return mRotateAndCropRotation.has_value()
            && mRotateAndCropRotation != ui::ROTATION_0;
    }

    std::optional<Rotation> CameraCompatibilityInfo::getRotateAndCropRotation() const {
        return mRotateAndCropRotation;
    }

    void CameraCompatibilityInfo::setRotateAndCropRotation(std::optional<Rotation>
            rotateAndCropRotation) {
        mRotateAndCropRotation = rotateAndCropRotation;
    }

    bool CameraCompatibilityInfo::shouldOverrideSensorOrientation() const {
        return mShouldOverrideSensorOrientation;
    }

    void CameraCompatibilityInfo::setShouldOverrideSensorOrientation(
            bool shouldOverrideSensorOrientation) {
        mShouldOverrideSensorOrientation = shouldOverrideSensorOrientation;
    }

    bool CameraCompatibilityInfo::shouldLetterboxForCameraCompat() const {
        return mShouldLetterboxForCameraCompat;
    }

    void CameraCompatibilityInfo::setShouldLetterboxForCameraCompat(
            bool shouldLetterboxForCameraCompat) {
        mShouldLetterboxForCameraCompat = shouldLetterboxForCameraCompat;
    }

    std::optional<Rotation> CameraCompatibilityInfo::getDisplayRotationSandbox() const {
        return mDisplayRotationSandbox;
    }

    void CameraCompatibilityInfo::setDisplayRotationSandbox(std::optional<Rotation>
            displayRotationSandbox) {
        mDisplayRotationSandbox = displayRotationSandbox;
    }

    bool CameraCompatibilityInfo::shouldAllowTransformInverseDisplay() const {
        return mShouldAllowTransformInverseDisplay;
    }

    void CameraCompatibilityInfo::setShouldAllowTransformInverseDisplay(bool
            shouldAllowTransformInverseDisplay) {
        mShouldAllowTransformInverseDisplay = shouldAllowTransformInverseDisplay;
    }

} // namespace res
} // namespace content
} // namespace android
