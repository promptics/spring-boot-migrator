package org.springframework.rewrite.resource.finder;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.rewrite.resource.ProjectResourceSet;

public class ModifiedResourcePathStringFilter implements ProjectResourceFinder<List<String>> {
   public List<String> apply(ProjectResourceSet projectResourceSet) {
      return projectResourceSet.stream()
         .filter(r -> r.hasChanges() && !r.isDeleted() && !r.getAbsolutePath().toFile().isDirectory())
         .map(r -> r.getAbsolutePath().toString())
         .collect(Collectors.toList());
   }
}
