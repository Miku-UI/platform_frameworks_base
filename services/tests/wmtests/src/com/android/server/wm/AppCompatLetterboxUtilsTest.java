/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.wm;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.AppCompatLetterboxUtils.calculateLetterboxInnerBounds;
import static com.android.server.wm.AppCompatLetterboxUtils.calculateLetterboxOuterBounds;
import static com.android.server.wm.AppCompatLetterboxUtils.calculateLetterboxPosition;
import static com.android.server.wm.AppCompatLetterboxUtils.fullyContainsOrNotIntersects;

import static org.mockito.Mockito.mock;

import android.graphics.Point;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Tests for the {@link AppCompatLetterboxUtils} class.
 *
 * Build/Install/Run:
 * atest WmTests:AppCompatLetterboxUtilsTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatLetterboxUtilsTest extends WindowTestsBase {

    @Test
    public void allEmptyWhenIsAppNotLetterboxed() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.setTopActivityLetterboxPolicyRunning(false);
            robot.getLetterboxPosition();
            robot.assertPosition(/* x */ 0, /* y */0);
            robot.getInnerBounds();
            robot.assertInnerBounds(/* left */ 0, /* top */ 0, /* right */ 0, /* bottom */ 0);
            robot.getOuterBounds();
            robot.assertOuterBounds(/* left */ 0, /* top */ 0, /* right */ 0, /* bottom */ 0);
        });
    }

    @Test
    public void positionIsFromActivity() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.setTopActivityLetterboxPolicyRunning(true);
            robot.activity().configureTopActivityBounds(
                    new Rect(/* left */ 200, /* top */ 400, /* right */ 300, /* bottom */ 400));
            robot.getLetterboxPosition();

            robot.assertPosition(/* x */ 200, /* y */ 400);
        });
    }

    @Test
    public void outerBoundsWhenFixedRotationTransformDisplayBoundsIsAvailable() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.setTopActivityLetterboxPolicyRunning(true);
            robot.activity().setFixedRotationTransformDisplayBounds(
                    new Rect(/* left */ 1, /* top */ 2, /* right */ 3, /* bottom */ 4));
            robot.getOuterBounds();

            robot.assertOuterBounds(/* left */ 1, /* top */ 2, /* right */ 3, /* bottom */ 4);
        });
    }

    @Test
    public void outerBoundsNoFixedRotationTransformDisplayBoundsInMultiWindow() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.setTopActivityLetterboxPolicyRunning(true);
            robot.activity().setFixedRotationTransformDisplayBounds(null);
            robot.activity().setTopActivityInMultiWindowMode(true);
            robot.getOuterBounds();

            robot.checkOuterBoundsAreTaskFragmentBounds();
        });
    }

    @Test
    public void outerBoundsNoFixedRotationTransformDisplayBoundsNotInMultiWindow() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.setTopActivityLetterboxPolicyRunning(true);
            robot.activity().setFixedRotationTransformDisplayBounds(null);
            robot.activity().setTopActivityInMultiWindowMode(false);
            robot.getOuterBounds();

            robot.checkOuterBoundsAreRootTaskParentBounds();
        });
    }

    @Test
    public void innerBoundsTransparencyPolicyIsRunning() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.setTopActivityLetterboxPolicyRunning(true);
            robot.setTopActivityTransparentPolicyRunning(true);

            robot.getInnerBounds();

            robot.checkInnerBoundsAreActivityBounds();
        });
    }

    @Test
    public void innerBoundsTransparencyPolicyIsNotRunning() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.setTopActivityLetterboxPolicyRunning(true);
            robot.setTopActivityTransparentPolicyRunning(false);
            robot.setWindowFrame(
                    new Rect(/* left */ 100, /* top */ 200, /* right */ 300, /* bottom */ 400));

            robot.getInnerBounds();

            robot.assertInnerBounds(/* left */ 100, /* top */ 200, /* right */ 300, /* bottom */
                    400);
        });
    }

    @Test
    public void testNoBoundsToCheck() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck();
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    @Test
    public void testEmptyBoundsToCheck() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck(new Rect(10, 10, 20, 20), new Rect(30, 30, 40, 40));
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    @Test
    public void testContainsEmptyRect() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck(new Rect(10, 10, 20, 20), new Rect());
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    @Test
    public void testCheckFullyContainsOrNotIntersects_NoIntersection() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck(new Rect(10, 10, 20, 20));
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    @Test
    public void testCheckFullyContainsOrNotIntersects_FullyContains() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck(new Rect(-5, -5, 15, 15));
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    @Test
    public void testCheckFullyContainsOrNotIntersects_PartiallyIntersects() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck(new Rect(5, 5, 15, 15));
            robot.checkFullyContainsOrNotIntersects(/* expected */ false);
        });
    }

    @Test
    public void testCheckFullyContainsOrNotIntersects_MultipleBoundsNoIntersection() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck(new Rect(10, 10, 20, 20), new Rect(-20, -20, -10, -10));
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    @Test
    public void testCheckFullyContainsOrNotIntersects_MultipleBoundsWithOneContaining() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck(new Rect(10, 10, 20, 20), new Rect(-5, -5, 15, 15));
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    @Test
    public void testCheckFullyContainsOrNotIntersects_MultipleBoundsWithOneIntersecting() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck(new Rect(10, 10, 20, 20), new Rect(5, 5, 15, 15));
            robot.checkFullyContainsOrNotIntersects(/* expected */ false);
        });
    }

    @Test
    public void testCheckFullyContainsOrNotIntersects_MultipleBoundsWithEmptyAndNoIntersection() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck(new Rect(), new Rect(10, 10, 20, 20));
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    @Test
    public void testCheckFullyContainsOrNotIntersects_MultipleBoundsWithEmptyAndContaining() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(/* left */ 0, /* top */ 0, /* right */ 10, /* bottom */ 10);
            robot.setBoundsToCheck(new Rect(), new Rect(-5, -5, 15, 15));
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    @Test
    public void testCheckFullyContainsOrNotIntersects_EmptyRectToCheck() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(new Rect());
            robot.setBoundsToCheck(new Rect(10, 10, 20, 20), new Rect(-5, -5, 15, 15));
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    @Test
    public void testCheckFullyContainsOrNotIntersects_EmptyRectToCheckAndEmptyBounds() {
        runTestScenario((robot) -> {
            robot.setWindowFrameArea(new Rect());
            robot.setBoundsToCheck(new Rect());
            robot.checkFullyContainsOrNotIntersects(/* expected */ true);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<LetterboxUtilsRobotTest> consumer) {
        final LetterboxUtilsRobotTest robot = new LetterboxUtilsRobotTest(this);
        consumer.accept(robot);
    }

    private static class LetterboxUtilsRobotTest extends AppCompatRobotBase {

        private final Point mPosition = new Point();
        private final Rect mInnerBound = new Rect();
        private final Rect mOuterBound = new Rect();

        private final Rect mWindowFrameArea = new Rect();

        private Rect[] mBoundsToCheck;

        @NonNull
        private final WindowState mWindowState;

        LetterboxUtilsRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
            mWindowState = mock(WindowState.class);
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            spyOn(activity.mAppCompatController.getLetterboxPolicy());
            spyOn(activity.mAppCompatController.getTransparentPolicy());
        }

        void setTopActivityLetterboxPolicyRunning(boolean running) {
            doReturn(running).when(activity().top().mAppCompatController
                    .getLetterboxPolicy()).isRunning();
        }

        void setTopActivityTransparentPolicyRunning(boolean running) {
            doReturn(running).when(activity().top().mAppCompatController
                    .getTransparentPolicy()).isRunning();
        }

        void setWindowFrame(@NonNull Rect frame) {
            doReturn(frame).when(mWindowState).getFrame();
        }

        void setWindowFrameArea(int left, int top, int right, int bottom) {
            mWindowFrameArea.set(left, top, right, bottom);
        }

        void setWindowFrameArea(@NonNull Rect windowFrameArea) {
            mWindowFrameArea.set(windowFrameArea);
        }

        void setBoundsToCheck(@NonNull Rect... boundsToCheck) {
            mBoundsToCheck = boundsToCheck;
        }

        void getLetterboxPosition() {
            calculateLetterboxPosition(activity().top(), mPosition);
        }

        void getInnerBounds() {
            calculateLetterboxInnerBounds(activity().top(), mWindowState, mInnerBound);
        }

        void getOuterBounds() {
            calculateLetterboxOuterBounds(activity().top(), mOuterBound);
        }

        void assertPosition(int expectedX, int expectedY) {
            Assert.assertEquals(expectedX, mPosition.x);
            Assert.assertEquals(expectedY, mPosition.y);
        }

        void assertInnerBounds(int expectedLeft, int expectedTop, int expectedRight,
                int expectedBottom) {
            Assert.assertEquals(expectedLeft, mInnerBound.left);
            Assert.assertEquals(expectedTop, mInnerBound.top);
            Assert.assertEquals(expectedRight, mInnerBound.right);
            Assert.assertEquals(expectedBottom, mInnerBound.bottom);
        }

        void assertOuterBounds(int expectedLeft, int expectedTop, int expectedRight,
                int expectedBottom) {
            Assert.assertEquals(expectedLeft, mOuterBound.left);
            Assert.assertEquals(expectedTop, mOuterBound.top);
            Assert.assertEquals(expectedRight, mOuterBound.right);
            Assert.assertEquals(expectedBottom, mOuterBound.bottom);
        }

        void checkOuterBoundsAreRootTaskParentBounds() {
            Assert.assertEquals(mOuterBound,
                    activity().top().getRootTask().getParent().getBounds());
        }

        void checkOuterBoundsAreTaskFragmentBounds() {
            Assert.assertEquals(mOuterBound,
                    activity().top().getTaskFragment().getBounds());
        }

        void checkInnerBoundsAreActivityBounds() {
            Assert.assertEquals(mInnerBound, activity().top().getBounds());
        }

        void checkFullyContainsOrNotIntersects(boolean expected) {
            Assert.assertEquals(expected,
                    fullyContainsOrNotIntersects(mWindowFrameArea, mBoundsToCheck));
        }

    }
}
