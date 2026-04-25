package org.springframework.rewrite;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.marker.Marker;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.tree.ParsingEventListener;
import org.openrewrite.tree.ParsingExecutionContextView;
import org.openrewrite.xml.tree.Xml.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parser.ParserContext;
import org.springframework.rewrite.parser.ProjectScanner;
import org.springframework.rewrite.parser.RewriteProjectParsingResult;
import org.springframework.rewrite.parser.SourceFileParser;
import org.springframework.rewrite.parser.SpringRewriteProperties;
import org.springframework.rewrite.parser.StyleDetector;
import org.springframework.rewrite.parser.events.StartedParsingProjectEvent;
import org.springframework.rewrite.parser.events.SuccessfullyParsedProjectEvent;
import org.springframework.rewrite.parser.maven.MavenBuildFileParser;
import org.springframework.rewrite.parser.maven.MavenProject;
import org.springframework.rewrite.parser.maven.MavenProjectAnalyzer;
import org.springframework.rewrite.parser.maven.ProvenanceMarkerFactory;
import org.springframework.rewrite.scopes.ScanScope;
import org.springframework.util.StringUtils;

public class RewriteProjectParser {
   private static final Logger LOGGER = LoggerFactory.getLogger(RewriteProjectParser.class);
   private final ProvenanceMarkerFactory provenanceMarkerFactory;
   private final MavenBuildFileParser buildFileParser;
   private final SourceFileParser sourceFileParser;
   private final StyleDetector styleDetector;
   private final SpringRewriteProperties springRewriteProperties;
   private final ParsingEventListener parsingEventListener;
   private final ApplicationEventPublisher eventPublisher;
   private final ScanScope scanScope;
   private final ConfigurableListableBeanFactory beanFactory;
   private final ProjectScanner scanner;
   private final ExecutionContext executionContext;
   private final MavenProjectAnalyzer mavenProjectAnalyzer;

   public RewriteProjectParser(
      ProvenanceMarkerFactory provenanceMarkerFactory,
      MavenBuildFileParser buildFileParser,
      SourceFileParser sourceFileParser,
      StyleDetector styleDetector,
      SpringRewriteProperties springRewriteProperties,
      ParsingEventListener parsingEventListener,
      ApplicationEventPublisher eventPublisher,
      ScanScope scanScope,
      ConfigurableListableBeanFactory beanFactory,
      ProjectScanner scanner,
      ExecutionContext executionContext,
      MavenProjectAnalyzer mavenProjectAnalyzer
   ) {
      this.provenanceMarkerFactory = provenanceMarkerFactory;
      this.buildFileParser = buildFileParser;
      this.sourceFileParser = sourceFileParser;
      this.styleDetector = styleDetector;
      this.springRewriteProperties = springRewriteProperties;
      this.parsingEventListener = parsingEventListener;
      this.eventPublisher = eventPublisher;
      this.scanScope = scanScope;
      this.beanFactory = beanFactory;
      this.scanner = scanner;
      this.executionContext = executionContext;
      this.mavenProjectAnalyzer = mavenProjectAnalyzer;
   }

   public RewriteProjectParsingResult parse(Path baseDir) {
      List<Resource> resources = this.scanner.scan(baseDir);
      return this.parse(baseDir, resources);
   }

   public RewriteProjectParsingResult parse(Path givenBaseDir, List<Resource> resources) {
      this.scanScope.clear(this.beanFactory);
      Path baseDir = normalizePath(givenBaseDir);
      this.eventPublisher.publishEvent(new StartedParsingProjectEvent(resources));
      ParsingExecutionContextView.view(this.executionContext).setParsingListener(this.parsingEventListener);
      List<NamedStyles> styles = List.of();
      List<MavenProject> sortedProjects = this.mavenProjectAnalyzer.getBuildProjects(baseDir, resources);
      ParserContext parserContext = new ParserContext(baseDir, resources, sortedProjects);
      Map<Path, List<Marker>> provenanceMarkers = this.provenanceMarkerFactory.generateProvenanceMarkers(baseDir, parserContext);
      List<Document> parsedBuildFiles = this.buildFileParser
         .parseBuildFiles(
            baseDir,
            parserContext.getBuildFileResources(),
            parserContext.getActiveProfiles(),
            this.executionContext,
            this.springRewriteProperties.isSkipMavenParsing(),
            provenanceMarkers
         );
      parserContext.setParsedBuildFiles(parsedBuildFiles);
      LOGGER.trace("Start to parse %d source files in %d modules".formatted(resources.size() + parsedBuildFiles.size(), parsedBuildFiles.size()));
      List<SourceFile> otherSourceFiles = this.sourceFileParser
         .parseOtherSourceFiles(baseDir, parserContext, resources, provenanceMarkers, styles, this.executionContext);
      List<Document> sortedBuildFileDocuments = parserContext.getSortedBuildFileDocuments();
      List<SourceFile> resultingList = new ArrayList<>();
      resultingList.addAll(sortedBuildFileDocuments);
      resultingList.addAll(otherSourceFiles);
      List<SourceFile> sourceFiles = this.styleDetector.sourcesWithAutoDetectedStyles(resultingList.stream());
      this.eventPublisher.publishEvent(new SuccessfullyParsedProjectEvent(sourceFiles));
      return new RewriteProjectParsingResult(sourceFiles, this.executionContext);
   }

   @NotNull
   private static Path normalizePath(Path givenBaseDir) {
      if (!givenBaseDir.isAbsolute()) {
         givenBaseDir = givenBaseDir.toAbsolutePath().normalize();
      }

      String cleanedPath = StringUtils.cleanPath(givenBaseDir.toString());
      return Path.of(cleanedPath);
   }
}
