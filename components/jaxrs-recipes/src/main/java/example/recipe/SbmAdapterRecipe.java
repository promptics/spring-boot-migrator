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
package example.recipe;


import freemarker.template.Configuration;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.maven.MavenSettings;
import org.openrewrite.maven.cache.LocalMavenArtifactCache;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.rewrite.parser.JavaParserBuilder;
import org.springframework.rewrite.parser.maven.MavenSettingsInitializer;
import org.springframework.rewrite.parser.maven.RewriteMavenArtifactDownloader;
import org.springframework.rewrite.resource.*;
import org.springframework.rewrite.scopes.ProjectMetadata;
import org.springframework.sbm.build.impl.MavenBuildFileRefactoringFactory;
import org.springframework.sbm.build.impl.RewriteMavenParser;
import org.springframework.sbm.build.resource.BuildFileResourceWrapper;
import org.springframework.sbm.engine.context.ProjectContext;
import org.springframework.sbm.engine.context.ProjectContextFactory;
import org.springframework.sbm.engine.recipe.RewriteRecipeLoader;
import org.springframework.sbm.java.JavaSourceProjectResourceWrapper;
import org.springframework.sbm.java.refactoring.JavaRefactoringFactory;
import org.springframework.sbm.java.refactoring.JavaRefactoringFactoryImpl;
import org.springframework.sbm.java.util.BasePackageCalculator;
import org.springframework.sbm.jee.jaxrs.MigrateJaxRsRecipe;
import org.springframework.sbm.jee.jaxrs.actions.ConvertJaxRsAnnotations;
import org.springframework.sbm.project.resource.ProjectResourceSetHolder;
import org.springframework.sbm.project.resource.ProjectResourceWrapper;
import org.springframework.sbm.project.resource.ProjectResourceWrapperRegistry;
import org.springframework.sbm.project.resource.SbmApplicationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Fabian Krüger
 */
public class SbmAdapterRecipe extends ScanningRecipe<SbmAdapterRecipe.Accumulator> {

    private ProjectResourceSetFactory projectResourceSetFactory;
    private ProjectContextFactory projectContextFactory;

    /**
     * Holds inputs collected during scanning and the modified versions produced
     * by the wrapped SBM recipe. {@code modifiedInputs} is populated in
     * {@link #generate} and consumed by the visitor returned from
     * {@link #getVisitor} so that mutations to existing sources flow through
     * OR's expected pipeline (modified inputs via getVisitor, new sources via
     * generate). Required since OR 8.80.1's LargeSourceSetCheckingExpectedCycles
     * asserts that generate() only returns newly-synthesized paths.
     */
    public static class Accumulator {
        final List<SourceFile> collectedInputs = new ArrayList<>();
        final Map<Path, SourceFile> modifiedInputs = new HashMap<>();
    }

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "SBM JAX-RS adapter";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Adapter that runs the SBM MigrateJaxRsRecipe action through the OpenRewrite ScanningRecipe pipeline.";
    }

    public SbmAdapterRecipe() {

    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
                if(tree instanceof SourceFile) {
                    acc.collectedInputs.add((SourceFile) tree);
                }
                return super.visit(tree, executionContext);
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext executionContext) {
        // create the required classes
        initBeans();

        List<SourceFile> sourceFiles = acc.collectedInputs;

        // FIXME: base dir calculation is fake
        Path baseDir = Path.of(".").resolve("testcode/jee/jaxrs/bootify-jaxrs/given").toAbsolutePath().normalize(); //executionContext.getMessage("base.dir");

        // Create the SBM resource set abstraction
        ProjectResourceSet projectResourceSet = projectResourceSetFactory.create(baseDir, sourceFiles);
        // Create the SBM ProjectContext
        ProjectContext pc = projectContextFactory.createProjectContext(baseDir, projectResourceSet);

        // Execute the SBM Action = the JAXRS Recipe
//        new ConvertJaxRsAnnotations().apply(pc);

        RewriteRecipeLoader recipeLoader = new RewriteRecipeLoader();
        new MigrateJaxRsRecipe().jaxRs(recipeLoader).apply(pc);

        java.util.Set<Path> inputPaths = sourceFiles.stream()
                .map(SourceFile::getSourcePath)
                .collect(java.util.stream.Collectors.toSet());

        List<SourceFile> newSources = new ArrayList<>();
        for (org.springframework.rewrite.resource.RewriteSourceFileHolder<?> pr : pc.getProjectResources().list()) {
            SourceFile sf = pr.getSourceFile();
            if (inputPaths.contains(sf.getSourcePath())) {
                acc.modifiedInputs.put(sf.getSourcePath(), sf);
            } else {
                newSources.add(sf);
            }
        }
        return newSources;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext executionContext) {
                if (tree instanceof SourceFile sf) {
                    SourceFile modified = acc.modifiedInputs.get(sf.getSourcePath());
                    if (modified != null) {
                        return modified;
                    }
                }
                return tree;
            }
        };
    }

    private void initBeans() {
        // ExecutionContext is not retrieved from OpenRewrite here
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext("org.springframework.freemarker", "org.springframework.sbm", "org.springframework.rewrite");
        ctx.register(Configuration.class);
        this.projectContextFactory = ctx.getBean(ProjectContextFactory.class);
        this.projectResourceSetFactory = ctx.getBean(ProjectResourceSetFactory.class);
    }

}
