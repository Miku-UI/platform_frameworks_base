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

import static com.google.errorprone.BugPattern.LinkType.NONE;
import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.VisitorState;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;

import java.util.Collections;
import java.util.Optional;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Verifies that when multiple {@code @SystemServerLock} instances are held,
 * they are acquired in a strictly increasing order.
 *
 * <p>Note: This checker does a full transitive scan through possible method invocations for every
 * object annotated with {@code SystemServerLock}.
 */
@SuppressWarnings("BugPatternNaming")
@AutoService(BugChecker.class)
@BugPattern(
    name = "AndroidFrameworkSystemServerLock",
    summary = "Verifies that @SystemServerLock is acquired in correct order.",
    linkType = NONE,
    severity = ERROR)
public class SystemServerLockChecker extends BugChecker implements BugChecker.MethodTreeMatcher {

    /**
     * Running record of the highest-ordered lock that is known to call each method symbol.
     *
     * <p>The highest-known ordering may be updated multiple times as we find new entry points to
     * the same function. This map is used to avoid pointlessly re-scanning the same code blocks
     * with the same information.
     *
     * <p>As-is, the worst-case number of times we can visit the same method body is the number of
     * unique system server locks that are held on the way to that method body.
     */
    private final Map<MethodSymbol, HeldLock> highestIncoming = new HashMap<>();
    private final Map<MethodSymbol, CallStack> highestIncomingStack = new HashMap<>();

    /**
     * Running record of the symbols that are known to override each of the keys.
     *
     * <p>This is used to account for polymorphism - in case someone calls A.method(), the
     * actual invocation might be on B.method() where B is a subclass of A. Thus whenever
     * we consider the callflow of a method that holds system locks, we create virtual calls to
     * all of its potential overrides too.
     */
    private final Map<MethodSymbol, Set<MethodSymbol>> knownOverrides = new HashMap<>();

    /**
     * This is the launchpad for starting a full scan of the call graph for inconsistencies with
     * the defined system server lock order.
     *
     * <p>At a high level, we're doing a sort of the call graph according to highest lock held
     * during execution of a function. In practice there may be multiple scans of the same method
     * because we don't have the opportunity to sort upfront.
     */
    @Override
    public Description matchMethod(MethodTree tree, VisitorState state) {
        final MethodSymbol symbol = ASTHelpers.getSymbol(tree);

        if (symbol.isConstructor()) {
            return Description.NO_MATCH;
        }
        if (tree.getBody() == null) {
            return Description.NO_MATCH;
        }

        // Issues with inner locks will be reported through the TreeScanner later.
        final BiConsumer<HeldLock, HeldLock> reporter =
                (newLock, existingLock) -> reportMatch(state, tree, newLock, existingLock);

        // We may have scanned a method that this one overrides. If so, the same locks can be held.
        Optional<HeldLock> highest = LockResolver.findGuardedBy(tree, state);
        Optional<CallStack> highestStack = Optional.empty();

        for (MethodSymbol superMethod : ASTHelpers.findSuperMethods(symbol, state.getTypes())) {
            final var candidate = Optional.ofNullable(highestIncoming.get(superMethod));
            if (!highest.isPresent()
                    || (candidate.isPresent() && candidate.get().order > highest.get().order)) {
                highest = HeldLock.merge(candidate, highest);
                highestStack = Optional.ofNullable(highestIncomingStack.get(superMethod));
            }
            knownOverrides.computeIfAbsent(superMethod, k -> new HashSet<>()).add(symbol);
        }

        // Skip if we already scanned this method with a higher-ordered lock held.
        if (highest.isPresent()) {
            if (highestIncoming.containsKey(symbol)
                    && highestIncoming.get(symbol).order >= highest.get().order) {
                return Description.NO_MATCH;
            }
            highestIncoming.put(symbol, highest.get());
            highestIncomingStack.put(symbol, highestStack.orElse(null));
        }

        final var callStack = new CallStack(state.getPath(), highestIncomingStack.get(symbol));

        new CallPathTracer(this, state, callStack, reporter).scan(state.getPath(), highest);
        // Follow virtual calls to methods that override this one.
        for (var child : knownOverrides.getOrDefault(symbol, Collections.emptySet())) {
            scanMethodInvocation(state, child, callStack, highest, reporter);
        }
        return Description.NO_MATCH;
    }

