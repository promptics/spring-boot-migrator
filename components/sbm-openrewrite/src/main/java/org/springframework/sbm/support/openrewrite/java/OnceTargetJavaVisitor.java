/*
 * Copyright 2021 - 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.sbm.support.openrewrite.java;

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * Stage-1 crutch for the OpenRewrite 7 -&gt; 8 migration.
 *
 * <p>Pre-OR-8 SBM visitors held a raw target {@link J} node in a field and matched it in
 * {@code visitX} via {@code target.getId().equals(node.getId())}. That worked because
 * pre-OR-8 {@code with*()} operations returned a new element with a fresh {@link java.util.UUID}.
 * OR 8.1+ preserves the id across {@code with*()}, so the same check now matches again on
 * every subsequent visit of the transformed node and the transformation runs in a loop.</p>
 *
 * <p>This base class centralizes the {@code targetVisited} boolean workaround that individual
 * visitors were adding. Subclasses call {@link #matchesTarget(J)} once per candidate; it
 * returns {@code true} exactly the first time the original target is seen, flips an internal
 * flag, and returns {@code false} thereafter.</p>
 *
 * <p><b>Do not add new callers.</b> This is a temporary helper; the proper OR-8 patterns are
 * {@code ScanningRecipe}, {@code Preconditions.check(...)} and {@code SearchResult} markers.
 * See {@code docs/stage2-scanningrecipe-migration.md}.</p>
 */
public abstract class OnceTargetJavaVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final J target;
    private boolean visited;

    protected OnceTargetJavaVisitor(J target) {
        this.target = target;
    }

    /**
     * Returns {@code true} exactly once, on the first visit of the node that was supplied to
     * the constructor. Marks the target as visited as a side effect.
     */
    protected final boolean matchesTarget(J node) {
        if (visited || node == null || target == null) {
            return false;
        }
        if (!target.getId().equals(node.getId())) {
            return false;
        }
        visited = true;
        return true;
    }

    protected final J getTarget() {
        return target;
    }
}
