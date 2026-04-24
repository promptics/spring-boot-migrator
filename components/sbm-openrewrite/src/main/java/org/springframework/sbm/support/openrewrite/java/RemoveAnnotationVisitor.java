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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @deprecated Use {@link RemoveAnnotationRecipe} instead. The recipe variant is a proper
 *     two-phase {@code ScanningRecipe} and is idempotent under the OR 8.1+ id-preserving
 *     {@code with*()} semantics.
 */
@Deprecated(forRemoval = true)
public class RemoveAnnotationVisitor extends OnceTargetJavaVisitor {

    private final String fqAnnotationName;

    public RemoveAnnotationVisitor(J target, String fqAnnotationName) {
        super(target);
        this.fqAnnotationName = fqAnnotationName;
    }

    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext executionContext) {
        J.ClassDeclaration classDecl = super.visitClassDeclaration(cd, executionContext);
        if (matchesTarget(classDecl)) {
            List<J.Annotation> keptAnnotations = classDecl.getLeadingAnnotations()
                    .stream()
                    .filter(a -> {
                        FullyQualified fullyQualified = TypeUtils.asFullyQualified(a.getType());
                        return fullyQualified == null || !fullyQualified.getFullyQualifiedName().equals(fqAnnotationName);
                    })
                    .collect(Collectors.toList());
            if (classDecl.getLeadingAnnotations().size() != keptAnnotations.size()) {
                // TODO: Analyze annotation for more types referenced by annotation and maybeRemoveImports, then remove the call to removeUnusedImports in MigrateJeeTransactionsToSpringBootAction
                maybeRemoveImport(fqAnnotationName);
                classDecl = classDecl.withLeadingAnnotations(keptAnnotations);
            }
        }
        return classDecl;
    }

    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext executionContext) {
        J.MethodDeclaration methodDecl = super.visitMethodDeclaration(md, executionContext);
        if (matchesTarget(methodDecl)) {
            List<J.Annotation> annotations = methodDecl.getLeadingAnnotations()
                    .stream()
                    .filter(a -> {
                        FullyQualified fullyQualified = TypeUtils.asFullyQualified(a.getType());
                        return fullyQualified == null || !fullyQualified.getFullyQualifiedName().equals(fqAnnotationName);
                    })
                    .collect(Collectors.toList());
            if (methodDecl.getLeadingAnnotations().size() != annotations.size()) {
                maybeRemoveImport(fqAnnotationName);
                methodDecl = methodDecl.withLeadingAnnotations(annotations);
            }
        }
        return methodDecl;
    }

    @Override
    public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations mv, ExecutionContext executionContext) {
        J.VariableDeclarations multiVariable = super.visitVariableDeclarations(mv, executionContext);
        if (matchesTarget(multiVariable)) {
            List<J.Annotation> annotations = multiVariable.getLeadingAnnotations().stream()
                    .filter(a -> {
                        FullyQualified fullyQualified = TypeUtils.asFullyQualified(a.getType());
                        return fullyQualified == null || !fullyQualified.getFullyQualifiedName().equals(fqAnnotationName);
                    })
                    .collect(Collectors.toList());
            if (multiVariable.getLeadingAnnotations().size() != annotations.size()) {
                maybeRemoveImport(fqAnnotationName);
                multiVariable = multiVariable.withLeadingAnnotations(annotations);
            }
        }
        return multiVariable;
    }

}
