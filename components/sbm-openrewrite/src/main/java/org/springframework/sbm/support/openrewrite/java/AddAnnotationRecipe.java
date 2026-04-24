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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Adds an annotation to the class / method / variable declaration identified by the given
 * target {@link UUID}.
 *
 * <p>Two-phase {@link ScanningRecipe}: the scanner collects the target id into the
 * accumulator if it is still present in the source; the visitor applies the annotation
 * template and also removes the id from the accumulator, so applying the recipe twice is a
 * no-op on the second pass.</p>
 *
 * <p>Replaces the pre-OR-8 {@link AddAnnotationVisitor} which relied on a raw
 * {@code target.getId().equals(node.getId())} check plus a {@code targetVisited} boolean
 * flag. OR 8.1+ preserves element ids across {@code with*()} operations, which defeats that
 * check. See {@code docs/stage2-scanningrecipe-migration.md}.</p>
 */
public class AddAnnotationRecipe extends ScanningRecipe<Set<UUID>> {

    private final UUID targetId;
    private final String snippet;
    private final String[] imports;
    private final Supplier<JavaParser.Builder> javaParserBuilderSupplier;

    public AddAnnotationRecipe(JavaParser.Builder javaParserBuilder, J target, String snippet, String annotationImport, String... otherImports) {
        this(() -> javaParserBuilder, target.getId(), snippet, annotationImport, otherImports);
    }

    public AddAnnotationRecipe(J target, String snippet, String annotationImport, String... otherImports) {
        this(() -> null, target.getId(), snippet, annotationImport, otherImports);
    }

    public AddAnnotationRecipe(Supplier<JavaParser.Builder> javaParserBuilderSupplier, UUID targetId, String snippet, String annotationImport, String... otherImports) {
        this.targetId = targetId;
        this.snippet = snippet;
        this.imports = otherImports == null
                ? new String[]{annotationImport}
                : concat(annotationImport, otherImports);
        this.javaParserBuilderSupplier = javaParserBuilderSupplier;
    }

    @Override
    public String getDisplayName() {
        return "Add an annotation to a specific element";
    }

    @Override
    public String getDescription() {
        return "Adds the annotation `" + snippet + "` to the element identified by its tree id.";
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
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext p) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, p);
                if (acc.remove(cd.getId())) {
                    JavaTemplate template = buildTemplate();
                    addImports();
                    JavaCoordinates coordinates = cd.getCoordinates().addAnnotation((o1, o2) -> 0);
                    cd = template.apply(getCursor(), coordinates);
                }
                return cd;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, ExecutionContext p) {
                J.MethodDeclaration md = super.visitMethodDeclaration(methodDecl, p);
                if (acc.remove(md.getId())) {
                    JavaTemplate template = buildTemplate();
                    addImports();
                    md = template.apply(getCursor(), md.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                }
                return md;
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext p) {
                J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, p);
                if (acc.remove(vd.getId())) {
                    JavaTemplate template = buildTemplate();
                    addImports();
                    vd = template.apply(getCursor(), vd.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
                }
                return vd;
            }

            private JavaTemplate buildTemplate() {
                return JavaTemplate.builder(snippet)
                        .imports(imports)
                        .build();
            }

            private void addImports() {
                Stream.of(imports).forEach(i -> maybeAddImport(i, null, false));
            }
        };
    }

    private static String[] concat(String annotationImport, String[] otherImports) {
        String[] result = new String[otherImports.length + 1];
        result[0] = annotationImport;
        System.arraycopy(otherImports, 0, result, 1, otherImports.length);
        return result;
    }
}
