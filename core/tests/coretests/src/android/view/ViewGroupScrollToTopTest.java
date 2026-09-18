/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.view;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.animation.TranslateAnimation;
import android.view.flags.Flags;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_SCROLL_TO_TOP)
public class ViewGroupScrollToTopTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void testDispatchScrollToTop_callsOnScrollToTop() {
        final Context context = getInstrumentation().getContext();
        final TestView view = spy(new TestView(context, 0, 0, 100, 100));
        doReturn(true).when(view).onScrollToTop(anyInt());

        assertTrue(view.dispatchScrollToTop(50));
        verify(view).onScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_parentHandlesBeforeChildren() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = spy(new TestViewGroup(context, 0, 0, 100, 100));
        final TestView child = spy(new TestView(context, 0, 0, 100, 100));
        root.addView(child);

        // Both want to handle it
        doReturn(true).when(child).onScrollToTop(anyInt());
        doReturn(true).when(root).onScrollToTop(anyInt());

        assertTrue(root.dispatchScrollToTop(50));

        // Parent handles it first, child is never called
        verify(root).onScrollToTop(anyInt());
        verify(child, never()).onScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_intersectingGetsEvent() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 100);

        // Child range: [10, 40]. Tap: 20.
        final TestView child = spy(new TestView(context, 10, 0, 30, 100));
        root.addView(child);
        doReturn(true).when(child).onScrollToTop(anyInt());

        assertTrue(root.dispatchScrollToTop(20));

        // Expect local coordinate: 20 - 10 = 10
        verify(child).dispatchScrollToTop(10);
    }

    @Test
    public void testDispatchScrollToTop_coordinateTransformationWithScroll() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 100);

        final TestView child = spy(new TestView(context, 10, 0, 30, 100));
        child.setScrollX(5); // Internal scroll should be ignored by parent dispatch logic
        root.addView(child);
        doReturn(true).when(child).dispatchScrollToTop(anyInt());

        root.dispatchScrollToTop(20);

        // 20 (parent) - 10 (left) = 10.
        verify(child).dispatchScrollToTop(10);
    }

    @Test
    public void testDispatchScrollToTop_coordinateTransformationWithTranslationX() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 100);

        // Child Layout: [10, 40]. Visual Translation: +20px.
        // Visual bounds shift to [30, 60].
        final TestView child = spy(new TestView(context, 10, 0, 40, 100));
        child.setTranslationX(20f);
        root.addView(child);

        doReturn(true).when(child).dispatchScrollToTop(anyInt());

        // Dispatch at x=40.
        // Inside visual bounds [30, 60].
        assertTrue(root.dispatchScrollToTop(40));

        // Coordinate Mapping:
        // Parent(40) - Left(10) - TranslationX(20) = 10.
        verify(child).dispatchScrollToTop(10);
    }

    @Test
    public void testDispatchScrollToTop_parentScrollOffsets() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 100);

        // Child at (200, 200). Initially off-screen.
        final TestView child = spy(new TestView(context, 200, 200, 300, 300));
        root.addView(child);

        // Scroll parent to (200, 200). Child becomes visible at top-left.
        root.setScrollX(200);
        root.setScrollY(200);

        doReturn(true).when(child).dispatchScrollToTop(anyInt());

        // Dispatch x=10.
        assertTrue(root.dispatchScrollToTop(10));
        verify(child).dispatchScrollToTop(10);
    }

    @Test
    public void testDispatchScrollToTop_ySorting() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 200);

        final TestView child1 = spy(new TestView(context, 0, 50, 100, 100)); // Lower
        final TestView child2 = spy(new TestView(context, 0, 10, 100, 60));  // Higher

        root.addView(child1);
        root.addView(child2);

        doReturn(false).when(child2).dispatchScrollToTop(anyInt());
        doReturn(true).when(child1).dispatchScrollToTop(anyInt());

        // Both intersect x=50. Sort by visual Y (ascending).
        assertTrue(root.dispatchScrollToTop(50));

        InOrder inOrder = inOrder(child1, child2);
        inOrder.verify(child2).dispatchScrollToTop(anyInt()); // Top-most first
        inOrder.verify(child1).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_zSortingWithElevation() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 100);

        final TestView childHigh = spy(new TestView(context, 0, 0, 100, 100));
        childHigh.setElevation(10f);

        final TestView childLow = spy(new TestView(context, 0, 0, 100, 100));
        childLow.setElevation(0f);

        root.addView(childHigh);
        root.addView(childLow);

        doReturn(false).when(childHigh).dispatchScrollToTop(anyInt());
        doReturn(true).when(childLow).dispatchScrollToTop(anyInt());

        assertTrue(root.dispatchScrollToTop(50));

        // Higher Z-index visited first
        InOrder inOrder = inOrder(childHigh, childLow);
        inOrder.verify(childHigh).dispatchScrollToTop(anyInt());
        inOrder.verify(childLow).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_defaultZSorting() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 100);

        final TestView child1 = spy(new TestView(context, 0, 0, 100, 100));
        final TestView child2 = spy(new TestView(context, 0, 0, 100, 100));

        root.addView(child1);
        root.addView(child2); // Added last -> Drawn on top

        doReturn(true).when(child1).dispatchScrollToTop(anyInt());
        doReturn(false).when(child2).dispatchScrollToTop(anyInt());

        assertTrue(root.dispatchScrollToTop(50));

        // Last added (top) visited first
        InOrder inOrder = inOrder(child2, child1);
        inOrder.verify(child2).dispatchScrollToTop(anyInt());
        inOrder.verify(child1).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_zSortingWithTranslationZ() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 100);

        final TestView childHigh = spy(new TestView(context, 0, 0, 100, 100));
        childHigh.setTranslationZ(10f);

        final TestView childLow = spy(new TestView(context, 0, 0, 100, 100));
        childLow.setElevation(5f);
        childLow.setTranslationZ(-5f);

        root.addView(childHigh);
        root.addView(childLow);

        doReturn(false).when(childHigh).dispatchScrollToTop(anyInt());
        doReturn(true).when(childLow).dispatchScrollToTop(anyInt());

        assertTrue(root.dispatchScrollToTop(50));

        // childHigh (Total Z=10) > childLow (Total Z=0).
        InOrder inOrder = inOrder(childHigh, childLow);
        inOrder.verify(childHigh).dispatchScrollToTop(anyInt());
        inOrder.verify(childLow).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_fallbackToNonIntersecting() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 100);

        // Intersects x=10
        final TestView childIntersect = spy(new TestView(context, 0, 0, 20, 100));
        // Does NOT intersect x=10
        final TestView childNonIntersect = spy(new TestView(context, 30, 0, 50, 100));

        root.addView(childIntersect);
        root.addView(childNonIntersect);

        doReturn(false).when(childIntersect).dispatchScrollToTop(anyInt());
        doReturn(true).when(childNonIntersect).dispatchScrollToTop(anyInt());

        // Intersecting child ignores event, logic should fallback to non-intersecting
        assertTrue(root.dispatchScrollToTop(10));

        InOrder inOrder = inOrder(childIntersect, childNonIntersect);
        inOrder.verify(childIntersect).dispatchScrollToTop(anyInt());
        inOrder.verify(childNonIntersect).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_invisibleChildIgnored() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 100);

        final TestView child = spy(new TestView(context, 0, 0, 100, 100));
        child.setVisibility(View.INVISIBLE);
        root.addView(child);

        assertFalse(root.dispatchScrollToTop(50));
        verify(child, never()).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_invisibleButAnimating_isVisited() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 100);

        final TestView child = spy(new TestView(context, 0, 0, 100, 100));
        child.setVisibility(View.INVISIBLE);
        child.setAnimation(new TranslateAnimation(0, 0, 0, 0));
        root.addView(child);

        doReturn(true).when(child).dispatchScrollToTop(anyInt());

        assertTrue(root.dispatchScrollToTop(50));
        verify(child).dispatchScrollToTop(50);
    }

    @Test
    public void testDispatchScrollToTop_nonIntersecting_ySorting() {
        // Verify that even among fallback (non-intersecting) views, we still respect Y-order
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 200);

        final TestView childLower = spy(new TestView(context, 60, 50, 100, 100));
        final TestView childHigher = spy(new TestView(context, 60, 10, 100, 60));

        root.addView(childLower);
        root.addView(childHigher);

        doReturn(false).when(childHigher).dispatchScrollToTop(anyInt());
        doReturn(true).when(childLower).dispatchScrollToTop(anyInt());

        // Dispatch at x=20 (intersects neither).
        assertTrue(root.dispatchScrollToTop(20));

        InOrder inOrder = inOrder(childLower, childHigher);
        inOrder.verify(childHigher).dispatchScrollToTop(anyInt()); // Top-most first
        inOrder.verify(childLower).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_customChildDrawingOrder() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = spy(new TestViewGroup(context, 0, 0, 100, 100));

        final TestView child1 = spy(new TestView(context, 0, 0, 100, 100));
        final TestView child2 = spy(new TestView(context, 0, 0, 100, 100));

        root.setChildrenDrawingOrderEnabled(true);
        root.addView(child1);
        root.addView(child2);

        // Force reverse drawing order: child2 (0), child1 (1) -> child1 on top
        doReturn(1).when(root).getChildDrawingOrder(anyInt(), org.mockito.ArgumentMatchers.eq(0));
        doReturn(0).when(root).getChildDrawingOrder(anyInt(), org.mockito.ArgumentMatchers.eq(1));

        doReturn(true).when(child2).dispatchScrollToTop(anyInt());
        doReturn(false).when(child1).dispatchScrollToTop(anyInt());

        assertTrue(root.dispatchScrollToTop(50));

        InOrder inOrder = inOrder(child1, child2);
        inOrder.verify(child1).dispatchScrollToTop(anyInt()); // Visited first due to custom order
        inOrder.verify(child2).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_scaledView_losesPriority() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 200, 200);

        // Child 1: High Z, but scaled down.
        // Layout [0-100], Visual [0-50].
        final TestView childSmall = spy(new TestView(context, 0, 0, 100, 100));
        childSmall.setPivotX(0);
        childSmall.setScaleX(0.5f);
        childSmall.setElevation(10f);

        // Child 2: Low Z, normal size.
        // Layout/Visual [60-160].
        final TestView childNormal = spy(new TestView(context, 60, 0, 160, 100));
        childNormal.setElevation(0f);

        root.addView(childSmall);
        root.addView(childNormal);

        doReturn(false).when(childNormal).dispatchScrollToTop(anyInt());
        doReturn(true).when(childSmall).dispatchScrollToTop(anyInt());

        // Dispatch at x=80.
        // Intersects childNormal [60-160].
        // Does NOT intersect childSmall [0-50] (despite layout [0-100]).
        // Intersection priority overrides Z-order.
        assertTrue(root.dispatchScrollToTop(80));

        InOrder inOrder = inOrder(childSmall, childNormal);
        inOrder.verify(childNormal).dispatchScrollToTop(anyInt()); // Priority
        inOrder.verify(childSmall).dispatchScrollToTop(anyInt());  // Fallback
    }

    @Test
    public void testDispatchScrollToTop_scaledView_visualIntersection_fallback() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 200, 200);

        // Layout [50-150]. Scaled 0.5x about center. Visual [75-125].
        final TestView child = spy(new TestView(context, 50, 50, 150, 150));
        child.setPivotX(50);
        child.setPivotY(50);
        child.setScaleX(0.5f);
        root.addView(child);

        doReturn(true).when(child).dispatchScrollToTop(anyInt());

        // 1. Dispatch x=60 (Outside Visual, Inside Layout).
        // Should dispatch as Fallback.
        assertTrue(root.dispatchScrollToTop(60));
        // Coord: 60 - 50(Left) = 10 -> -40(RelPivot) -> -80(InvScale) -> -30(Local)
        verify(child).dispatchScrollToTop(-30);

        // 2. Dispatch x=80 (Inside Visual).
        // Should dispatch as Priority.
        assertTrue(root.dispatchScrollToTop(80));
        // Coord: 80 - 50(Left) = 30 -> -20(RelPivot) -> -40(InvScale) -> 10(Local)
        verify(child).dispatchScrollToTop(10);
    }

    @Test
    public void testDispatchScrollToTop_rotatedView_visualIntersection() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 200, 200);

        // Layout (50, 50). Rotated 90 degrees around (50, 10).
        final TestView child = spy(new TestView(context, 50, 50, 150, 70));
        child.setPivotX(50);
        child.setPivotY(10);
        child.setRotation(90f);
        root.addView(child);

        doReturn(true).when(child).dispatchScrollToTop(anyInt());

        // 1. Fallback: Tap x=60 (outside visual bounds).
        // Maps to local x=-10. (See logic below).
        assertTrue(root.dispatchScrollToTop(60));
        verify(child).dispatchScrollToTop(-10);
        clearInvocations(child);

        // 2. Priority: Tap x=100 (inside visual bounds).
        // Coordinate Calculation for Rotated View:
        // Parent Point (100, 0) relative to pivot maps to (0, -60).
        // Inverse Rotate (-90 deg): (x, y) -> (y, -x) = (-60, 0).
        // Add Pivot (50, 10) -> (-10, 10).
        // Local X = -10.
        assertTrue(root.dispatchScrollToTop(100));
        verify(child).dispatchScrollToTop(-10);
    }

    @Test
    public void testDispatchScrollToTop_rotationChangesIntersectionAndOrder() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 200, 200);

        // Child 1: High Z. Rotated 90 deg.
        // Visual bounds shift left: [-100, 0].
        final TestView childRotated = spy(new TestView(context, 0, 0, 100, 100));
        childRotated.setElevation(10f);
        childRotated.setPivotX(0);
        childRotated.setPivotY(0);
        childRotated.setRotation(90f);

        // Child 2: Low Z. Normal.
        // Visual bounds [0, 100].
        final TestView childNormal = spy(new TestView(context, 0, 0, 100, 100));
        childNormal.setElevation(0f);

        root.addView(childRotated);
        root.addView(childNormal);

        doReturn(false).when(childNormal).dispatchScrollToTop(anyInt());
        doReturn(false).when(childRotated).dispatchScrollToTop(anyInt());

        // Dispatch at x=50.
        // Inside childNormal (Priority). Outside childRotated (Fallback).
        root.dispatchScrollToTop(50);

        InOrder inOrder = inOrder(childNormal, childRotated);

        // 1. Normal view visited first (Intersecting)
        inOrder.verify(childNormal).dispatchScrollToTop(50);

        // 2. Rotated view visited second (Fallback)
        // Parent (50, 0) -> Pivot (0,0) -> InvRot (-90) -> (0, -50) -> Local X=0.
        inOrder.verify(childRotated).dispatchScrollToTop(0);
    }

    @Test
    public void testDispatchScrollToTop_transformedCoordinateDelivery() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 200, 200);

        // Scaled 2x. Pivot left edge (0).
        final TestView child = spy(new TestView(context, 50, 0, 150, 100));
        child.setPivotX(0);
        child.setScaleX(2.0f);
        root.addView(child);

        doReturn(true).when(child).dispatchScrollToTop(anyInt());

        // Tap x=70. Relative=20. Inverse Scale(2.0) -> 10.
        root.dispatchScrollToTop(70);
        verify(child).dispatchScrollToTop(10);
    }

    @Test
    public void testDispatchScrollToTop_visualYSort_scaledView() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 400);

        // Child 1: Layout Y=100.
        final TestView child1 = spy(new TestView(context, 0, 100, 100, 200));

        // Child 2: Layout Y=150. Translated Y=-100 -> Visual Y=50.
        // Visually "higher" than Child 1.
        final TestView child2 = spy(new TestView(context, 0, 150, 100, 250));
        child2.setTranslationY(-100);

        root.addView(child1);
        root.addView(child2);

        doReturn(false).when(child2).dispatchScrollToTop(anyInt());
        doReturn(true).when(child1).dispatchScrollToTop(anyInt());

        // Sort by Visual Y. Child 2 (50) < Child 1 (100).
        assertTrue(root.dispatchScrollToTop(50));

        InOrder inOrder = inOrder(child1, child2);
        inOrder.verify(child2).dispatchScrollToTop(anyInt());
        inOrder.verify(child1).dispatchScrollToTop(anyInt());
    }

    @Test
    public void testDispatchScrollToTop_mixedPriorities() {
        final Context context = getInstrumentation().getContext();
        final TestViewGroup root = new TestViewGroup(context, 0, 0, 100, 200);

        // A: Intersects, Y=100, High Z
        final TestView childA = spy(new TestView(context, 0, 100, 100, 200));
        childA.setElevation(10f);

        // B: Intersects, Y=10, Low Z. (Wins over A due to Visual Y).
        final TestView childB = spy(new TestView(context, 0, 10, 100, 50));
        childB.setElevation(0f);

        // C: No Intersect. (Loses to A/B due to Intersection check).
        final TestView childC = spy(new TestView(context, 200, 0, 300, 100));
        childC.setElevation(100f);

        root.addView(childA);
        root.addView(childB);
        root.addView(childC);

        doReturn(false).when(childA).dispatchScrollToTop(anyInt());
        doReturn(false).when(childB).dispatchScrollToTop(anyInt());
        doReturn(false).when(childC).dispatchScrollToTop(anyInt());

        root.dispatchScrollToTop(50);

        // Expected: Intersecting + Top Y > Intersecting + Bottom Y > Non-Intersecting
        InOrder inOrder = inOrder(childB, childA, childC);
        inOrder.verify(childB).dispatchScrollToTop(anyInt());
        inOrder.verify(childA).dispatchScrollToTop(anyInt());
        inOrder.verify(childC).dispatchScrollToTop(anyInt());
    }

    public static class TestViewGroup extends ViewGroup {
        TestViewGroup(Context context, int left, int top, int right, int bottom) {
            super(context);
            setFrame(left, top, right, bottom);
        }

        @Override
        public void setChildrenDrawingOrderEnabled(boolean enabled) {
            super.setChildrenDrawingOrderEnabled(enabled);
        }

        @Override
        public int getChildDrawingOrder(int childCount, int i) {
            return super.getChildDrawingOrder(childCount, i);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
        }
    }

    public static class TestView extends View {
        TestView(Context context, int left, int top, int right, int bottom) {
            super(context);
            setFrame(left, top, right, bottom);
        }
    }
}
