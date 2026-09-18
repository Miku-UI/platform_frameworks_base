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
import com.sun.tools.javac.code.Symbol;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class HeldLock {
    final Symbol symbol;
    final int order;

    // Informational-only (not used for comparisons). The call stack is used for printing
    // an actionable error message, and the "also-held" sets are a heuristic to avoid
    // mis-detecting every re-entrant lock as a problem.
    final CallStack callStack;
    final Set<Symbol> alsoHeld;
    final Set<Integer> alsoHeldOrder;

    public HeldLock(Symbol symbol, int order, CallStack callStack) {
        this(symbol, order, callStack, Collections.singleton(symbol),
                Collections.singleton(order));
    }

    public HeldLock(Symbol symbol, int order, CallStack callStack, Set<Symbol> alsoHeld,
            Set<Integer> alsoHeldOrder) {
        this.symbol = symbol;
        this.order = order;
        this.callStack = callStack;
        this.alsoHeld = alsoHeld;
        this.alsoHeldOrder = alsoHeldOrder;
    }

    HeldLock alsoHolding(Set<Symbol> also, Set<Integer> alsoOrder) {
        if (alsoHeld.containsAll(also) && alsoHeldOrder.containsAll(alsoOrder)) {
            return this;
        }
        return new HeldLock(symbol, order, callStack,
                new ImmutableSet.Builder<Symbol>().addAll(alsoHeld).addAll(also).build(),
                new ImmutableSet.Builder<Integer>().addAll(alsoHeldOrder).addAll(alsoOrder)
                        .build());
    }

    static Optional<HeldLock> merge(Optional<HeldLock> a, Optional<HeldLock> b) {
        if (!a.isPresent()) return b;
        if (!b.isPresent()) return a;
        return Optional.of(a.get().order < b.get().order
            ? b.get().alsoHolding(a.get().alsoHeld, a.get().alsoHeldOrder)
            : a.get().alsoHolding(b.get().alsoHeld, b.get().alsoHeldOrder));
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HeldLock hlo) {
            return (o == this) || (Objects.equals(symbol, hlo.symbol) && order == hlo.order);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, order);
    }
}
