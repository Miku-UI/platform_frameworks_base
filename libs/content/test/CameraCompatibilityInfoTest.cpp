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

#include <android/content/res/CameraCompatibilityInfo.h>
#include <binder/Parcel.h>
#include <gtest/gtest.h>

using android::content::res::CameraCompatibilityInfo;

TEST(CameraCompatibilityInfoTest, DefaultsTest) {
    CameraCompatibilityInfo compatInfo;

    EXPECT_EQ(compatInfo.getRotateAndCropRotation().value(), android::ui::ROTATION_0);
    EXPECT_FALSE(compatInfo.shouldOverrideSensorOrientation());
    EXPECT_FALSE(compatInfo.shouldLetterboxForCameraCompat());
    EXPECT_EQ(compatInfo.getDisplayRotationSandbox().value(), android::ui::ROTATION_0);
    EXPECT_TRUE(compatInfo.shouldAllowTransformInverseDisplay());
}

TEST(CameraCompatibilityInfoTest, ParcelTest) {
    CameraCompatibilityInfo compatInfo;
    compatInfo.setRotateAndCropRotation(android::ui::ROTATION_90);
    compatInfo.setShouldOverrideSensorOrientation(true);
    compatInfo.setShouldLetterboxForCameraCompat(true);
    compatInfo.setDisplayRotationSandbox(android::ui::ROTATION_270);
    compatInfo.setShouldAllowTransformInverseDisplay(false);

    android::Parcel parcel;
    compatInfo.writeToParcel(&parcel);
    parcel.setDataPosition(0);

    CameraCompatibilityInfo compatInfo2;
    compatInfo2.readFromParcel(&parcel);

    EXPECT_EQ(compatInfo.getRotateAndCropRotation().value(),
              compatInfo2.getRotateAndCropRotation().value());
    EXPECT_EQ(compatInfo.shouldOverrideSensorOrientation(),
              compatInfo2.shouldOverrideSensorOrientation());
    EXPECT_EQ(compatInfo.shouldLetterboxForCameraCompat(),
              compatInfo2.shouldLetterboxForCameraCompat());
    EXPECT_EQ(compatInfo.getDisplayRotationSandbox().value(),
              compatInfo2.getDisplayRotationSandbox().value());
    EXPECT_EQ(compatInfo.shouldAllowTransformInverseDisplay(),
              compatInfo2.shouldAllowTransformInverseDisplay());
}

TEST(CameraCompatibilityInfoTest, ShouldRotateAndCropTest) {
    CameraCompatibilityInfo compatInfo;
    compatInfo.setRotateAndCropRotation(android::ui::ROTATION_90);
    EXPECT_TRUE(compatInfo.shouldRotateAndCrop());

    compatInfo.setRotateAndCropRotation(android::ui::ROTATION_180);
    EXPECT_TRUE(compatInfo.shouldRotateAndCrop());

    compatInfo.setRotateAndCropRotation(android::ui::ROTATION_270);
    EXPECT_TRUE(compatInfo.shouldRotateAndCrop());

    compatInfo.setRotateAndCropRotation(android::ui::ROTATION_0);
    EXPECT_FALSE(compatInfo.shouldRotateAndCrop());

    compatInfo.setRotateAndCropRotation(std::nullopt);
    EXPECT_FALSE(compatInfo.shouldRotateAndCrop());
}