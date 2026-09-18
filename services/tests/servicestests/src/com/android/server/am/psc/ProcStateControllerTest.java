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

package com.android.server.am.psc;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.NonNull;
import android.platform.test.annotations.Presubmit;
import android.ravenwood.annotation.RavenwoodKeepWholeClass;

import com.android.server.am.psc.TestGraphElements.TestEdge;
import com.android.server.am.psc.TestGraphElements.TestProcessNode;
import com.android.server.am.psc.TestGraphElements.TestSystemNode;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link ProcStateController}.
 * Build/Install/Run:
 * atest FrameworksServicesTests:ProcStateControllerTest
 * atest FrameworksServicesTestsRavenwood_ProcessStateController:ProcStateControllerTest
 */
@Presubmit
@RavenwoodKeepWholeClass
public class ProcStateControllerTest {
    private ProcStateController mProcStateController;

    @Before
    public void setUp() {
        mProcStateController = new ProcStateController();
    }

    @Test
    public void testFullUpdate_DijkstraTraversal() {
        //        ------- [ System Node ]----------
        //       /         |           \           \
        //   (5)/      (10)|        (15)\            \ (7)
        //    v            v             v            v
        //   [A] --(6)--> [B] --(17)--> [C] <--(8)-- [D]
        //    ^ |\        /                           |
        //    |  \__(9)_/                             |
        //    |                                       |
        //    \------------------(10)----------------/
        TestSystemNode systemNode = new TestSystemNode();
        TestProcessNode nodeA = new TestProcessNode.Builder().build();
        TestProcessNode nodeB = new TestProcessNode.Builder().build();
        TestProcessNode nodeC = new TestProcessNode.Builder().build();
        TestProcessNode nodeD = new TestProcessNode.Builder().build();
        createTestEdge(systemNode, nodeA, 5);
        createTestEdge(systemNode, nodeB, 10);
        createTestEdge(systemNode, nodeC, 15);
        createTestEdge(systemNode, nodeD, 7);
        createTestEdge(nodeA, nodeB, 6);
        createTestEdge(nodeB, nodeC, 17);
        createTestEdge(nodeD, nodeC, 8);
        createTestEdge(nodeD, nodeA, 10);
        createTestEdge(nodeB, nodeA, 9);

        mProcStateController.fullUpdate(systemNode);

        assertThat(nodeA.getProcState()).isEqualTo(5);
        assertThat(nodeB.getProcState()).isEqualTo(6);
        assertThat(nodeC.getProcState()).isEqualTo(8);
        assertThat(nodeD.getProcState()).isEqualTo(7);
    }

    /**
     * Creates a test edge between a source and a target node with a predefined evaluated process
     * state.
     *
     * @param source The source node of the edge.
     * @param target The target node of the edge.
     * @param evaluatedProcState The process state that this edge will propagate.
     * @return The created {@link TestEdge} instance.
     */
    private static TestEdge createTestEdge(@NonNull GraphNode source,
            @NonNull TestProcessNode target, int evaluatedProcState) {
        final TestEdge edge = new TestEdge.Builder(source, target)
                .withEvaluatedProcState(evaluatedProcState)
                .build();
        if (source instanceof TestSystemNode) {
            ((TestSystemNode) source).addOutgoingEdge(edge);
        } else if (source instanceof TestProcessNode) {
            ((TestProcessNode) source).addOutgoingEdge(edge);
        } else {
            throw new IllegalArgumentException("Unknown source type");
        }
        return edge;
    }
}
