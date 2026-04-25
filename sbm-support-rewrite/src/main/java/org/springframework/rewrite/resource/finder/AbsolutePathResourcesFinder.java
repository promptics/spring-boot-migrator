package org.springframework.rewrite.resource.finder;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.openrewrite.SourceFile;
import org.springframework.rewrite.resource.ProjectResourceSet;
import org.springframework.rewrite.resource.RewriteSourceFileHolder;

public class AbsolutePathResourcesFinder implements ProjectResourceFinder<List<RewriteSourceFileHolder<? extends SourceFile>>> {
   private final Set<Path> absoluteResourcePaths;

   public AbsolutePathResourcesFinder(List<Path> absoluteResourcePaths) {
      this(new HashSet<>(absoluteResourcePaths));
   }

   public AbsolutePathResourcesFinder(Path... absoluteResourcePath) {
      this(Arrays.asList(absoluteResourcePath));
   }

   public AbsolutePathResourcesFinder(Path absoluteResourcePath) {
      this(Set.of(absoluteResourcePath));
   }

   public AbsolutePathResourcesFinder(Set<Path> absoluteResourcePaths) {
      String invalidPaths = absoluteResourcePaths.stream()
         .filter(absoluteResourcePath -> absoluteResourcePath == null || !absoluteResourcePath.isAbsolute())
         .map(p -> p == null ? "null" : p.toString())
         .collect(Collectors.joining("', '"));
      if (invalidPaths != null) {
         throw new IllegalArgumentException("Given paths '" + invalidPaths + "' were not absolute");
      } else {
         this.absoluteResourcePaths = absoluteResourcePaths.stream().map(Path::normalize).collect(Collectors.toSet());
      }
   }

   public List<RewriteSourceFileHolder<? extends SourceFile>> apply(ProjectResourceSet projectResourceSet) {
      return projectResourceSet.stream().filter(r -> this.absoluteResourcePaths.contains(r.getAbsolutePath())).collect(Collectors.toList());
   }
}
