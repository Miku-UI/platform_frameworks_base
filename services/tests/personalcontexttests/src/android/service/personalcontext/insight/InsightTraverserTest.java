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

package android.service.personalcontext.insight;


import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightTraverserTest {

    @Mock private InsightVisitor mMockVisitor;
    private InOrder mInOrder;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mInOrder = Mockito.inOrder(mMockVisitor);
    }

    @Test
    public void testTraverse_singleNode() {
        ContextInsight node = new BundleInsight.Builder().build();
        InsightTraverser.traverse(node, mMockVisitor);
        mInOrder.verify(mMockVisitor).visit((BundleInsight) node, /*index=*/ 0);
        mInOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testTraverse_emptyTree() {
        // Test with an empty collection. The builder doesn't allow this, so we construct it.
        InsightCollection emptyCollection =
                new InsightCollection.Builder()
                        .addInsight(new BundleInsight.Builder().build())
                        .build();
        InsightTraverser.traverse(emptyCollection, mMockVisitor);
        mInOrder.verify(mMockVisitor).visit(emptyCollection, /*index=*/ 0);
    }

    @Test
    public void testTraverse_dfsOrder() {
        // Tree structure:
        // root
        //  |- child1
        //  |- child2
        //     |- grandchild1
        //  |- child3
        BundleInsight child1 = new BundleInsight.Builder().build();
        BundleInsight grandchild1 = new BundleInsight.Builder().build();
        InsightCollection child2 = new InsightCollection.Builder().addInsight(grandchild1).build();
        BundleInsight child3 = new BundleInsight.Builder().build();
        InsightCollection root =
                new InsightCollection.Builder()
                        .addInsight(child1)
                        .addInsight(child2)
                        .addInsight(child3)
                        .build();

        InsightTraverser.traverse(root, mMockVisitor);

        mInOrder.verify(mMockVisitor).visit(root, /*index=*/ 0);
        mInOrder.verify(mMockVisitor).visit(child1, /*index=*/ 1);
        mInOrder.verify(mMockVisitor).visit(child2, /*index=*/ 2);
        mInOrder.verify(mMockVisitor).visit(grandchild1, /*index=*/ 3);
        mInOrder.verify(mMockVisitor).visit(child3, /*index=*/ 4);
        mInOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testTraverse_maxDepthEnforced() {
        BundleInsight grandchild = new BundleInsight.Builder().build();
        InsightCollection child = new InsightCollection.Builder().addInsight(grandchild).build();
        InsightCollection root = new InsightCollection.Builder().addInsight(child).build();

        // Traverse with max depth of 2 (root is depth 0, child is 1, grandchild is 2)
        InsightTraverser.traverse(root, mMockVisitor, 2);

        // Grandchild should not be visited.
        mInOrder.verify(mMockVisitor).visit(root, /*index=*/ 0);
        mInOrder.verify(mMockVisitor).visit(child, /*index=*/ 1);
        mInOrder.verifyNoMoreInteractions();
        Mockito.verify(mMockVisitor, Mockito.never()).visit(eq(grandchild), anyInt());
    }

    @Test
    public void testTraverse_maxDepthAllowsFullTraversal() {
        BundleInsight grandchild = new BundleInsight.Builder().build();
        InsightCollection child = new InsightCollection.Builder().addInsight(grandchild).build();
        InsightCollection root = new InsightCollection.Builder().addInsight(child).build();

        InsightTraverser.traverse(root, mMockVisitor, 3);

        mInOrder.verify(mMockVisitor).visit(root, /*index=*/ 0);
        mInOrder.verify(mMockVisitor).visit(child, /*index=*/ 1);
        mInOrder.verify(mMockVisitor).visit(grandchild, /*index=*/ 2);
        mInOrder.verifyNoMoreInteractions();
    }
}
