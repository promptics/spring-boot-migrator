package org.springframework.rewrite.parser.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Parser.Input;
import org.openrewrite.marker.Marker;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.maven.MavenParser.Builder;
import org.openrewrite.xml.tree.Xml.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;
import org.springframework.rewrite.utils.ResourceUtil;
import org.springframework.util.Assert;

public class MavenBuildFileParser {
   private static final Logger LOGGER = LoggerFactory.getLogger(MavenBuildFileParser.class);
   private final MavenSettingsInitializer mavenSettingsInitilizer;

   public MavenBuildFileParser(MavenSettingsInitializer mavenSettingsInitilizer) {
      this.mavenSettingsInitilizer = mavenSettingsInitilizer;
   }

   public List<Document> parseBuildFiles(
      Path baseDir,
      List<Resource> buildFiles,
      List<String> activeProfiles,
      ExecutionContext executionContext,
      boolean skipMavenParsing,
      Map<Path, List<Marker>> provenanceMarkers
   ) {
      Assert.notNull(baseDir, "Base directory must be provided but was null.");
      Assert.notEmpty(buildFiles, "No build files provided.");
      List<Resource> nonPomFiles = retrieveNonPomFiles(buildFiles);
      Assert.isTrue(
         nonPomFiles.isEmpty(),
         "Provided resources which are not Maven build files: '%s'".formatted(nonPomFiles.stream().map(r -> ResourceUtil.getPath(r).toAbsolutePath()).toList())
      );
      List<Resource> resourcesWithoutProvenanceMarker = this.findResourcesWithoutProvenanceMarker(baseDir, buildFiles, provenanceMarkers);
      Assert.isTrue(
         resourcesWithoutProvenanceMarker.isEmpty(),
         "No provenance marker provided for these pom files %s"
            .formatted(resourcesWithoutProvenanceMarker.stream().map(r -> ResourceUtil.getPath(r).toAbsolutePath()).toList())
      );
      if (skipMavenParsing) {
         LOGGER.info("Maven parsing skipped [parser.skipMavenParsing=true].");
         return List.of();
      } else {
         // mavenConfig(Path) was removed in OR 8.47; .mvn/maven.config is picked up automatically now
         Builder mavenParserBuilder = MavenParser.builder();
         this.mavenSettingsInitilizer.initializeMavenSettings();
         mavenParserBuilder.activeProfiles(activeProfiles.toArray(new String[0]));
         return this.parsePoms(baseDir, buildFiles, mavenParserBuilder, executionContext)
            .map(pp -> this.markPomFile(pp, provenanceMarkers.getOrDefault(baseDir.resolve(pp.getSourcePath()), Collections.emptyList())))
            .toList();
      }
   }

   private List<Resource> findResourcesWithoutProvenanceMarker(Path baseDir, List<Resource> buildFileResources, Map<Path, List<Marker>> provenanceMarkers) {
      return buildFileResources.stream().filter(r -> !provenanceMarkers.containsKey(ResourceUtil.getPath(r))).toList();
   }

   @NotNull
   private static Path getUnifiedPath(Path baseDir, Resource r) {
      return LinuxWindowsPathUnifier.unifiedPath(baseDir.resolve(ResourceUtil.getPath(r)));
   }

   private static List<Resource> retrieveNonPomFiles(List<Resource> buildFileResources) {
      return buildFileResources.stream().filter(r -> !"pom.xml".equals(ResourceUtil.getPath(r).getFileName().toString())).toList();
   }

   private Document markPomFile(Document pp, List<Marker> markers) {
      for (Marker marker : markers) {
         pp = pp.withMarkers(pp.getMarkers().addIfAbsent(marker));
      }

      return pp;
   }

   private Map<Path, Document> createResult(Path basePath, List<Resource> pomFiles, List<SourceFile> parsedPoms) {
      return parsedPoms.stream()
         .map(pom -> this.mapResourceToDocument(basePath, pom, pomFiles))
         .collect(Collectors.toMap(e -> ResourceUtil.getPath(e.getKey()), e -> e.getValue()));
   }

   private Entry<Resource, Document> mapResourceToDocument(Path basePath, SourceFile pom, List<Resource> parsedPoms) {
      Document doc = (Document)pom;
      Resource resource = parsedPoms.stream()
         .filter(p -> ResourceUtil.getPath(p).toString().equals(basePath.resolve(pom.getSourcePath()).toAbsolutePath().normalize().toString()))
         .findFirst()
         .orElseThrow(
            () -> new IllegalStateException(
                  "Could not find matching path for Xml.Document '%s'".formatted(pom.getSourcePath().toAbsolutePath().normalize().toString())
               )
         );
      return Map.entry(resource, doc);
   }

   private Stream<Document> parsePoms(Path baseDir, List<Resource> pomFiles, Builder mavenParserBuilder, ExecutionContext executionContext) {
      Iterable<Input> pomFileInputs = pomFiles.stream().map(p -> new Input(ResourceUtil.getPath(p), () -> ResourceUtil.getInputStream(p))).toList();
      return mavenParserBuilder.build().parseInputs(pomFileInputs, baseDir, executionContext).map(Document.class::cast);
   }

   public List<Resource> filterAndSortBuildFiles(List<Resource> resources) {
      return resources.stream()
         .filter(r -> "pom.xml".equals(ResourceUtil.getPath(r).toFile().getName()))
         .filter(r -> filterTestResources(r))
         .sorted((r1, r2) -> {
            Path r1Path = ResourceUtil.getPath(r1);
            ArrayList<String> r1PathParts = new ArrayList<>();
            r1Path.iterator().forEachRemaining(it -> r1PathParts.add(it.toString()));
            Path r2Path = ResourceUtil.getPath(r2);
            ArrayList<String> r2PathParts = new ArrayList<>();
            r2Path.iterator().forEachRemaining(it -> r2PathParts.add(it.toString()));
            return Integer.compare(r1PathParts.size(), r2PathParts.size());
         })
         .toList();
   }

   private static boolean filterTestResources(Resource r) {
      String path = ResourceUtil.getPath(r).toString();
      boolean underTest = path.contains(Path.of("src/test").toString());
      if (underTest) {
         LOGGER.info("Ignore build file '%s' having 'src/test' in its path indicating it's a build file for tests.".formatted(path));
      }

      return !underTest;
   }
}
