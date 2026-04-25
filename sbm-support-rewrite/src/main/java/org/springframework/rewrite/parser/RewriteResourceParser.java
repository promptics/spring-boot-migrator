package org.springframework.rewrite.parser;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Parser.Input;
import org.openrewrite.hcl.HclParser;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaParser.Builder;
import org.openrewrite.json.JsonParser;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.protobuf.ProtoParser;
import org.openrewrite.quark.QuarkParser;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.yaml.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.utils.ResourceUtil;

public class RewriteResourceParser {
   private static final Logger LOGGER = LoggerFactory.getLogger(RewriteResourceParser.class);
   private static final Set<String> DEFAULT_IGNORED_DIRECTORIES = new HashSet<>(
      Arrays.asList("build", "target", "out", ".sonar", ".gradle", ".idea", ".project", "node_modules", ".git", ".metadata", ".DS_Store")
   );
   private final Path baseDir;
   private final Collection<PathMatcher> exclusions;
   private final int sizeThresholdMb;
   private final Collection<Path> excludedDirectories;
   private final Collection<PathMatcher> plainTextMasks;
   private final Builder<? extends JavaParser, ?> javaParserBuilder;
   private final ExecutionContext executionContext;

   public RewriteResourceParser(
      Path baseDir,
      Collection<String> exclusions,
      Collection<String> plainTextMasks,
      int sizeThresholdMb,
      Collection<Path> excludedDirectories,
      Builder<? extends JavaParser, ?> javaParserBuilder,
      ExecutionContext executionContext
   ) {
      this.baseDir = baseDir;
      this.javaParserBuilder = javaParserBuilder;
      this.executionContext = executionContext;
      this.exclusions = this.pathMatchers(baseDir, exclusions);
      this.sizeThresholdMb = sizeThresholdMb;
      this.excludedDirectories = excludedDirectories;
      this.plainTextMasks = this.pathMatchers(baseDir, plainTextMasks);
   }

   private Collection<PathMatcher> pathMatchers(Path basePath, Collection<String> pathExpressions) {
      return pathExpressions.stream().map(o -> basePath.getFileSystem().getPathMatcher("glob:" + o)).collect(Collectors.toList());
   }

   public Stream<SourceFile> parse(Path searchDir, List<Resource> resources, Set<Path> alreadyParsed) {
      List<Resource> resourcesLeft = resources.stream()
         .filter(r -> alreadyParsed.stream().noneMatch(path -> ResourceUtil.getPath(r).toString().startsWith(path.toString())))
         .toList();
      return this.parseSourceFiles(searchDir, resourcesLeft, alreadyParsed, this.executionContext);
   }

