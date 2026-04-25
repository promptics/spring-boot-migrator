package org.springframework.rewrite.resource.finder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.rewrite.resource.ProjectResource;
import org.springframework.rewrite.resource.ProjectResourceSet;
import org.springframework.rewrite.utils.OsAgnosticPathMatcher;
import org.springframework.util.PathMatcher;

public class PathPatternMatchingProjectResourceFinder implements ProjectResourceFinder<List<ProjectResource>> {
   private final List<String> matchingPatterns;
   private final PathMatcher matcher = new OsAgnosticPathMatcher();

   public PathPatternMatchingProjectResourceFinder(List<String> matchingPatterns) {
      this.validateMatchingPatterns(matchingPatterns);
      this.matchingPatterns = matchingPatterns;
   }

   public PathPatternMatchingProjectResourceFinder(String... matchingPatterns) {
      this(Arrays.asList(matchingPatterns));
   }

   private void validateMatchingPatterns(List<String> matchingPatterns) {
      for (String pattern : matchingPatterns) {
         if (!this.matcher.isPattern(pattern)) {
            throw new RuntimeException("The provided pattern '" + pattern + "' is invalid. Please check AntPathMatcher javadoc for examples of valid patterns.");
         }
      }
   }

   private boolean filterResources(ProjectResource projectResource) {
      return this.matchingPatterns.stream().anyMatch(pattern -> this.matcher.match(pattern, projectResource.getAbsolutePath().toString()));
   }

   public List<ProjectResource> apply(ProjectResourceSet projectResourceSet) {
      return projectResourceSet.stream().filter(this::filterResources).collect(Collectors.toList());
   }
}
