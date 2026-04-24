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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adds or replaces an attribute on the annotation identified by the given target
 * {@link UUID}.
 *
 * <p>Two-phase {@link ScanningRecipe}: the scanner collects the target id; the visitor
 * rewrites the annotation template and removes the id from the accumulator, so applying
 * the recipe twice is a no-op on the second pass.</p>
 *
 * <p>Replaces the pre-OR-8 {@link AddOrReplaceAnnotationAttribute} visitor. See
 * {@code docs/stage2-scanningrecipe-migration.md}.</p>
 */
public class AddOrReplaceAnnotationAttributeRecipe extends ScanningRecipe<Set<UUID>> {

    private final UUID targetAnnotationId;
    private final String attribute;
    private final Object value;
    private final Class<?> valueType;
    private final Supplier<JavaParser.Builder> javaParserBuilderSupplier;

    public AddOrReplaceAnnotationAttributeRecipe(Supplier<JavaParser.Builder> javaParserBuilderSupplier, J.Annotation targetAnnotation, String attribute, Object value, Class<?> valueType) {
        this.targetAnnotationId = targetAnnotation.getId();
        this.attribute = attribute.trim();
        this.value = value;
        this.valueType = valueType;
        this.javaParserBuilderSupplier = javaParserBuilderSupplier;
    }

    public AddOrReplaceAnnotationAttributeRecipe(J.Annotation targetAnnotation, String attribute, Object value, Class<?> valueType) {
        this(JavaParser::fromJavaVersion, targetAnnotation, attribute, value, valueType);
    }

    @Override
    public String getDisplayName() {
        return "Add or replace an annotation attribute on a specific annotation";
    }

    @Override
    public String getDescription() {
        return "Adds or replaces attribute `" + attribute + "` on the annotation identified by its tree id.";
    }

    @Override
    public Set<UUID> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<UUID> acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                if (targetAnnotationId.equals(annotation.getId())) {
                    acc.add(annotation.getId());
                }
                return super.visitAnnotation(annotation, ctx);
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<UUID> acc) {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                if (!acc.remove(annotation.getId())) {
                    return super.visitAnnotation(annotation, ctx);
                }
                String templateString = renderTemplateString(annotation);
                JavaTemplate template = JavaTemplate.builder(templateString)
                        .javaParser(javaParserBuilderSupplier.get())
                        .build();
                return template.apply(getCursor(), annotation.getCoordinates().replace(), new Object[]{});
            }
        };
    }

    private String renderTemplateString(J.Annotation annotation) {
        boolean attributeHandled = false;
        List<Expression> annotationArguments = annotation.getArguments();

        StringBuilder templateString = new StringBuilder("@" + annotation.getSimpleName());
        templateString.append("(");
        if (hasArguments(annotationArguments)) {
            for (Expression exp : annotationArguments) {
                if (exp.getClass().isAssignableFrom(J.Assignment.class)) {
                    J.Assignment assignment = (J.Assignment) exp;
                    if (assignment.getVariable().print().equals(attribute)) {
                        attributeHandled = true;
                        renderAttribute(templateString);
                    } else {
                        templateString.append(assignment.printTrimmed());
                    }
                    if (hasMoreElements(annotationArguments, exp)) {
                        templateString.append(", ");
                    }
                }
            }
            if (!attributeHandled) {
                templateString.append(", ");
            }
        }
        if (!attributeHandled) {
            renderAttribute(templateString);
        }
        templateString.append(")");
        return templateString.toString();
    }

    private boolean hasArguments(List<Expression> annotationArguments) {
        return annotationArguments != null && !annotationArguments.isEmpty();
    }

    private void renderAttribute(StringBuilder templateString) {
        templateString.append(attribute.trim());
        templateString.append(" = ");
        templateString.append(renderValue(value, valueType));
    }

    private boolean hasMoreElements(List<Expression> annotationArguments, Expression exp) {
        return annotationArguments.indexOf(exp) < annotationArguments.size() - 1;
    }

    private String renderValue(Object value, Class<?> valueType) {
        if (valueType == String.class) {
            return "\"" + value.toString().trim() + "\"";
        } else if (value == Character.class) {
            return "'" + value + "'";
        }
        return value.toString();
    }
}
