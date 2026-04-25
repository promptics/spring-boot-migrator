package org.springframework.rewrite.resource.finder;

import java.nio.file.Path;
import java.util.Optional;
import org.openrewrite.SourceFile;
import org.springframework.rewrite.resource.ProjectResourceSet;
import org.springframework.rewrite.resource.RewriteSourceFileHolder;

public class AbsolutePathResourceFinder implements ProjectResourceFinder<Optional<RewriteSourceFileHolder<? extends SourceFile>>> {
   private final Path absoluteResourcePath;

   public AbsolutePathResourceFinder(Path absoluteResourcePath) {
      this.absoluteResourcePath = absoluteResourcePath;
   }

   public Optional<RewriteSourceFileHolder<? extends SourceFile>> apply(ProjectResourceSet projectResourceSet) {
      if (this.absoluteResourcePath != null && this.absoluteResourcePath.isAbsolute()) {
         Path searchForPath = this.absoluteResourcePath.normalize();
         return projectResourceSet.stream().filter(r -> searchForPath.equals(r.getAbsolutePath())).findFirst();
      } else {
         throw new IllegalArgumentException("Given path '" + this.absoluteResourcePath + "' is not absolute");
      }
   }
}
