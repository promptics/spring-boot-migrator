package org.springframework.rewrite.parser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.marker.Marker;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.xml.tree.Xml.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parser.maven.MavenModuleParser;
import org.springframework.rewrite.parser.maven.MavenProject;

public class SourceFileParser {
   private static final Logger LOGGER = LoggerFactory.getLogger(RewriteParserConfiguration.class);
   private final MavenModuleParser moduleParser;

   public SourceFileParser(MavenModuleParser moduleParser) {
      this.moduleParser = moduleParser;
   }

   public List<SourceFile> parseOtherSourceFiles(
      Path baseDir,
      ParserContext parserContext,
      List<Resource> resources,
      Map<Path, List<Marker>> provenanceMarkers,
      List<NamedStyles> styles,
      ExecutionContext executionContext
   ) {
      Set<SourceFile> parsedSourceFiles = new LinkedHashSet<>();
      Map<MavenProject, ModuleParsingResult> parsingResultsMap = new HashMap<>();
      parserContext.getSortedProjects()
         .forEach(
            currentMavenProject -> {
               Document moduleBuildFile = currentMavenProject.getSourceFile();
               List<Marker> markers = provenanceMarkers.get(currentMavenProject.getPomFilePath());
               if (markers == null || markers.isEmpty()) {
                  LOGGER.warn("Could not find provenance markers for resource '%s'".formatted(parserContext.getMatchingBuildFileResource(currentMavenProject)));
               }

               ModuleParsingResult result = this.moduleParser
                  .parseModule(baseDir, resources, currentMavenProject, moduleBuildFile, markers, styles, executionContext, parsingResultsMap);
               parsingResultsMap.put(currentMavenProject, result);
               Set<Path> classpath = new HashSet<>();
               Map<MavenProject, Set<Path>> modelClasspathMap = new HashMap<>();
               currentMavenProject.getDependentProjects().forEach(m -> {
                  Set<Path> dependencyPaths = modelClasspathMap.get(m);
                  classpath.addAll(dependencyPaths);
               });
               parsedSourceFiles.addAll(result.sourceFiles());
            }
         );
      return new ArrayList<>(parsedSourceFiles);
   }
}
