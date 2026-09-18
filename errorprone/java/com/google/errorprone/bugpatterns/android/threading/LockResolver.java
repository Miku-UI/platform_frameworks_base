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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.bugpatterns.threadsafety.GuardedByBinder;
import com.google.errorprone.bugpatterns.threadsafety.GuardedByExpression;
import com.google.errorprone.bugpatterns.threadsafety.GuardedByFlags;
import com.google.errorprone.bugpatterns.threadsafety.GuardedBySymbolResolver;
import com.google.errorprone.bugpatterns.threadsafety.GuardedByUtils;
import com.google.errorprone.util.ASTHelpers;
import com.google.errorprone.util.MoreAnnotations;
import com.google.errorprone.VisitorState;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Verifies that when multiple {@code @SystemServerLock} instances are held,
 * they are acquired in a strictly increasing order.
 *
 * <p>Note: This checker does a full transitive scan through possible method invocations for every
 * object annotated with {@code SystemServerLock}.
 */
public class LockResolver {

    public static final String ANNOTATION = "com.android.internal.annotations.SystemServerLock";

    private LockResolver() {}

    /**
     * Potentially resolves the symbol and its order for a given synchronized (x) block.
     *
     * @return {@link Optional#empty()} if the lock primitive is non-trivial, or if the lock
     * primitive is not a SystemServerLock.
     */
    public static Optional<HeldLock> resolveLock(SynchronizedTree tree, CallStack callStack) {
        return Optional.ofNullable(
                    ASTHelpers.getSymbol(ASTHelpers.stripParentheses(tree.getExpression())))
                .flatMap(s -> resolveLock(s, callStack));
    }

    public static Optional<HeldLock> resolveLock(Symbol symbol, CallStack callStack) {
        return getLockOrder(symbol).map(o -> new HeldLock(symbol, o, callStack));
    }

    public static Optional<HeldLock> findGuardedBy(MethodTree tree, VisitorState state) {
        final MethodSymbol symbol = ASTHelpers.getSymbol(tree);

        final var resolver = GuardedBySymbolResolver.from(tree, state);
        final var callStack = new CallStack(state.getPath(), null);

        Optional<HeldLock> result = Optional.empty();
        for (String guard : getGuardValues(symbol)) {
            result = HeldLock.merge(
                    result,
                    GuardedByBinder.bindString(guard, resolver, GuardedByFlags.allOn())
                            .map(GuardedByExpression::sym)
                            .flatMap(sym -> resolveLock(sym, callStack)));
        }
        return result;
    }

    /**
     * Resolves @GuardedBy flags including the non-standard Android annotation.
     *
     * @see GuardedByUtils#getGuardValues(Symbol, GuardedByFlags)
     */
    private static ImmutableSet<String> getGuardValues(Symbol sym) {
        return sym.getRawAttributes().stream()
                .filter(a -> a.getAnnotationType().asElement().toString().endsWith(".GuardedBy"))
                .flatMap(
                    a ->
                        MoreAnnotations.getValue(a, "value")
                            .map(MoreAnnotations::asStrings)
                            .orElse(Stream.empty()))
                .collect(ImmutableSet.toImmutableSet());
    }

    /**
     * Resolves the order for a given lock, assuming it is a SystemServerLock.
     *
     * @return {@link Optional#empty()} if the lock symbol does not resolve to a SystemServerLock.
     */
    private static Optional<Integer> getLockOrder(Symbol symbol) {
        return symbol.getRawAttributes().stream()
                .filter(a -> a.type.tsym.getQualifiedName().toString().equals(ANNOTATION))
                .flatMap(a -> a.getElementValues().entrySet().stream()
                        .filter(e -> e.getKey().getSimpleName().toString().equals("value"))
                        .map(e -> (Integer) e.getValue().getValue()))
                .findAny();
    }
}
