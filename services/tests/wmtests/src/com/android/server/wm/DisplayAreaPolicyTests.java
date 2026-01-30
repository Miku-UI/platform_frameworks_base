/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER;
import static android.window.DisplayAreaOrganizer.FEATURE_VENDOR_FIRST;

import static com.android.server.wm.WindowContainer.POSITION_BOTTOM;
import static com.android.server.wm.WindowContainer.POSITION_TOP;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import android.platform.test.annotations.Presubmit;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.server.wm.DisplayAreaPolicyBuilderTest.SurfacelessDisplayAreaRoot;

import com.google.android.collect.Lists;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for the {@link DisplayAreaPolicy}.
 *
 * Build/Install/Run:
 *  atest WmTests:DisplayAreaPolicyTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class DisplayAreaPolicyTests extends WindowTestsBase {

    @Test
    public void testGetDefaultTaskDisplayArea() {
        final Pair<DisplayAreaPolicy, List<TaskDisplayArea>> result =
                createPolicyWith2TaskDisplayAreas();
        final DisplayAreaPolicy policy = result.first;
        final TaskDisplayArea taskDisplayArea1 = result.second.get(0);
        assertEquals(taskDisplayArea1, policy.getDefaultTaskDisplayArea());
    }

    @Test
    public void testTaskDisplayArea_taskPositionChanged_updatesTaskDisplayAreaPosition() {
        final Pair<DisplayAreaPolicy, List<TaskDisplayArea>> result =
                createPolicyWith2TaskDisplayAreas();
        final DisplayAreaPolicy policy = result.first;
        final TaskDisplayArea taskDisplayArea1 = result.second.get(0);
        final TaskDisplayArea taskDisplayArea2 = result.second.get(1);
        final Task stack1 = taskDisplayArea1.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final Task stack2 = taskDisplayArea2.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        // Initial order
        assertTaskDisplayAreasOrder(policy, taskDisplayArea1, taskDisplayArea2);

        // Move stack in tda1 to top
        stack1.getParent().positionChildAt(POSITION_TOP, stack1, true /* includingParents */);

        assertTaskDisplayAreasOrder(policy, taskDisplayArea2, taskDisplayArea1);

        // Move stack in tda2 to top, but not including parents
        stack2.getParent().positionChildAt(POSITION_TOP, stack2, false /* includingParents */);

        assertTaskDisplayAreasOrder(policy, taskDisplayArea2, taskDisplayArea1);

        // Move stack in tda1 to bottom
        stack1.getParent().positionChildAt(POSITION_BOTTOM, stack1, true /* includingParents */);

        assertTaskDisplayAreasOrder(policy, taskDisplayArea1, taskDisplayArea2);

        // Move stack in tda2 to bottom, but not including parents
        stack2.getParent().positionChildAt(POSITION_BOTTOM, stack2, false /* includingParents */);

        assertTaskDisplayAreasOrder(policy, taskDisplayArea1, taskDisplayArea2);
    }

    @Test
    public void testDisplayAreaGroup_taskPositionChanged_updatesDisplayAreaGroupPosition() {
        final TaskDisplayArea taskDisplayArea1 = new TaskDisplayArea(mWm,
                "Tasks1", FEATURE_DEFAULT_TASK_CONTAINER,
                false /* createdByOrganizer */, true /* canHostHomeTask*/);
        final TaskDisplayArea taskDisplayArea2 = new TaskDisplayArea(mWm,
                "Tasks2", FEATURE_VENDOR_FIRST + 3,
                false /* createdByOrganizer */, true /* canHostHomeTask*/);
        final TaskDisplayArea taskDisplayArea3 = new TaskDisplayArea(mWm,
                "Tasks3", FEATURE_VENDOR_FIRST + 4,
                false /* createdByOrganizer */, true /* canHostHomeTask*/);
        final TaskDisplayArea taskDisplayArea4 = new TaskDisplayArea(mWm,
                "Tasks4", FEATURE_VENDOR_FIRST + 5,
                false /* createdByOrganizer */, true /* canHostHomeTask*/);
        final TaskDisplayArea taskDisplayArea5 = new TaskDisplayArea(mWm,
                "Tasks5", FEATURE_VENDOR_FIRST + 6,
                false /* createdByOrganizer */, true /* canHostHomeTask*/);
        final DisplayAreaPolicy.Provider policyProvider =
                (wmService, content, root, imeContainer) -> {
                    final RootDisplayArea group1 = new SurfacelessDisplayAreaRoot(wmService,
                            "group1", FEATURE_VENDOR_FIRST + 1);
                    final RootDisplayArea group2 = new SurfacelessDisplayAreaRoot(wmService,
                            "group2", FEATURE_VENDOR_FIRST + 2);
                    final DisplayAreaPolicyBuilder.HierarchyBuilder rootHierarchy =
                            new DisplayAreaPolicyBuilder.HierarchyBuilder(root)
                                    .setImeContainer(imeContainer)
                                    .setTaskDisplayAreas(Lists.newArrayList(
                                            taskDisplayArea1, taskDisplayArea2));
                    return new DisplayAreaPolicyBuilder(
                            DEFAULT_DISPLAY, rootHierarchy)
                            .addDisplayAreaGroupHierarchy(
                                    new DisplayAreaPolicyBuilder.HierarchyBuilder(group1)
                                            .setTaskDisplayAreas(Lists.newArrayList(
                                                    taskDisplayArea3, taskDisplayArea4))
                            )
                            .addDisplayAreaGroupHierarchy(
                                    new DisplayAreaPolicyBuilder.HierarchyBuilder(group2)
                                            .setTaskDisplayAreas(Lists.newArrayList(
                                                    taskDisplayArea5)))
                            .build(wmService);
                };
        final DisplayContent dc = createDisplayContentWithDisplayAreaPolicy(policyProvider);
        final DisplayAreaPolicy policy = dc.mDisplayAreaPolicy;
        final Task stack1 = taskDisplayArea1.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final Task stack3 = taskDisplayArea3.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final Task stack4 = taskDisplayArea4.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        // Initial order
        assertTaskDisplayAreasOrder(policy, taskDisplayArea1, taskDisplayArea2, taskDisplayArea3,
                taskDisplayArea4, taskDisplayArea5);

        // Move bottom stack in tda1 to top
        stack1.getParent().positionChildAt(POSITION_TOP, stack1, true /* includingParents */);

        assertTaskDisplayAreasOrder(policy, taskDisplayArea2, taskDisplayArea3, taskDisplayArea4,
                taskDisplayArea5, taskDisplayArea1);

        // Move bottom stack in tda2 to top
        stack3.getParent().positionChildAt(POSITION_TOP, stack3, true /* includingParents */);

        assertTaskDisplayAreasOrder(policy, taskDisplayArea2, taskDisplayArea5, taskDisplayArea1,
                taskDisplayArea4, taskDisplayArea3);

        // Move bottom stack in tda2 to top
        stack4.getParent().positionChildAt(POSITION_TOP, stack4, true /* includingParents */);

        assertTaskDisplayAreasOrder(policy, taskDisplayArea2, taskDisplayArea5, taskDisplayArea1,
                taskDisplayArea3, taskDisplayArea4);

        // Move top stack in tda2 to bottom
        stack4.getParent().positionChildAt(POSITION_BOTTOM, stack4, true /* includingParents */);

        assertTaskDisplayAreasOrder(policy, taskDisplayArea4, taskDisplayArea3, taskDisplayArea2,
                taskDisplayArea5, taskDisplayArea1);
    }

    @Test
    public void testTaskDisplayAreasCanHostHomeTask() {
        final TaskDisplayArea taskDisplayAreaWithHome = new TaskDisplayArea(mWm,
                "Tasks1", FEATURE_DEFAULT_TASK_CONTAINER,
                false /* createdByOrganizer */, true /* canHostHomeTask*/);
        final TaskDisplayArea taskDisplayAreaWithNoHome = new TaskDisplayArea(mWm,
                "Tasks2", FEATURE_VENDOR_FIRST + 1,
                false /* createdByOrganizer */, false /* canHostHomeTask*/);
        final DisplayAreaPolicy.Provider policyProvider =
                (wmService, content, root, imeContainer) -> {
                    final DisplayAreaPolicyBuilder.HierarchyBuilder rootHierarchy =
                            new DisplayAreaPolicyBuilder.HierarchyBuilder(root)
                                    .setImeContainer(imeContainer)
                                    .setTaskDisplayAreas(Lists.newArrayList(
                                            taskDisplayAreaWithHome, taskDisplayAreaWithNoHome));
                    return new DisplayAreaPolicyBuilder(DEFAULT_DISPLAY, rootHierarchy)
                            .build(wmService);
                };
        final DisplayContent dc = createDisplayContentWithDisplayAreaPolicy(policyProvider);
        doReturn(true).when(dc).isHomeSupported();
        final DisplayAreaPolicy policy = dc.mDisplayAreaPolicy;

        assertTaskDisplayAreaPresentAndCanHaveHome(policy, FEATURE_DEFAULT_TASK_CONTAINER, true);
        assertTaskDisplayAreaPresentAndCanHaveHome(policy, FEATURE_VENDOR_FIRST + 1, false);

        final Task stackHome = taskDisplayAreaWithHome.getOrCreateRootHomeTask(true);
        final Task stackNoHome = taskDisplayAreaWithNoHome.getOrCreateRootHomeTask(true);
        assertNotNull(stackHome);
        assertNull(stackNoHome);
    }

    private void assertTaskDisplayAreaPresentAndCanHaveHome(DisplayAreaPolicy policy,
                                                            int featureId,
                                                            boolean canHaveHome) {
        final ArrayList<TaskDisplayArea> matchedAreas = new ArrayList<>();
        policy.mRoot.forAllWindowContainers(w -> {
            final TaskDisplayArea tda = w.asTaskDisplayArea();
            if (tda != null && tda.mFeatureId == featureId) {
                matchedAreas.add(tda);
            }
        });
        assertTrue(matchedAreas.size() > 0);
        assertEquals(canHaveHome, matchedAreas.get(0).canHostHomeTask());
    }

    private void assertTaskDisplayAreasOrder(DisplayAreaPolicy policy,
            TaskDisplayArea... expectTdaOrder) {
        List<TaskDisplayArea> expectOrder = new ArrayList<>();
        Collections.addAll(expectOrder, expectTdaOrder);

        // Verify hierarchy
        List<TaskDisplayArea> actualOrder = new ArrayList<>();
        policy.mRoot.forAllTaskDisplayAreas(taskDisplayArea -> {
            actualOrder.add(taskDisplayArea);
        }, false /* traverseTopToBottom */);
        assertEquals(expectOrder, actualOrder);
    }

    private Pair<DisplayAreaPolicy, List<TaskDisplayArea>> createPolicyWith2TaskDisplayAreas() {
        final List<TaskDisplayArea> taskDisplayAreaList = new ArrayList<>();
        final DisplayAreaPolicy.Provider policyProvider =
                (wmService, content, root, imeContainer) -> {
                    final TaskDisplayArea taskDisplayArea1 = new TaskDisplayArea(wmService,
                            "Tasks1", FEATURE_DEFAULT_TASK_CONTAINER,
                            false /* createdByOrganizer */, true /* canHostHomeTask*/);
                    final TaskDisplayArea taskDisplayArea2 = new TaskDisplayArea(wmService,
                            "Tasks2", FEATURE_VENDOR_FIRST,
                            false /* createdByOrganizer */, true /* canHostHomeTask*/);
                    taskDisplayAreaList.add(taskDisplayArea1);
                    taskDisplayAreaList.add(taskDisplayArea2);
                    final DisplayAreaPolicyBuilder.HierarchyBuilder rootHierarchy =
                            new DisplayAreaPolicyBuilder.HierarchyBuilder(root)
                                    .setImeContainer(imeContainer)
                                    .setTaskDisplayAreas(taskDisplayAreaList);
                    return new DisplayAreaPolicyBuilder(DEFAULT_DISPLAY, rootHierarchy)
                            .build(wmService);
                };
        final DisplayContent dc = createDisplayContentWithDisplayAreaPolicy(policyProvider);
        return Pair.create(dc.mDisplayAreaPolicy, taskDisplayAreaList);
    }

    private DisplayContent createDisplayContentWithDisplayAreaPolicy(
            DisplayAreaPolicy.Provider policyProvider) {
        doReturn(policyProvider).when(mWm).getDisplayAreaPolicyProvider();
        return createNewDisplay();
    }
}
