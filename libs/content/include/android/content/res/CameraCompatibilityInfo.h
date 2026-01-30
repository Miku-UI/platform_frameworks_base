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

#ifndef ANDROID_CONTENT_RES_CAMERA_COMPATIBILITY_INFO_H
#define ANDROID_CONTENT_RES_CAMERA_COMPATIBILITY_INFO_H

#include <ui/Rotation.h>
#include <binder/Parcelable.h>
#include <binder/Parcel.h>

namespace android {
namespace content {
namespace res {
struct CameraCompatibilityInfo : public Parcelable {
    CameraCompatibilityInfo() = default;
    CameraCompatibilityInfo(const CameraCompatibilityInfo& rhs) = default;
    CameraCompatibilityInfo(CameraCompatibilityInfo&& rhs) = default;
    virtual ~CameraCompatibilityInfo() = default;

private:
    std::optional<ui::Rotation> mRotateAndCropRotation = std::nullopt;
    bool mShouldOverrideSensorOrientation = false;
    bool mShouldLetterboxForCameraCompat = false;
    std::optional<ui::Rotation> mDisplayRotationSandbox = std::nullopt;
    bool mShouldAllowTransformInverseDisplay = true;

public:
    virtual status_t writeToParcel(Parcel* parcel) const;
    virtual status_t readFromParcel(const Parcel* parcel);

    bool shouldRotateAndCrop() const;

    std::optional<ui::Rotation> getRotateAndCropRotation() const;

    void setRotateAndCropRotation(std::optional<ui::Rotation> rotateAndCropRotation);

    bool shouldOverrideSensorOrientation() const;

    void setShouldOverrideSensorOrientation(bool shouldOverrideSensorOrientation);

    bool shouldLetterboxForCameraCompat() const;

    void setShouldLetterboxForCameraCompat(bool shouldLetterboxForCameraCompat);

    std::optional<ui::Rotation> getDisplayRotationSandbox() const;

    void setDisplayRotationSandbox(std::optional<ui::Rotation> displayRotationSandbox);

    bool shouldAllowTransformInverseDisplay() const;

    void setShouldAllowTransformInverseDisplay(bool shouldAllowTransformInverseDisplay);
};

} // namespace res
} // namespace content
} // namespace android

#endif // ANDROID_CONTENT_RES_CAMERA_COMPATIBILITY_INFO_H
