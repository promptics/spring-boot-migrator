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


import org.jetbrains.annotations.NotNull;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.springframework.rewrite.parser.JavaParserBuilder;
import org.springframework.rewrite.resource.ProjectResourceSet;
import org.springframework.rewrite.resource.ProjectResourceSetFactory;
import org.springframework.rewrite.resource.RewriteMigrationResultMerger;
import org.springframework.rewrite.resource.RewriteSourceFileWrapper;
import org.springframework.sbm.engine.context.ProjectContext;
import org.springframework.sbm.engine.context.ProjectContextFactory;
import org.springframework.sbm.java.refactoring.JavaRefactoringFactory;
import org.springframework.sbm.java.refactoring.JavaRefactoringFactoryImpl;
import org.springframework.sbm.java.util.BasePackageCalculator;
import org.springframework.sbm.jee.jaxrs.actions.ConvertJaxRsAnnotations;
import org.springframework.sbm.project.resource.ProjectResourceSetHolder;
import org.springframework.sbm.project.resource.ProjectResourceWrapper;
import org.springframework.sbm.project.resource.ProjectResourceWrapperRegistry;
import org.springframework.sbm.project.resource.SbmApplicationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Fabian Krüger
 */
public class SbmAdapterRecipe extends Recipe {



    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "";
    }

    public SbmAdapterRecipe() {

    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {

            private ProjectResourceSetFactory projectResourceSetFactory;
            private ProjectContextFactory projectContextFactory;

            @Override
            public void visit(@Nullable List<? extends Tree> nodes, ExecutionContext executionContext) {

                // create the required classes
                initBeans(executionContext);

                // transform nodes to SourceFiles
                List<SourceFile> sourceFiles = nodes.stream().filter(SourceFile.class::isInstance).map(SourceFile.class::cast).toList();

                // FIXME: base dir calculation is fake
                Path baseDir = executionContext.getMessage("base.dir");

                // Create the SBM resource set abstraction
                ProjectResourceSet projectResourceSet = projectResourceSetFactory.create(baseDir, sourceFiles);
                // Create the SBM ProjectContext
                ProjectContext pc = projectContextFactory.createProjectContext(baseDir, projectResourceSet);

                // Execute the SBM Action = the JAXRS Recipe
                new ConvertJaxRsAnnotations().apply(pc);

                // Merge back result
                List<? extends Tree> modifiedNodes = merge(nodes, pc.getProjectResources());

                // Process other
                super.visit(modifiedNodes, executionContext);
            }

            private void initBeans(ExecutionContext executionContext) {
                RewriteSourceFileWrapper sourceFileWrapper = new RewriteSourceFileWrapper();
                SbmApplicationProperties sbmApplicationProperties = new SbmApplicationProperties();
                JavaParserBuilder parserBuilder = new JavaParserBuilder();
                List<ProjectResourceWrapper> projectResourceWrappers = new ArrayList<>();

                RewriteMigrationResultMerger merger = new RewriteMigrationResultMerger(sourceFileWrapper);
                ProjectResourceSetHolder holder = new ProjectResourceSetHolder(executionContext, merger);

                projectResourceSetFactory = new ProjectResourceSetFactory(new RewriteMigrationResultMerger(sourceFileWrapper), sourceFileWrapper, executionContext);
                ProjectResourceWrapperRegistry registry = new ProjectResourceWrapperRegistry(projectResourceWrappers);
                JavaRefactoringFactory refactoringFactory = new JavaRefactoringFactoryImpl(holder, executionContext);
                BasePackageCalculator calculator = new BasePackageCalculator(sbmApplicationProperties);

                ProjectResourceSetFactory resourceSetFactory = new ProjectResourceSetFactory(merger, sourceFileWrapper, executionContext);

                projectContextFactory = new ProjectContextFactory(registry, holder, refactoringFactory, calculator, parserBuilder, executionContext, merger, resourceSetFactory);
            }
        };
    }

    private List<? extends Tree> merge(List<? extends Tree> nodes, ProjectResourceSet projectResources) {
        // merge the changed results into the given list and return the result
        return new ArrayList<>();
    }

}
