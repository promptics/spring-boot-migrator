package org.springframework.rewrite.boot.autoconfigure;

import org.openrewrite.ExecutionContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.rewrite.resource.ProjectResourceSerializer;
import org.springframework.rewrite.resource.ProjectResourceSetFactory;
import org.springframework.rewrite.resource.ProjectResourceSetSerializer;
import org.springframework.rewrite.resource.RewriteMigrationResultMerger;
import org.springframework.rewrite.resource.RewriteSourceFileWrapper;

@AutoConfiguration
public class ProjectResourceSetConfiguration {
   @Bean
   RewriteSourceFileWrapper rewriteSourceFileWrapper() {
      return new RewriteSourceFileWrapper();
   }

   @Bean
   RewriteMigrationResultMerger rewriteMigrationResultMerger(RewriteSourceFileWrapper rewriteSourceFileWrapper) {
      return new RewriteMigrationResultMerger(rewriteSourceFileWrapper);
   }

   @Bean
   ProjectResourceSerializer projectResourceSerializer() {
      return new ProjectResourceSerializer();
   }

   @Bean
   ProjectResourceSetSerializer projectResourceSetSerializer(ProjectResourceSerializer resourceSerializer) {
      return new ProjectResourceSetSerializer(resourceSerializer);
   }

   @Bean
   ProjectResourceSetFactory projectResourceSetFactory(
      RewriteMigrationResultMerger rewriteMigrationResultMerger, RewriteSourceFileWrapper sourceFileWrapper, ExecutionContext executionContext
   ) {
      return new ProjectResourceSetFactory(rewriteMigrationResultMerger, sourceFileWrapper, executionContext);
   }
}
