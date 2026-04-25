package org.springframework.rewrite.parser.maven;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.FileAttributes;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.Parser.Input;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaParser.Builder;
import org.openrewrite.java.internal.JavaTypeCache;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.J.CompilationUnit;
import org.openrewrite.java.tree.JavaType.FullyQualified;
import org.openrewrite.marker.Generated;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.tree.ParsingExecutionContextView;
import org.openrewrite.xml.tree.Xml.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parser.ModuleParsingResult;
import org.springframework.rewrite.parser.RewriteResourceParser;
import org.springframework.rewrite.parser.SourceSetParsingResult;
import org.springframework.rewrite.parser.SpringRewriteProperties;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;
import org.springframework.rewrite.utils.ResourceUtil;

public class MavenModuleParser {
   private static final Logger LOGGER = LoggerFactory.getLogger(MavenModuleParser.class);
   private final SpringRewriteProperties springRewriteProperties;

   public MavenModuleParser(SpringRewriteProperties springRewriteProperties) {
      this.springRewriteProperties = springRewriteProperties;
   }

   public ModuleParsingResult parseModule(
      Path baseDir,
      List<Resource> resources,
      MavenProject currentProject,
      Document moduleBuildFile,
      List<Marker> provenanceMarkers,
      List<NamedStyles> styles,
      ExecutionContext executionContext,
      Map<MavenProject, ModuleParsingResult> parsingResultsMap
   ) {
      List<SourceFile> sourceFiles = new ArrayList<>();
      Object mavenSourceEncoding = currentProject.getProjectEncoding();
      if (mavenSourceEncoding != null) {
         ParsingExecutionContextView.view(executionContext).setCharset(Charset.forName(mavenSourceEncoding.toString()));
      }

      boolean logCompilationWarningsAndErrors = this.springRewriteProperties.isLogCompilationWarningsAndErrors();
      Builder<? extends JavaParser, ?> javaParserBuilder = JavaParser.fromJavaVersion()
         .styles(styles)
         .logCompilationWarningsAndErrors(logCompilationWarningsAndErrors);
      Path buildFilePath = currentProject.getBasedir().resolve(moduleBuildFile.getSourcePath());
      LOGGER.info("Parsing module " + buildFilePath);
      Set<Path> skipResourceScanDirs = this.pathsToOtherMavenProjects(currentProject, buildFilePath);
      RewriteResourceParser rp = new RewriteResourceParser(
         baseDir,
         this.springRewriteProperties.getIgnoredPathPatterns(),
         this.springRewriteProperties.getPlainTextMasks(),
         this.springRewriteProperties.getSizeThresholdMb(),
         skipResourceScanDirs,
         javaParserBuilder.clone(),
         executionContext
      );
      Set<Path> alreadyParsed = new HashSet<>();
      Path moduleBuildFilePath = baseDir.resolve(moduleBuildFile.getSourcePath());
      alreadyParsed.add(moduleBuildFilePath);
      alreadyParsed.addAll(skipResourceScanDirs);
      SourceSetParsingResult mainSourcesParsingResult = this.parseMainSourceSet(
         baseDir, currentProject, javaParserBuilder, parsingResultsMap, executionContext, alreadyParsed, provenanceMarkers, resources, rp
      );
      SourceSetParsingResult testSourcesParsingResult = this.parseTestSourceSet(
         baseDir,
         currentProject,
         javaParserBuilder,
         parsingResultsMap,
         executionContext,
         alreadyParsed,
         provenanceMarkers,
         resources,
         rp,
         mainSourcesParsingResult
      );
      Stream<SourceFile> parsedResourceFiles = rp.parse(moduleBuildFilePath.getParent(), resources, alreadyParsed)
         .map(this.addProvenance(baseDir, provenanceMarkers, null));
      List<SourceFile> mainAndTestSources = this.mergeAndFilterExcluded(
         baseDir, this.springRewriteProperties.getIgnoredPathPatterns(), mainSourcesParsingResult.sourceFiles(), testSourcesParsingResult.sourceFiles()
      );
      List<SourceFile> resourceFilesList = parsedResourceFiles.toList();
      sourceFiles.addAll(mainAndTestSources);
      sourceFiles.addAll(resourceFilesList);
      return new ModuleParsingResult(currentProject, mainSourcesParsingResult, testSourcesParsingResult, resourceFilesList);
   }

