package org.springframework.rewrite.resource.finder;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.rewrite.resource.ProjectResourceSet;

public class GenericTypeListFinder<T> implements ProjectResourceFinder<List<T>> {
   private final Class<T> type;

   public GenericTypeListFinder(Class<T> type) {
      this.type = type;
   }

   public Class<T> getType() {
      return this.type;
   }

   public List<T> apply(ProjectResourceSet projectResourceSet) {
      return projectResourceSet.stream().filter(pr -> this.type.isAssignableFrom(pr.getClass())).map(this.type::cast).collect(Collectors.toList());
   }
}
