package org.springframework.rewrite.parser.maven;

import java.nio.file.Paths;
import java.util.function.Consumer;
import org.openrewrite.ExecutionContext;
import org.openrewrite.maven.cache.LocalMavenArtifactCache;
import org.openrewrite.maven.cache.MavenArtifactCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.rewrite.boot.autoconfigure.ScopeConfiguration;
import org.springframework.rewrite.scopes.ProjectMetadata;

@AutoConfiguration
@Import({ScopeConfiguration.class})
public class RewriteParserMavenConfiguration {
   @Bean
   MavenProvenanceMarkerFactory mavenProvenanceMarkerFactory() {
      return new MavenProvenanceMarkerFactory();
   }

   @Bean
   MavenBuildFileParser buildFileParser(MavenSettingsInitializer mavenSettingsInitializer) {
      return new MavenBuildFileParser(mavenSettingsInitializer);
   }

   @Bean
   RewriteMavenArtifactDownloader artifactDownloader(
      MavenArtifactCache mavenArtifactCache, ProjectMetadata projectMetadata, Consumer<Throwable> artifactDownloaderErrorConsumer
   ) {
      return new RewriteMavenArtifactDownloader(mavenArtifactCache, projectMetadata.getMavenSettings(), artifactDownloaderErrorConsumer);
   }

   @Bean
   @ConditionalOnMissingBean({MavenArtifactCache.class})
   MavenArtifactCache mavenArtifactCache() {
      return new LocalMavenArtifactCache(Paths.get(System.getProperty("user.home"), ".m2", "repository"))
         .orElse(new LocalMavenArtifactCache(Paths.get(System.getProperty("user.home"), ".rewrite", "cache", "artifacts")));
   }

   @Bean
   MavenSettingsInitializer mavenSettingsInitializer(ExecutionContext executionContext, ProjectMetadata projectMetadata) {
      return new MavenSettingsInitializer(executionContext, projectMetadata);
   }
}
