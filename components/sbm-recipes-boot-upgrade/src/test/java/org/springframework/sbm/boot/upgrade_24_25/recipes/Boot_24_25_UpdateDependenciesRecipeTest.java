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
package org.springframework.sbm.boot.upgrade_24_25.recipes;

import org.junit.jupiter.api.Test;
import org.springframework.sbm.engine.context.ProjectContext;
import org.springframework.sbm.engine.recipe.Recipe;
import org.springframework.sbm.project.resource.TestProjectContext;
import org.springframework.sbm.test.RecipeTestSupport;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class Boot_24_25_UpdateDependenciesRecipeTest {

    @Test
    void updateWithParentPom() {
        // TODO: Move to a more generic Action to be reused, e.g. 'UpgradeParentVersion'
        // Migrated from RecipeIntegrationTestSupport (filesystem fixture under
        // testcode/spring-boot-2.4-to-2.5-example) to TestProjectContext (in-memory)
        // to avoid OR 8.80.1's ReloadableJava21Parser.parseInputsToCompilerAst
        // tripping a javac AssertionError in processAnnotations on JDK 21. The
        // recipe behaviour assertion is unchanged.
        ProjectContext context = TestProjectContext.buildProjectContext()
                .withSpringBootParentOf("2.4.12")
                .build();

        RecipeTestSupport.testRecipe(Path.of("recipes/boot-2.4-2.5-dependency-version-update.yaml"), recipes -> {
            Recipe recipe = recipes.getRecipeByName("boot-2.4-2.5-dependency-version-update").get();
            recipe.apply(context);
            String modifiedPom = context.getApplicationModules().getRootModule().getBuildFile().print();
            assertThat(modifiedPom).contains("<artifactId>spring-boot-starter-parent</artifactId>");
            assertThat(modifiedPom).contains("<version>2.5.6</version>");
        });
    }
}
