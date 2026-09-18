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

import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.staticMethod;
import static com.google.errorprone.matchers.method.MethodMatchers.instanceMethod;

import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.VisitorState;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.code.Symbol.MethodSymbol;

import java.util.Optional;
import java.util.Map;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Traverses the call graph looking for lock inversions in synchronized blocks.
 *
 * <p>Whenever a new cross-method invocation is found, the {@code scanMethodInvocation} is
 * called to request another scan there with a new instance of the class.
 *
 * @see BugChecker.SuppressibleTreePathScanner
 */
class CallPathTracer extends TreePathScanner<Void, Optional<HeldLock>> {

    /**
     * Methods which invoke lambdas on the same thread.
     *
     * <p>Adapted from {@code com.google.errorprone.bugpatterns.threasafety.HeldLockAnalyzer}.
     */
    @SuppressWarnings("CheckContents")
    private static final Matcher<ExpressionTree> INVOKES_LAMBDAS_IMMEDIATELY =
            anyOf(
                    instanceMethod().onDescendantOf("java.lang.Iterable"),
                    instanceMethod().onDescendantOf("java.util.Iterator"),
                    instanceMethod().onDescendantOf("java.util.List"),
                    instanceMethod().onDescendantOf("java.util.Map"),
                    instanceMethod().onDescendantOf("java.util.stream.BaseStream"),
                    instanceMethod().onExactClass("java.util.Optional"),
                    staticMethod().onClass("com.google.common.collect.Iterables"));

    /**
     * Methods which are so widely overridden that it is not sensible to apply a substitution
     * principle.
     *
     * <p>For example, if class X implements a Runnable, we would get far too many false positives
     * by assuming that every Runnable is an X. However, in other places this assumption can help
     * uncover surprises hidden by the abstraction.
     */
    private static final Matcher<ExpressionTree> COMMON_ABSTRACT_METHOD =
            instanceMethod().onClass(
                    (type, s) ->
                            type.toString().startsWith("android.util.")
                                    || type.toString().startsWith("java.lang.")
                                    || type.toString().startsWith("java.util.concurrent.")
                                    || type.toString().startsWith("java.util.function."));

    private final SystemServerLockChecker checker;
    private final VisitorState state;
    private final BiConsumer<HeldLock, HeldLock> reportMatch;

    private CallStack currentStack;

    public CallPathTracer(SystemServerLockChecker checker, VisitorState state,
            CallStack callStack, BiConsumer<HeldLock, HeldLock> reportMatch) {
        this.checker = checker;
        this.state = state;
        this.currentStack = callStack;
        this.reportMatch = reportMatch;
    }

    @Override
    public Void scan(Tree tree, Optional<HeldLock> highest) {
      return checker.isSuppressed(tree, state) ? null : super.scan(tree, highest);
    }

    @Override
    public Void scan(TreePath treePath, Optional<HeldLock> highest) {
      return checker.isSuppressed(treePath.getLeaf(), state)
            ? null
            : super.scan(treePath, highest);
    }

    @SuppressWarnings("BadInstanceof")
    @Override
    public Void visitMethodInvocation(MethodInvocationTree tree, Optional<HeldLock> highest) {
        if (highest.isPresent() && !COMMON_ABSTRACT_METHOD.matches(tree, state)) {
            if (ASTHelpers.getSymbol(tree) instanceof MethodSymbol symbol) {
                checker.scanMethodInvocation(
                        state, symbol, currentStack, highest, reportMatch);
            }
        }
        return super.visitMethodInvocation(tree, highest);
    }

    @Override
    public Void visitNewClass(NewClassTree tree, Optional<HeldLock> highest) {
        scan(tree.getEnclosingExpression(), highest);
        scan(tree.getIdentifier(), highest);
        scan(tree.getTypeArguments(), highest);
        scan(tree.getArguments(), highest);
        // Method declarations inside anonymous classes will be analyzed separately.
        return null;
    }

    @SuppressWarnings("BadInstanceof")
    @Override
    public Void visitLambdaExpression(LambdaExpressionTree tree, Optional<HeldLock> highest) {
        if (getCurrentPath().getParentPath().getLeaf() instanceof MethodInvocationTree mit
                && INVOKES_LAMBDAS_IMMEDIATELY.matches(mit, state)) {
            return super.visitLambdaExpression(tree, highest);
        }
        // Don't look inside lambdas if we aren't sure whether they'll be executed in the
        // same synchronization context.
        return null;
    }

    @Override
    @SuppressWarnings("BadInstanceof")
    public Void visitMemberReference(MemberReferenceTree tree, Optional<HeldLock> highest) {
        if (highest.isPresent()) {
            if (getCurrentPath().getParentPath().getLeaf() instanceof MethodInvocationTree mit
                    && INVOKES_LAMBDAS_IMMEDIATELY.matches(mit, state)
                    && ASTHelpers.getSymbol(tree) instanceof MethodSymbol symbol) {
                checker.scanMethodInvocation(state, symbol, currentStack, highest, reportMatch);
            }
        }
        return super.visitMemberReference(tree, highest);
    }

    @Override
    public Void visitSynchronized(SynchronizedTree tree, Optional<HeldLock> highest) {
        final var tmp = currentStack;
        try {
            currentStack = new CallStack(getCurrentPath(), currentStack);
            final var newLock = LockResolver.resolveLock(tree, currentStack);
            if (newLock.isPresent() && highest.isPresent()
                    && newLock.get().order < highest.get().order
                    && !highest.get().alsoHeld.contains(newLock.get().symbol)
                    && !highest.get().alsoHeldOrder.contains(newLock.get().order)) {
                reportMatch.accept(newLock.get(), highest.get());
                return null;
            } else {
                return super.visitSynchronized(tree, HeldLock.merge(newLock, highest));
            }
        } finally {
            currentStack = tmp;
        }
    }
}