   SourceSetParsingResult parseMainSourceSet(
      @Nullable Path baseDir,
      MavenProject currentProject,
      Builder<? extends JavaParser, ?> javaParserBuilder,
      Map<MavenProject, ModuleParsingResult> parsingResultsMap,
      ExecutionContext executionContext,
      Set<Path> alreadyParsed,
      List<Marker> provenanceMarkers,
      List<Resource> resources,
      RewriteResourceParser rp
   ) {
      List<Resource> javaSourcesInSrc = currentProject.getMainJavaSources();
      List<Path> classpathJars = currentProject.getCompileClasspathElements();
      LOGGER.debug("Dependencies on main classpath: %s".formatted(classpathJars));
      javaParserBuilder.classpath(classpathJars);
      List<SourceFile> sourceFilesFromOtherModules = currentProject.getDependencyProjects()
         .stream()
         .map(project -> parsingResultsMap.get(project))
         .flatMap(result -> result.mainSourcesParsingResult().sourceFiles().stream())
         .toList();
      String[] dependsOnSources = sourceFilesFromOtherModules.stream().map(SourceFile::printAll).toArray(String[]::new);
      javaParserBuilder.dependsOn(dependsOnSources);
      JavaTypeCache typeCache = getJavaTypeCache(currentProject, parsingResultsMap, sourceFilesFromOtherModules);
      javaParserBuilder.typeCache(typeCache);
      Set<FullyQualified> sourceSetClassesCp = new HashSet<>();
      sourceFilesFromOtherModules.stream()
         .filter(CompilationUnit.class::isInstance)
         .map(CompilationUnit.class::cast)
         .flatMap(s -> s.getClasses().stream())
         .map(ClassDeclaration::getType)
         .forEach(sourceSetClassesCp::add);
      return this.parseSourceSet(
         baseDir,
         currentProject,
         javaSourcesInSrc,
         javaParserBuilder,
         sourceSetClassesCp,
         executionContext,
         alreadyParsed,
         classpathJars,
         typeCache,
         provenanceMarkers,
         "main",
         resources,
         rp,
         "src/main"
      );
   }

   SourceSetParsingResult parseTestSourceSet(
      @Nullable Path baseDir,
      MavenProject currentProject,
      Builder<? extends JavaParser, ?> javaParserBuilder,
      Map<MavenProject, ModuleParsingResult> parsingResultsMap,
      ExecutionContext executionContext,
      Set<Path> alreadyParsed,
      List<Marker> provenanceMarkers,
      List<Resource> resources,
      RewriteResourceParser rp,
      SourceSetParsingResult mainSourcesParsingResult
   ) {
      List<Resource> javaSourcesInSrc = currentProject.getTestJavaSources();
      List<Path> classpathJars = currentProject.getTestClasspathElements();
      LOGGER.debug("Dependencies on main classpath: %s".formatted(classpathJars));
      javaParserBuilder.classpath(classpathJars);
      List<SourceFile> sourceFilesFromOtherModules = currentProject.getDependencyProjects()
         .stream()
         .map(project -> parsingResultsMap.get(project))
         .flatMap(result -> Stream.concat(result.mainSourcesParsingResult().sourceFiles().stream(), result.testSourcesParsingResult().sourceFiles().stream()))
         .toList();
      List<SourceFile> sourceFilesFromMain = mainSourcesParsingResult.sourceFiles();
      String[] dependsOnSources = Stream.concat(sourceFilesFromMain.stream(), sourceFilesFromOtherModules.stream())
         .map(SourceFile::printAll)
         .toArray(String[]::new);
      javaParserBuilder.dependsOn(dependsOnSources);
      JavaTypeCache typeCache = getJavaTypeCache(currentProject, parsingResultsMap, sourceFilesFromOtherModules);
      javaParserBuilder.typeCache(typeCache);
      Set<FullyQualified> sourceSetClassesCp = new HashSet<>();
      Stream.concat(sourceFilesFromMain.stream(), sourceFilesFromOtherModules.stream())
         .filter(CompilationUnit.class::isInstance)
         .map(CompilationUnit.class::cast)
         .flatMap(s -> s.getClasses().stream())
         .map(ClassDeclaration::getType)
         .forEach(sourceSetClassesCp::add);
      return this.parseSourceSet(
         baseDir,
         currentProject,
         javaSourcesInSrc,
         javaParserBuilder,
         sourceSetClassesCp,
         executionContext,
         alreadyParsed,
         classpathJars,
         typeCache,
         provenanceMarkers,
         "test",
         resources,
         rp,
         "src/test"
      );
   }

