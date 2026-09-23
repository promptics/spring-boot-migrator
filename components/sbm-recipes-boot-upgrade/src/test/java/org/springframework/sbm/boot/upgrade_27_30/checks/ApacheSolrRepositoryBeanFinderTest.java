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
package org.springframework.sbm.boot.upgrade_27_30.checks;


import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.springframework.sbm.engine.context.ProjectContext;
import org.springframework.sbm.java.api.JavaSourceAndType;
import org.springframework.sbm.java.api.Type;
import org.springframework.sbm.project.resource.TestProjectContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ApacheSolrRepositoryBeanFinderTest {

    // The finder matches on the declared interface's FQN, so a stub parsed alongside the
    // sources under test resolves it without downloading the real, since-removed artifact.
    @Language("java")
    private static String SOLR_CRUD_REPOSITORY =
            """
            package org.springframework.data.solr.repository;
            public interface SolrCrudRepository<T, ID> {}
            """;

    @Language("java")
    private static String PRODUCT =
            """
            package foo.bar;
            public class Product {}
            """;

    @Language("java")
    private static String SOLR_REPO =
            """
            package foo.bar;
            import org.springframework.data.solr.repository.SolrCrudRepository;
            public class ProductRepository implements SolrCrudRepository<Product, String> {}
            """;

    @Language("java")
    private static String NO_SOLR_REPO =
            """
            package foo.bar;
            public class ProductRepository {}
            """;

    @Test
    public void givenModuleWithSolrRepository_find_expectNonEmptyList(){
        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                                                            .withJavaSources(SOLR_CRUD_REPOSITORY, PRODUCT, SOLR_REPO)
                                                            .build();

        ApacheSolrRepositoryBeanFinder solrRepositoryBeanFinder = new ApacheSolrRepositoryBeanFinder();
        List<JavaSourceAndType> solrRepositories = projectContext.search(solrRepositoryBeanFinder);
        assertThat(solrRepositories).isNotEmpty();
        Type type = solrRepositories.get(0).getType();
        assertThat(type.getFullyQualifiedName()).isEqualTo("foo.bar.ProductRepository");
    }

    @Test
    public void givenModuleWithoutSolrRepository_find_expectNonEmptyList(){
        ProjectContext projectContext = TestProjectContext.buildProjectContext()
                                                            .withJavaSources(NO_SOLR_REPO)
                                                            .build();

        ApacheSolrRepositoryBeanFinder solrRepositoryBeanFinder = new ApacheSolrRepositoryBeanFinder();
        List<JavaSourceAndType> solrRepositories = projectContext.search(solrRepositoryBeanFinder);
        assertThat(solrRepositories).isEmpty();
    }
}
