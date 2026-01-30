#!/bin/sh

# This file contains some utilty function to build and run tests without atest.
#
# Motivated by http://go/sysui-soong-gradle-comparison.
#
# Usage:
#   $ source build/envsetup.sh
#   $ lunch <target>
#   $ source ${ANDROID_BUILD_TOP}/frameworks/base/packages/SystemUI/scripts/test_utils.sh
#
# Note: sourcing this script will take ~10s because of the call to get_build_var, this is expected.

echo "Computing TARGET_ARCH..."
export TARGET_ARCH=$(get_build_var TARGET_ARCH)
echo "Exported TARGET_ARCH=$TARGET_ARCH"

# Build and push an `android_test` test target.
#
# Usage: run_class_tests <test_app_target>
#
# Examples:
#   $ make_tests SystemUITests
#   $ make_tests PlatformComposeSceneTransitionLayoutTests
make_tests() {
  echo "Building $1..."
  m $1 -j && \
    echo "Pushing $1..." && \
    adb install -t -r -g $ANDROID_PRODUCT_OUT/testcases/$1/`get_build_var TARGET_ARCH`/$1.apk
}

# Run one or more tests from a test class.
#
# Usage: run_class_tests <test_app_package> <test_class>[#function] [instrumentation]
#
# Examples:
#   $ make_tests SystemUITests && \
#       run_class_tests com.android.systemui.tests com.android.systemui.scene.domain.interactor.SceneInteractorTest#allContentKeys android.testing.TestableInstrumentation
#   $ make_tests PlatformComposeSceneTransitionLayoutTests && \
#       run_class_tests com.android.compose.animation.scene.tests com.android.compose.animation.scene.ElementTest
#   $ make_tests SystemUIGoogleScreenshotTests && \
#       run_class_tests com.android.systemui.testing.screenshot.test com.android.systemui.qs.footer.FooterActionsScreenshotTest
run_class_tests() {
  echo "Running tests in $2..."
  echo "Note: This will skip all steps defined in AndroidTest.xml, so target preparers or xml defined setup steps won't run"
  adb shell am instrument -w -e class $2 $1/${3:-androidx.test.runner.AndroidJUnitRunner}
}

# Run one or more tests from a class.
#
# Usage: run_package_tests <test_app_package> <test_package> [instrumentation]
#
# Example:
#   $ make_tests SystemUITests && \
#       run_package_tests com.android.systemui.tests com.android.systemui.scene.shared.model android.testing.TestableInstrumentation
run_package_tests() {
  echo "Running tests in $2..."
  echo "Note: This will skip all steps defined in AndroidTest.xml, so target preparers or xml defined setup steps won't run"
  adb shell am instrument -w -e package $2 $1/${3:-androidx.test.runner.AndroidJUnitRunner}
}

# Run one or more tests from a test class in SystemUI-tests.
#
# This is just a helper to make it easier to build and run tests covering SystemUI-core.
#
# Usage: make_run_sysui_tests <test_class>[#function]
#
# Example:
#  $ make_run_sysui_tests com.android.systemui.scene.domain.interactor.SceneInteractorTest
#  $ make_run_sysui_tests com.android.systemui.scene.domain.interactor.SceneInteractorTest#allContentKeys
make_run_sysui_tests() {
    make_tests SystemUITests && run_class_tests com.android.systemui.tests $1 android.testing.TestableInstrumentation
}