   SourceSetParsingResult parseSourceSet(
      @Nullable Path baseDir,
      MavenProject currentProject,
      List<Resource> javaSourcesInSrc,
      Builder<? extends JavaParser, ?> javaParserBuilder,
      Set<FullyQualified> localClassesCp,
      ExecutionContext executionContext,
      Set<Path> alreadyParsed,
      List<Path> classpathJars,
      JavaTypeCache typeCache,
      List<Marker> provenanceMarkers,
      String sourceSetName,
      List<Resource> resources,
      RewriteResourceParser rp,
      String sourceDir
   ) {
      List<Resource> javaSources = new ArrayList<>();
      List<Resource> javaSourcesInTarget = currentProject.getJavaSourcesInTarget();
      javaSources.addAll(javaSourcesInTarget);
      javaSources.addAll(javaSourcesInSrc);
      Iterable<Input> inputs = javaSources.stream().map(r -> {
         FileAttributes fileAttributes = null;
         Path path = ResourceUtil.getPath(r);
         boolean isSynthetic = Files.exists(path);
         Supplier<InputStream> inputStreamSupplier = () -> ResourceUtil.getInputStream(r);
         return new Input(path, fileAttributes, inputStreamSupplier, isSynthetic);
      }).toList();
      List<? extends SourceFile> cus = javaParserBuilder.build().parseInputs(inputs, baseDir, executionContext).peek(s -> {
         ((CompilationUnit)s).getClasses().stream().map(ClassDeclaration::getType).forEach(localClassesCp::add);
         alreadyParsed.add(baseDir.resolve(s.getSourcePath()));
      }).toList();
      JavaSourceSet javaSourceSet = sourceSet(sourceSetName, classpathJars, typeCache);
      List<Marker> markers = new ArrayList<>(provenanceMarkers);
      javaSourceSet = appendToClasspath(localClassesCp, javaSourceSet);
      ClasspathDependencies classpathDependencies = new ClasspathDependencies(classpathJars);
      markers.add(javaSourceSet);
      markers.add(classpathDependencies);
      List<Path> parsedJavaPaths = javaSourcesInTarget.stream().<Path>map(ResourceUtil::getPath).toList();
      Stream<SourceFile> parsedJavaSources = cus.stream().map(this.addProvenance(baseDir, markers, parsedJavaPaths));
      LOGGER.debug("[%s] Scanned %d java source files in main scope.".formatted(currentProject, javaSources.size()));
      Path buildDirectory = LinuxWindowsPathUnifier.unifiedPath(Paths.get(currentProject.getBuildDirectory()));
      List<SourceFile> filteredJavaSources = filterOutResourcesInDir(parsedJavaSources, buildDirectory);
      int sourcesParsedBefore = alreadyParsed.size();
      alreadyParsed.addAll(parsedJavaPaths);
      List<Resource> resourcesLeft = resources.stream()
         .filter(r -> alreadyParsed.stream().noneMatch(path -> LinuxWindowsPathUnifier.pathStartsWith(r, path)))
         .toList();
      LOGGER.info("Parsing test resources");
      Path searchDir = currentProject.getModulePath().resolve(sourceDir).resolve("resources");
      List<SourceFile> parsedResourceFiles = rp.parseSourceFiles(searchDir, resourcesLeft, alreadyParsed, executionContext)
         .map(this.addProvenance(baseDir, markers, null))
         .toList();
      LOGGER.info("Parsed %d main resources".formatted(parsedResourceFiles.size()));
      LOGGER.debug("[%s] Scanned %d resource files in main scope.".formatted(currentProject, alreadyParsed.size() - sourcesParsedBefore));
      filteredJavaSources.addAll(parsedResourceFiles);
      return new SourceSetParsingResult(filteredJavaSources, javaSourceSet.getClasspath(), typeCache);
   }

