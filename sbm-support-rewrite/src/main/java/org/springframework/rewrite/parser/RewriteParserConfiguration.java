package org.springframework.rewrite.parser;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.openrewrite.ExecutionContext;
import org.openrewrite.maven.cache.CompositeMavenPomCache;
import org.openrewrite.maven.cache.InMemoryMavenPomCache;
import org.openrewrite.maven.cache.MavenPomCache;
import org.openrewrite.maven.cache.RocksdbMavenPomCache;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;
import org.openrewrite.tree.ParsingEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.springframework.rewrite.RewriteProjectParser;
import org.springframework.rewrite.boot.autoconfigure.ProjectResourceSetConfiguration;
import org.springframework.rewrite.boot.autoconfigure.ScopeConfiguration;
import org.springframework.rewrite.parser.events.RewriteParsingEventListenerAdapter;
import org.springframework.rewrite.parser.maven.MavenBuildFileParser;
import org.springframework.rewrite.parser.maven.MavenModuleParser;
import org.springframework.rewrite.parser.maven.MavenProjectAnalyzer;
import org.springframework.rewrite.parser.maven.MavenProjectFactory;
import org.springframework.rewrite.parser.maven.MavenProjectGraph;
import org.springframework.rewrite.parser.maven.MavenProjectSorter;
import org.springframework.rewrite.parser.maven.MavenProvenanceMarkerFactory;
import org.springframework.rewrite.parser.maven.ProvenanceMarkerFactory;
import org.springframework.rewrite.parser.maven.RewriteParserMavenConfiguration;
import org.springframework.rewrite.scopes.ScanScope;

@AutoConfiguration(
   after = {ScopeConfiguration.class}
)
@EnableConfigurationProperties({SpringRewriteProperties.class})
@Import({ScanScope.class, ScopeConfiguration.class, RewriteParserMavenConfiguration.class, ProjectResourceSetConfiguration.class})
public class RewriteParserConfiguration {
   private static final Logger LOGGER = LoggerFactory.getLogger(RewriteParserConfiguration.class);

   @Bean
   ProjectScanner projectScanner(ResourceLoader resourceLoader, SpringRewriteProperties springRewriteProperties) {
      return new ProjectScanner(resourceLoader, springRewriteProperties);
   }

   @Bean
   ProvenanceMarkerFactory provenanceMarkerFactory(MavenProvenanceMarkerFactory mavenPovenanceMarkerFactory) {
      return new ProvenanceMarkerFactory(mavenPovenanceMarkerFactory);
   }

   @Bean
   @org.springframework.rewrite.scopes.annotations.ScanScope
   JavaParserBuilder javaParserBuilder() {
      return new JavaParserBuilder();
   }

   @Bean
   Consumer<Throwable> artifactDownloaderErrorConsumer() {
      return t -> {
         throw new RuntimeException(t);
      };
   }

   @Bean
   MavenModuleParser mavenModuleParser(SpringRewriteProperties springRewriteProperties) {
      return new MavenModuleParser(springRewriteProperties);
   }

   @Bean
   SourceFileParser sourceFileParser(MavenModuleParser mavenModuleParser) {
      return new SourceFileParser(mavenModuleParser);
   }

   @Bean
   StyleDetector styleDetector() {
      return new StyleDetector();
   }

   @Bean
   @ConditionalOnMissingBean({ParsingEventListener.class})
   ParsingEventListener parsingEventListener(ApplicationEventPublisher eventPublisher) {
      return new RewriteParsingEventListenerAdapter(eventPublisher);
   }

   @Bean
   MavenProjectAnalyzer mavenProjectAnalyzer(MavenArtifactDownloader artifactDownloader) {
      MavenProjectGraph mavenProjectGraph = new MavenProjectGraph();
      MavenProjectSorter mavenProjectSorter = new MavenProjectSorter(mavenProjectGraph);
      MavenProjectFactory mavenProjectFactory = new MavenProjectFactory(artifactDownloader);
      return new MavenProjectAnalyzer(mavenProjectSorter, mavenProjectFactory);
   }

   @Bean
   RewriteProjectParser rewriteProjectParser(
      ProvenanceMarkerFactory provenanceMarkerFactory,
      MavenBuildFileParser buildFileParser,
      SourceFileParser sourceFileParser,
      StyleDetector styleDetector,
      SpringRewriteProperties springRewriteProperties,
      ParsingEventListener parsingEventListener,
      ApplicationEventPublisher eventPublisher,
      ScanScope scanScope,
      ConfigurableListableBeanFactory beanFactory,
      ProjectScanner projectScanner,
      ExecutionContext executionContext,
      MavenProjectAnalyzer mavenProjectAnalyzer
   ) {
      return new RewriteProjectParser(
         provenanceMarkerFactory,
         buildFileParser,
         sourceFileParser,
         styleDetector,
         springRewriteProperties,
         parsingEventListener,
         eventPublisher,
         scanScope,
         beanFactory,
         projectScanner,
         executionContext,
         mavenProjectAnalyzer
      );
   }

   @Bean
   @ConditionalOnMissingBean({MavenPomCache.class})
   MavenPomCache mavenPomCache(SpringRewriteProperties springRewriteProperties) {
      MavenPomCache mavenPomCache = new InMemoryMavenPomCache();
      if (springRewriteProperties.isPomCacheEnabled()) {
         if (!"64".equals(System.getProperty("sun.arch.data.model", "64"))) {
            LOGGER.warn(
               "parser.isPomCacheEnabled was set to true but RocksdbMavenPomCache is not supported on 32-bit JVM. falling back to InMemoryMavenPomCache"
            );
         } else {
            try {
               mavenPomCache = new CompositeMavenPomCache(
                  new InMemoryMavenPomCache(), new RocksdbMavenPomCache(Path.of(springRewriteProperties.getPomCacheDirectory()))
               );
            } catch (Exception var6) {
               LOGGER.warn("Unable to initialize RocksdbMavenPomCache, falling back to InMemoryMavenPomCache");
               if (LOGGER.isDebugEnabled()) {
                  StringWriter sw = new StringWriter();
                  var6.printStackTrace(new PrintWriter(sw));
                  String exceptionAsString = sw.toString();
                  LOGGER.debug(exceptionAsString);
               }
            }
         }
      }

      return mavenPomCache;
   }
}
