package org.springframework.rewrite.parser;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.rewrite.utils.LinuxWindowsPathUnifier;
import org.springframework.rewrite.utils.ResourceUtil;

public class ProjectScanner {
   private static final Logger LOGGER = LoggerFactory.getLogger(ProjectScanner.class);
   private final ResourceLoader resourceLoader;
   private final SpringRewriteProperties springRewriteProperties;

   public ProjectScanner(ResourceLoader resourceLoader, SpringRewriteProperties springRewriteProperties) {
      this.resourceLoader = resourceLoader;
      this.springRewriteProperties = springRewriteProperties;
   }

   public List<Resource> scan(Path baseDir) {
      if (!baseDir.isAbsolute()) {
         baseDir = baseDir.toAbsolutePath().normalize();
      }

      if (!baseDir.toFile().exists()) {
         throw new IllegalArgumentException("Provided path does not exist: " + baseDir);
      } else {
         Path absoluteRootPath = baseDir;
         String pattern = "file:" + baseDir.toString() + "/**";

         try {
            Resource[] resources = ResourcePatternUtils.getResourcePatternResolver(this.resourceLoader).getResources(pattern);
            LOGGER.debug("Scanned %d resources in dir: '%s'".formatted(resources.length, absoluteRootPath.toString()));
            List<Resource> resultingResources = this.filterIgnoredResources(absoluteRootPath, resources);
            int numResulting = resultingResources.size();
            int numIgnored = resources.length - numResulting;
            LOGGER.debug("Scan returns %s resources, %d resources were ignored.".formatted(numResulting, numIgnored));
            LOGGER.trace(
               "Resources resulting from scan: %s"
                  .formatted(resultingResources.stream().map(getResourceStringFunction(absoluteRootPath)).collect(Collectors.joining(", ")))
            );
            return resultingResources;
         } catch (IOException var8) {
            throw new RuntimeException("Can't get resources for pattern '" + pattern + "'", var8);
         }
      }
   }

   @NotNull
   private static Function<Resource, String> getResourceStringFunction(Path absoluteRootPath) {
      return r -> LinuxWindowsPathUnifier.relativize(absoluteRootPath, ResourceUtil.getPath(r)).toString();
   }

   @NotNull
   private List<Resource> filterIgnoredResources(Path baseDir, Resource[] resources) {
      Set<String> effectivePathMatcherPatterns = new HashSet<>();
      List<PathMatcher> pathMatchers = this.springRewriteProperties
         .getIgnoredPathPatterns()
         .stream()
         .map(p -> (String)(p.startsWith("glob:") ? p : "glob:" + p))
         .peek(p -> effectivePathMatcherPatterns.add(p))
         .map(baseDir.getFileSystem()::getPathMatcher)
         .toList();
      LOGGER.trace("Ignore resources matching any of these PathMatchers: %s".formatted(effectivePathMatcherPatterns.stream().collect(Collectors.joining(", "))));
      List<Resource> resultingResources = Stream.of(resources).filter(r -> this.isAccepted(baseDir, r, pathMatchers)).toList();
      if (resultingResources.isEmpty()) {
         throw new IllegalArgumentException("No resources were scanned. Check directory and ignore patterns.");
      } else {
         return resultingResources;
      }
   }

   private boolean isAccepted(Path baseDir, Resource r, List<PathMatcher> pathMatchers) {
      if (ResourceUtil.getPath(r).toFile().isDirectory()) {
         return false;
      } else {
         Optional<PathMatcher> isIgnored = pathMatchers.stream().filter(matcher -> {
            Path resourcePath = ResourceUtil.getPath(r);
            return matcher.matches(resourcePath);
         }).findFirst();
         if (isIgnored.isPresent() && LOGGER.isInfoEnabled()) {
            Set<String> ignoredPathPatterns = this.springRewriteProperties.getIgnoredPathPatterns();
            LOGGER.info(
               "Ignoring scanned resource '%s' given these path matchers: %s.".formatted(baseDir.relativize(ResourceUtil.getPath(r)), ignoredPathPatterns)
            );
         }

         return isIgnored.isEmpty();
      }
   }
}