    public void scanMethodInvocation(VisitorState state, MethodSymbol symbol, CallStack callStack,
            Optional<HeldLock> highest, BiConsumer<HeldLock, HeldLock> reportMatch) {
        if (!highest.isPresent()) {
            // There is no reason to scan a method if we didn't find any locks on the callstack.
            return;
        }
        if (highestIncoming.containsKey(symbol)
                && highestIncoming.get(symbol).order >= highest.get().order) {
            // We already scanned this method with a higher-ordered lock held.
            return;
        }
        highestIncoming.put(symbol, highest.get());
        highestIncomingStack.put(symbol, callStack);

        final var javacEnv = JavacProcessingEnvironment.instance(state.context);
        TreePath methodPath = Trees.instance(javacEnv).getPath(symbol);
        if (methodPath != null) {
            new CallPathTracer(
                    this,
                    state.withPath(methodPath),
                    new CallStack(methodPath, callStack),
                    reportMatch)
                .scan(methodPath, highest);
        }
        // Follow virtual calls to methods that override this one.
        for (var child : knownOverrides.getOrDefault(symbol, Collections.emptySet())) {
            scanMethodInvocation(state, child, callStack, highest, reportMatch);
        }
    }

    /**
     * Construct an appropriate error message and report it up to Errorprone.
     *
     * <p>Beware: ErrorProne's description system only supports reporting findings on the same
     * file as the {@code state} they are invoked over, or the file name and line number will
     * be wrong. We have to check the callstack for eligible expressions.
     */
    void reportMatch(VisitorState state, Tree tree, HeldLock newLock, HeldLock existingLock) {
        final var matchExpression = existingLock.callStack
                .findCallerIn(state.getPath().getCompilationUnit())
                .map(TreePath::getLeaf).orElse(tree);

        final var stack = formatStackTrace(newLock.callStack, existingLock.callStack.caller);

        state.reportMatch(buildDescription(matchExpression)
                .setMessage("Lock '" + symbolToString(newLock.symbol)
                        + "' (order=" + newLock.order
                        + ") is acquired inside lock '" + symbolToString(existingLock.symbol)
                        + "' (order=" + existingLock.order
                        + "). System server locks must be acquired in increasing order"
                        + " to prevent deadlocks."
                        + stack)
                .build());
    }

    /**
     * Format a method or field reference into a string as {@code ClassName.fieldName}.
     */
    private static String symbolToString(Symbol symbol) {
        if (symbol.owner instanceof ClassSymbol) {
            return symbol.owner.name.toString() + "." + symbol.name.toString();
        } else {
            return symbol.name.toString();
        }
    }

    /**
     * Represents the call path from top to base as a java exception-style string.
     *
     * <p>If top is not encountered in the stack  the stack trace will be formatted in full.
     */
    @SuppressWarnings("BadInstanceof")
    private static String formatStackTrace(CallStack base, CallStack top) {
        final var sb = new StringBuilder();
        for (var i = base; i != null; i = i.caller) {
            if (i.tree.getLeaf() instanceof MethodTree mt) {
                if (ASTHelpers.getSymbol(mt) instanceof MethodSymbol symbol) {
                    sb.append("\n  at " + symbolToString(symbol));
                }
            }
            if (i.tree.getLeaf() instanceof SynchronizedTree st) {
                if (ASTHelpers.getSymbol(ASTHelpers.stripParentheses(st.getExpression()))
                        instanceof Symbol symbol) {
                    sb.append("\n  synchronized on " + symbolToString(symbol));
                }
            }
            if (top != null && i.tree.getLeaf().equals(top.tree.getLeaf())) {
                break;
            }
        }
        return sb.toString();
    }
}
