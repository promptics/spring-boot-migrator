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
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Removes an annotation from the class / method / variable declaration identified by the
 * given target {@link UUID}.
 *
 * <p>Two-phase {@link ScanningRecipe}: the scanner collects the target id into the accumulator
 * if it is still present in the source; the visitor removes the annotation and also removes
 * the id from the accumulator, so applying the recipe twice is a no-op on the second pass.</p>
 *
 * <p>Replaces the pre-OR-8 {@link RemoveAnnotationVisitor} which relied on a raw
 * {@code target.getId().equals(node.getId())} check. OR 8.1+ preserves element ids across
 * {@code with*()} operations, which defeats that check. See
 * {@code docs/stage2-scanningrecipe-migration.md}.</p>
 */
public class RemoveAnnotationRecipe extends ScanningRecipe<Set<UUID>> {

    private final UUID targetId;
    private final String fqAnnotationName;

    public RemoveAnnotationRecipe(J target, String fqAnnotationName) {
        this(target.getId(), fqAnnotationName);
    }

    public RemoveAnnotationRecipe(UUID targetId, String fqAnnotationName) {
        this.targetId = targetId;
        this.fqAnnotationName = fqAnnotationName;
    }

    @Override
    public String getDisplayName() {
        return "Remove annotation from a specific element";
    }

    @Override
    public String getDescription() {
        return "Removes the annotation `" + fqAnnotationName + "` from the element identified by its tree id.";
    }

    @Override
    public Set<UUID> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<UUID> acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                if (targetId.equals(cd.getId())) {
                    acc.add(cd.getId());
                }
                return super.visitClassDeclaration(cd, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                if (targetId.equals(md.getId())) {
                    acc.add(md.getId());
                }
                return super.visitMethodDeclaration(md, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations vd, ExecutionContext ctx) {
                if (targetId.equals(vd.getId())) {
                    acc.add(vd.getId());
                }
                return super.visitVariableDeclarations(vd, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<UUID> acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                J.ClassDeclaration classDecl = super.visitClassDeclaration(cd, ctx);
                if (acc.remove(classDecl.getId())) {
                    classDecl = stripAnnotations(classDecl);
                }
                return classDecl;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration methodDecl = super.visitMethodDeclaration(md, ctx);
                if (acc.remove(methodDecl.getId())) {
                    methodDecl = stripMethodAnnotations(methodDecl);
                }
                return methodDecl;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations mv, ExecutionContext ctx) {
                J.VariableDeclarations multiVariable = super.visitVariableDeclarations(mv, ctx);
                if (acc.remove(multiVariable.getId())) {
                    multiVariable = stripVariableAnnotations(multiVariable);
                }
                return multiVariable;
            }

            private J.ClassDeclaration stripAnnotations(J.ClassDeclaration cd) {
                List<J.Annotation> kept = filterOutMatching(cd.getLeadingAnnotations());
                if (cd.getLeadingAnnotations().size() == kept.size()) {
                    return cd;
                }
                maybeRemoveImport(fqAnnotationName);
                return cd.withLeadingAnnotations(kept);
            }

            private J.MethodDeclaration stripMethodAnnotations(J.MethodDeclaration md) {
                List<J.Annotation> kept = filterOutMatching(md.getLeadingAnnotations());
                if (md.getLeadingAnnotations().size() == kept.size()) {
                    return md;
                }
                maybeRemoveImport(fqAnnotationName);
                return md.withLeadingAnnotations(kept);
            }

            private J.VariableDeclarations stripVariableAnnotations(J.VariableDeclarations vd) {
                List<J.Annotation> kept = filterOutMatching(vd.getLeadingAnnotations());
                if (vd.getLeadingAnnotations().size() == kept.size()) {
                    return vd;
                }
                maybeRemoveImport(fqAnnotationName);
                return vd.withLeadingAnnotations(kept);
            }

            private List<J.Annotation> filterOutMatching(List<J.Annotation> annotations) {
                return annotations.stream()
                        .filter(a -> {
                            FullyQualified fq = TypeUtils.asFullyQualified(a.getType());
                            return fq == null || !fq.getFullyQualifiedName().equals(fqAnnotationName);
                        })
                        .collect(Collectors.toList());
            }
        };
    }
}
