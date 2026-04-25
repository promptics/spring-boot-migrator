package org.springframework.rewrite.boot.autoconfigure;

import java.util.function.Supplier;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.maven.cache.MavenPomCache;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.rewrite.scopes.ExecutionScope;
import org.springframework.rewrite.scopes.ProjectMetadata;
import org.springframework.rewrite.scopes.ScanScope;

@AutoConfiguration
public class ScopeConfiguration {
   @Bean
   ExecutionScope executionScope() {
      return new ExecutionScope();
   }

   @Bean
   ScanScope scanScope() {
      return new ScanScope();
   }

   @Bean
   public static BeanFactoryPostProcessor beanFactoryPostProcessor(ExecutionScope executionScope, ScanScope scanScope) {
      return beanFactory -> {
         beanFactory.registerScope("scanScope", scanScope);
         beanFactory.registerScope("executionScope", executionScope);
      };
   }

   @Bean
   @org.springframework.rewrite.scopes.annotations.ScanScope
   ProjectMetadata projectMetadata() {
      return new ProjectMetadata();
   }

   @Bean
   @ConditionalOnMissingBean(
      name = {"executionContextSupplier"}
   )
   Supplier<ExecutionContext> executionContextSupplier() {
      return () -> new InMemoryExecutionContext(t -> {
            throw new RuntimeException(t);
         });
   }

   @Bean
   @org.springframework.rewrite.scopes.annotations.ScanScope
   ExecutionContext executionContext(ProjectMetadata projectMetadata, Supplier<ExecutionContext> executionContextSupplier, MavenPomCache mavenPomCache) {
      return executionContextSupplier.get();
   }
}