   public Stream<SourceFile> parseSourceFiles(Path searchDir, List<Resource> resources, Set<Path> alreadyParsed, ExecutionContext ctx) {
      List<Path> resourcesLeft = new ArrayList<>();
      List<Path> quarkPaths = new ArrayList<>();
      List<Path> plainTextPaths = new ArrayList<>();
      List<Resource> filteredResources = resources.stream().filter(r -> ResourceUtil.getPath(r).toString().startsWith(searchDir.toString())).toList();
      filteredResources.forEach(
         resource -> {
            Path file = ResourceUtil.getPath(resource);
            Path dir = file.getParent();
            if (!this.isExcluded(dir)
               && !this.isIgnoredDirectory(searchDir, dir)
               && !this.excludedDirectories.contains(dir)
               && !alreadyParsed.contains(new FileSystemResource(dir))
               && !alreadyParsed.contains(resource)) {
               long fileSize = ResourceUtil.contentLength(resource);
               if (this.isOverSizeThreshold(fileSize)) {
                  LOGGER.info("Parsing as quark " + file + " as its size " + fileSize / 1048576L + "Mb exceeds size threshold " + this.sizeThresholdMb + "Mb");
                  quarkPaths.add(file);
               } else if (this.isParsedAsPlainText(file)) {
                  plainTextPaths.add(file);
               } else {
                  resourcesLeft.add(file);
               }
            }
         }
      );
      Stream<SourceFile> sourceFiles = Stream.empty();
      JavaParser javaParser = this.javaParserBuilder.build();
      List<Path> javaPaths = new ArrayList<>();
      JsonParser jsonParser = new JsonParser();
      List<Path> jsonPaths = new ArrayList<>();
      XmlParser xmlParser = new XmlParser();
      List<Path> xmlPaths = new ArrayList<>();
      YamlParser yamlParser = new YamlParser();
      List<Path> yamlPaths = new ArrayList<>();
      PropertiesParser propertiesParser = new PropertiesParser();
      List<Path> propertiesPaths = new ArrayList<>();
      ProtoParser protoParser = new ProtoParser();
      List<Path> protoPaths = new ArrayList<>();
      HclParser hclParser = HclParser.builder().build();
      List<Path> hclPaths = new ArrayList<>();
      PlainTextParser plainTextParser = new PlainTextParser();
      QuarkParser quarkParser = new QuarkParser();
      resourcesLeft.forEach(path -> {
         if (javaParser.accept(path) && !path.toString().endsWith(".qute.java")) {
            javaPaths.add(path);
         }

         if (jsonParser.accept(path)) {
            jsonPaths.add(path);
         } else if (xmlParser.accept(path)) {
            xmlPaths.add(path);
         } else if (yamlParser.accept(path)) {
            yamlPaths.add(path);
         } else if (propertiesParser.accept(path)) {
            propertiesPaths.add(path);
         } else if (protoParser.accept(path)) {
            protoPaths.add(path);
         } else if (hclParser.accept(path)) {
            hclPaths.add(path);
         } else if (quarkParser.accept(path)) {
            quarkPaths.add(path);
         }
      });
      Map<Path, Resource> pathToResource = filteredResources.stream().collect(Collectors.toMap(r -> ResourceUtil.getPath(r), r -> (Resource)r));
      if (!javaPaths.isEmpty()) {
         List<Input> inputs = getInputs(pathToResource, javaPaths);
         sourceFiles = Stream.concat(sourceFiles, (Stream<SourceFile>)(Stream<?>) javaParser.parseInputs(inputs, this.baseDir, ctx));
         alreadyParsed.addAll(javaPaths);
      }

      if (!jsonPaths.isEmpty()) {
         List<Input> inputs = getInputs(pathToResource, jsonPaths);
         sourceFiles = Stream.concat(sourceFiles, (Stream<SourceFile>)(Stream<?>) jsonParser.parseInputs(inputs, this.baseDir, ctx));
         alreadyParsed.addAll(jsonPaths);
      }

      if (!xmlPaths.isEmpty()) {
         List<Input> inputs = getInputs(pathToResource, xmlPaths);
         sourceFiles = Stream.concat(sourceFiles, (Stream<SourceFile>)(Stream<?>) xmlParser.parseInputs(inputs, this.baseDir, ctx));
         alreadyParsed.addAll(xmlPaths);
      }

      if (!yamlPaths.isEmpty()) {
         List<Input> inputs = getInputs(pathToResource, yamlPaths);
         sourceFiles = Stream.concat(sourceFiles, (Stream<SourceFile>)(Stream<?>) yamlParser.parseInputs(inputs, this.baseDir, ctx));
         alreadyParsed.addAll(yamlPaths);
      }

      if (!propertiesPaths.isEmpty()) {
         List<Input> inputs = getInputs(pathToResource, propertiesPaths);
         sourceFiles = Stream.concat(sourceFiles, (Stream<SourceFile>)(Stream<?>) propertiesParser.parseInputs(inputs, this.baseDir, ctx));
         alreadyParsed.addAll(propertiesPaths);
      }

      if (!protoPaths.isEmpty()) {
         List<Input> inputs = getInputs(pathToResource, protoPaths);
         sourceFiles = Stream.concat(sourceFiles, (Stream<SourceFile>)(Stream<?>) protoParser.parseInputs(inputs, this.baseDir, ctx));
         alreadyParsed.addAll(protoPaths);
      }

      if (!hclPaths.isEmpty()) {
         List<Input> inputs = getInputs(pathToResource, hclPaths);
         sourceFiles = Stream.concat(sourceFiles, (Stream<SourceFile>)(Stream<?>) hclParser.parseInputs(inputs, this.baseDir, ctx));
         alreadyParsed.addAll(hclPaths);
      }

      if (!plainTextPaths.isEmpty()) {
         List<Input> inputs = getInputs(pathToResource, plainTextPaths);
         sourceFiles = Stream.concat(sourceFiles, (Stream<SourceFile>)(Stream<?>) plainTextParser.parseInputs(inputs, this.baseDir, ctx));
         alreadyParsed.addAll(plainTextPaths);
      }

      if (!quarkPaths.isEmpty()) {
         List<Input> inputs = getInputs(pathToResource, quarkPaths);
         sourceFiles = Stream.concat(sourceFiles, (Stream<SourceFile>)(Stream<?>) quarkParser.parseInputs(inputs, this.baseDir, ctx));
         alreadyParsed.addAll(quarkPaths);
      }

      return sourceFiles;
   }

   @NotNull
   private static List<Input> getInputs(Map<Path, Resource> pathResourceMap, List<Path> paths) {
      return paths.stream().map(path -> new Input(path, () -> ResourceUtil.getInputStream(pathResourceMap.get(path)))).toList();
   }

   private boolean isOverSizeThreshold(long fileSize) {
      return this.sizeThresholdMb > 0 && fileSize > (long)this.sizeThresholdMb * 1024L * 1024L;
   }

   private boolean isExcluded(Path path) {
      if (!this.exclusions.isEmpty()) {
         for (PathMatcher excluded : this.exclusions) {
            if (excluded.matches(this.baseDir.relativize(path))) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isParsedAsPlainText(Path path) {
      if (!this.plainTextMasks.isEmpty()) {
         Path computed = this.baseDir.relativize(path);
         if (!computed.startsWith("/")) {
            computed = Paths.get("/").resolve(computed);
         }

         for (PathMatcher matcher : this.plainTextMasks) {
            if (matcher.matches(computed)) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isIgnoredDirectory(Path searchDir, Path path) {
      for (Path pathSegment : searchDir.relativize(path)) {
         if (DEFAULT_IGNORED_DIRECTORIES.contains(pathSegment.toString())) {
            return true;
         }
      }

      return false;
   }
}