   public <T extends SourceFile> UnaryOperator<T> addProvenance(Path baseDir, List<Marker> provenance, @Nullable Collection<Path> generatedSources) {
      return s -> {
         Markers markers = s.getMarkers();

         for (Marker marker : provenance) {
            markers = markers.addIfAbsent(marker);
         }

         if (generatedSources != null && generatedSources.contains(baseDir.resolve(s.getSourcePath()))) {
            markers = markers.addIfAbsent(new Generated(Tree.randomId()));
         }

         return (T)s.withMarkers(markers);
      };
   }

   private List<SourceFile> mergeAndFilterExcluded(Path baseDir, Set<String> exclusions, List<SourceFile> mainSources, List<SourceFile> testSources) {
      List<PathMatcher> pathMatchers = exclusions.stream().map(pattern -> baseDir.getFileSystem().getPathMatcher("glob:" + pattern)).toList();
      return (List<SourceFile>)(pathMatchers.isEmpty()
         ? Stream.concat(mainSources.stream(), testSources.stream()).toList()
         : new ArrayList<>(Stream.concat(mainSources.stream(), testSources.stream()).filter(s -> isNotExcluded(baseDir, pathMatchers, s)).toList()));
   }

   private static boolean isNotExcluded(Path baseDir, List<PathMatcher> exclusions, SourceFile s) {
      return exclusions.stream().noneMatch(pm -> pm.matches(baseDir.resolve(s.getSourcePath()).toAbsolutePath().normalize()));
   }

   private Set<Path> pathsToOtherMavenProjects(MavenProject mavenProject, Path moduleBuildFile) {
      return mavenProject.getCollectedProjects()
         .stream()
         .filter(p -> !LinuxWindowsPathUnifier.pathEquals(p.getBuildFile().getPath(), moduleBuildFile))
         .map(p -> p.getFile().toPath().getParent())
         .collect(Collectors.toSet());
   }

   private static JavaTypeCache getJavaTypeCache(
      MavenProject currentProject, Map<MavenProject, ModuleParsingResult> parsingResultsMap, List<SourceFile> sourceFilesFromOtherModules
   ) {
      JavaTypeCache typeCache;
      if (!sourceFilesFromOtherModules.isEmpty()) {
         Optional<JavaTypeCache> optJavaTypeCache = currentProject.getDependencyProjects()
            .stream()
            .map(mp -> parsingResultsMap.get(mp).mainSourcesParsingResult().typeCache())
            // JavaTypeCache.size() was removed in OR 8.x; pick the first available type cache
            .findFirst();
         typeCache = optJavaTypeCache.orElseThrow(
            () -> new IllegalStateException("No TypeCahche from previous build found for project " + currentProject.getProjectId())
         );
      } else {
         typeCache = new JavaTypeCache();
      }

      return typeCache;
   }

   @NotNull
   private static ArrayList<SourceFile> filterOutResourcesInDir(Stream<SourceFile> parsedJava, Path buildDirectory) {
      return parsedJava.filter(s -> !s.getSourcePath().startsWith(buildDirectory)).collect(Collectors.toCollection(ArrayList::new));
   }

   @NotNull
   private static JavaSourceSet appendToClasspath(Set<FullyQualified> appendingClasspath, JavaSourceSet javaSourceSet) {
      List<FullyQualified> curCp = javaSourceSet.getClasspath();
      appendingClasspath.forEach(f -> {
         if (!curCp.contains(f)) {
            curCp.add(f);
         }
      });
      return javaSourceSet.withClasspath(new ArrayList<>(curCp));
   }

   @NotNull
   private static JavaSourceSet sourceSet(String name, List<Path> dependencies, JavaTypeCache typeCache) {
      return JavaSourceSet.build(name, dependencies, typeCache, false);
   }
}
