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

package com.google.errorprone.bugpatterns.android.threading;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;

import java.util.Optional;

/**
 * Representing a call stack as a path made up of {@link TreePath}.
 */
public class CallStack {
    final TreePath tree;
    final CallStack caller;

    CallStack(TreePath tree, CallStack caller) {
        this.tree = tree;
        this.caller = caller;
    }

   /**
     * Finds the first {@link TreePath} in this call stack that is part of the given
     * {@link CompilationUnitTree}.
     *
     * <p>This is used when we need to report an error from deep in the call path against the
     * closest line in the original compilation unit that is being analyzed.
     *
     * @param cut The compilation unit to search within.
     * @return An {@link Optional} containing the found {@link TreePath},
     *     or {@link Optional#empty()} if no caller in the stack is in the given compilation unit.
     */
    final Optional<TreePath> findCallerIn(CompilationUnitTree cut) {
        return tree.getCompilationUnit().equals(cut)
                ? Optional.of(tree)
                : caller != null
                        ? caller.findCallerIn(cut)
                        : Optional.empty();
    }
}
